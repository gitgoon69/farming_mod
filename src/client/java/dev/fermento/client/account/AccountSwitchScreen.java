package dev.fermento.client.account;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import dev.fermento.client.account.AccountSwitchService.SavedAccount;
import dev.fermento.client.account.PrismAccountSource.PrismAccount;
import dev.fermento.client.compat.ClientScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Switch account from Prism's local accounts, a pasted token, or a saved session.
 */
public final class AccountSwitchScreen extends Screen {
	private static final int PANEL_W = 440;
	private static final int PANEL_H = 300;

	private final Screen parent;
	private EditBox tokenBox;
	private String status = "Choose a Prism account, or paste a token.";
	private int statusColor = 0xFFB8C4A0;
	private boolean busy;
	private int panelX;
	private int panelY;
	private List<PrismAccount> prismAccounts = List.of();
	private final List<Hit> hits = new ArrayList<>();

	public AccountSwitchScreen(Screen parent) {
		super(Component.literal("Switch account"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		prismAccounts = PrismAccountSource.load();
		if (prismAccounts.isEmpty()) {
			status = status.startsWith("Checking") || status.startsWith("Connected") || status.startsWith("Token")
					|| status.startsWith("Could") || status.startsWith("Removed")
					? status
					: "Token of an account you own. Saved locally for quick switch.";
		} else if (!busy && (status.startsWith("Choose") || status.contains("paste"))) {
			status = "Click a Prism account — no need to copy the token.";
			statusColor = 0xFFB8C4A0;
		}

		panelX = (this.width - PANEL_W) / 2;
		panelY = (this.height - PANEL_H) / 2;
		this.clearWidgets();

		int tokenY = tokenFieldY();
		int x = panelX + 16;
		int w = PANEL_W - 32;
		String previous = tokenBox != null ? tokenBox.getValue() : "";
		tokenBox = new EditBox(this.font, x, tokenY, w, 18, Component.literal("Access token"));
		tokenBox.setMaxLength(4096);
		tokenBox.setValue(previous);
		tokenBox.setHint(Component.literal(prismAccounts.isEmpty()
				? "Minecraft access token"
				: "Optional — only if the account is not in Prism"));
		tokenBox.setEditable(!busy);
		this.addRenderableWidget(tokenBox);
		if (prismAccounts.isEmpty()) {
			this.setInitialFocus(tokenBox);
		}

		this.addRenderableWidget(Button.builder(Component.literal(busy ? "Checking…" : "Connect"), button -> connect())
				.bounds(x, tokenY + 26, 120, 20)
				.build()).active = !busy;
		this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.onClose())
				.bounds(x + 128, tokenY + 26, 70, 20)
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
		graphics.text(font, "Current: " + AccountSwitchService.currentName(client), px + 14, py + 21, 0xFFB8C4A0, false);

		int y = py + 48;
		if (!prismAccounts.isEmpty()) {
			graphics.text(font, "Prism accounts", px + 16, y, 0xFFB8C4A0, false);
			y += 12;
			String currentName = AccountSwitchService.currentName(client);
			String currentUuid = currentUuid(client);
			int shown = Math.min(prismAccounts.size(), 5);
			for (int i = 0; i < shown; i++) {
				PrismAccount account = prismAccounts.get(i);
				boolean current = account.isCurrent(currentName, currentUuid);
				drawRow(graphics, font, px + 16, y, account.name(), current ? "Now" : "Use", mouseX, mouseY, current);
				if (!busy && !current) {
					hits.add(new Hit(px + 16, y, PANEL_W - 32, 20, () -> connectPrism(account)));
				}
				y += 22;
			}
			if (prismAccounts.size() > 5) {
				graphics.text(font, "+" + (prismAccounts.size() - 5) + " more in Prism", px + 16, y, 0xFF8A9680, false);
				y += 12;
			}
		}

		graphics.text(font, prismAccounts.isEmpty() ? "Access token" : "Or paste a token",
				px + 16, tokenFieldY() - 14, 0xFFB8C4A0, false);

		if (prismAccounts.isEmpty()) {
			int listY = py + 148;
			graphics.text(font, "Saved accounts", px + 16, listY - 12, 0xFFB8C4A0, false);
			List<SavedAccount> saved = AccountSwitchService.get().saved();
			if (saved.isEmpty()) {
				graphics.text(font, "None yet — connect once to keep the account here.", px + 16, listY + 4, 0xFF8A9680, false);
			} else {
				for (int i = 0; i < saved.size() && i < 4; i++) {
					SavedAccount account = saved.get(i);
					int rowY = listY + i * 22;
					drawRow(graphics, font, px + 16, rowY, account.name, "Use", mouseX, mouseY, false);
					graphics.fill(px + PANEL_W - 20, rowY + 3, px + PANEL_W - 4, rowY + 17, 0xFF5A403C);
					graphics.text(font, "x", px + PANEL_W - 16, rowY + 6, 0xFFFFF3C4, false);
					if (!busy) {
						hits.add(new Hit(px + 16, rowY, PANEL_W - 54, 20, () -> startLogin(account.token, false)));
						hits.add(new Hit(px + PANEL_W - 22, rowY, 18, 20, () -> removeSaved(account)));
					}
				}
			}
		}

		graphics.fill(px, py + PANEL_H - 22, px + PANEL_W, py + PANEL_H, 0xFF10140E);
		graphics.text(font, fit(font, status, PANEL_W - 24), px + 12, py + PANEL_H - 16, statusColor, false);

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void drawRow(
			GuiGraphicsExtractor graphics,
			Font font,
			int x,
			int y,
			String name,
			String action,
			int mouseX,
			int mouseY,
			boolean current
	) {
		int rowW = PANEL_W - 32;
		boolean hover = !current && mouseX >= x && mouseX < x + rowW && mouseY >= y && mouseY < y + 20;
		graphics.fill(x, y, x + rowW, y + 20, current ? 0xFF2A341C : (hover ? 0xFF2C3A20 : 0xFF1A2216));
		graphics.text(font, name, x + 6, y + 6, current ? 0xFFC5E1A5 : 0xFFFFF3C4, false);
		int bw = font.width(action) + 14;
		int bx = x + rowW - bw - 4;
		graphics.fill(bx, y + 3, bx + bw, y + 17, current ? 0xFF3A4C22 : 0xFF4A3A14);
		graphics.text(font, action, bx + 7, y + 6, 0xFFFFD54A, false);
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

	private int tokenFieldY() {
		int y = panelY + 62;
		if (!prismAccounts.isEmpty()) {
			y += Math.min(prismAccounts.size(), 5) * 22;
			if (prismAccounts.size() > 5) {
				y += 12;
			}
			y += 8;
		}
		return y;
	}

	private void connect() {
		if (busy || tokenBox == null) {
			return;
		}
		startLogin(tokenBox.getValue(), false);
	}

	private void connectPrism(PrismAccount account) {
		startLogin(account.token(), true);
	}

	private void startLogin(String raw, boolean fromPrism) {
		if (busy) {
			return;
		}
		busy = true;
		status = "Checking token with Minecraft Services…";
		statusColor = 0xFFFFF176;
		this.rebuildWidgets();
		AccountSwitchService.get().login(raw, Minecraft.getInstance(), (ok, message) -> {
			busy = false;
			if (!ok && fromPrism && message != null && message.toLowerCase().contains("expired")) {
				status = message + " Launch this account once in Prism to refresh.";
			} else {
				status = message;
			}
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

	private static String currentUuid(Minecraft client) {
		User user = client.getUser();
		return user == null || user.getProfileId() == null ? "" : user.getProfileId().toString();
	}

	private static String fit(Font font, String line, int max) {
		if (font.width(line) <= max) {
			return line;
		}
		while (line.length() > 3 && font.width(line + "…") > max) {
			line = line.substring(0, line.length() - 1);
		}
		return line + "…";
	}

	private record Hit(int x, int y, int w, int h, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}
	}
}
