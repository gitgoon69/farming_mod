package dev.farmingprofit.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.farmingprofit.FarmingProfitMod;
import net.fabricmc.loader.api.FabricLoader;

public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("farmingprofit.json");

	public boolean hudEnabled = true;
	public int hudX = 8;
	public int hudY = 48;
	/** OFFER = sell offer (buyPrice Cofl), INSTANT = instant sell (sellPrice Cofl). */
	public String priceMode = "OFFER";
	public boolean includeSeeds = true;
	public int afkTimeoutSeconds = 15;
	/** Aim hitbox: 1×1×1 cube when mature, lowest otherwise (wheat, carrots, potatoes, nether wart, mushrooms, cocoa). */
	public boolean fullCropHitboxes = true;
	/** Right-click rod → /loadout → Pest, click again → Farm. */
	public boolean pestRodLoadout = true;
	public String pestLoadoutName = "Pest";
	public String farmLoadoutName = "Farm";
	/** Check GitHub on login for this Minecraft version. */
	public boolean checkUpdates = true;
	/** Sends UUID + username + version to Supabase on login (private table). */
	public boolean usagePing = true;
	/** Large title + sound at 2m50 pest cooldown, 5s countdown. */
	public boolean pestCooldownAlert = true;
	/** Fires the alert when tab cooldown reaches this time (170 = 2m50). */
	public int pestCooldownAlertAtSeconds = 170;
	/** Displayed countdown length (seconds). */
	public int pestCooldownAlertCountdown = 5;
	/** At 2m50 → Pest loadout, 0.5–1s after spawn → /setspawn + Farm loadout. */
	public boolean autoPestLoadout = true;
	/** Hypixel server pack last (vanilla + your packs take priority). */
	public boolean hideServerResourcePack = true;
	/** Mining: auto right-click when pickaxe ability is ready (cooldown at 0). */
	public boolean autoPickaxeAbility = true;

	public static ModConfig load() {
		if (!Files.exists(PATH)) {
			ModConfig config = new ModConfig();
			config.save();
			return config;
		}
		try (Reader reader = Files.newBufferedReader(PATH)) {
			ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
			return loaded != null ? loaded : new ModConfig();
		} catch (IOException e) {
			FarmingProfitMod.LOGGER.warn("Could not read farmingprofit.json", e);
			return new ModConfig();
		}
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			FarmingProfitMod.LOGGER.warn("Could not write farmingprofit.json", e);
		}
	}

	public boolean useSellOffer() {
		return !"INSTANT".equalsIgnoreCase(priceMode);
	}
}
