package dev.farmingprofit.client.hud;

import dev.farmingprofit.client.config.ModConfig;
import dev.farmingprofit.client.garden.PestCooldownTracker;
import dev.farmingprofit.client.gui.SettingsScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Gros titre central quand le cooldown pest du tab passe sous le seuil.
 */
public final class PestCooldownAlertHud {
	private PestCooldownAlertHud() {
	}

	public static void render(
			GuiGraphicsExtractor graphics,
			DeltaTracker deltaTracker,
			ModConfig config,
			PestCooldownTracker tracker
	) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || SettingsScreen.blockingHud(client)) {
			return;
		}
		if (!tracker.alerting(config)) {
			return;
		}

		int countdown = tracker.alertCountdownSeconds();
		Font font = client.font;
		int windowWidth = client.getWindow().getGuiScaledWidth();
		int windowHeight = client.getWindow().getGuiScaledHeight();

		String title = "PEST";
		String countText = String.valueOf(countdown);
		String hint = "Cooldown 2m50";

		int color = countdown <= 2 ? 0xFFFF1744 : 0xFFFFEA00;
		int y = Math.round(windowHeight * 0.22f);
		graphics.fill(0, y - 12, windowWidth, y + 78, 0xC0000000);

		drawCentered(graphics, font, title, windowWidth, y, 3.2f, 0xFFFF6D00);
		drawCentered(graphics, font, countText, windowWidth, y + 28, countdown <= 2 ? 6.4f : 5.6f, color);
		drawCentered(graphics, font, hint, windowWidth, y + 64, 1.4f, 0xFFFFF8E1);
	}

	private static void drawCentered(
			GuiGraphicsExtractor graphics,
			Font font,
			String text,
			int windowWidth,
			int y,
			float scale,
			int color
	) {
		int width = font.width(text);
		float x = (windowWidth - width * scale) / 2.0f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.text(font, text, 1, 1, 0xFF000000, false);
		graphics.text(font, text, 0, 0, color, false);
		graphics.pose().popMatrix();
	}
}
