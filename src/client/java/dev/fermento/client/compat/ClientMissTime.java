package dev.fermento.client.compat;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;

/**
 * {@code Minecraft.missTime} (26.1.2) or the equivalent field if moved in 26.2.
 */
public final class ClientMissTime {
	private static final Field FIELD = find();

	private ClientMissTime() {
	}

	public static void clear(Minecraft client) {
		if (FIELD == null) {
			return;
		}
		try {
			FIELD.setInt(client, 0);
		} catch (IllegalAccessException ignored) {
		}
	}

	private static Field find() {
		try {
			return Minecraft.class.getField("missTime");
		} catch (NoSuchFieldException ignored) {
		}
		for (String name : new String[] {"destroyDelay", "breakDelay"}) {
			try {
				Field field = Minecraft.class.getField(name);
				if (field.getType() == int.class) {
					return field;
				}
			} catch (NoSuchFieldException ignored) {
			}
		}
		return null;
	}
}
