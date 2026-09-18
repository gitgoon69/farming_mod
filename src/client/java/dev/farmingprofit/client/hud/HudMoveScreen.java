package dev.farmingprofit.client.hud;

import org.lwjgl.glfw.GLFW;

import dev.farmingprofit.client.compat.ClientScreens;
import dev.farmingprofit.client.config.ModConfig;
import dev.farmingprofit.client.garden.FarmingTracker;
import dev.farmingprofit.client.prices.CoflBazaarService;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Screen to drag the HUD with the mouse (like SkyHanni / Skyblocker).
 */
public class HudMoveScreen extends Screen {
	private final ModConfig config;
	private final FarmingTracker tracker;
	private final CoflBazaarService prices;
	private final Screen parent;
	private boolean dragging;
	private double dragOffsetX;
	private double dragOffsetY;

	public HudMoveScreen(ModConfig config, FarmingTracker tracker, CoflBazaarService prices) {
		this(config, tracker, prices, null);
	}

	public HudMoveScreen(ModConfig config, FarmingTracker tracker, CoflBazaarService prices, Screen parent) {
		super(Component.literal("Move Farming Profit"));
		this.config = config;
		this.tracker = tracker;
		this.prices = prices;
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int by = this.height - 28;
		this.addRenderableWidget(Button.builder(Component.literal("Reset"), button -> {
			config.hudX = 8;
			config.hudY = 48;
			config.save();
		}).bounds(cx - 160, by, 100, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
				.bounds(cx - 50, by, 100, 20).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
	}

	@Override
	protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		ProfitHud.render(graphics, config, tracker, prices, true);

		ProfitHud.Bounds box = ProfitHud.lastBounds();
		graphics.fill(box.x() - 1, box.y() - 1, box.x() + box.width() + 1, box.y(), 0xFFFFD54A);
		graphics.fill(box.x() - 1, box.y() + box.height(), box.x() + box.width() + 1, box.y() + box.height() + 1, 0xFFFFD54A);
		graphics.fill(box.x() - 1, box.y(), box.x(), box.y() + box.height(), 0xFFFFD54A);
		graphics.fill(box.x() + box.width(), box.y(), box.x() + box.width() + 1, box.y() + box.height(), 0xFFFFD54A);

		String hint = "Drag the HUD  •  Arrows for 1px  •  Esc to save  •  x=" + config.hudX + " y=" + config.hudY;
		int hintW = this.font.width(hint);
		graphics.text(this.font, hint, (this.width - hintW) / 2, 12, 0xFFFFFFFF, true);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && ProfitHud.lastBounds().contains(event.x(), event.y())) {
			dragging = true;
			dragOffsetX = event.x() - config.hudX;
			dragOffsetY = event.y() - config.hudY;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			moveTo((int) Math.round(event.x() - dragOffsetX), (int) Math.round(event.y() - dragOffsetY));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			config.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int step = event.hasShiftDown() ? 10 : 1;
		if (event.isLeft()) {
			moveTo(config.hudX - step, config.hudY);
			return true;
		}
		if (event.isRight()) {
			moveTo(config.hudX + step, config.hudY);
			return true;
		}
		if (event.isUp()) {
			moveTo(config.hudX, config.hudY - step);
			return true;
		}
		if (event.isDown()) {
			moveTo(config.hudX, config.hudY + step);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		config.save();
		if (parent != null) {
			ClientScreens.set(Minecraft.getInstance(), parent);
			return;
		}
		super.onClose();
	}

	private void moveTo(int x, int y) {
		ProfitHud.Bounds box = ProfitHud.lastBounds();
		int maxX = Math.max(0, this.width - box.width());
		int maxY = Math.max(0, this.height - box.height() - 36);
		config.hudX = Mth.clamp(x, 0, maxX);
		config.hudY = Mth.clamp(y, 0, maxY);
	}
}
