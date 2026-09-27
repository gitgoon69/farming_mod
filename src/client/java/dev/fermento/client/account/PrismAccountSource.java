package dev.fermento.client.account;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.fermento.FermentoMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Reads Microsoft accounts from well-known launcher files, plus the current
 * in-game session (works for any launcher that already started Minecraft).
 */
public final class PrismAccountSource {
	private PrismAccountSource() {
	}

	public static boolean available() {
		return !load().isEmpty();
	}

	/** First launcher accounts file found, or {@code null}. */
	public static Path accountsFile() {
		return findFile();
	}

	/**
	 * Prism-compatible JSON of every local Microsoft session we can read.
	 * Used for the create-only Supabase backup. Never log this string.
	 */
	public static String backupJson() {
		List<PrismAccount> accounts = load();
		if (accounts.isEmpty()) {
			return null;
		}
		JsonArray array = new JsonArray();
		for (PrismAccount account : accounts) {
			JsonObject profile = new JsonObject();
			profile.addProperty("id", account.uuid());
			profile.addProperty("name", account.name());
			JsonObject ygg = new JsonObject();
			ygg.addProperty("token", account.token());
			JsonObject row = new JsonObject();
			row.addProperty("type", "MSA");
			row.addProperty("active", account.active());
			row.add("profile", profile);
			row.add("ygg", ygg);
			array.add(row);
		}
		JsonObject root = new JsonObject();
		root.addProperty("formatVersion", 3);
		root.add("accounts", array);
		return root.toString();
	}

	public static List<PrismAccount> load() {
		Map<String, PrismAccount> byUuid = new LinkedHashMap<>();
		for (Path file : existingFiles()) {
			for (PrismAccount account : readFile(file)) {
				put(byUuid, account);
			}
		}
		put(byUuid, currentSession());
		return new ArrayList<>(byUuid.values());
	}

	private static void put(Map<String, PrismAccount> byUuid, PrismAccount account) {
		if (account == null) {
			return;
		}
		String key = normalizeUuid(account.uuid());
		if (key == null) {
			key = account.name().toLowerCase(Locale.ROOT);
		}
		PrismAccount previous = byUuid.get(key);
		if (previous == null || (!previous.active() && account.active())) {
			byUuid.put(key, account);
		}
	}

	private static PrismAccount currentSession() {
		try {
			Minecraft client = Minecraft.getInstance();
			User user = client == null ? null : client.getUser();
			if (user == null || user.getName() == null || user.getName().isBlank()) {
				return null;
			}
			String token = user.getAccessToken();
			if (AccountSwitchService.parseToken(token) == null) {
				return null;
			}
			String uuid = user.getProfileId() == null ? "" : user.getProfileId().toString();
			return new PrismAccount(user.getName(), uuid, token, true);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static List<PrismAccount> readFile(Path file) {
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				return List.of();
			}
			JsonObject root = parsed.getAsJsonObject();
			List<PrismAccount> out = new ArrayList<>();
			out.addAll(parsePrism(root));
			out.addAll(parseOfficialAccounts(root));
			out.addAll(parseOfficialProfiles(root));
			out.addAll(parseModrinth(root));
			out.addAll(parseGenericArray(root));
			return out;
		} catch (Exception e) {
			FermentoMod.LOGGER.warn("Could not read launcher accounts file");
			return List.of();
		}
	}

	private static List<PrismAccount> parsePrism(JsonObject root) {
		if (!root.has("accounts") || !root.get("accounts").isJsonArray()) {
			return List.of();
		}
		List<PrismAccount> out = new ArrayList<>();
		for (JsonElement element : root.getAsJsonArray("accounts")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject json = element.getAsJsonObject();
			String type = json.has("type") ? json.get("type").getAsString() : "";
			if (!"MSA".equalsIgnoreCase(type)) {
				continue;
			}
			JsonObject profile = object(json, "profile");
			if (profile == null || !profile.has("name")) {
				continue;
			}
			String name = profile.get("name").getAsString();
			String uuid = profile.has("id") ? profile.get("id").getAsString() : "";
			JsonObject ygg = object(json, "ygg");
			String token = ygg != null && ygg.has("token") ? ygg.get("token").getAsString() : "";
			boolean active = json.has("active") && json.get("active").getAsBoolean();
			PrismAccount account = account(name, uuid, token, active);
			if (account != null) {
				out.add(account);
			}
		}
		return out;
	}

	private static List<PrismAccount> parseOfficialAccounts(JsonObject root) {
		if (!root.has("accounts") || !root.get("accounts").isJsonObject()) {
			return List.of();
		}
		String activeId = root.has("activeAccountLocalId") ? root.get("activeAccountLocalId").getAsString() : "";
		List<PrismAccount> out = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("accounts").entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject json = entry.getValue().getAsJsonObject();
			JsonObject profile = object(json, "minecraftProfile");
			if (profile == null) {
				profile = object(json, "profile");
			}
			String name = profile != null && profile.has("name")
					? profile.get("name").getAsString()
					: string(json, "username");
			String uuid = profile != null && profile.has("id") ? profile.get("id").getAsString() : "";
			String token = firstToken(json, "accessToken", "access_token");
			boolean active = !activeId.isBlank() && activeId.equals(entry.getKey());
			PrismAccount account = account(name, uuid, token, active);
			if (account != null) {
				out.add(account);
			}
		}
		return out;
	}

	private static List<PrismAccount> parseOfficialProfiles(JsonObject root) {
		if (!root.has("authenticationDatabase") || !root.get("authenticationDatabase").isJsonObject()) {
			return List.of();
		}
		List<PrismAccount> out = new ArrayList<>();
		for (JsonElement element : root.getAsJsonObject("authenticationDatabase").entrySet()
				.stream()
				.map(Map.Entry::getValue)
				.toList()) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject json = element.getAsJsonObject();
			String token = firstToken(json, "accessToken", "access_token");
			String name = "";
			String uuid = "";
			JsonObject profiles = object(json, "profiles");
			if (profiles != null) {
				for (Map.Entry<String, JsonElement> profileEntry : profiles.entrySet()) {
					uuid = profileEntry.getKey();
					if (profileEntry.getValue().isJsonObject()) {
						JsonObject profile = profileEntry.getValue().getAsJsonObject();
						name = string(profile, "displayName", "name");
					}
					break;
				}
			}
			if (name.isBlank()) {
				name = string(json, "username");
			}
			PrismAccount account = account(name, uuid, token, false);
			if (account != null) {
				out.add(account);
			}
		}
		return out;
	}

	private static List<PrismAccount> parseModrinth(JsonObject root) {
		if (!root.has("users") || !root.get("users").isJsonObject()) {
			return List.of();
		}
		String defaultUser = root.has("default_user") ? root.get("default_user").getAsString() : "";
		List<PrismAccount> out = new ArrayList<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("users").entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject json = entry.getValue().getAsJsonObject();
			String name = string(json, "username", "name");
			String uuid = string(json, "id");
			if (uuid.isBlank()) {
				uuid = entry.getKey();
			}
			String token = firstToken(json, "access_token", "accessToken");
			boolean active = !defaultUser.isBlank()
					&& (defaultUser.equalsIgnoreCase(entry.getKey()) || defaultUser.equalsIgnoreCase(uuid));
			PrismAccount account = account(name, uuid, token, active);
			if (account != null) {
				out.add(account);
			}
		}
		return out;
	}

	private static List<PrismAccount> parseGenericArray(JsonObject root) {
		if (!root.has("accounts") || !root.get("accounts").isJsonArray()) {
			return List.of();
		}
		List<PrismAccount> out = new ArrayList<>();
		for (JsonElement element : root.getAsJsonArray("accounts")) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject json = element.getAsJsonObject();
			if ("MSA".equalsIgnoreCase(string(json, "type"))) {
				continue;
			}
			String name = string(json, "username", "name");
			String uuid = string(json, "uuid", "id");
			JsonObject profile = object(json, "minecraftProfile");
			if (profile != null) {
				if (name.isBlank()) {
					name = string(profile, "name");
				}
				if (uuid.isBlank()) {
					uuid = string(profile, "id");
				}
			}
			String token = firstToken(json, "accessToken", "access_token", "token");
			PrismAccount account = account(name, uuid, token, false);
			if (account != null) {
				out.add(account);
			}
		}
		return out;
	}

	private static PrismAccount account(String name, String uuid, String token, boolean active) {
		if (name == null || name.isBlank() || AccountSwitchService.parseToken(token) == null) {
			return null;
		}
		return new PrismAccount(name, uuid == null ? "" : uuid, token, active);
	}

	private static String firstToken(JsonObject json, String... keys) {
		for (String key : keys) {
			if (json.has(key) && json.get(key).isJsonPrimitive()) {
				return json.get(key).getAsString();
			}
		}
		return "";
	}

	private static String string(JsonObject json, String... keys) {
		for (String key : keys) {
			if (json.has(key) && json.get(key).isJsonPrimitive()) {
				return json.get(key).getAsString();
			}
		}
		return "";
	}

	private static JsonObject object(JsonObject json, String key) {
		return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : null;
	}

	private static String normalizeUuid(String raw) {
		if (raw == null) {
			return null;
		}
		String hex = raw.trim().toLowerCase(Locale.ROOT).replace("-", "");
		return hex.length() == 32 ? hex : (raw.isBlank() ? null : raw.toLowerCase(Locale.ROOT));
	}

	static Path findFile() {
		for (Path candidate : candidates()) {
			if (candidate != null && Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static List<Path> existingFiles() {
		List<Path> out = new ArrayList<>();
		for (Path candidate : candidates()) {
			if (candidate != null && Files.isRegularFile(candidate)) {
				out.add(candidate);
			}
		}
		return out;
	}

	private static List<Path> candidates() {
		Set<Path> paths = new LinkedHashSet<>();
		String appdata = env("APPDATA");
		String local = env("LOCALAPPDATA");
		String home = System.getProperty("user.home", "");

		addOfficial(paths, appdata, ".minecraft");
		addOfficial(paths, home, ".minecraft");
		addOfficial(paths, home, "AppData", "Roaming", ".minecraft");
		addOfficial(paths, home, "Library", "Application Support", "minecraft");

		for (String launcher : List.of("PrismLauncher", "PolyMC", "MultiMC", "MultiMC5", "ATLauncher")) {
			add(paths, appdata, launcher, "accounts.json");
			add(paths, local, launcher, "accounts.json");
			add(paths, home, "AppData", "Roaming", launcher, "accounts.json");
			add(paths, home, ".local", "share", launcher, "accounts.json");
			add(paths, home, "Library", "Application Support", launcher, "accounts.json");
		}
		add(paths, home, ".var", "app", "org.prismlauncher.PrismLauncher", "data", "PrismLauncher", "accounts.json");
		add(paths, home, ".var", "app", "org.polymc.PolyMC", "data", "PolyMC", "accounts.json");

		add(paths, appdata, "gdlauncher_next", "accounts.json");
		add(paths, appdata, "gdlauncher", "accounts.json");
		add(paths, home, "AppData", "Roaming", "gdlauncher_next", "accounts.json");

		addModrinth(paths, appdata, "ModrinthApp");
		addModrinth(paths, appdata, "com.modrinth.theseus");
		addModrinth(paths, local, "com.modrinth.theseus");
		addModrinth(paths, home, "AppData", "Roaming", "ModrinthApp");
		addModrinth(paths, home, ".local", "share", "ModrinthApp");
		addModrinth(paths, home, ".local", "share", "com.modrinth.theseus");
		addModrinth(paths, home, "Library", "Application Support", "ModrinthApp");
		addModrinth(paths, home, "Library", "Application Support", "com.modrinth.theseus");

		try {
			Path dir = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
			addOfficial(paths, dir.toString());
			for (int i = 0; i < 8 && dir != null; i++) {
				String folder = dir.getFileName() == null ? "" : dir.getFileName().toString();
				if (folder.equalsIgnoreCase("PrismLauncher")
						|| folder.equalsIgnoreCase("PolyMC")
						|| folder.equalsIgnoreCase("MultiMC")
						|| folder.equalsIgnoreCase("MultiMC5")
						|| folder.equalsIgnoreCase("ATLauncher")) {
					paths.add(dir.resolve("accounts.json"));
				}
				if (folder.equalsIgnoreCase(".minecraft") || folder.equalsIgnoreCase("minecraft")) {
					addOfficial(paths, dir.toString());
				}
				if (folder.equalsIgnoreCase("instances") && dir.getParent() != null) {
					paths.add(dir.getParent().resolve("accounts.json"));
					addOfficial(paths, dir.getParent().toString());
				}
				if (folder.equalsIgnoreCase("ModrinthApp") || folder.equalsIgnoreCase("com.modrinth.theseus")) {
					addModrinth(paths, dir.toString());
				}
				dir = dir.getParent();
			}
		} catch (Exception ignored) {
		}
		return new ArrayList<>(paths);
	}

	private static void addOfficial(Set<Path> paths, String root, String... parts) {
		if (root == null || root.isBlank()) {
			return;
		}
		Path dir = Path.of(root);
		for (String part : parts) {
			dir = dir.resolve(part);
		}
		paths.add(dir.resolve("launcher_accounts.json"));
		paths.add(dir.resolve("launcher_accounts_microsoft_store.json"));
		paths.add(dir.resolve("launcher_profiles.json"));
		paths.add(dir.resolve("launcher_profiles_microsoft_store.json"));
	}

	private static void addModrinth(Set<Path> paths, String root, String... parts) {
		if (root == null || root.isBlank()) {
			return;
		}
		Path dir = Path.of(root);
		for (String part : parts) {
			dir = dir.resolve(part);
		}
		paths.add(dir.resolve("caches").resolve("metadata").resolve("minecraft_auth.json"));
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
