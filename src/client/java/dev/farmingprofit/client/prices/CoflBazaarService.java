package dev.farmingprofit.client.prices;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.farmingprofit.FarmingProfitMod;
import dev.farmingprofit.client.garden.Crop;

/**
 * Bazaar prices via the Cofl API {@code GET /api/bazaar/{itemTag}/snapshot}.
 * Crop price = enchanted item price / 160.
 */
public final class CoflBazaarService {
	private static final String SNAPSHOT = "https://sky.coflnet.com/api/bazaar/%s/snapshot";
	private static final long REFRESH_MS = 5 * 60 * 1000L;

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
		Thread thread = new Thread(runnable, "farmingprofit-cofl");
		thread.setDaemon(true);
		return thread;
	});
	private final Map<String, BazaarQuote> quotes = new ConcurrentHashMap<>();
	private volatile long lastRefresh;
	private volatile boolean refreshing;
	private volatile String lastError;

	public void start() {
		refreshIfNeeded(true);
	}

	public void tick() {
		refreshIfNeeded(false);
	}

	public void refreshNow() {
		refreshIfNeeded(true);
	}

	public BazaarQuote quote(String productId) {
		return quotes.get(productId);
	}

	public String lastError() {
		return lastError;
	}

	public boolean ready() {
		return !quotes.isEmpty();
	}

	/**
	 * Unit price of a normal crop, derived from enchanted (/160).
	 * Mushrooms: average of red + brown. Wheat: optionally + seeds.
	 */
	public double unitPrice(Crop crop, boolean sellOffer, boolean includeSeeds) {
		double enchanted = selected(crop.enchantedBazaarId, sellOffer);
		if (crop == Crop.MUSHROOM) {
			double brown = selected(Crop.ENCHANTED_BROWN_MUSHROOM, sellOffer);
			if (enchanted > 0 && brown > 0) {
				enchanted = (enchanted + brown) / 2.0;
			} else if (brown > 0) {
				enchanted = brown;
			}
		}
		if (enchanted <= 0) {
			return 0;
		}
		double perCrop = enchanted / Crop.ENCHANTED_RATIO;
		if (crop == Crop.WHEAT && includeSeeds) {
			double seedEnchanted = selected(Crop.ENCHANTED_SEEDS, sellOffer);
			if (seedEnchanted > 0) {
				// Skyblocker ratio: ~40% wheat / 60% seeds in the Cultivating counter.
				perCrop = perCrop * 0.4 + (seedEnchanted / Crop.ENCHANTED_RATIO) * 0.6;
			}
		}
		return perCrop;
	}

	private double selected(String productId, boolean sellOffer) {
		BazaarQuote quote = quotes.get(productId);
		if (quote == null || !quote.valid()) {
			return 0;
		}
		return quote.selectedPrice(sellOffer);
	}

	private void refreshIfNeeded(boolean force) {
		long now = System.currentTimeMillis();
		if (!force && now - lastRefresh < REFRESH_MS) {
			return;
		}
		if (refreshing) {
			return;
		}
		refreshing = true;
		lastRefresh = now;

		Set<String> ids = Set.of(
				Crop.WHEAT.enchantedBazaarId,
				Crop.CARROT.enchantedBazaarId,
				Crop.POTATO.enchantedBazaarId,
				Crop.NETHER_WART.enchantedBazaarId,
				Crop.PUMPKIN.enchantedBazaarId,
				Crop.MELON.enchantedBazaarId,
				Crop.COCOA_BEANS.enchantedBazaarId,
				Crop.SUGAR_CANE.enchantedBazaarId,
				Crop.CACTUS.enchantedBazaarId,
				Crop.MUSHROOM.enchantedBazaarId,
				Crop.ENCHANTED_BROWN_MUSHROOM,
				Crop.SUNFLOWER.enchantedBazaarId,
				Crop.MOONFLOWER.enchantedBazaarId,
				Crop.WILD_ROSE.enchantedBazaarId,
				Crop.ENCHANTED_SEEDS
		);

		CompletableFuture<?>[] tasks = ids.stream()
				.map(id -> CompletableFuture.runAsync(() -> fetchOne(id), executor))
				.toArray(CompletableFuture[]::new);

		CompletableFuture.allOf(tasks).whenComplete((_, error) -> {
			refreshing = false;
			if (error != null) {
				lastError = error.getMessage();
				FarmingProfitMod.LOGGER.warn("Cofl refresh failed: {}", error.toString());
			} else {
				lastError = null;
				FarmingProfitMod.LOGGER.info("Cofl Bazaar prices updated ({} items).", quotes.size());
			}
		});
	}

	private void fetchOne(String productId) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(SNAPSHOT.formatted(productId)))
					.timeout(Duration.ofSeconds(15))
					.header("User-Agent", "FarmingProfit/" + FarmingProfitMod.MOD_ID + " (Minecraft Fabric)")
					.GET()
					.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				FarmingProfitMod.LOGGER.warn("Cofl {} → HTTP {}", productId, response.statusCode());
				return;
			}
			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			double buy = json.has("buyPrice") ? json.get("buyPrice").getAsDouble() : 0;
			double sell = json.has("sellPrice") ? json.get("sellPrice").getAsDouble() : 0;
			quotes.put(productId, new BazaarQuote(productId, buy, sell, System.currentTimeMillis()));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			FarmingProfitMod.LOGGER.warn("Cofl {} : {}", productId, e.toString());
		}
	}
}
