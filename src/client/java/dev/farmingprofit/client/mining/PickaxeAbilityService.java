package dev.farmingprofit.client.mining;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.farmingprofit.FarmingProfitMod;
import dev.farmingprofit.client.compat.ClientScreens;
import dev.farmingprofit.client.config.ModConfig;
import dev.farmingprofit.client.garden.Crop;
import dev.farmingprofit.client.garden.GardenDetector;
import dev.farmingprofit.client.garden.SkyblockItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.HitResult;

/**
 * Auto ability pioche SkyBlock : quand le cooldown (overlay / chat) arrive à 0,
 * un clic droit est envoyé si une pioche de mining est en main.
 */
public final class PickaxeAbilityService {
	private static final Pattern ABILITY_USED = Pattern.compile(
			"(?i)you used your (.+?) pickaxe ability"
	);
	private static final Pattern ABILITY_LORE = Pattern.compile(
			"(?i)ability:\\s*(.+?)\\s+right click"
	);
	private static final Pattern COOLDOWN_LORE = Pattern.compile(
			"(?i)cooldown:\\s*(?:(\\d+)\\s*m\\s*)?(?:(\\d+)\\s*s)?"
	);
	private static final Pattern BREAKING_POWER = Pattern.compile("(?i)breaking power");
	private static final int RETRY_INTERVAL_TICKS = 8;
	private static final int MAX_RETRIES = 3;
	private static final long DEFAULT_COOLDOWN_MS = 120_000L;

	private final ModConfig config;
	private boolean sawCooldown;
	private long readyAtMs;
	private int retryTicks;
	private int retriesLeft;

	public PickaxeAbilityService(ModConfig config) {
		this.config = config;
	}

	public void reset() {
		sawCooldown = false;
		readyAtMs = 0;
		retryTicks = 0;
		retriesLeft = 0;
	}

	public void onChat(String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		String text = strip(raw);
		if (text.startsWith("[Farming Profit]")) {
			return;
		}
		Matcher used = ABILITY_USED.matcher(text);
		if (!used.find()) {
			return;
		}
		long cooldownMs = cooldownMsFromHeld(used.group(1));
		readyAtMs = System.currentTimeMillis() + cooldownMs;
		sawCooldown = true;
		retriesLeft = 0;
		FarmingProfitMod.LOGGER.info("Pickaxe ability used, ready in {}ms", cooldownMs);
	}

	public void tick(Minecraft client) {
		if (!config.autoPickaxeAbility) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || ClientScreens.isOpen(client)) {
			return;
		}
		if (!GardenDetector.onSkyblock() || GardenDetector.inGarden()) {
			return;
		}

		ItemStack held = player.getMainHandItem();
		if (!isMiningPickaxe(held)) {
			return;
		}

		boolean onCooldown = player.getCooldowns().isOnCooldown(held);
		if (onCooldown) {
			sawCooldown = true;
			retriesLeft = 0;
			retryTicks = 0;
			return;
		}

		boolean timerReady = readyAtMs > 0 && System.currentTimeMillis() >= readyAtMs;
		boolean justReady = sawCooldown;
		if (!justReady && !timerReady && retriesLeft <= 0) {
			return;
		}

		if (player.isUsingItem() || player.isHandsBusy()) {
			return;
		}
		if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.ENTITY) {
			return;
		}

		if (retriesLeft <= 0) {
			retriesLeft = MAX_RETRIES;
			retryTicks = 0;
		} else {
			retryTicks++;
			if (retryTicks < RETRY_INTERVAL_TICKS) {
				return;
			}
			retryTicks = 0;
		}

		if (retriesLeft <= 0) {
			return;
		}
		retriesLeft--;
		sawCooldown = false;
		if (timerReady) {
			readyAtMs = 0;
		}
		client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
		FarmingProfitMod.LOGGER.info("Auto pickaxe ability");
	}

	public static boolean isMiningPickaxe(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		String id = SkyblockItems.skyblockId(stack).toUpperCase(Locale.ROOT);
		if (Crop.isFarmingTool(id)) {
			return false;
		}
		if (id.contains("PICKAXE") || id.contains("_DRILL") || id.endsWith("DRILL") || id.contains("GEMSTONE_GAUNTLET")) {
			return true;
		}
		if (stack.is(ItemTags.PICKAXES)) {
			return true;
		}
		String lore = loreText(stack);
		return BREAKING_POWER.matcher(lore).find() && ABILITY_LORE.matcher(lore).find();
	}

	private long cooldownMsFromHeld(String abilityName) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			long fromLore = parseCooldownMs(loreText(client.player.getMainHandItem()));
			if (fromLore > 0) {
				return fromLore;
			}
		}
		return defaultCooldownMs(abilityName);
	}

	private static long defaultCooldownMs(String abilityName) {
		if (abilityName == null) {
			return DEFAULT_COOLDOWN_MS;
		}
		String name = abilityName.toLowerCase(Locale.ROOT);
		if (name.contains("pickobulus")) {
			return 110_000L;
		}
		if (name.contains("maniac")) {
			return 59_000L;
		}
		if (name.contains("vein")) {
			return 60_000L;
		}
		if (name.contains("gemstone infusion") || name.contains("hazardous")) {
			return 140_000L;
		}
		return DEFAULT_COOLDOWN_MS;
	}

	private static long parseCooldownMs(String lore) {
		Matcher matcher = COOLDOWN_LORE.matcher(lore);
		if (!matcher.find()) {
			return -1;
		}
		int minutes = parseGroup(matcher, 1);
		int seconds = parseGroup(matcher, 2);
		if (minutes < 0 && seconds < 0) {
			return -1;
		}
		long total = Math.max(0, minutes) * 60L + Math.max(0, seconds);
		return total > 0 ? total * 1000L : -1;
	}

	private static int parseGroup(Matcher matcher, int group) {
		String value = matcher.group(group);
		if (value == null || value.isBlank()) {
			return -1;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static String loreText(ItemStack stack) {
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		StringBuilder text = new StringBuilder();
		for (Component line : lore.lines()) {
			text.append(strip(line.getString())).append('\n');
		}
		return text.toString();
	}

	private static String strip(String value) {
		String stripped = ChatFormatting.stripFormatting(value);
		return stripped == null ? "" : stripped.trim();
	}
}
