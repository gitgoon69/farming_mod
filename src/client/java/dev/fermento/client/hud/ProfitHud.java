package dev.fermento.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.fermento.client.compat.ClientHudHidden;
import dev.fermento.client.config.ModConfig;
import dev.fermento.client.garden.Crop;
import dev.fermento.client.garden.FarmingTracker;
import dev.fermento.client.garden.SkyblockItems;
import dev.fermento.client.gui.SettingsScreen;
import dev.fermento.client.prices.CoflBazaarService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class ProfitHud {
	private static final int TITLE = 0xFFFFD54A;
	private static final int LABEL = 0xFFBDBDBD;
	private static final int VALUE = 0xFFFFF8E1;
	private static final int GOLD = 0xFFFFC107;
	private static final int MUTED = 0xFF9E9E9E;

	private static Bounds lastBounds = new Bounds(8, 48, 160, 80);

	private ProfitHud() {
	}

	public static Bounds lastBounds() {
		return lastBounds;
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ModConfig config, FarmingTracker tracker, CoflBazaarService prices) {
		render(graphics, config, tracker, prices, false);
	}

	public static void render(GuiGraphicsExtractor graphics, ModConfig config, FarmingTracker tracker, CoflBazaarService prices, boolean force) {
		Minecraft client = Minecraft.getInstance();
		if (!force) {
			if (ClientHudHidden.hidden(client) || SettingsScreen.blockingHud(client)) {
				return;
			}
			if (!config.hudEnabled || client.player == null) {
				return;
			}
			if (!Crop.isFarmingTool(SkyblockItems.skyblockId(client.player.getMainHandItem()))) {
				return;
			}
		}

		FarmingTracker.Snapshot snap = tracker.snapshot(config, prices);
		Font font = client.font;
		List<String> lines = buildLines(snap);

		int pad = 4;
		int lineH = 10;
		int width = 0;
		for (String line : lines) {
			width = Math.max(width, font.width(stripCodes(line)));
		}
		int height = lines.size() * lineH + pad * 2;
		int x = config.hudX;
		int y = config.hudY;

		lastBounds = new Bounds(x - pad, y - pad, width + pad * 2, height);

		int drawY = y;
		for (int i = 0; i < lines.size(); i++) {
			int color = i == 0 ? TITLE : VALUE;
			if (lines.get(i).startsWith("  ")) {
				color = LABEL;
			}
			if (lines.get(i).contains("coins/h") || lines.get(i).startsWith("Session")) {
				color = GOLD;
			}
			if (snap.paused() && lines.get(i).contains("AFK")) {
				color = MUTED;
			}
			graphics.text(font, lines.get(i), x, drawY, color, true);
			drawY += lineH;
		}
	}

	public record Bounds(int x, int y, int width, int height) {
		public boolean contains(double mouseX, double mouseY) {
			return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
		}
	}

	private static List<String> buildLines(FarmingTracker.Snapshot snap) {
		List<String> lines = new ArrayList<>();
		lines.add("Fermento");

		if (snap.crop() == null) {
			lines.add("Hold a crop hoe");
			return lines;
		}

		Crop crop = snap.crop();
		lines.add(crop.displayName + (snap.paused() ? "  (AFK)" : ""));

		if (snap.counter() >= 0) {
			lines.add("Counter: " + formatCount(snap.counter()));
		} else {
			lines.add("Cultivating required");
		}

		lines.add("Crops/min: " + formatCount(Math.round(snap.adjustedCropsPerMinute())));
		lines.add("Blocks/s: " + String.format(Locale.US, "%.1f", snap.blocksPerSecond()));

		if (!snap.pricesReady()) {
			lines.add("Cofl prices: loading...");
			if (snap.priceError() != null) {
				lines.add(snap.priceError());
			}
		} else if (snap.unitPrice() <= 0) {
			lines.add("Prices unavailable");
		} else {
			String mode = snap.sellOffer() ? "offer" : "instant";
			lines.add("Coins/h: " + formatCoins(snap.coinsPerHour()) + "  (" + mode + ")");
			lines.add("Session: " + formatCoins(snap.sessionProfit())
					+ "  |  " + formatDuration(snap.activeMs()));
			if (snap.sessionCoinsPerHour() > 0) {
				lines.add("Session/h: " + formatCoins(snap.sessionCoinsPerHour()));
			}
			lines.add(formatCoins(snap.unitPrice()) + "/crop");
		}
		return lines;
	}

	public static String formatCoins(double value) {
		if (value >= 1_000_000_000) {
			return String.format(Locale.US, "%.2fB", value / 1_000_000_000.0);
		}
		if (value >= 1_000_000) {
			return String.format(Locale.US, "%.2fM", value / 1_000_000.0);
		}
		if (value >= 10_000) {
			return String.format(Locale.US, "%.1fk", value / 1_000.0);
		}
		if (value >= 1000) {
			return String.format(Locale.US, "%.0f", value);
		}
		if (value >= 10) {
			return String.format(Locale.US, "%.1f", value);
		}
		return String.format(Locale.US, "%.2f", value);
	}

	public static String formatCount(long value) {
		if (value >= 1_000_000) {
			return String.format(Locale.US, "%.2fM", value / 1_000_000.0);
		}
		if (value >= 10_000) {
			return String.format(Locale.US, "%.1fk", value / 1_000.0);
		}
		return String.format(Locale.US, "%,d", value);
	}

	public static String formatDuration(long ms) {
		long totalSec = Math.max(0, ms / 1000);
		long h = totalSec / 3600;
		long m = (totalSec % 3600) / 60;
		long s = totalSec % 60;
		if (h > 0) {
			return String.format(Locale.US, "%dh %02dm", h, m);
		}
		if (m > 0) {
			return String.format(Locale.US, "%dm %02ds", m, s);
		}
		return s + "s";
	}

	private static String stripCodes(String text) {
		return text;
	}
}
