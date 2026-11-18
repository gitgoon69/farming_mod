package dev.stook.client.mining;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.stook.client.compat.ClientScreens;
import dev.stook.client.config.ModConfig;
import dev.stook.client.garden.Crop;
import dev.stook.client.garden.GardenDetector;
import dev.stook.client.garden.SkyblockItems;
import dev.stook.client.garden.TabList;
import dev.stook.client.prices.CoflBazaarService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * End Stone profit. Coins follow loot that actually arrived (sack lines, otherwise
 * inventory while you are breaking the block), priced at NPC. The block pace is only
 * a stand-in until that loot shows up, and only for the block you are mining plus the
 * adjacent blocks Mining Spread breaks. Other players' End Stone is ignored.
 */
public final class EndstoneTracker {
	private static final long BLOCK_WINDOW_MS = 5_000L;
	private static final long FORTUNE_HOLD_MS = 8_000L;
	private static final long HUD_LINGER_MS = 120_000L;
	private static final long MINE_INTENT_MS = 800L;
	private static final long COUNT_DEDUPE_MS = 1_500L;
	private static final long WATCH_MS = 600L;
	private static final long SACK_CREDIT_MS = 2_500L;
	private static final int ENCHANTED_RATIO = 160;

	/**
	 * Sack hover or chat line: {@code +1,200 End Stone}, {@code +1,200x End Stone},
	 * {@code +1.2k Enchanted End Stone}, {@code +4 Mite Gel}.
	 */
	private static final Pattern LOOT_GAIN = Pattern.compile(
			"(?i)\\+\\s*([\\d.,]+)\\s*([kmb]?)\\s*[x×]?\\s*((?:enchanted\\s+)?end\\s*stone|mite\\s*gel)\\b"
	);

	private final Deque<Long> blockBreaks = new ArrayDeque<>();
	private final Map<BlockPos, Long> watched = new HashMap<>();
	private final Map<BlockPos, Long> recentlyCounted = new HashMap<>();

	private MiningFortune.Reading fortune = MiningFortune.Reading.NONE;
	private String actionBar = "";
	private long fortuneAt;
	private double sackCredit;
	private long sackCreditAt;
	private double gelCredit;
	private long gelCreditAt;
	private boolean holdingPickaxe;
	private long lastActivityMs;
	private long lastTickMs;
	private long activeMs;
	private boolean paused = true;
	private boolean sawSackStones;
	private double sessionEndstone;
	private double sessionEstimate;
	private double sessionGel;
	private long sessionBlocks;
	private long lastMineIntentMs;
	private double inventoryStones;
	private double inventoryGel;
	private boolean inventoryReady;
	private int sackMessages;
	private boolean tracking;

	public void tick(Minecraft client, ModConfig config) {
		long now = System.currentTimeMillis();
		prune(now);
		tracking = config.endstoneProfit;

		if (!tracking || client.player == null || client.level == null) {
			holdingPickaxe = false;
			watched.clear();
			pauseIfIdle(now, config);
			lastTickMs = now;
			return;
		}

		holdingPickaxe = miningTool(client.player.getMainHandItem());
		refreshFortune(client, now);
		boolean mining = activelyMining(client);
		boolean looking = lookingAtEndStone(client);
		boolean inEnd = GardenDetector.inTheEnd();
		if (holdingPickaxe && mining && (inEnd || looking)) {
			noteActivity(now);
		}
		readInventory(client, mining && looking && !ClientScreens.isOpen(client));
		watchBreaks(client, now, mining);

		if (!holdingPickaxe && !sessionLive(now)) {
			pauseIfIdle(now, config);
			lastTickMs = now;
			return;
		}

		if (!paused && lastActivityMs > 0) {
			long idle = now - lastActivityMs;
			if (idle > config.afkTimeoutSeconds * 1000L) {
				paused = true;
			} else if (lastTickMs > 0) {
				activeMs += Math.max(0, now - lastTickMs);
			}
		}

		lastTickMs = now;
	}

	public void onBlockBroken(BlockPos pos, Block block) {
		if (block != Blocks.END_STONE || pos == null) {
			return;
		}
		countBreak(pos, System.currentTimeMillis());
	}

	public void onActionBar(String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		actionBar = clean(raw);
	}

	/**
	 * Watches the End Stone you are aiming at and the blocks touching it.
	 * Mining Spread only breaks those neighbors, so other players nearby are not counted.
	 */
	private void watchBreaks(Minecraft client, long now, boolean mining) {
		if (client.player == null || client.level == null) {
			watched.clear();
			return;
		}
		if (mining) {
			BlockPos focus = aimedEndStone(client);
			if (focus != null) {
				watchAround(client, focus, now);
			}
		}
		collectWatched(client, now, mining);
		undoRestored(client, now);
	}

	private void watchAround(Minecraft client, BlockPos focus, long now) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					cursor.set(focus.getX() + dx, focus.getY() + dy, focus.getZ() + dz);
					if (!client.level.hasChunkAt(cursor)) {
						continue;
					}
					if (client.level.getBlockState(cursor).is(Blocks.END_STONE)) {
						watched.put(cursor.immutable(), now);
					}
				}
			}
		}
	}

	private void collectWatched(Minecraft client, long now, boolean mining) {
		Iterator<Map.Entry<BlockPos, Long>> it = watched.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Long> entry = it.next();
			if (now - entry.getValue() > WATCH_MS) {
				it.remove();
				continue;
			}
			if (!client.level.hasChunkAt(entry.getKey())) {
				continue;
			}
			var state = client.level.getBlockState(entry.getKey());
			if (state.is(Blocks.END_STONE)) {
				continue;
			}
			it.remove();
			if (mining && (state.isAir() || state.is(Blocks.BEDROCK))) {
				countBreak(entry.getKey(), now);
			}
		}
	}

	private BlockPos aimedEndStone(Minecraft client) {
		if (client.level == null || !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = hit.getBlockPos();
		if (!client.level.getBlockState(pos).is(Blocks.END_STONE)) {
			return null;
		}
		return pos;
	}

	private boolean countBreak(BlockPos pos, long now) {
		if (!tracking || pos == null) {
			return false;
		}
		BlockPos key = pos.immutable();
		Long previous = recentlyCounted.get(key);
		if (previous != null && now - previous < COUNT_DEDUPE_MS) {
			return false;
		}
		recentlyCounted.put(key, now);
		sessionBlocks++;
		blockBreaks.addLast(now);
		noteActivity(now);
		double drops = fortune.dropsPerBlock();
		sessionEstimate += drops > 0 ? drops : 1.0;
		return true;
	}

	private void undoRestored(Minecraft client, long now) {
		if (client.level == null || recentlyCounted.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<BlockPos, Long>> it = recentlyCounted.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Long> entry = it.next();
			if (now - entry.getValue() > 2_000L) {
				it.remove();
				continue;
			}
			if (!client.level.hasChunkAt(entry.getKey()) || !client.level.getBlockState(entry.getKey()).is(Blocks.END_STONE)) {
				continue;
			}
			it.remove();
			sessionBlocks = Math.max(0, sessionBlocks - 1);
			double drops = fortune.dropsPerBlock();
			sessionEstimate = Math.max(0, sessionEstimate - (drops > 0 ? drops : 1.0));
			while (!blockBreaks.isEmpty() && blockBreaks.peekLast() == entry.getValue()) {
				blockBreaks.removeLast();
				break;
			}
		}
	}

	private boolean activelyMining(Minecraft client) {
		boolean held = client.player != null && (client.options.keyAttack.isDown() || client.player.swinging);
		boolean breaking = client.gameMode != null && client.gameMode.isDestroying();
		if (held || breaking) {
			lastMineIntentMs = System.currentTimeMillis();
			return true;
		}
		return lastMineIntentMs > 0 && System.currentTimeMillis() - lastMineIntentMs <= MINE_INTENT_MS;
	}

	private boolean lookingAtEndStone(Minecraft client) {
		if (client.level == null || !(client.hitResult instanceof BlockHitResult hit)) {
			return false;
		}
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		return client.level.getBlockState(hit.getBlockPos()).is(Blocks.END_STONE);
	}

	public void onChat(Component message) {
		if (!tracking || message == null) {
			return;
		}
		String visible = clean(message.getString());
		String hover = hoverText(message);
		String lowerVisible = visible.toLowerCase(Locale.ROOT);
		String lowerHover = hover.toLowerCase(Locale.ROOT);
		boolean sackMessage = lowerVisible.contains("[sacks]")
				|| lowerHover.contains("sack")
				|| lowerHover.contains("added");
		if (!sackMessage) {
			return;
		}
		String source = lootText(hover) ? hover : visible;
		if (!applyLootText(source)) {
			return;
		}
		noteActivity(System.currentTimeMillis());
		sackMessages++;
	}

	private void readInventory(Minecraft client, boolean countGains) {
		if (client.player == null) {
			inventoryReady = false;
			return;
		}
		double stones = 0;
		double gel = 0;
		for (ItemStack stack : client.player.getInventory().getNonEquipmentItems()) {
			stones += stonesIn(stack);
			gel += gelIn(stack);
		}
		stones += stonesIn(client.player.getOffhandItem());
		gel += gelIn(client.player.getOffhandItem());
		if (!inventoryReady) {
			inventoryStones = stones;
			inventoryGel = gel;
			inventoryReady = true;
			return;
		}
		double stoneGain = stones - inventoryStones;
		double gelGain = gel - inventoryGel;
		inventoryStones = stones;
		inventoryGel = gel;
		if (!countGains) {
			return;
		}
		long now = System.currentTimeMillis();
		boolean gained = false;
		if (stoneGain > 0 && stoneGain < 20_000) {
			stoneGain = subtractCredit(stoneGain, now, true);
			if (stoneGain > 0) {
				addStones(stoneGain, false);
				gained = true;
			}
		}
		if (gelGain > 0 && gelGain < 2_000) {
			gelGain = subtractCredit(gelGain, now, false);
			if (gelGain > 0) {
				sessionGel += gelGain;
				gained = true;
			}
		}
		if (gained) {
			noteActivity(now);
		}
	}

	private double subtractCredit(double gain, long now, boolean stones) {
		long creditedAt = stones ? sackCreditAt : gelCreditAt;
		double credit = stones ? sackCredit : gelCredit;
		if (creditedAt <= 0 || now - creditedAt > SACK_CREDIT_MS || credit <= 0) {
			return gain;
		}
		double covered = Math.min(gain, credit);
		if (stones) {
			sackCredit -= covered;
		} else {
			gelCredit -= covered;
		}
		return gain - covered;
	}

	public boolean hudVisible(Minecraft client, ModConfig config) {
		if (!config.hudEnabled || !config.endstoneProfit || client.player == null) {
			return false;
		}
		if (Crop.isFarmingTool(SkyblockItems.skyblockId(client.player.getMainHandItem()))) {
			return false;
		}
		if (GardenDetector.inTheEnd() || sessionLive(System.currentTimeMillis())) {
			return true;
		}
		return miningTool(client.player.getMainHandItem()) && lookingAtEndStone(client);
	}

	public void reset() {
		blockBreaks.clear();
		fortune = MiningFortune.Reading.NONE;
		actionBar = "";
		fortuneAt = 0;
		sackCredit = 0;
		sackCreditAt = 0;
		gelCredit = 0;
		gelCreditAt = 0;
		holdingPickaxe = false;
		lastActivityMs = 0;
		lastTickMs = 0;
		activeMs = 0;
		paused = true;
		sawSackStones = false;
		sessionEndstone = 0;
		sessionEstimate = 0;
		sessionGel = 0;
		sessionBlocks = 0;
		lastMineIntentMs = 0;
		sackMessages = 0;
		tracking = false;
		inventoryReady = false;
		inventoryStones = 0;
		inventoryGel = 0;
		recentlyCounted.clear();
		watched.clear();
	}

	public Snapshot snapshot(ModConfig config, CoflBazaarService prices) {
		boolean sellOffer = config.useSellOffer();
		double unit = prices.endstoneUnit(sellOffer);
		double gelUnit = prices.miteGelUnit(sellOffer);
		boolean bazaar = prices.endstoneFromBazaar(sellOffer);
		double stones = sawSackStones ? sessionEndstone : sessionEstimate;
		double stoneCoins = Math.max(0, stones) * unit;
		double gelCoins = Math.max(0, sessionGel) * gelUnit;
		double sessionProfit = stoneCoins + gelCoins;
		double hours = activeMs / 3_600_000.0;
		double bps = blocksPerSecond();
		double perBlock = fortune.dropsPerBlock() > 0 ? fortune.dropsPerBlock() : 1.0;
		double pace = bps * perBlock * 3600.0 * unit;
		double coinsPerHour = coinsPerHour(pace, sessionProfit, hours, unit, gelUnit);
		String rateSource = rateSource(pace);
		double sessionPerHour = hours > 0 ? sessionProfit / hours : 0;

		return new Snapshot(
				fortune,
				bps,
				sessionBlocks,
				coinsPerHour,
				sessionPerHour,
				sessionProfit,
				Math.max(0, stones),
				Math.max(0, sessionGel),
				gelCoins,
				activeMs,
				paused,
				unit,
				bazaar,
				sellOffer,
				sawSackStones,
				rateSource,
				sackMessages
		);
	}

	private double coinsPerHour(double pace, double sessionProfit, double hours, double unit, double gelUnit) {
		if (sawSackStones && hours >= 3.0 / 3600.0) {
			return sessionProfit / hours;
		}
		double gelPerHour = hours > 0 ? Math.max(0, sessionGel) * gelUnit / hours : 0;
		return pace + gelPerHour;
	}

	private String rateSource(double pace) {
		if (sawSackStones && activeMs >= 3_000L) {
			return "loot";
		}
		if (pace > 0 && fortune.known()) {
			return "fortune";
		}
		if (pace > 0) {
			return "blocks";
		}
		return "";
	}

	private void refreshFortune(Minecraft client, long now) {
		MiningFortune.Reading read = MiningFortune.read(actionBar, TabList.lines(client));
		if (read.known()) {
			boolean keepBlock = !read.blockKnown() && fortune.blockKnown() && now - fortuneAt < FORTUNE_HOLD_MS;
			fortune = keepBlock ? read.mergeMissing(fortune) : read;
			fortuneAt = now;
			return;
		}
		if (fortuneAt > 0 && now - fortuneAt > FORTUNE_HOLD_MS) {
			fortune = MiningFortune.Reading.NONE;
			fortuneAt = 0;
		}
	}

	private boolean applyLootText(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		boolean touched = false;
		Matcher matcher = LOOT_GAIN.matcher(text);
		while (matcher.find()) {
			double amount = parseAmount(matcher.group(1), matcher.group(2));
			if (amount <= 0) {
				continue;
			}
			String item = matcher.group(3).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
			if (item.contains("mite")) {
				sessionGel += amount;
				gelCredit += amount;
				gelCreditAt = System.currentTimeMillis();
				touched = true;
				continue;
			}
			if (item.contains("enchanted")) {
				amount *= ENCHANTED_RATIO;
			}
			addStones(amount, true);
			touched = true;
		}
		return touched;
	}

	private void addStones(double stones, boolean fromSack) {
		if (stones <= 0) {
			return;
		}
		sessionEndstone += stones;
		sawSackStones = true;
		if (fromSack) {
			sackCredit += stones;
			sackCreditAt = System.currentTimeMillis();
		}
		double perBlock = fortune.dropsPerBlock() > 0 ? fortune.dropsPerBlock() : 1.0;
		long implied = Math.round(sessionEndstone / perBlock);
		if (implied > sessionBlocks) {
			sessionBlocks = implied;
		}
	}

	private static boolean lootText(String text) {
		return text != null && LOOT_GAIN.matcher(text).find();
	}

	private static double stonesIn(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		String id = SkyblockItems.skyblockId(stack).toUpperCase(Locale.ROOT);
		int count = stack.getCount();
		if (id.equals("ENCHANTED_ENDSTONE") || id.equals("ENCHANTED_END_STONE")) {
			return count * (double) ENCHANTED_RATIO;
		}
		if (id.equals("END_STONE") || id.equals("ENDSTONE")) {
			return count;
		}
		String name = clean(stack.getHoverName().getString()).toLowerCase(Locale.ROOT);
		if (name.contains("enchanted") && (name.contains("end stone") || name.contains("endstone"))) {
			return count * (double) ENCHANTED_RATIO;
		}
		if (name.contains("end stone") || name.contains("endstone") || stack.is(Items.END_STONE)) {
			return count;
		}
		return 0;
	}

	private static double gelIn(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		String id = SkyblockItems.skyblockId(stack).toUpperCase(Locale.ROOT);
		if (id.equals("MITE_GEL")) {
			return stack.getCount();
		}
		String name = clean(stack.getHoverName().getString()).toLowerCase(Locale.ROOT);
		return name.contains("mite gel") ? stack.getCount() : 0;
	}

	private static double parseAmount(String number, String suffix) {
		if (number == null || number.isBlank()) {
			return 0;
		}
		try {
			double value = Double.parseDouble(number.replace(",", "").trim());
			if (suffix == null || suffix.isEmpty()) {
				return value;
			}
			return switch (suffix.toLowerCase(Locale.ROOT)) {
				case "k" -> value * 1_000.0;
				case "m" -> value * 1_000_000.0;
				case "b" -> value * 1_000_000_000.0;
				default -> value;
			};
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private boolean sessionLive(long now) {
		return lastActivityMs > 0 && now - lastActivityMs <= HUD_LINGER_MS;
	}

	private void noteActivity(long now) {
		lastActivityMs = now;
		paused = false;
	}

	private double blocksPerSecond() {
		if (blockBreaks.size() >= 2) {
			long first = blockBreaks.peekFirst();
			long last = blockBreaks.peekLast();
			long dt = last - first;
			if (dt > 0) {
				return (blockBreaks.size() - 1) / (dt / 1000.0);
			}
		}
		if (sessionBlocks > 0 && activeMs >= 1_000L) {
			return sessionBlocks / (activeMs / 1000.0);
		}
		return 0;
	}

	private void prune(long now) {
		while (!blockBreaks.isEmpty() && blockBreaks.peekFirst() + BLOCK_WINDOW_MS < now) {
			blockBreaks.removeFirst();
		}
	}

	private void pauseIfIdle(long now, ModConfig config) {
		if (!paused && lastActivityMs > 0 && now - lastActivityMs > config.afkTimeoutSeconds * 1000L) {
			paused = true;
		}
	}

	private static boolean miningTool(ItemStack stack) {
		if (PickaxeAbilityService.isMiningPickaxe(stack)) {
			return true;
		}
		String id = SkyblockItems.skyblockId(stack).toUpperCase(Locale.ROOT);
		return id.contains("STONK");
	}

	private static String hoverText(Component component) {
		StringBuilder text = new StringBuilder();
		collectHover(component, text, 0);
		try {
			component.visit((style, string) -> {
				appendHover(style.getHoverEvent(), text);
				return java.util.Optional.empty();
			}, net.minecraft.network.chat.Style.EMPTY);
		} catch (RuntimeException ignored) {
		}
		return text.toString();
	}

	private static void collectHover(Component component, StringBuilder text, int depth) {
		if (component == null || depth > 8) {
			return;
		}
		appendHover(component.getStyle().getHoverEvent(), text);
		for (Component sibling : component.getSiblings()) {
			collectHover(sibling, text, depth + 1);
		}
	}

	private static void appendHover(HoverEvent hover, StringBuilder text) {
		if (hover instanceof HoverEvent.ShowText show && show.value() != null) {
			text.append(clean(show.value().getString())).append('\n');
		}
	}

	private static String clean(String raw) {
		if (raw == null) {
			return "";
		}
		String stripped = ChatFormatting.stripFormatting(raw);
		if (stripped == null) {
			stripped = raw;
		}
		return stripped
				.replace('\u00A0', ' ')
				.replace('\u202F', ' ')
				.replaceAll("\\s+", " ")
				.trim();
	}

	public record Snapshot(
			MiningFortune.Reading fortune,
			double blocksPerSecond,
			long blocks,
			double coinsPerHour,
			double sessionCoinsPerHour,
			double sessionProfit,
			double stones,
			double miteGel,
			double miteGelCoins,
			long activeMs,
			boolean paused,
			double unitPrice,
			boolean bazaar,
			boolean sellOffer,
			boolean fromSacks,
			String rateSource,
			int sackMessages
	) {
	}
}
