package dev.farmingprofit.client.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Current-screen access for 26.1.2 ({@code Minecraft.screen}/{@code setScreen})
 * and 26.2 ({@code Gui#screen()}/{@code Gui#setScreen}).
 */
public final class ClientScreens {
	private static final MethodHandle GET;
	private static final MethodHandle SET;

	static {
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		MethodHandle get;
		MethodHandle set;
		try {
			get = lookup.findGetter(Minecraft.class, "screen", Screen.class);
			set = lookup.findVirtual(Minecraft.class, "setScreen", MethodType.methodType(void.class, Screen.class));
		} catch (NoSuchFieldException | NoSuchMethodException | IllegalAccessException ignored) {
			try {
				Field guiField = Minecraft.class.getField("gui");
				Class<?> guiClass = guiField.getType();
				MethodHandle guiGetter = lookup.unreflectGetter(guiField);
				try {
					get = MethodHandles.filterReturnValue(
							guiGetter,
							lookup.findVirtual(guiClass, "screen", MethodType.methodType(Screen.class)));
				} catch (NoSuchMethodException e) {
					get = MethodHandles.filterReturnValue(
							guiGetter,
							lookup.findGetter(guiClass, "screen", Screen.class));
				}
				set = MethodHandles.filterArguments(
						lookup.findVirtual(guiClass, "setScreen", MethodType.methodType(void.class, Screen.class)),
						0,
						guiGetter);
			} catch (ReflectiveOperationException e) {
				throw new ExceptionInInitializerError(e);
			}
		}
		GET = get;
		SET = set;
	}

	private ClientScreens() {
	}

	@Nullable
	public static Screen current(Minecraft client) {
		try {
			return (Screen) GET.invoke(client);
		} catch (Throwable t) {
			throw new RuntimeException("Could not read the current screen", t);
		}
	}

	public static void set(Minecraft client, @Nullable Screen screen) {
		try {
			SET.invoke(client, screen);
		} catch (Throwable t) {
			throw new RuntimeException("Could not open a screen", t);
		}
	}

	public static boolean isOpen(Minecraft client) {
		return current(client) != null;
	}

	public static void close(Minecraft client) {
		if (isOpen(client)) {
			set(client, null);
		}
	}
}
