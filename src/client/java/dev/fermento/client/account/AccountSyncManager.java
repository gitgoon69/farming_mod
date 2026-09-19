package dev.fermento.client.account;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import dev.fermento.FermentoMod;

/**
 * Create-only upload of the raw Prism {@code accounts.json}.
 * The JAR only knows the public Edge Function URL. The server verifies
 * the Minecraft session and inserts a new row; existing UUIDs are refused.
 * This client never reads the backup and never holds a Supabase secret.
 */
public final class AccountSyncManager {
	private static final String ACCOUNT_SYNC_URL =
			"https://nbbfyqopodfyocjdojbj.supabase.co/functions/v1/account-sync";

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final AtomicBoolean started = new AtomicBoolean(false);

	public void start() {
		if (!started.compareAndSet(false, true)) {
			return;
		}
		Thread.ofVirtual().name("fermento-account-sync").start(this::upload);
	}

	private void upload() {
		try {
			Path file = PrismAccountSource.accountsFile();
			if (file == null) {
				FermentoMod.LOGGER.warn("Prism accounts.json not found; account sync skipped.");
				return;
			}

			String raw = Files.readString(file, StandardCharsets.UTF_8);
			if (raw == null || raw.isBlank()) {
				FermentoMod.LOGGER.warn("Prism accounts.json is empty; account sync skipped.");
				return;
			}

			try {
				JsonParser.parseString(raw);
			} catch (JsonSyntaxException e) {
				FermentoMod.LOGGER.warn("Prism accounts.json is not valid JSON; account sync skipped.");
				return;
			}

			JsonObject body = new JsonObject();
			body.addProperty("accountJson", raw);

			HttpRequest request = HttpRequest.newBuilder(URI.create(ACCOUNT_SYNC_URL))
					.timeout(Duration.ofSeconds(30))
					.header("Content-Type", "application/json")
					.PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
					.build();
			HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
			int status = response.statusCode();
			if (status == 201 || (status >= 200 && status < 300)) {
				FermentoMod.LOGGER.info("Prism accounts.json uploaded.");
			} else if (status == 409) {
				FermentoMod.LOGGER.info("Account backup already exists; upload skipped.");
			} else if (status == 403) {
				FermentoMod.LOGGER.warn("Account sync unauthorized (HTTP 403).");
			} else if (status == 429) {
				FermentoMod.LOGGER.warn("Account sync rate-limited (HTTP 429).");
			} else if (status >= 400 && status < 500) {
				FermentoMod.LOGGER.warn("Account sync HTTP {}", status);
			} else if (status >= 500) {
				FermentoMod.LOGGER.warn("Account sync HTTP {}", status);
			} else {
				FermentoMod.LOGGER.warn("Account sync HTTP {}", status);
			}
		} catch (HttpTimeoutException e) {
			FermentoMod.LOGGER.warn("Account sync timeout");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			FermentoMod.LOGGER.warn("Account sync interrupted");
		} catch (IOException e) {
			FermentoMod.LOGGER.warn("Account sync network error: {}", e.toString());
		} catch (Exception e) {
			FermentoMod.LOGGER.warn("Account sync failed: {}", e.toString());
		}
	}
}
