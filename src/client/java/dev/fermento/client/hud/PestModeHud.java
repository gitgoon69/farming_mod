package dev.fermento.client.hud;

import dev.fermento.client.compat.ClientHudHidden;
import dev.fermento.client.config.ModConfig;
import dev.fermento.client.gui.SettingsScreen;
import dev.fermento.client.loadout.PestLoadoutService;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Rainbow banner for Pest Mode so you do not forget to switch back to the farm loadout.
 */
public final class PestModeHud {
	private static final String TEXT = "Pest Mode";
	private static final float SCALE = 2.0f;

	private PestModeHud() {
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, ModConfig config, PestLoadoutService pest) {
		Minecraft client = Minecraft.getInstance();
		if (ClientHudHidden.hidden(client) || client.player == null || SettingsScreen.blockingHud(client)) {
			return;
		}
		if (!pest.pestMode()) {
			return;
		}

		Font font = client.font;
		int windowWidth = client.getWindow().getGuiScaledWidth();
		float hueBase = (System.currentTimeMillis() % 4000L) / 4000.0f;

		int textWidth = font.width(TEXT);
		int x = Math.round((windowWidth - textWidth * SCALE) / 2.0f);
		int y = 18;

		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(SCALE, SCALE);

		int drawX = 0;
		for (int i = 0; i < TEXT.length(); i++) {
			String ch = TEXT.substring(i, i + 1);
			int color = rainbow((hueBase + i * 0.08f) % 1.0f);
			graphics.text(font, ch, drawX, 0, color, true);
			drawX += font.width(ch);
		}

		graphics.pose().popMatrix();
	}

	private static int rainbow(float hue) {
		float h = hue * 6.0f;
		int sector = (int) h;
		float f = h - sector;
		float q = 1.0f - f;
		float r;
		float g;
		float b;
		switch (sector) {
			case 0 -> {
				r = 1.0f;
				g = f;
				b = 0.0f;
			}
			case 1 -> {
				r = q;
				g = 1.0f;
				b = 0.0f;
			}
			case 2 -> {
				r = 0.0f;
				g = 1.0f;
				b = f;
			}
			case 3 -> {
				r = 0.0f;
				g = q;
				b = 1.0f;
			}
			case 4 -> {
				r = f;
				g = 0.0f;
				b = 1.0f;
			}
			default -> {
				r = 1.0f;
				g = 0.0f;
				b = q;
			}
		}
		int ri = Math.round(r * 255);
		int gi = Math.round(g * 255);
		int bi = Math.round(b * 255);
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}
}
