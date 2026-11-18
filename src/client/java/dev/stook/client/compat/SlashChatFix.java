package dev.stook.client.compat;

import java.lang.reflect.Field;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * Polar types "." when the slash key is pressed. Put "/" in chat instead,
 * including when that key opens the chat line.
 */
public final class SlashChatFix {
	private static final int OPEN_WAIT_TICKS = 5;

	private static GLFWKeyCallback callback;
	private static boolean installed;
	private static volatile boolean slashEdge;
	private static boolean slashWasDown;
	private static int pendingOpen;
	private static boolean hadChat;
	private static String lastValue;
	private static int lastCursor;
	private static Field inputField;

	private SlashChatFix() {
	}

	public static void tick(Minecraft client) {
		install(client);
		boolean down = keyDown(client);
		if (down && !slashWasDown) {
			slashEdge = true;
		}
		slashWasDown = down;

		boolean pressedTick = slashEdge;
		if (slashEdge) {
			pendingOpen = OPEN_WAIT_TICKS;
			slashEdge = false;
		}

		EditBox input = chatInput(client);
		if (input != null) {
			String value = input.getValue();
			int cursor = input.getCursorPosition();
			if (!hadChat && pendingOpen > 0 && ".".equals(value)) {
				input.setValue("/");
				input.setCursorPosition(1);
				input.setHighlightPos(1);
				value = "/";
				cursor = 1;
			} else if (hadChat && pressedTick) {
				int dots = insertedDots(lastValue, lastCursor, value, cursor);
				if (dots > 0) {
					int at = lastCursor;
					String fixed = value.substring(0, at) + "/".repeat(dots) + value.substring(at + dots);
					input.setValue(fixed);
					int caret = at + dots;
					input.setCursorPosition(caret);
					input.setHighlightPos(caret);
					value = fixed;
					cursor = caret;
				}
			}
			lastValue = value;
			lastCursor = cursor;
			hadChat = true;
			pendingOpen = 0;
			return;
		}

		hadChat = false;
		lastValue = null;
		lastCursor = 0;
		if (pendingOpen > 0) {
			pendingOpen--;
		}
	}

	private static int insertedDots(String before, int cursorBefore, String after, int cursorAfter) {
		if (before == null || after == null || cursorBefore < 0) {
			return 0;
		}
		int added = after.length() - before.length();
		if (added <= 0 || cursorAfter != cursorBefore + added || cursorBefore + added > after.length()) {
			return 0;
		}
		for (int i = 0; i < added; i++) {
			if (after.charAt(cursorBefore + i) != '.') {
				return 0;
			}
		}
		int head = Math.min(cursorBefore, before.length());
		if (!after.substring(0, cursorBefore).equals(before.substring(0, head))) {
			return 0;
		}
		if (!after.substring(cursorBefore + added).equals(before.substring(head))) {
			return 0;
		}
		return added;
	}

	private static void install(Minecraft client) {
		if (installed || client.getWindow() == null) {
			return;
		}
		long handle = client.getWindow().handle();
		if (handle == 0L) {
			return;
		}
		GLFWKeyCallbackI[] previous = new GLFWKeyCallbackI[1];
		callback = new GLFWKeyCallback() {
			@Override
			public void invoke(long window, int key, int scancode, int action, int mods) {
				if (key == GLFW.GLFW_KEY_SLASH && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
					slashEdge = true;
				}
				GLFWKeyCallbackI prior = previous[0];
				if (prior != null) {
					prior.invoke(window, key, scancode, action, mods);
				}
			}
		};
		previous[0] = GLFW.glfwSetKeyCallback(handle, callback);
		installed = true;
	}

	private static boolean keyDown(Minecraft client) {
		if (client.getWindow() == null) {
			return false;
		}
		long handle = client.getWindow().handle();
		return handle != 0L && GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_SLASH) == GLFW.GLFW_PRESS;
	}

	private static EditBox chatInput(Minecraft client) {
		if (!(ClientScreens.current(client) instanceof ChatScreen chat)) {
			return null;
		}
		try {
			Field field = inputField;
			if (field == null) {
				for (Field candidate : ChatScreen.class.getDeclaredFields()) {
					if (candidate.getType() == EditBox.class) {
						candidate.setAccessible(true);
						field = candidate;
						inputField = candidate;
						break;
					}
				}
			}
			if (field == null) {
				return null;
			}
			return (EditBox) field.get(chat);
		} catch (IllegalAccessException e) {
			return null;
		}
	}
}
