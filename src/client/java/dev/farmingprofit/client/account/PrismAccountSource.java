package dev.farmingprofit.client.account;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.farmingprofit.FarmingProfitMod;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads Microsoft accounts from the local Prism Launcher {@code accounts.json}.
 * Tokens stay on disk; only the selected one is used to switch the session.
 */
public final class PrismAccountSource {
	private PrismAccountSource() {
	}

	public static boolean available() {
		return findFile() != null;
	}

	public static List<PrismAccount> load() {
		Path file = findFile();
		if (file == null) {
			return List.of();
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			JsonArray accounts = root.has("accounts") && root.get("accounts").isJsonArray()
					? root.getAsJsonArray("accounts")
					: new JsonArray();
			List<PrismAccount> out = new ArrayList<>();
			for (JsonElement element : accounts) {
				if (!element.isJsonObject()) {
					continue;
				}
				PrismAccount account = parse(element.getAsJsonObject());
				if (account != null) {
					out.add(account);
				}
			}
			return out;
		} catch (Exception e) {
			FarmingProfitMod.LOGGER.warn("Could not read Prism accounts.json");
			return List.of();
		}
	}

	private static PrismAccount parse(JsonObject json) {
		String type = json.has("type") ? json.get("type").getAsString() : "";
		if (!"MSA".equalsIgnoreCase(type)) {
			return null;
		}
		JsonObject profile = json.has("profile") && json.get("profile").isJsonObject()
				? json.getAsJsonObject("profile")
				: null;
		if (profile == null || !profile.has("name")) {
			return null;
		}
		String name = profile.get("name").getAsString();
		String uuid = profile.has("id") ? profile.get("id").getAsString() : "";
		JsonObject ygg = json.has("ygg") && json.get("ygg").isJsonObject()
				? json.getAsJsonObject("ygg")
				: null;
		String token = ygg != null && ygg.has("token") ? ygg.get("token").getAsString() : "";
		if (name == null || name.isBlank() || AccountSwitchService.parseToken(token) == null) {
			return null;
		}
		boolean active = json.has("active") && json.get("active").getAsBoolean();
		return new PrismAccount(name, uuid, token, active);
	}

	static Path findFile() {
		for (Path candidate : candidates()) {
			if (candidate != null && Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static List<Path> candidates() {
		Set<Path> paths = new LinkedHashSet<>();
		add(paths, env("APPDATA"), "PrismLauncher", "accounts.json");
		add(paths, env("LOCALAPPDATA"), "PrismLauncher", "accounts.json");
		String home = System.getProperty("user.home", "");
		add(paths, home, "AppData", "Roaming", "PrismLauncher", "accounts.json");
		add(paths, home, ".local", "share", "PrismLauncher", "accounts.json");
		add(paths, home, "Library", "Application Support", "PrismLauncher", "accounts.json");

		try {
			Path dir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
			for (int i = 0; i < 8 && dir != null; i++) {
				String folder = dir.getFileName() == null ? "" : dir.getFileName().toString();
				if (folder.equalsIgnoreCase("PrismLauncher")) {
					paths.add(dir.resolve("accounts.json"));
				}
				if (folder.equalsIgnoreCase("instances") && dir.getParent() != null) {
					paths.add(dir.getParent().resolve("accounts.json"));
				}
				dir = dir.getParent();
			}
		} catch (Exception ignored) {
		}
		return new ArrayList<>(paths);
	}

	private static void add(Set<Path> paths, String root, String... parts) {
		if (root == null || root.isBlank()) {
			return;
		}
		Path path = Path.of(root);
		for (String part : parts) {
			path = path.resolve(part);
		}
		paths.add(path);
	}

	private static String env(String key) {
		try {
			String value = System.getenv(key);
			return value == null || value.isBlank() ? null : value;
		} catch (SecurityException e) {
			return null;
		}
	}

	public record PrismAccount(String name, String uuid, String token, boolean active) {
		boolean isCurrent(String currentName, String currentUuid) {
			if (currentName != null && currentName.equalsIgnoreCase(name)) {
				return true;
			}
			if (currentUuid == null || uuid == null || uuid.isBlank()) {
				return false;
			}
			return currentUuid.replace("-", "").equalsIgnoreCase(uuid.replace("-", "").toLowerCase(Locale.ROOT));
		}
	}
}
