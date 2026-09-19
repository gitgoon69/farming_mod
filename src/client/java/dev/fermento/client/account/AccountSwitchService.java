package dev.fermento.client.account;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.fermento.FermentoMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Switches the local Minecraft session using an official access token
 * (validated against Minecraft Services). Accounts are stored locally
 * so the same profiles can be reused without pasting the token again.
 */
public final class AccountSwitchService {
	private static final URI PROFILE_API = URI.create("https://api.minecraftservices.com/minecraft/profile");
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("fermento-accounts.json");
	private static final Path LEGACY_PATH = FabricLoader.getInstance().getConfigDir().resolve("farmingprofit-accounts.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_SAVED = 8;
	private static final AccountSwitchService INSTANCE = new AccountSwitchService();

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "fermento-account");
		thread.setDaemon(true);
		return thread;
	});

	private AccountSwitchService() {
	}

	public static AccountSwitchService get() {
		return INSTANCE;
	}

	public static String currentName(Minecraft client) {
		User user = client.getUser();
		if (user == null || user.getName() == null || user.getName().isBlank()) {
			return "Unknown";
		}
		return user.getName();
	}

	public List<SavedAccount> saved() {
		return load().accounts;
	}

	public void remove(String uuid) {
		Store store = load();
		store.accounts.removeIf(account -> uuid.equalsIgnoreCase(account.uuid));
		save(store);
	}

	public void login(String rawToken, Minecraft client, BiConsumer<Boolean, String> done) {
		String token = parseToken(rawToken);
		if (token == null) {
			done.accept(false, "Paste a Minecraft access token.");
			return;
		}
		executor.execute(() -> {
			try {
				Profile profile = fetchProfile(token);
				client.execute(() -> {
					try {
						rememberCurrent(client);
						applyUser(client, profile, token);
						remember(profile.name(), profile.uuid(), token);
						boolean world = inWorld(client);
						if (world) {
							disconnectToTitle(client);
							return;
						}
						done.accept(true, "Connected as " + profile.name());
					} catch (Exception e) {
						FermentoMod.LOGGER.warn("Could not apply account {}", profile.name(), e);
						done.accept(false, "Could not apply the session: " + rootMessage(e));
					}
				});
			} catch (Exception e) {
				client.execute(() -> done.accept(false, rootMessage(e)));
			}
		});
	}

	private Profile fetchProfile(String token) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(PROFILE_API)
				.timeout(Duration.ofSeconds(15))
				.header("Authorization", "Bearer " + token)
				.GET()
				.build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		int status = response.statusCode();
		if (status == 401 || status == 403) {
			throw new IOException("Token invalid or expired.");
		}
		if (status == 429) {
			throw new IOException("Minecraft Services rate-limited. Try again shortly.");
		}
		if (status < 200 || status >= 300) {
			throw new IOException("Minecraft Services HTTP " + status + ".");
		}
		JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
		if (!json.has("id") || !json.has("name")) {
			throw new IOException("Token is not a Minecraft access token.");
		}
		String name = json.get("name").getAsString();
		UUID uuid = parseUndashedUuid(json.get("id").getAsString());
		if (name == null || name.isBlank() || uuid == null) {
			throw new IOException("Incomplete Minecraft profile.");
		}
		return new Profile(name, uuid);
	}

	private static void applyUser(Minecraft client, Profile profile, String token) {
		User next = createUser(profile.name(), profile.uuid(), token);
		setUserField(client, next);
		User applied = client.getUser();
		if (applied == null || !profile.uuid().equals(applied.getProfileId())) {
			throw new IllegalStateException("The client session did not update.");
		}
	}

	private static User createUser(String name, UUID uuid, String token) {
		RuntimeException last = null;
		for (Constructor<?> ctor : User.class.getConstructors()) {
			Class<?>[] types = ctor.getParameterTypes();
			if (types.length < 3
					|| types[0] != String.class
					|| types[1] != UUID.class
					|| types[2] != String.class) {
				continue;
			}
			Object[] args = new Object[types.length];
			args[0] = name;
			args[1] = uuid;
			args[2] = token;
			for (int i = 3; i < types.length; i++) {
				if (types[i] == Optional.class) {
					args[i] = Optional.empty();
				} else if (types[i].isEnum()) {
					args[i] = firstEnum(types[i]);
				} else {
					args[i] = null;
				}
			}
			try {
				return (User) ctor.newInstance(args);
			} catch (ReflectiveOperationException e) {
				last = new RuntimeException(e);
			}
		}
		if (last != null) {
			throw last;
		}
		throw new IllegalStateException("No compatible User constructor.");
	}

	private static void setUserField(Minecraft client, User user) {
		try {
			VarHandle handle = MethodHandles.privateLookupIn(Minecraft.class, MethodHandles.lookup())
					.findVarHandle(Minecraft.class, "user", User.class);
			handle.set(client, user);
			return;
		} catch (Exception ignored) {
		}
		try {
			var field = Minecraft.class.getDeclaredField("user");
			field.setAccessible(true);
			field.set(client, user);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not replace Minecraft.user", e);
		}
	}

	private static void rememberCurrent(Minecraft client) {
		User user = client.getUser();
		if (user == null || user.getProfileId() == null || !looksLikeToken(user.getAccessToken())) {
			return;
		}
		remember(user.getName(), user.getProfileId(), user.getAccessToken());
	}

	private static void remember(String name, UUID uuid, String token) {
		if (name == null || name.isBlank() || uuid == null || !looksLikeToken(token)) {
			return;
		}
		Store store = load();
		String id = uuid.toString();
		store.accounts.removeIf(account -> id.equalsIgnoreCase(account.uuid));
		SavedAccount saved = new SavedAccount();
		saved.name = name;
		saved.uuid = id;
		saved.token = token;
		store.accounts.addFirst(saved);
		while (store.accounts.size() > MAX_SAVED) {
			store.accounts.removeLast();
		}
		save(store);
	}

	private static Store load() {
		Path source = Files.exists(PATH) ? PATH : (Files.exists(LEGACY_PATH) ? LEGACY_PATH : null);
		if (source == null) {
			return new Store();
		}
		try (Reader reader = Files.newBufferedReader(source)) {
			Store loaded = GSON.fromJson(reader, Store.class);
			if (loaded == null) {
				return new Store();
			}
			if (loaded.accounts == null) {
				loaded.accounts = new ArrayList<>();
				return loaded;
			}
			Iterator<SavedAccount> it = loaded.accounts.iterator();
			while (it.hasNext()) {
				SavedAccount account = it.next();
				if (account == null || account.name == null || account.uuid == null || !looksLikeToken(account.token)) {
					it.remove();
				}
			}
			if (!source.equals(PATH)) {
				save(loaded);
			}
			return loaded;
		} catch (Exception e) {
			FermentoMod.LOGGER.warn("Could not read fermento-accounts.json", e);
			return new Store();
		}
	}

	private static void save(Store store) {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(store, writer);
			}
		} catch (IOException e) {
			FermentoMod.LOGGER.warn("Could not write fermento-accounts.json", e);
		}
	}

	static String parseToken(String raw) {
		if (raw == null) {
			return null;
		}
		String text = raw.trim();
		if (text.isEmpty()) {
			return null;
		}
		if (text.regionMatches(true, 0, "Bearer ", 0, 7)) {
			text = text.substring(7).trim();
		}
		if (text.startsWith("{")) {
			try {
				JsonObject json = JsonParser.parseString(text).getAsJsonObject();
				for (String key : List.of("accessToken", "access_token", "token")) {
					if (json.has(key)) {
						text = json.get(key).getAsString();
						break;
					}
				}
			} catch (Exception ignored) {
			}
		}
		if (text.regionMatches(true, 0, "token:", 0, 6)) {
			String[] parts = text.split(":", 3);
			if (parts.length >= 2) {
				text = parts[1];
			}
		} else {
			String[] parts = text.split(":");
			if (parts.length >= 3 && looksLikeToken(parts[parts.length - 1])) {
				text = parts[parts.length - 1];
			}
		}
		text = text.trim();
		return looksLikeToken(text) ? text : null;
	}

	private static boolean looksLikeToken(String token) {
		if (token == null) {
			return false;
		}
		String value = token.trim();
		if (value.length() < 20 || value.length() > 4096) {
			return false;
		}
		if ("0".equals(value) || "null".equalsIgnoreCase(value) || "undefined".equalsIgnoreCase(value)) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c <= 32 || c == ' ') {
				return false;
			}
		}
		return true;
	}

	private static UUID parseUndashedUuid(String raw) {
		if (raw == null) {
			return null;
		}
		String hex = raw.trim().replace("-", "");
		if (hex.length() != 32) {
			return UUID.fromString(raw.trim());
		}
		return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
				+ hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
	}

	private static boolean inWorld(Minecraft client) {
		return client.player != null || client.getConnection() != null;
	}

	private static void disconnectToTitle(Minecraft client) {
		Screen title = newTitleScreen();
		Exception last = null;
		for (Method method : Minecraft.class.getMethods()) {
			if (!method.getName().equals("disconnect") && !method.getName().equals("disconnectFromWorld")) {
				continue;
			}
			Class<?>[] types = method.getParameterTypes();
			try {
				if (types.length == 2 && Screen.class.isAssignableFrom(types[0]) && types[1] == boolean.class) {
					method.invoke(client, title, false);
					return;
				}
				if (types.length == 3 && Screen.class.isAssignableFrom(types[0]) && types[1] == boolean.class) {
					method.invoke(client, title, false, true);
					return;
				}
				if (types.length == 1 && Screen.class.isAssignableFrom(types[0])) {
					method.invoke(client, title);
					return;
				}
				if (types.length == 1 && Component.class.isAssignableFrom(types[0])) {
					method.invoke(client, Component.literal("Switched account"));
					return;
				}
			} catch (Exception e) {
				last = e;
			}
		}
		if (last != null) {
			FermentoMod.LOGGER.warn("Could not disconnect after account switch", last);
		}
	}

	private static Screen newTitleScreen() {
		try {
			Class<?> cls = Class.forName("net.minecraft.client.gui.screens.TitleScreen");
			return (Screen) cls.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not open the title screen", e);
		}
	}

	private static Object firstEnum(Class<?> type) {
		Object[] constants = type.getEnumConstants();
		if (constants == null || constants.length == 0) {
			return null;
		}
		for (Object constant : constants) {
			String name = ((Enum<?>) constant).name();
			if (name.contains("MSA") || name.contains("MICROSOFT")) {
				return constant;
			}
		}
		return constants[0];
	}

	private static String rootMessage(Throwable error) {
		Throwable cause = error;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		String message = cause.getMessage();
		return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
	}

	public static final class SavedAccount {
		public String name;
		public String uuid;
		public String token;
	}

	private static final class Store {
		List<SavedAccount> accounts = new ArrayList<>();
	}

	private record Profile(String name, UUID uuid) {
	}
}
