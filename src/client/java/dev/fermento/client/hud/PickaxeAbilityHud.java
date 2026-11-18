package dev.fermento.client.hud;

import dev.fermento.client.compat.ClientHudHidden;
import dev.fermento.client.gui.SettingsScreen;
import dev.fermento.client.mining.PickaxeAbilityService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Pickaxe ability timer above the hotbar, and a large title when it fires.
 */
public final class PickaxeAbilityHud {
	private PickaxeAbilityHud() {
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, PickaxeAbilityService pickaxe) {
		Minecraft client = Minecraft.getInstance();
		if (ClientHudHidden.hidden(client) || client.player == null || SettingsScreen.blockingHud(client)) {
			return;
		}
		PickaxeAbilityService.View view = pickaxe.view(client);
		if (!view.visible()) {
			return;
		}

		Font font = client.font;
		int windowWidth = client.getWindow().getGuiScaledWidth();
		int windowHeight = client.getWindow().getGuiScaledHeight();
		drawTimer(graphics, font, view, windowWidth, windowHeight);
		if (view.launched()) {
			drawLaunch(graphics, font, view.name(), windowWidth, windowHeight);
		}
	}

	private static void drawTimer(GuiGraphicsExtractor graphics, Font font, PickaxeAbilityService.View view, int windowWidth, int windowHeight) {
		String name = view.name();
		String time = view.timerText();
		boolean ready = "READY".equals(time);
		float timeScale = 2.0f;
		int timeWidth = Math.round(font.width(time) * timeScale);
		int panelWidth = Math.max(Math.max(font.width(name), timeWidth) + 18, 92);
		int panelHeight = 40;
		int x = (windowWidth - panelWidth) / 2;
		int y = windowHeight - 86;

		graphics.fill(x - 1, y - 1, x + panelWidth + 1, y + panelHeight + 1, 0xFF3D4A24);
		graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xE010140E);
		graphics.fill(x, y, x + panelWidth, y + 2, ready ? 0xFF7CFF6B : 0xFFFFD54A);

		int nameX = x + (panelWidth - font.width(name)) / 2;
		graphics.text(font, name, nameX, y + 5, 0xFFFFF3C4, false);

		int timeColor = ready ? 0xFF7CFF6B : (view.remainingMs() <= 5_000L ? 0xFFFFEA00 : 0xFFFFF8E1);
		float timeX = x + (panelWidth - timeWidth) / 2.0f;
		graphics.pose().pushMatrix();
		graphics.pose().translate(timeX, y + 16);
		graphics.pose().scale(timeScale, timeScale);
		graphics.text(font, time, 0, 0, timeColor, true);
		graphics.pose().popMatrix();

		int barX = x + 6;
		int barY = y + panelHeight - 5;
		int barWidth = panelWidth - 12;
		graphics.fill(barX, barY, barX + barWidth, barY + 2, 0xFF2A2418);
		float progress = view.totalMs() <= 0L
				? (ready ? 1f : 0f)
				: 1f - Mth.clamp(view.remainingMs() / (float) view.totalMs(), 0f, 1f);
		int filled = Math.round(barWidth * progress);
		if (filled > 0) {
			graphics.fill(barX, barY, barX + filled, barY + 2, ready ? 0xFF7CFF6B : 0xFFFFD54A);
		}
	}

	private static void drawLaunch(GuiGraphicsExtractor graphics, Font font, String name, int windowWidth, int windowHeight) {
		int y = Math.round(windowHeight * 0.28f);
		graphics.fill(0, y - 14, windowWidth, y + 62, 0xC0000000);
		drawCentered(graphics, font, "ABILITY", windowWidth, y, 2.4f, 0xFFFFD54A);
		float nameScale = 4.8f;
		while (font.width(name) * nameScale > windowWidth - 24 && nameScale > 2.2f) {
			nameScale -= 0.2f;
		}
		drawCentered(graphics, font, name, windowWidth, y + 26, nameScale, 0xFFFFF8E1);
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
