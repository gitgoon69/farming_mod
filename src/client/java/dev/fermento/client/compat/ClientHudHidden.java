package dev.fermento.client.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * {@code Options.hideGui} on 26.1.2, {@code Hud#isHidden()} on 26.2.
 */
public final class ClientHudHidden {
	private static final MethodHandle GET = resolve();

	private ClientHudHidden() {
	}

	public static boolean hidden(Minecraft client) {
		try {
			return (boolean) GET.invoke(client);
		} catch (Throwable t) {
			return false;
		}
	}

	private static MethodHandle resolve() {
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		try {
			MethodHandle options = lookup.findGetter(Minecraft.class, "options", Options.class);
			MethodHandle hideGui = lookup.findGetter(Options.class, "hideGui", boolean.class);
			return MethodHandles.filterReturnValue(options, hideGui);
		} catch (NoSuchFieldException | IllegalAccessException ignored) {
		}
		try {
			Field guiField = Minecraft.class.getField("gui");
			Class<?> guiClass = guiField.getType();
			MethodHandle gui = lookup.unreflectGetter(guiField);
			Field hudField = guiClass.getField("hud");
			Class<?> hudClass = hudField.getType();
			MethodHandle hud = MethodHandles.filterReturnValue(gui, lookup.unreflectGetter(hudField));
			try {
				return MethodHandles.filterReturnValue(
						hud,
						lookup.findVirtual(hudClass, "isHidden", MethodType.methodType(boolean.class)));
			} catch (NoSuchMethodException e) {
				return MethodHandles.filterReturnValue(hud, lookup.findGetter(hudClass, "hidden", boolean.class));
			}
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}
