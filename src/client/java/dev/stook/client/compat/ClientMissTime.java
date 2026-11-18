package dev.stook.client.compat;

import java.lang.reflect.Field;

import dev.stook.StookMod;
import net.minecraft.client.Minecraft;

/**
 * {@code Minecraft.missTime} is protected on 26.1.2 and 26.2.
 * A public-only lookup never sees it, so the post-menu attack delay stays stuck.
 */
public final class ClientMissTime {
	private static final Field FIELD = find();

	private ClientMissTime() {
	}

	public static void clear(Minecraft client) {
		if (FIELD == null || client == null) {
			return;
		}
		try {
			FIELD.setInt(client, 0);
		} catch (IllegalAccessException ignored) {
		}
	}

	private static Field find() {
		for (String name : new String[] {"missTime", "destroyDelay", "breakDelay"}) {
			try {
				Field field = Minecraft.class.getDeclaredField(name);
				if (field.getType() != int.class) {
					continue;
				}
				field.setAccessible(true);
				return field;
			} catch (ReflectiveOperationException | RuntimeException ignored) {
			}
		}
		StookMod.LOGGER.warn("Could not reach Minecraft.missTime; attack resume after a menu will stay delayed.");
		return null;
	}
}
