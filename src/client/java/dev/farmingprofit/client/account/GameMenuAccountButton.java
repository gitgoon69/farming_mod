package dev.farmingprofit.client.account;

import dev.farmingprofit.client.compat.ClientScreens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Account switcher entry on the title screen and the pause menu.
 */
public final class GameMenuAccountButton {
	private GameMenuAccountButton() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!isGameMenu(screen)) {
				return;
			}
			String name = AccountSwitchService.currentName(client);
			if (name.length() > 16) {
				name = name.substring(0, 15) + "…";
			}
			Screens.getWidgets(screen).add(Button.builder(
					Component.literal("Account: " + name),
					button -> ClientScreens.set(client, new AccountSwitchScreen(screen))
			).bounds(width - 176, 8, 168, 20).build());
		});
	}

	private static boolean isGameMenu(Screen screen) {
		Class<?> cls = screen.getClass();
		while (cls != null) {
			String simple = cls.getSimpleName();
			if ("TitleScreen".equals(simple) || "PauseScreen".equals(simple)) {
				return true;
			}
			cls = cls.getSuperclass();
		}
		return false;
	}
}
