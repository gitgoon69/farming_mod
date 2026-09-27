package dev.fermento.client.mining;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.fermento.client.config.ModConfig;
import dev.fermento.client.garden.Crop;
import dev.fermento.client.garden.SkyblockItems;
import dev.fermento.client.garden.TabList;
import dev.fermento.client.prices.CoflBazaarService;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * End Stone profit, same idea as the Garden HUD and SkyHanni's gemstone coins/hour:
 * count what actually landed in sacks, price it at {@code max(NPC, bazaar)}.
 * Until a sack message arrives, pace uses Mining Fortune + Block Fortune.
 */
public final class EndstoneTracker {
	private static final long BLOCK_WINDOW_MS = 5_000L;
	private static final long SACK_WINDOW_MS = 15_000L;
	private static final long FORTUNE_HOLD_MS = 8_000L;
	private static final long HUD_LINGER_MS = 120_000L;
	private static final int ENCHANTED_RATIO = 160;

	/**
	 * SkyHanni {@code SackApi} hover lines: {@code +12 End Stone (Mining Sack)}.
	 */
	private static final Pattern SACK_DELTA = Pattern.compile("([+-][\\d,]+)\\s+(.+?)\\s+\\(([^)]+)\\)");

	private final Deque<Long> blockBreaks = new ArrayDeque<>();
	private final Deque<Sample> sackWindow = new ArrayDeque<>();

	private MiningFortune.Reading fortune = MiningFortune.Reading.NONE;
	private long fortuneAt;
	private boolean holdingPickaxe;
	private long lastActivityMs;
	private long lastTickMs;
	private long activeMs;
	private boolean paused = true;
	private boolean sawSackStones;
	private double sessionEndstone;
	private double sessionEstimate;
	private double sessionGel;
	private int sackMessages;
	private boolean tracking;

	public void tick(Minecraft client, ModConfig config) {
		long now = System.currentTimeMillis();
		prune(now);
		tracking = config.endstoneProfit;

		if (!tracking || client.player == null || client.level == null) {
			holdingPickaxe = false;
			pauseIfIdle(now, config);
			lastTickMs = now;
			return;
		}

		holdingPickaxe = miningTool(client.player.getMainHandItem());
		refreshFortune(client, now);

		if (!holdingPickaxe && (lastActivityMs <= 0 || now - lastActivityMs > HUD_LINGER_MS)) {
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

	public void onBlockBroken(Block block) {
		if (!tracking || block != Blocks.END_STONE) {
			return;
		}
		long now = System.currentTimeMillis();
		blockBreaks.addLast(now);
		noteActivity(now);
		double drops = fortune.dropsPerBlock();
		if (drops > 0) {
			sessionEstimate += drops;
		}
	}

	public void onChat(Component message) {
		if (!tracking || message == null || !sessionLive(System.currentTimeMillis())) {
			return;
		}
		String visible = clean(message.getString());
		if (!visible.startsWith("[Sacks]")) {
			return;
		}
		String hover = hoverText(message);
		if (hover.isBlank()) {
			return;
		}
		boolean touched = false;
		Matcher matcher = SACK_DELTA.matcher(hover);
		while (matcher.find()) {
			int delta = parseSigned(matcher.group(1));
			if (delta == 0) {
				continue;
			}
			if (apply(itemName(matcher.group(2)), delta)) {
				touched = true;
			}
		}
		if (!touched) {
			return;
		}
		long now = System.currentTimeMillis();
		noteActivity(now);
		sackMessages++;
		sackWindow.addLast(new Sample(sessionEndstone, sessionGel, now));
	}

	public boolean hudVisible(Minecraft client, ModConfig config) {
		if (!config.hudEnabled || !config.endstoneProfit || client.player == null) {
			return false;
		}
		ItemStack held = client.player.getMainHandItem();
		if (Crop.isFarmingTool(SkyblockItems.skyblockId(held)) || !miningTool(held)) {
			return false;
		}
		return sessionLive(System.currentTimeMillis());
	}

	public void reset() {
		blockBreaks.clear();
		sackWindow.clear();
		fortune = MiningFortune.Reading.NONE;
		fortuneAt = 0;
		holdingPickaxe = false;
		lastActivityMs = 0;
		lastTickMs = 0;
		activeMs = 0;
		paused = true;
		sawSackStones = false;
		sessionEndstone = 0;
		sessionEstimate = 0;
		sessionGel = 0;
		sackMessages = 0;
		tracking = false;
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
		double pace = bps * fortune.dropsPerBlock() * 3600.0 * unit;
		double coinsPerHour = coinsPerHour(pace, sessionProfit, hours, unit, gelUnit);
		String rateSource = rateSource(pace);
		double sessionPerHour = hours > 0 ? sessionProfit / hours : 0;

		return new Snapshot(
				fortune,
				bps,
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
		double sackRate = sackStonesPerSecond();
		if (sackRate >= 0) {
			double gelRate = sackGelPerSecond();
			return sackRate * 3600.0 * unit + Math.max(0, gelRate) * 3600.0 * gelUnit;
		}
		if (sawSackStones && hours > 0) {
			return sessionProfit / hours;
		}
		double gelPerHour = hours > 0 ? Math.max(0, sessionGel) * gelUnit / hours : 0;
		return pace + gelPerHour;
	}

	private String rateSource(double pace) {
		if (sackStonesPerSecond() >= 0 || (sawSackStones && activeMs > 0)) {
			return "sacks";
		}
		if (pace > 0) {
			return "fortune";
		}
		return "";
	}

	private void refreshFortune(Minecraft client, long now) {
		MiningFortune.Reading read = MiningFortune.read("", TabList.lines(client));
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

	private boolean apply(String item, int delta) {
		if (item.equals("enchanted end stone")) {
			sessionEndstone += delta * (double) ENCHANTED_RATIO;
			sawSackStones = true;
			return true;
		}
		if (item.equals("end stone")) {
			sessionEndstone += delta;
			sawSackStones = true;
			return true;
		}
		if (item.equals("mite gel") && delta > 0) {
			sessionGel += delta;
			return true;
		}
		return false;
	}

	private boolean sessionLive(long now) {
		return lastActivityMs > 0 && now - lastActivityMs <= HUD_LINGER_MS;
	}

	private void noteActivity(long now) {
		lastActivityMs = now;
		paused = false;
	}

	private double blocksPerSecond() {
		if (blockBreaks.size() < 2) {
			return 0;
		}
		long first = blockBreaks.peekFirst();
		long last = blockBreaks.peekLast();
		long dt = last - first;
		if (dt <= 0) {
			return 0;
		}
		return (blockBreaks.size() - 1) / (dt / 1000.0);
	}

	private double sackStonesPerSecond() {
		return rate(true);
	}

	private double sackGelPerSecond() {
		return rate(false);
	}

	private double rate(boolean stones) {
		if (sackWindow.size() < 2) {
			return -1;
		}
		Sample first = sackWindow.peekFirst();
		Sample last = sackWindow.peekLast();
		long dt = last.time - first.time;
		if (dt < 1_000L) {
			return -1;
		}
		double delta = (stones ? last.stones : last.gel) - (stones ? first.stones : first.gel);
		if (stones && delta <= 0) {
			return -1;
		}
		return delta / (dt / 1000.0);
	}

	private void prune(long now) {
		while (!blockBreaks.isEmpty() && blockBreaks.peekFirst() + BLOCK_WINDOW_MS < now) {
			blockBreaks.removeFirst();
		}
		while (!sackWindow.isEmpty() && sackWindow.peekFirst().time + SACK_WINDOW_MS < now) {
			sackWindow.removeFirst();
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
		return text.toString();
	}

	private static void collectHover(Component component, StringBuilder text, int depth) {
		if (component == null || depth > 8) {
			return;
		}
		HoverEvent hover = component.getStyle().getHoverEvent();
		if (hover instanceof HoverEvent.ShowText show && show.value() != null) {
			text.append(clean(show.value().getString())).append('\n');
		}
		for (Component sibling : component.getSiblings()) {
			collectHover(sibling, text, depth + 1);
		}
	}

	private static String itemName(String raw) {
		String name = clean(raw).toLowerCase(Locale.ROOT);
		name = name.replaceAll("^[^a-z]+", "").trim();
		return name;
	}

	private static int parseSigned(String raw) {
		if (raw == null || raw.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(raw.replace(",", "").trim());
		} catch (NumberFormatException e) {
			return 0;
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

	private record Sample(double stones, double gel, long time) {
	}

	public record Snapshot(
			MiningFortune.Reading fortune,
			double blocksPerSecond,
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
