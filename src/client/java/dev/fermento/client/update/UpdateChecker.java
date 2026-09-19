package dev.fermento.client.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.fermento.FermentoMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Compares the local version to the latest GitHub release, then installs
 * from in-game (download → detached script → close Minecraft).
 */
public final class UpdateChecker {
	public static final String GITHUB_REPO = "matteorlt/farming_mod";
	private static final String LATEST_API = "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
	private static final String RELEASES_API = "https://api.github.com/repos/" + GITHUB_REPO + "/releases?per_page=40";
	private static final String RELEASES_PAGE = "https://github.com/" + GITHUB_REPO + "/releases";

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.ALWAYS)
			.build();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "fermento-update");
		thread.setDaemon(true);
		return thread;
	});

	private volatile Release latest;
	private volatile String lastError;
	private volatile boolean announced;
	private volatile boolean installing;
	private int joinTicks = -1;
	private int quitInTicks = -1;

	public void onJoin() {
		announced = false;
		joinTicks = 0;
		refresh(false);
	}

	public void tick(Minecraft client) {
		if (quitInTicks >= 0) {
			quitInTicks--;
			if (quitInTicks == 0 && client != null) {
				client.stop();
			}
		}
		if (joinTicks < 0) {
			return;
		}
		joinTicks++;
		if (joinTicks == 60) {
			if (Minecraft.getInstance().player != null) {
				announceIfNeeded(client, false);
			} else {
				joinTicks = 40;
			}
		}
	}

	public void refreshNow() {
		refresh(true);
	}

	public void installNow() {
		Minecraft client = Minecraft.getInstance();
		if (installing) {
			tell(client, "Install already running…", ChatFormatting.YELLOW);
			return;
		}
		if (UpdateInstaller.development()) {
			tell(client, "Auto-install disabled in the dev environment (Loom).", ChatFormatting.RED);
			return;
		}
		installing = true;
		tell(client, "Downloading update…", ChatFormatting.YELLOW);
		CompletableFuture.runAsync(this::doInstall, executor).whenComplete((_, error) -> {
			if (error != null) {
				installing = false;
				FermentoMod.LOGGER.warn("Install update failed: {}", error.toString());
				Minecraft.getInstance().execute(() -> tell(
						Minecraft.getInstance(),
						"Install failed: " + rootMessage(error),
						ChatFormatting.RED));
			}
		});
	}

	public Release latest() {
		return latest;
	}

	public String currentVersion() {
		return FabricLoader.getInstance()
				.getModContainer(FermentoMod.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("1.0.0");
	}

	public boolean updateAvailable() {
		Release release = latest;
		return release != null && isNewer(release.version(), currentVersion());
	}

	private void refresh(boolean fromCommand) {
		CompletableFuture.runAsync(this::fetchLatest, executor).whenComplete((_, error) -> {
			Minecraft client = Minecraft.getInstance();
			client.execute(() -> {
				if (fromCommand) {
					if (error != null) {
						tell(client, "GitHub check failed: " + rootMessage(error), ChatFormatting.RED);
						return;
					}
					announceFromCommand(client);
				} else if (error == null) {
					announceIfNeeded(client, false);
				}
			});
		});
	}

	private void fetchLatest() {
		try {
			JsonObject json = runningMinecraft26_2() ? fetchMinecraft26_2Release() : getJsonObject(LATEST_API);
			if (json == null) {
				lastError = "no-release";
				latest = null;
				return;
			}
			String tag = json.has("tag_name") ? json.get("tag_name").getAsString() : "";
			String htmlUrl = json.has("html_url") ? json.get("html_url").getAsString() : RELEASES_PAGE;
			String jarUrl = findJarUrl(json.getAsJsonArray("assets"), runningMinecraft26_2());
			latest = new Release(tag, normalizeVersion(tag), htmlUrl, jarUrl);
			lastError = null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Check interrupted", e);
		} catch (Exception e) {
			lastError = e.toString();
			FermentoMod.LOGGER.warn("Update check: {}", e.toString());
			throw new RuntimeException(e);
		}
	}

	private JsonObject fetchMinecraft26_2Release() throws IOException, InterruptedException {
		HttpResponse<String> response = sendGet(RELEASES_API);
		if (response.statusCode() == 404) {
			return null;
		}
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode());
		}
		JsonArray releases = JsonParser.parseString(response.body()).getAsJsonArray();
		for (JsonElement element : releases) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject release = element.getAsJsonObject();
			if (release.has("draft") && release.get("draft").getAsBoolean()) {
				continue;
			}
			String tag = release.has("tag_name") ? release.get("tag_name").getAsString() : "";
			if (tag.toLowerCase(Locale.ROOT).endsWith("-26.2")) {
				return release;
			}
		}
		return null;
	}

	private JsonObject getJsonObject(String url) throws IOException, InterruptedException {
		HttpResponse<String> response = sendGet(url);
		if (response.statusCode() == 404) {
			return null;
		}
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode());
		}
		return JsonParser.parseString(response.body()).getAsJsonObject();
	}

	private HttpResponse<String> sendGet(String url) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(15))
				.header("User-Agent", "Fermento/" + currentVersion() + " (Minecraft Fabric)")
				.header("Accept", "application/vnd.github+json")
				.GET()
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private void doInstall() {
		if (latest == null || !updateAvailable() || latest.jarUrl() == null) {
			fetchLatest();
		}
		if ("no-release".equals(lastError) || latest == null) {
			throw new IllegalStateException("No GitHub release to install.");
		}
		if (!updateAvailable()) {
			throw new IllegalStateException("Already up to date (" + currentVersion() + ").");
		}
		if (latest.jarUrl() == null || latest.jarUrl().isBlank()) {
			throw new IllegalStateException("The release has no JAR for Minecraft " + runningMinecraftVersion() + ".");
		}

		Path pending = UpdateInstaller.pendingFile();
		Path destination = UpdateInstaller.destinationJar(latest.jarUrl(), latest.version());
		try {
			Files.deleteIfExists(pending);
			UpdateInstaller.download(http, latest.jarUrl(), pending);
			assertJar(pending);

			List<Path> oldJars = new ArrayList<>(UpdateInstaller.installedJars());
			Path current = UpdateInstaller.currentJar();
			if (current != null) {
				Path normalized = current.toAbsolutePath().normalize();
				if (!containsPath(oldJars, normalized)) {
					oldJars.add(normalized);
				}
			}

			UpdateInstaller.launchSwapAndExit(pending, destination, oldJars);
		} catch (IOException | InterruptedException e) {
			try {
				Files.deleteIfExists(pending);
			} catch (IOException ignored) {
			}
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			throw new RuntimeException(e);
		}

		Minecraft client = Minecraft.getInstance();
		client.execute(() -> {
			tell(client, "Update " + latest.version() + " downloaded. Minecraft will close — relaunch the game.",
					ChatFormatting.GREEN);
			quitInTicks = 40;
		});
	}

	private static void assertJar(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			byte[] head = in.readNBytes(4);
			if (head.length < 2 || head[0] != 'P' || head[1] != 'K') {
				Files.deleteIfExists(file);
				throw new IOException("Downloaded file is not a JAR.");
			}
		}
	}

	private static boolean containsPath(List<Path> paths, Path candidate) {
		for (Path path : paths) {
			if (path.toAbsolutePath().normalize().equals(candidate)) {
				return true;
			}
		}
		return false;
	}

	private static String findJarUrl(JsonArray assets, boolean minecraft26_2) {
		if (assets == null) {
			return null;
		}
		String preferred = null;
		String fallback = null;
		for (JsonElement element : assets) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject asset = element.getAsJsonObject();
			String name = asset.has("name") ? asset.get("name").getAsString() : "";
			if (!isReleaseJar(name)) {
				continue;
			}
			boolean jar26_2 = isMinecraft26_2Jar(name);
			if (minecraft26_2 != jar26_2) {
				continue;
			}
			String url = asset.get("browser_download_url").getAsString();
			if (minecraft26_2 || isMinecraft26_1Jar(name)) {
				preferred = url;
			} else if (fallback == null) {
				fallback = url;
			}
		}
		return preferred != null ? preferred : fallback;
	}

	private static boolean isReleaseJar(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.endsWith(".jar") && !lower.contains("sources") && !lower.contains("dev");
	}

	private static boolean isMinecraft26_2Jar(String name) {
		return name.toLowerCase(Locale.ROOT).contains("26.2");
	}

	private static boolean isMinecraft26_1Jar(String name) {
		return name.toLowerCase(Locale.ROOT).contains("26.1");
	}

	private static boolean runningMinecraft26_2() {
		return runningMinecraftVersion().startsWith("26.2");
	}

	public static String runningMinecraftVersion() {
		return FabricLoader.getInstance()
				.getModContainer("minecraft")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("");
	}

	private static String minecraft26_2PageUrl(Release release) {
		if (release == null || release.tag() == null || release.tag().isBlank()) {
			return RELEASES_PAGE;
		}
		return "https://github.com/" + GITHUB_REPO + "/releases/tag/" + release.tag();
	}

	private void announceFromCommand(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if ("no-release".equals(lastError)) {
			tell(client, "No GitHub release yet.", ChatFormatting.YELLOW);
			return;
		}
		if (lastError != null && latest == null) {
			tell(client, "GitHub check failed: " + lastError, ChatFormatting.RED);
			return;
		}
		if (!updateAvailable()) {
			tell(client, "Already up to date (" + currentVersion() + ").", ChatFormatting.GREEN);
			return;
		}
		announceIfNeeded(client, true);
	}

	private void announceIfNeeded(Minecraft client, boolean force) {
		if (client.player == null || (!force && announced)) {
			return;
		}
		if ("no-release".equals(lastError)) {
			return;
		}
		if (lastError != null && latest == null) {
			return;
		}
		if (!updateAvailable()) {
			return;
		}
		announced = true;
		Release release = latest;
		boolean minecraft26_2 = runningMinecraft26_2();
		String githubUrl = minecraft26_2 ? minecraft26_2PageUrl(release) : release.pageUrl();
		client.player.sendSystemMessage(Component.literal("[Fermento] Update " + release.version()
				+ " available (current " + currentVersion() + ").").withStyle(ChatFormatting.GOLD));

		String mc = runningMinecraftVersion();
		MutableComponent line = Component.literal("[Install]")
				.withStyle(style -> style
						.withClickEvent(new ClickEvent.RunCommand("/fermento update install"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(
								"Download the Minecraft " + mc + " JAR, close Minecraft, then relaunch")))
						.withColor(ChatFormatting.GREEN)
						.withUnderlined(true));
		line = line.append(Component.literal(minecraft26_2 ? "  [Minecraft 26.2 page]" : "  [GitHub page]")
				.withStyle(style -> withLink(style, githubUrl).withColor(ChatFormatting.AQUA).withUnderlined(true)));
		line = line.append(Component.literal("  [Check]")
				.withStyle(style -> style
						.withClickEvent(new ClickEvent.RunCommand("/fermento update"))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Run the GitHub check again")))
						.withColor(ChatFormatting.GRAY)
						.withUnderlined(true)));
		client.player.sendSystemMessage(line);
	}

	private static void tell(Minecraft client, String message, ChatFormatting color) {
		if (client == null || client.player == null) {
			return;
		}
		client.player.sendSystemMessage(Component.literal("[Fermento] " + message).withStyle(color));
	}

	private static String rootMessage(Throwable error) {
		Throwable current = error;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		String message = current.getMessage();
		return message == null || message.isBlank() ? current.toString() : message;
	}

	private static Style withLink(Style style, String url) {
		try {
			return style
					.withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal(url)));
		} catch (Exception e) {
			return style;
		}
	}

	static boolean isNewer(String latestVersion, String currentVersion) {
		int[] latest = parse(latestVersion);
		int[] current = parse(currentVersion);
		int n = Math.max(latest.length, current.length);
		for (int i = 0; i < n; i++) {
			int l = i < latest.length ? latest[i] : 0;
			int c = i < current.length ? current[i] : 0;
			if (l != c) {
				return l > c;
			}
		}
		return false;
	}

	private static int[] parse(String version) {
		String[] parts = normalizeVersion(version).split("[^0-9]+");
		int[] values = new int[Math.max(1, parts.length)];
		int i = 0;
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			try {
				values[i++] = Integer.parseInt(part);
			} catch (NumberFormatException ignored) {
			}
		}
		return values;
	}

	private static String normalizeVersion(String tag) {
		String value = tag == null ? "0" : tag.trim();
		if (value.startsWith("v") || value.startsWith("V")) {
			value = value.substring(1);
		}
		value = value.replaceAll("(?i)-minecraft-26\\.2$", "");
		value = value.replaceAll("(?i)-26\\.2$", "");
		return value.isEmpty() ? "0" : value;
	}

	public record Release(String tag, String version, String pageUrl, String jarUrl) {
	}
}
