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
	/** Hitbox de visée : cube 1×1×1 si mature, plus basse sinon (blé, carottes, patates, nether wart, champignons, cacao). */
	public boolean fullCropHitboxes = true;
	/** Clic droit canne → /loadout → Pest, reclic → Farm. */
	public boolean pestRodLoadout = true;
	public String pestLoadoutName = "Pest";
	public String farmLoadoutName = "Farm";
	/** Vérifie GitHub (matteorlt/farming_mod) au login. */
	public boolean checkUpdates = true;
	/** Gros titre + son à 2m50 de cooldown pest, compte à rebours 5s. */
	public boolean pestCooldownAlert = true;
	/** Déclenche l’alerte quand le cooldown tab atteint ce temps (170 = 2m50). */
	public int pestCooldownAlertAtSeconds = 170;
	/** Durée du compte à rebours affiché (secondes). */
	public int pestCooldownAlertCountdown = 5;
	/** À 2m50 → loadout Pest, 0.5–1s après le spawn → /setspawn + loadout Farm. */
	public boolean autoPestLoadout = true;
	/** Pack serveur Hypixel en tout dernier (vanilla + tes packs passent devant). */
	public boolean hideServerResourcePack = true;
	/** Mining : clic droit auto quand l’ability pioche est prête (cooldown à 0). */
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
			FarmingProfitMod.LOGGER.warn("Impossible de lire farmingprofit.json", e);
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
			FarmingProfitMod.LOGGER.warn("Impossible d'écrire farmingprofit.json", e);
		}
	}

	public boolean useSellOffer() {
		return !"INSTANT".equalsIgnoreCase(priceMode);
	}
}
