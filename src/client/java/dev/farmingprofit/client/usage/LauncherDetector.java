package dev.farmingprofit.client.usage;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Identifies well-known Minecraft launchers from brand, game dir, and parent process.
 * Never sends full paths — only a short id.
 */
final class LauncherDetector {
	private LauncherDetector() {
	}

	static String detect() {
		String brand = prop("minecraft.launcher.brand");
		String fromBrand = fromBrand(brand);
		if (fromBrand != null) {
			return fromBrand;
		}
		if (prop("org.prismlauncher.instance.name") != null) {
			return "prism";
		}
		if (prop("multimc.instance.title") != null) {
			return "multimc";
		}

		String fromProcess = fromProcess();
		if (fromProcess != null) {
			return fromProcess;
		}

		String fromPath = fromHaystack(pathHaystack());
		if (fromPath != null) {
			return fromPath;
		}

		if (looksOfficialGameDir()) {
			return "official";
		}
		if (brand != null && !brand.isBlank()) {
			return sanitize(brand);
		}
		return "unknown";
	}

	private static String fromBrand(String brand) {
		if (brand == null || brand.isBlank()) {
			return null;
		}
		return match(brand.toLowerCase(Locale.ROOT));
	}

	private static String fromHaystack(String haystack) {
		if (haystack.isEmpty()) {
			return null;
		}
		return match(haystack);
	}

	private static String match(String text) {
		// Launcher install dirs first — instance names can mention other stores.
		if (contains(text, "prismlauncher", "prism launcher")) {
			return "prism";
		}
		if (contains(text, "polymc", "poly mc")) {
			return "polymc";
		}
		if (contains(text, "multimc", "multi mc")) {
			return "multimc";
		}
		if (contains(text, "modrinthapp", "modrinth app", "modrinth-app", "com.modrinth.theseus")
				|| text.equals("modrinth")
				|| text.equals("theseus")
				|| text.startsWith("theseus")) {
			return "modrinth";
		}
		if (contains(text, "curseforge", "overwolf")) {
			return "curseforge";
		}
		if (contains(text, "ftb-app", "ftbapp", "ftb app", "feedthebeast", "feed the beast")) {
			return "ftb";
		}
		if (contains(text, "atlauncher", "at launcher")) {
			return "atlauncher";
		}
		if (contains(text, "gdlauncher", "gd launcher", "carbonlauncher")) {
			return "gdlauncher";
		}
		if (contains(text, "techniclauncher", "technic launcher", "/technic/", "\\technic\\")) {
			return "technic";
		}
		if (contains(text, "skyclient", "sky client")) {
			return "skyclient";
		}
		if (contains(text, "featherclient", "feather-client", "feather client", "featherlauncher")) {
			return "feather";
		}
		if (contains(text, "lunarclient", "lunar client", ".lunarclient")) {
			return "lunar";
		}
		if (contains(text, "badlion")) {
			return "badlion";
		}
		if (contains(text, "labymod")) {
			return "labymod";
		}
		if (contains(text, "legacylauncher", "legacy launcher", "llauncher")) {
			return "legacy-launcher";
		}
		if (contains(text, "tlauncher", "t-launcher")) {
			return "tlauncher";
		}
		if (contains(text, "sklauncher", "sk launcher")) {
			return "sklauncher";
		}
		if (contains(text, "hellominecraftlauncher", "hello minecraft") || text.equals("hmcl") || contains(text, "/hmcl", "\\hmcl")) {
			return "hmcl";
		}
		if (contains(text, "plain craft launcher", "pcl2", "/pcl/", "\\pcl\\")) {
			return "pcl";
		}
		if (contains(text, "pojavlauncher", "pojav")) {
			return "pojav";
		}
		if (contains(text, "minecraftlauncher", "minecraft launcher", "minecraft-launcher")) {
			return "official";
		}
		if (contains(text, "xboxapp", "xbox app")) {
			return "xbox";
		}
		return null;
	}

	private static String fromProcess() {
		try {
			Optional<ProcessHandle> parent = ProcessHandle.current().parent();
			if (parent.isEmpty()) {
				return null;
			}
			String first = processHaystack(parent.get());
			String matched = match(first);
			if (matched != null) {
				return matched;
			}
			Optional<ProcessHandle> grand = parent.get().parent();
			if (grand.isEmpty()) {
				return null;
			}
			return match(processHaystack(grand.get()));
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String processHaystack(ProcessHandle handle) {
		String command = handle.info().command().orElse("");
		String line = handle.info().commandLine().orElse("");
		return (command + " " + line).toLowerCase(Locale.ROOT);
	}

	private static String pathHaystack() {
		StringBuilder out = new StringBuilder();
		appendPath(out, FabricLoader.getInstance().getGameDir());
		String userDir = System.getProperty("user.dir");
		if (userDir != null) {
			out.append(' ').append(userDir.toLowerCase(Locale.ROOT));
		}
		return out.toString();
	}

	private static void appendPath(StringBuilder out, Path path) {
		if (path == null) {
			return;
		}
		out.append(' ').append(path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT));
	}

	private static boolean looksOfficialGameDir() {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		if (gameDir == null) {
			return false;
		}
		Path normalized = gameDir.toAbsolutePath().normalize();
		String name = normalized.getFileName() == null ? "" : normalized.getFileName().toString().toLowerCase(Locale.ROOT);
		if (!name.equals(".minecraft") && !name.equals("minecraft")) {
			return false;
		}
		Path parent = normalized.getParent();
		if (parent == null) {
			return false;
		}
		String parentName = parent.getFileName() == null ? "" : parent.getFileName().toString().toLowerCase(Locale.ROOT);
		return parentName.equals("roaming")
				|| parentName.equals("application support")
				|| parentName.equals(System.getProperty("user.name", "").toLowerCase(Locale.ROOT));
	}

	private static String prop(String key) {
		try {
			String value = System.getProperty(key);
			return value == null || value.isBlank() ? null : value;
		} catch (SecurityException e) {
			return null;
		}
	}

	private static boolean contains(String text, String... needles) {
		for (String needle : needles) {
			if (text.contains(needle)) {
				return true;
			}
		}
		return false;
	}

	private static String sanitize(String raw) {
		String value = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
		value = value.replaceAll("^-+|-+$", "");
		if (value.isBlank()) {
			return "unknown";
		}
		return value.length() > 32 ? value.substring(0, 32) : value;
	}
}
