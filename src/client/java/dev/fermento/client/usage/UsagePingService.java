package dev.fermento.client.usage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonObject;

import dev.fermento.FermentoMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Anonymous ping to Supabase on login (UUID + username + version).
 * Publishable key only: no table read access.
 */
public final class UsagePingService {
	private static final String SUPABASE_URL = "https://nbbfyqopodfyocjdojbj.supabase.co";
	private static final String PUBLISHABLE_KEY = "sb_publishable_JKwfPFQoHU4akDLtAWCpGw_6YCb6YO7";
	private static final URI PING = URI.create(SUPABASE_URL + "/rest/v1/rpc/ping_mod_user");

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "fermento-usage");
		thread.setDaemon(true);
		return thread;
	});

	public void onJoin() {
		Minecraft client = Minecraft.getInstance();
		User user = client.getUser();
		if (user == null) {
			return;
		}
		String username = user.getName();
		UUID uuid = user.getProfileId();
		if (username == null || username.isBlank() || uuid == null) {
			return;
		}
		String version = FabricLoader.getInstance()
				.getModContainer(FermentoMod.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
		String launcher = LauncherDetector.detect();
		CompletableFuture.runAsync(() -> ping(uuid, username, version, launcher), executor);
	}

	private void ping(UUID uuid, String username, String version, String launcher) {
		try {
			JsonObject body = new JsonObject();
			body.addProperty("p_uuid", uuid.toString());
			body.addProperty("p_username", username);
			body.addProperty("p_mod_version", version);
			body.addProperty("p_launcher", launcher);

			HttpRequest request = HttpRequest.newBuilder(PING)
					.timeout(Duration.ofSeconds(15))
					.header("apikey", PUBLISHABLE_KEY)
					.header("Authorization", "Bearer " + PUBLISHABLE_KEY)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
					.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			int status = response.statusCode();
			if (status < 200 || status >= 300) {
				FermentoMod.LOGGER.debug("Usage ping HTTP {}: {}", status, response.body());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			FermentoMod.LOGGER.debug("Usage ping: {}", e.toString());
		}
	}
}
