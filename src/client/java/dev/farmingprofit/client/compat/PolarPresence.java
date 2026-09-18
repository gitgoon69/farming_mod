package dev.farmingprofit.client.compat;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Polar accroche des méthodes vanilla en natif. Nos mixins réseau / GUI / packs
 * déplacent ces méthodes et provoquent un ACCESS_VIOLATION dans ntdll.
 */
public final class PolarPresence {
	private static final Logger LOGGER = LoggerFactory.getLogger("farmingprofit");
	private static final boolean PRESENT = detect();

	static {
		if (PRESENT) {
			LOGGER.warn("Polar détecté : tous les mixins Farming Profit sont désactivés (évite le crash ntdll).");
		}
	}

	private PolarPresence() {
	}

	public static boolean detected() {
		return PRESENT;
	}

	private static boolean detect() {
		try {
			FabricLoader loader = FabricLoader.getInstance();
			if (loader.isModLoaded("polar") || loader.isModLoaded("polarclient") || loader.isModLoaded("polar-client")) {
				return true;
			}
			for (ModContainer mod : loader.getAllMods()) {
				String blob = (mod.getMetadata().getId() + " " + mod.getMetadata().getName()).toLowerCase(Locale.ROOT);
				if (blob.contains("polar")) {
					return true;
				}
			}
			if (jarNamedPolar(loader.getGameDir().resolve("mods"))) {
				return true;
			}
		} catch (Throwable ignored) {
		}
		String classpath = System.getProperty("java.class.path", "").toLowerCase(Locale.ROOT);
		return classpath.contains("polar");
	}

	private static boolean jarNamedPolar(Path mods) {
		if (!Files.isDirectory(mods)) {
			return false;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(mods, "*.jar")) {
			for (Path path : stream) {
				if (path.getFileName().toString().toLowerCase(Locale.ROOT).contains("polar")) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}
}
