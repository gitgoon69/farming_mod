package dev.stook.client.compat;

import java.lang.reflect.Field;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCharCallbackI;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * Polar turns the slash key into "." in chat. Put "/" back, including the
 * character that opens the chat line.
 */
public final class SlashChatFix {
	private static final int ARM_TICKS = 8;

	private static GLFWKeyCallback keyCallback;
	private static GLFWCharCallback charCallback;
	private static GLFWKeyCallbackI keyPrevious;
	private static GLFWCharCallbackI charPrevious;
	private static boolean screenHook;
	private static int keyDepth;
	private static int charDepth;
	private static int layoutSlashKey = GLFW.GLFW_KEY_SLASH;
	private static boolean layoutScanned;

	private static volatile int arm;
	private static boolean slashWasDown;
	private static boolean hadChat;
	private static String lastValue;
	private static int lastCursor;
	private static boolean writing;
	private static Field inputField;

	private SlashChatFix() {
	}

	public static void tick(Minecraft client) {
		ensureHooks(client);
		boolean down = slashDown(client);
		if (down && !slashWasDown) {
			arm = ARM_TICKS;
		}
		slashWasDown = down;
		apply(client);
		if (arm > 0) {
			arm--;
		}
	}

	private static void apply(Minecraft client) {
		EditBox input = chatInput(client);
		if (input == null) {
			hadChat = false;
			lastValue = null;
			lastCursor = 0;
			return;
		}
		if ((arm > 0 || slashWasDown) && !writing) {
			rewrite(input);
		}
		lastValue = input.getValue();
		lastCursor = input.getCursorPosition();
		hadChat = true;
	}

	private static void rewrite(EditBox input) {
		String value = input.getValue();
		if (value == null || value.indexOf('.') < 0) {
			return;
		}
		int cursor = input.getCursorPosition();
		String fixed = null;
		int caret = cursor;
		if (!hadChat || lastValue == null) {
			if (dotsOnly(value)) {
				fixed = "/".repeat(value.length());
				caret = fixed.length();
			}
		} else {
			int dots = insertedDots(lastValue, lastCursor, value, cursor);
			if (dots > 0) {
				fixed = value.substring(0, lastCursor) + "/".repeat(dots) + value.substring(lastCursor + dots);
				caret = lastCursor + dots;
			} else if (value.startsWith(lastValue)) {
				String extra = value.substring(lastValue.length());
				if (dotsOnly(extra)) {
					fixed = lastValue + "/".repeat(extra.length());
					caret = fixed.length();
				}
			} else if (".".equals(value) && ("/".equals(lastValue) || lastValue.isEmpty())) {
				fixed = "/";
				caret = 1;
			}
		}
		if (fixed == null || fixed.equals(value)) {
			return;
		}
		writing = true;
		try {
			input.setValue(fixed);
			input.setCursorPosition(Math.min(caret, fixed.length()));
			input.setHighlightPos(Math.min(caret, fixed.length()));
			lastValue = fixed;
			lastCursor = Math.min(caret, fixed.length());
		} finally {
			writing = false;
		}
	}

	private static boolean dotsOnly(String value) {
		if (value == null || value.isEmpty()) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			if (value.charAt(i) != '.') {
				return false;
			}
		}
		return true;
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
		if (!after.regionMatches(0, before, 0, head)) {
			return 0;
		}
		if (!after.regionMatches(cursorBefore + added, before, head, before.length() - head)) {
			return 0;
		}
		return added;
	}

	private static void armFromKey(int key, int scancode) {
		if (key == GLFW.GLFW_KEY_SLASH || key == GLFW.GLFW_KEY_KP_DIVIDE || key == layoutSlashKey) {
			arm = ARM_TICKS;
			return;
		}
		String name = GLFW.glfwGetKeyName(key, scancode);
		if ("/".equals(name)) {
			layoutSlashKey = key;
			arm = ARM_TICKS;
		}
	}

	private static void ensureHooks(Minecraft client) {
		ensureScreenHook();
		if (client.getWindow() == null) {
			return;
		}
		long handle = client.getWindow().handle();
		if (handle == 0L) {
			return;
		}
		if (!layoutScanned) {
			layoutScanned = true;
			scanLayoutSlash();
		}
		if (keyCallback == null) {
			keyCallback = new GLFWKeyCallback() {
				@Override
				public void invoke(long window, int key, int scancode, int action, int mods) {
					if (keyDepth > 0) {
						return;
					}
					keyDepth++;
					try {
						if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
							armFromKey(key, scancode);
						}
						GLFWKeyCallbackI prior = keyPrevious;
						if (prior != null && prior != keyCallback) {
							prior.invoke(window, key, scancode, action, mods);
						}
					} finally {
						keyDepth--;
					}
				}
			};
		}
		if (charCallback == null) {
			charCallback = new GLFWCharCallback() {
				@Override
				public void invoke(long window, int codepoint) {
					if (charDepth > 0) {
						return;
					}
					charDepth++;
					try {
						if (codepoint == '/' || (codepoint == '.' && slashDown(Minecraft.getInstance()))) {
							arm = ARM_TICKS;
							codepoint = '/';
						}
						GLFWCharCallbackI prior = charPrevious;
						if (prior != null && prior != charCallback) {
							prior.invoke(window, codepoint);
						}
					} finally {
						charDepth--;
					}
				}
			};
		}
		GLFWKeyCallbackI installedKey = GLFW.glfwSetKeyCallback(handle, keyCallback);
		if (installedKey == null) {
			GLFW.glfwSetKeyCallback(handle, null);
		} else if (installedKey != keyCallback) {
			keyPrevious = installedKey;
		}
		GLFWCharCallbackI installedChar = GLFW.glfwSetCharCallback(handle, charCallback);
		if (installedChar == null) {
			GLFW.glfwSetCharCallback(handle, null);
		} else if (installedChar != charCallback) {
			charPrevious = installedChar;
		}
	}

	private static void scanLayoutSlash() {
		for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
			if ("/".equals(GLFW.glfwGetKeyName(key, 0))) {
				layoutSlashKey = key;
				return;
			}
		}
	}

	private static void ensureScreenHook() {
		if (screenHook) {
			return;
		}
		screenHook = true;
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof ChatScreen)) {
				return;
			}
			ScreenEvents.afterTick(screen).register(open -> apply(client));
			ScreenEvents.beforeExtract(screen).register((open, graphics, mouseX, mouseY, delta) -> {
				ensureHooks(client);
				apply(client);
			});
		});
	}

	private static boolean slashDown(Minecraft client) {
		if (client.getWindow() == null) {
			return false;
		}
		long handle = client.getWindow().handle();
		if (handle == 0L) {
			return false;
		}
		return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_SLASH) == GLFW.GLFW_PRESS
				|| GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_KP_DIVIDE) == GLFW.GLFW_PRESS
				|| (layoutSlashKey != GLFW.GLFW_KEY_SLASH
						&& GLFW.glfwGetKey(handle, layoutSlashKey) == GLFW.GLFW_PRESS);
	}

	private static EditBox chatInput(Minecraft client) {
		Screen screen = ClientScreens.current(client);
		if (!(screen instanceof ChatScreen chat)) {
			return null;
		}
		if (screen.getFocused() instanceof EditBox focused && focused.canConsumeInput()) {
			return focused;
		}
		try {
			Field field = inputField;
			if (field == null) {
				field = ChatScreen.class.getDeclaredField("input");
				field.setAccessible(true);
				inputField = field;
			}
			Object value = field.get(chat);
			return value instanceof EditBox box ? box : null;
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return null;
		}
	}
}
