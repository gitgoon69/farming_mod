package dev.farmingprofit.client.account;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import dev.farmingprofit.client.account.AccountSwitchService.SavedAccount;
import dev.farmingprofit.client.compat.ClientScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Paste a Minecraft access token or reuse a locally saved account.
 */
public final class AccountSwitchScreen extends Screen {
	private static final int PANEL_W = 440;
	private static final int PANEL_H = 268;

	private final Screen parent;
	private EditBox tokenBox;
	private String status = "Token of an account you own. Saved locally for quick switch.";
	private int statusColor = 0xFFB8C4A0;
	private boolean busy;
	private int panelX;
	private int panelY;
	private final List<Hit> hits = new ArrayList<>();

	public AccountSwitchScreen(Screen parent) {
		super(Component.literal("Switch account"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelX = (this.width - PANEL_W) / 2;
		panelY = (this.height - PANEL_H) / 2;
		this.clearWidgets();

		int x = panelX + 16;
		int y = panelY + 72;
		int w = PANEL_W - 32;
		String previous = tokenBox != null ? tokenBox.getValue() : "";
		tokenBox = new EditBox(this.font, x, y, w, 18, Component.literal("Access token"));
		tokenBox.setMaxLength(4096);
		tokenBox.setValue(previous);
		tokenBox.setHint(Component.literal("Minecraft access token"));
		tokenBox.setEditable(!busy);
		this.addRenderableWidget(tokenBox);
		this.setInitialFocus(tokenBox);

		this.addRenderableWidget(Button.builder(Component.literal(busy ? "Checking…" : "Connect"), button -> connect())
				.bounds(x, y + 26, 120, 20)
				.build()).active = !busy;
		this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.onClose())
				.bounds(x + 128, y + 26, 70, 20)
				.build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
	}

	@Override
	protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		hits.clear();
		Font font = this.font;
		Minecraft client = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
		int px = panelX;
		int py = panelY;

		graphics.fill(0, 0, this.width, this.height, 0xE0080C10);
		graphics.fill(px - 4, py - 4, px + PANEL_W + 4, py + PANEL_H + 4, 0xCC000000);
		graphics.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, 0xFF3D4A24);
		graphics.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFF141810);
		graphics.fillGradient(px, py, px + PANEL_W, py + 38, 0xFF3A4C22, 0xFF1A2214);
		graphics.fill(px, py + 38, px + PANEL_W, py + 39, 0xFFFFD54A);
		graphics.fill(px, py, px + 3, py + PANEL_H, 0xFFFFD54A);

		graphics.text(font, "Switch account", px + 14, py + 9, 0xFFFFD54A, true);
		String current = "Current: " + AccountSwitchService.currentName(client);
		graphics.text(font, current, px + 14, py + 21, 0xFFB8C4A0, false);
		graphics.text(font, "Access token", px + 16, py + 50, 0xFFB8C4A0, false);

		int listY = py + 128;
		graphics.text(font, "Saved accounts", px + 16, listY - 12, 0xFFB8C4A0, false);
		List<SavedAccount> saved = AccountSwitchService.get().saved();
		if (saved.isEmpty()) {
			graphics.text(font, "None yet — connect once to keep the account here.", px + 16, listY + 4, 0xFF8A9680, false);
		} else {
			int rowW = PANEL_W - 32;
			for (int i = 0; i < saved.size() && i < 5; i++) {
				SavedAccount account = saved.get(i);
				int y = listY + i * 22;
				boolean hover = mouseX >= px + 16 && mouseX < px + 16 + rowW && mouseY >= y && mouseY < y + 20;
				graphics.fill(px + 16, y, px + 16 + rowW, y + 20, hover ? 0xFF2C3A20 : 0xFF1A2216);
				graphics.text(font, account.name, px + 22, y + 6, 0xFFFFF3C4, false);
				graphics.fill(px + PANEL_W - 78, y + 3, px + PANEL_W - 22, y + 17, 0xFF4A3A14);
				graphics.text(font, "Use", px + PANEL_W - 64, y + 6, 0xFFFFD54A, false);
				graphics.fill(px + PANEL_W - 20, y + 3, px + PANEL_W - 16 + 12, y + 17, 0xFF5A403C);
				graphics.text(font, "x", px + PANEL_W - 16, y + 6, 0xFFFFF3C4, false);
				if (!busy) {
					hits.add(new Hit(px + 16, y, rowW - 36, 20, () -> connectSaved(account)));
					hits.add(new Hit(px + PANEL_W - 22, y, 18, 20, () -> removeSaved(account)));
				}
			}
		}

		graphics.fill(px, py + PANEL_H - 22, px + PANEL_W, py + PANEL_H, 0xFF10140E);
		String line = status;
		int max = PANEL_W - 24;
		if (font.width(line) > max) {
			while (line.length() > 3 && font.width(line + "…") > max) {
				line = line.substring(0, line.length() - 1);
			}
			line = line + "…";
		}
		graphics.text(font, line, px + 12, py + PANEL_H - 16, statusColor, false);

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || busy) {
			return false;
		}
		for (Hit hit : hits) {
			if (hit.contains(event.x(), event.y())) {
				hit.action.run();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (parent != null) {
			ClientScreens.set(Minecraft.getInstance(), parent);
			return;
		}
		super.onClose();
	}

	private void connect() {
		if (busy || tokenBox == null) {
			return;
		}
		startLogin(tokenBox.getValue());
	}

	private void connectSaved(SavedAccount account) {
		if (busy) {
			return;
		}
		startLogin(account.token);
	}

	private void startLogin(String raw) {
		busy = true;
		status = "Checking token with Minecraft Services…";
		statusColor = 0xFFFFF176;
		this.rebuildWidgets();
		AccountSwitchService.get().login(raw, Minecraft.getInstance(), (ok, message) -> {
			busy = false;
			status = message;
			statusColor = ok ? 0xFFC5E1A5 : 0xFFFFAB91;
			if (ok && tokenBox != null) {
				tokenBox.setValue("");
			}
			this.rebuildWidgets();
		});
	}

	private void removeSaved(SavedAccount account) {
		AccountSwitchService.get().remove(account.uuid);
		status = "Removed " + account.name;
		statusColor = 0xFFFFF176;
	}

	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}
	}
}
