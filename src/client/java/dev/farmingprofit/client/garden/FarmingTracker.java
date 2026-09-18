package dev.farmingprofit.client.garden;

import java.util.ArrayDeque;
import java.util.Deque;

import dev.farmingprofit.client.config.ModConfig;
import dev.farmingprofit.client.prices.CoflBazaarService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Counts crops via the Cultivating counter (Skyblocker) and active farm time (SkyHanni).
 */
public final class FarmingTracker {
	private static final long WINDOW_MS = 5_000L;

	private final Deque<Sample> counterWindow = new ArrayDeque<>();
	private final Deque<Long> blockBreaks = new ArrayDeque<>();

	private Crop currentCrop;
	private Crop lastBrokenCrop;
	private long lastBrokenMs;
	private boolean holdingFarmingTool;
	private long sessionStartCounter = -1;
	private long lastCounter = -1;
	private long sessionCrops;
	private long activeMs;
	private long lastActivityMs;
	private long lastTickMs;
	private boolean paused = true;

	public void tick(Minecraft client, ModConfig config) {
		long now = System.currentTimeMillis();
		prune(now);

		if (client.player == null || client.level == null) {
			holdingFarmingTool = false;
			pauseIfIdle(now, config);
			lastTickMs = now;
			return;
		}

		ItemStack held = client.player.getMainHandItem();
		String toolId = SkyblockItems.skyblockId(held);
		holdingFarmingTool = Crop.isFarmingTool(toolId);
		if (!holdingFarmingTool) {
			pauseIfIdle(now, config);
			lastTickMs = now;
			return;
		}

		Crop toolCrop = Crop.fromToolId(toolId);
		if (toolCrop == Crop.SUNFLOWER || toolCrop == Crop.MOONFLOWER) {
			toolCrop = Crop.resolveTimeFlower(toolCrop, client.level.getDefaultClockTime());
		}

		Crop brokenCrop = null;
		if (lastBrokenCrop != null && now - lastBrokenMs <= 3_000L) {
			brokenCrop = lastBrokenCrop;
		}

		Crop crop = brokenCrop != null ? brokenCrop : toolCrop;
		if (crop != null && crop != currentCrop) {
			resetSession(crop);
		}

		long counter = SkyblockItems.cultivatingOrCounter(held);
		if (counter >= 0) {
			if (sessionStartCounter < 0) {
				sessionStartCounter = counter;
				lastCounter = counter;
			}
			if (counter != lastCounter) {
				if (counter > lastCounter) {
					sessionCrops += counter - lastCounter;
				} else if (counter < lastCounter) {
					sessionStartCounter = counter;
					sessionCrops = 0;
					counterWindow.clear();
				}
				lastCounter = counter;
				lastActivityMs = now;
				paused = false;
			}
			if (counterWindow.isEmpty() || counterWindow.peekLast().value != counter) {
				counterWindow.addLast(new Sample(counter, now));
			}
		}

		if (!paused && lastActivityMs > 0) {
			long idle = now - lastActivityMs;
			if (idle > config.afkTimeoutSeconds * 1000L) {
				paused = true;
			} else if (lastTickMs > 0) {
				activeMs += Math.max(0, now - lastTickMs);
			}
		}

		lastTickMs = now;
	}

	public void onBlockBroken(Crop crop) {
		long now = System.currentTimeMillis();
		blockBreaks.addLast(now);
		lastActivityMs = now;
		paused = false;
		if (crop != null) {
			lastBrokenCrop = crop;
			lastBrokenMs = now;
			if (crop != currentCrop) {
				resetSession(crop);
			}
		}
	}

	public boolean holdingFarmingTool() {
		return holdingFarmingTool;
	}

	public void resetSession() {
		resetSession(currentCrop);
	}

	private void resetSession(Crop crop) {
		currentCrop = crop;
		if (crop == null) {
			lastBrokenCrop = null;
			lastBrokenMs = 0;
		}
		counterWindow.clear();
		sessionStartCounter = -1;
		lastCounter = -1;
		sessionCrops = 0;
		activeMs = 0;
		lastActivityMs = 0;
		paused = true;
	}

	public Snapshot snapshot(ModConfig config, CoflBazaarService prices) {
		Crop crop = currentCrop;
		float cropsPerMinute = cropsPerMinute();
		double bps = blocksPerSecond();

		boolean replenish = false;
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			replenish = SkyblockItems.hasReplenish(client.player.getMainHandItem());
		}

		float adjustedCropsPerMin = cropsPerMinute;
		if (replenish && crop != null && crop.replenishCrop && bps > 0) {
			adjustedCropsPerMin = Math.max(0, cropsPerMinute - (float) (bps * 60.0));
		}

		boolean sellOffer = config.useSellOffer();
		double unit = crop == null ? 0 : prices.unitPrice(crop, sellOffer, config.includeSeeds);
		double coinsPerHour = unit * adjustedCropsPerMin * 60.0;
		double sessionProfit = unit * sessionCrops;
		double sessionHours = activeMs / 3_600_000.0;
		double sessionCoinsPerHour = sessionHours > 0 ? sessionProfit / sessionHours : 0;

		return new Snapshot(
				crop,
				lastCounter,
				cropsPerMinute,
				adjustedCropsPerMin,
				bps,
				coinsPerHour,
				sessionCoinsPerHour,
				sessionProfit,
				sessionCrops,
				activeMs,
				paused,
				unit,
				sellOffer,
				prices.ready(),
				prices.lastError()
		);
	}

	private float cropsPerMinute() {
		if (counterWindow.size() < 2) {
			return 0;
		}
		Sample first = counterWindow.peekFirst();
		Sample last = counterWindow.peekLast();
		long dt = last.time - first.time;
		if (dt <= 0) {
			return 0;
		}
		return (float) (last.value - first.value) / dt * 60_000f;
	}

	private double blocksPerSecond() {
		if (blockBreaks.size() < 2) {
			return 0;
		}
		long first = blockBreaks.peekFirst();
		long last = blockBreaks.peekLast();
		long dt = last - first;
		if (dt <= 0) {
			return 0;
		}
		return (blockBreaks.size() - 1) / (dt / 1000.0);
	}

	private void prune(long now) {
		while (!counterWindow.isEmpty() && counterWindow.peekFirst().time + WINDOW_MS < now) {
			counterWindow.removeFirst();
		}
		while (!blockBreaks.isEmpty() && blockBreaks.peekFirst() + WINDOW_MS < now) {
			blockBreaks.removeFirst();
		}
	}

	private void pauseIfIdle(long now, ModConfig config) {
		if (!paused && lastActivityMs > 0 && now - lastActivityMs > config.afkTimeoutSeconds * 1000L) {
			paused = true;
		}
	}

	private record Sample(long value, long time) {
	}

	public record Snapshot(
			Crop crop,
			long counter,
			float cropsPerMinute,
			float adjustedCropsPerMinute,
			double blocksPerSecond,
			double coinsPerHour,
			double sessionCoinsPerHour,
			double sessionProfit,
			long sessionCrops,
			long activeMs,
			boolean paused,
			double unitPrice,
			boolean sellOffer,
			boolean pricesReady,
			String priceError
	) {
	}
}
