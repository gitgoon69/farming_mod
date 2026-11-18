package dev.stook.client.mining;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.stook.StookMod;
import dev.stook.client.compat.ClientScreens;
import dev.stook.client.config.ModConfig;
import dev.stook.client.garden.Crop;
import dev.stook.client.garden.GardenDetector;
import dev.stook.client.garden.SkyblockItems;
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
 * SkyBlock pickaxe auto ability: when cooldown (overlay / chat) hits 0,
 * a right-click is sent if a mining pickaxe is in hand.
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
	private static final long LAUNCH_FLASH_MS = 2_200L;
	private static final long FLASH_GAP_MS = 800L;

	private final ModConfig config;
	private boolean sawCooldown;
	private long readyAtMs;
	private long displayReadyAtMs;
	private long launchedAtMs;
	private long cooldownTotalMs;
	private String abilityName = "";
	private int retryTicks;
	private int retriesLeft;

	public PickaxeAbilityService(ModConfig config) {
		this.config = config;
	}

	public void reset() {
		sawCooldown = false;
		readyAtMs = 0;
		displayReadyAtMs = 0;
		launchedAtMs = 0;
		cooldownTotalMs = 0;
		abilityName = "";
		retryTicks = 0;
		retriesLeft = 0;
	}

	public View view(Minecraft client) {
		if (!config.autoPickaxeAbility || client.player == null) {
			return View.HIDDEN;
		}
		if (!GardenDetector.onSkyblock() || GardenDetector.inGarden()) {
			return View.HIDDEN;
		}
		ItemStack held = client.player.getMainHandItem();
		if (!isMiningPickaxe(held)) {
			return View.HIDDEN;
		}
		long now = System.currentTimeMillis();
		long total = cooldownTotalMs;
		String lore = loreText(held);
		if (total <= 0) {
			total = parseCooldownMs(lore);
		}
		long remaining = 0L;
		float percent = client.player.getCooldowns().getCooldownPercent(held, 0.0f);
		if (percent > 0.001f && total > 0) {
			remaining = (long) Math.ceil(percent * (double) total);
		} else if (displayReadyAtMs > now) {
			remaining = displayReadyAtMs - now;
		}
		String name = abilityName.isBlank() ? abilityNameFrom(lore) : abilityName;
		if (name.isBlank()) {
			name = "Ability";
		}
		boolean launched = launchedAtMs > 0 && now - launchedAtMs < LAUNCH_FLASH_MS;
		return new View(true, name, remaining, total, launched);
	}

	public void onChat(String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		String text = strip(raw);
		if (text.regionMatches(true, 0, "[Stook]", 0, 7)) {
			return;
		}
		Matcher used = ABILITY_USED.matcher(text);
		if (!used.find()) {
			return;
		}
		String name = cleanAbility(used.group(1));
		long cooldownMs = cooldownMsFromHeld(name);
		long now = System.currentTimeMillis();
		readyAtMs = now + cooldownMs;
		sawCooldown = true;
		retriesLeft = 0;
		noteActivated(name, cooldownMs, true);
		StookMod.LOGGER.info("Pickaxe ability used, ready in {}ms", cooldownMs);
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
		flashLaunch(abilityNameFrom(loreText(held)));
		StookMod.LOGGER.info("Auto pickaxe ability");
	}

	private void noteActivated(String name, long cooldownMs, boolean refreshFlash) {
		long now = System.currentTimeMillis();
		if (name != null && !name.isBlank()) {
			abilityName = cleanAbility(name);
		}
		if (cooldownMs > 0) {
			cooldownTotalMs = cooldownMs;
			displayReadyAtMs = now + cooldownMs;
			readyAtMs = now + cooldownMs;
		}
		if (refreshFlash && now - launchedAtMs > FLASH_GAP_MS) {
			launchedAtMs = now;
		}
	}

	private void flashLaunch(String name) {
		long now = System.currentTimeMillis();
		if (name != null && !name.isBlank()) {
			abilityName = cleanAbility(name);
		}
		if (now - launchedAtMs > FLASH_GAP_MS) {
			launchedAtMs = now;
		}
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

	private static String abilityNameFrom(String lore) {
		Matcher matcher = ABILITY_LORE.matcher(lore);
		if (!matcher.find()) {
			return "";
		}
		return cleanAbility(matcher.group(1));
	}

	private static String cleanAbility(String raw) {
		if (raw == null) {
			return "";
		}
		String name = raw.trim();
		if (name.length() > 32) {
			name = name.substring(0, 32).trim();
		}
		return name;
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

	public record View(boolean visible, String name, long remainingMs, long totalMs, boolean launched) {
		public static final View HIDDEN = new View(false, "", 0L, 0L, false);

		public String timerText() {
			if (totalMs <= 0 && remainingMs <= 0) {
				return "--";
			}
			long seconds = (Math.max(0L, remainingMs) + 999L) / 1000L;
			if (seconds <= 0L) {
				return "READY";
			}
			long minutes = seconds / 60L;
			long rest = seconds % 60L;
			return minutes + ":" + (rest < 10 ? "0" : "") + rest;
		}
	}
}
