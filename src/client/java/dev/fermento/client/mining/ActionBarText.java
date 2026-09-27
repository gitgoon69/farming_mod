package dev.fermento.client.mining;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;

/**
 * Hypixel prints mining speed and fortune on the action bar while you gain Mining XP.
 */
public final class ActionBarText {
	private ActionBarText() {
	}

	public static String current(Minecraft client) {
		if (client == null) {
			return "";
		}
		Gui gui = client.gui;
		if (gui.overlayMessageTime <= 0 || gui.overlayMessageString == null) {
			return "";
		}
		return clean(gui.overlayMessageString.getString());
	}

	static String clean(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "";
		}
		String stripped = ChatFormatting.stripFormatting(raw);
		return stripped == null ? "" : stripped;
	}
}
