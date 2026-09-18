package dev.farmingprofit.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.glfw.GLFW;

import dev.farmingprofit.FarmingProfitMod;
import dev.farmingprofit.client.compat.ClientScreens;
import dev.farmingprofit.client.compat.PolarPresence;
import dev.farmingprofit.client.config.ModConfig;
import dev.farmingprofit.client.garden.FarmingTracker;
import dev.farmingprofit.client.hud.HudMoveScreen;
import dev.farmingprofit.client.loadout.PestLoadoutService;
import dev.farmingprofit.client.mining.PickaxeAbilityService;
import dev.farmingprofit.client.prices.CoflBazaarService;
import dev.farmingprofit.client.update.UpdateChecker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Single settings menu: options, vanilla icons, animated toggles, particles.
 */
public final class SettingsScreen extends Screen {
	private static final int PANEL_W = 440;
	private static final int PANEL_H = 268;
	private static final int SIDE = 54;
	private static final int ROW_H = 32;

	private final ModConfig config;
	private final FarmingTracker tracker;
	private final CoflBazaarService prices;
	private final PestLoadoutService pest;
	private final PickaxeAbilityService pickaxe;
	private final UpdateChecker updates;

	private int tab;
	private float openAnim;
	private float tabAnim = 1f;
	private float scroll;
	private float scrollTarget;
	private String toast = "";
	private int toastTicks;
	private EditBox pestName;
	private EditBox farmName;

	private int panelX;
	private int panelY;
	private final List<Hit> hits = new ArrayList<>();
	private final Spark[] sparks = new Spark[22];
	private final float[] toggleSlide = new float[24];

	public SettingsScreen(
			ModConfig config,
			FarmingTracker tracker,
			CoflBazaarService prices,
			PestLoadoutService pest,
			PickaxeAbilityService pickaxe,
			UpdateChecker updates
	) {
		super(Component.literal("Farming Profit"));
		this.config = config;
		this.tracker = tracker;
		this.prices = prices;
		this.pest = pest;
		this.pickaxe = pickaxe;
		this.updates = updates;
		ThreadLocalRandom rng = ThreadLocalRandom.current();
		for (int i = 0; i < sparks.length; i++) {
			sparks[i] = new Spark(rng.nextFloat(), rng.nextFloat(), 0.15f + rng.nextFloat() * 0.7f, rng.nextFloat() * 6.28f);
		}
	}

	@Override
	protected void init() {
		panelX = (this.width - PANEL_W) / 2;
		panelY = (this.height - PANEL_H) / 2;
		this.clearWidgets();
		if (tab == 2) {
			int x = panelX + SIDE + 18;
			int y = panelY + PANEL_H - 62;
			int w = (PANEL_W - SIDE - 40) / 2 - 4;
			pestName = new EditBox(this.font, x, y, w, 18, Component.literal("Loadout Pest"));
			pestName.setValue(nz(config.pestLoadoutName, "Pest"));
			pestName.setMaxLength(24);
			pestName.setResponder(value -> {
				config.pestLoadoutName = value.isBlank() ? "Pest" : value.trim();
				config.save();
			});
			farmName = new EditBox(this.font, x + w + 8, y, w, 18, Component.literal("Loadout Farm"));
			farmName.setValue(nz(config.farmLoadoutName, "Farm"));
			farmName.setMaxLength(24);
			farmName.setResponder(value -> {
				config.farmLoadoutName = value.isBlank() ? "Farm" : value.trim();
				config.save();
			});
			this.addRenderableWidget(pestName);
			this.addRenderableWidget(farmName);
		} else {
			pestName = null;
			farmName = null;
		}
	}

	@Override
	public void tick() {
		openAnim += (1f - openAnim) * 0.18f;
		tabAnim += (1f - tabAnim) * 0.16f;
		scroll += (scrollTarget - scroll) * 0.28f;
		if (toastTicks > 0) {
			toastTicks--;
		}
		for (Spark spark : sparks) {
			spark.phase += 0.035f + spark.speed * 0.02f;
			spark.y -= 0.0022f * spark.speed;
			if (spark.y < -0.05f) {
				spark.y = 1.05f;
				spark.x = ThreadLocalRandom.current().nextFloat();
			}
		}
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
		float appear = easeOut(openAnim);

		graphics.fill(0, 0, this.width, this.height, alpha(0xE0080C10, appear));
		for (Spark spark : sparks) {
			int sx = (int) (spark.x * this.width);
			int sy = (int) (spark.y * this.height + Math.sin(spark.phase) * 6);
			int size = spark.speed > 0.5f ? 2 : 1;
			int color = spark.speed > 0.6f ? 0x88FFD54A : 0x55FFF8E1;
			graphics.fill(sx, sy, sx + size, sy + size, color);
		}

		int px = panelX;
		int py = panelY;

		graphics.fill(px - 4, py - 4, px + PANEL_W + 4, py + PANEL_H + 4, 0xCC000000);
		graphics.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, 0xFF3D4A24);
		graphics.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFF141810);
		graphics.fillGradient(px, py, px + PANEL_W, py + 38, 0xFF3A4C22, 0xFF1A2214);
		graphics.fill(px, py + 38, px + PANEL_W, py + 39, 0xFFFFD54A);
		graphics.fill(px, py, px + 3, py + PANEL_H, 0xFFFFD54A);
		graphics.fill(px + PANEL_W - 3, py, px + PANEL_W, py + PANEL_H, 0x66FFD54A);
		graphics.fill(px + SIDE, py + 39, px + SIDE + 1, py + PANEL_H - 22, 0x33FFD54A);

		graphics.item(stack(Items.GOLDEN_HOE), px + 10, py + 10);
		graphics.text(font, "Farming Profit", px + 32, py + 9, 0xFFFFD54A, true);
		graphics.text(font, "Garden · " + version() + " · 26.1.2", px + 32, py + 21, 0xFFB8C4A0, false);

		Item[] crops = {Items.WHEAT, Items.CARROT, Items.POTATO, Items.NETHER_WART, Items.COCOA_BEANS, Items.GOLDEN_CARROT};
		for (int i = 0; i < crops.length; i++) {
			int ix = px + PANEL_W - 22 - i * 18;
			int bob = (int) Math.round(Math.sin((System.currentTimeMillis() / 180.0) + i) * 1.5);
			graphics.item(stack(crops[i]), ix, py + 11 + bob);
		}

		drawTabs(graphics, mouseX, mouseY);
		drawContent(graphics, mouseX, mouseY);

		graphics.fill(px, py + PANEL_H - 22, px + PANEL_W, py + PANEL_H, 0xFF10140E);
		String hint = "Esc · scroll · /fprofit";
		graphics.text(font, hint, px + SIDE + 10, py + PANEL_H - 16, 0xFF8A9680, false);
		if (toastTicks > 0 && !toast.isEmpty()) {
			graphics.text(font, toast, px + PANEL_W - 12 - font.width(toast), py + PANEL_H - 16, 0xFFFFF176, true);
		}

		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int x = panelX;
		int y0 = panelY + 44;
		Tab[] tabs = Tab.values();
		Font font = this.font;
		for (int i = 0; i < tabs.length; i++) {
			int y = y0 + i * 40;
			boolean selected = i == tab;
			boolean hover = mouseX >= x + 6 && mouseX < x + SIDE - 4 && mouseY >= y && mouseY < y + 36;
			int bg = selected ? 0xFF3A4C22 : (hover ? 0xFF242C1A : 0xFF181E14);
			graphics.fill(x + 6, y, x + SIDE - 4, y + 36, bg);
			if (selected) {
				graphics.fill(x + 6, y, x + 9, y + 36, 0xFFFFD54A);
			}
			float bounce = selected ? 1f + (float) Math.sin(System.currentTimeMillis() / 220.0) * 0.08f : 1f;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x + 19, y + 6);
			graphics.pose().scale(bounce, bounce);
			graphics.item(stack(tabs[i].icon), -8, -8);
			graphics.pose().popMatrix();
			String label = tabs[i].label;
			int lw = font.width(label);
			graphics.text(font, label, x + 6 + (SIDE - 10 - lw) / 2, y + 24, selected ? 0xFFFFD54A : 0xFFB8C4A0, false);
			if (hover) {
				graphics.setTooltipForNextFrame(Component.literal(tabs[i].hint), mouseX, mouseY);
			}
			int index = i;
			hits.add(new Hit(x + 6, y, SIDE - 10, 36, () -> selectTab(index), tabs[i].hint));
		}
	}

	private void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Font font = this.font;
		int x = panelX + SIDE + 10;
		int y = panelY + 48;
		int w = PANEL_W - SIDE - 20;
		int bottom = panelY + PANEL_H - 28;
		graphics.enableScissor(x - 2, y - 2, x + w + 2, bottom);
		int drawY = y - Math.round(scroll);
		List<Row> rows = rows();
		float slide = easeOut(tabAnim);
		drawY += Math.round((1f - slide) * 12);

		if (tab == 1 && PolarPresence.detected()) {
			graphics.fill(x, drawY, x + w, drawY + 22, 0xAA5D2E18);
			graphics.text(font, "Polar: crop hitboxes and server pack are off (native crash).", x + 6, drawY + 7, 0xFFFFCC80, false);
			drawY += 26;
		}

		int toggleIndex = 0;
		for (Row row : rows) {
			int rowBottom = drawY + ROW_H;
			if (rowBottom >= y - 8 && drawY <= bottom) {
				boolean hover = mouseX >= x && mouseX < x + w && mouseY >= Math.max(drawY, y) && mouseY < Math.min(rowBottom, bottom);
				graphics.fill(x, drawY, x + w, rowBottom - 2, hover ? 0xFF2C3A20 : 0xFF1A2216);
				if (hover) {
					graphics.fill(x, drawY, x + 2, rowBottom - 2, 0xFFFFD54A);
					graphics.setTooltipForNextFrame(Component.literal(row.desc), mouseX, mouseY);
				}
				graphics.item(stack(row.icon), x + 4, drawY + 6);
				graphics.text(font, row.title, x + 26, drawY + 5, 0xFFFFF3C4, false);
				graphics.text(font, row.desc, x + 26, drawY + 16, 0xFF9AAA88, false);
				if (row.kind == Kind.TOGGLE) {
					boolean on = row.on.getAsBoolean();
					toggleSlide[toggleIndex] += ((on ? 1f : 0f) - toggleSlide[toggleIndex]) * 0.28f;
					drawToggle(graphics, x + w - 42, drawY + 8, toggleSlide[toggleIndex]);
					hits.add(new Hit(x, drawY, w, ROW_H - 2, row.action, row.desc));
				} else if (row.kind == Kind.CYCLE) {
					String value = row.value.get();
					int vw = font.width(value) + 14;
					int bx = x + w - vw - 6;
					graphics.fill(bx, drawY + 8, bx + vw, drawY + 24, 0xFF3A4C22);
					graphics.text(font, value, bx + 7, drawY + 12, 0xFFFFD54A, false);
					hits.add(new Hit(x, drawY, w, ROW_H - 2, row.action, row.desc));
				} else if (row.kind == Kind.STEPPER) {
					String value = row.value.get();
					int bx = x + w - 78;
					drawMini(graphics, bx, drawY + 8, 16, "-", mouseX, mouseY);
					graphics.text(font, value, bx + 22, drawY + 12, 0xFFFFF8E1, false);
					drawMini(graphics, bx + 56, drawY + 8, 16, "+", mouseX, mouseY);
					Runnable minus = row.minus;
					Runnable plus = row.plus;
					hits.add(new Hit(bx, drawY + 8, 16, 16, minus, "Less"));
					hits.add(new Hit(bx + 56, drawY + 8, 16, 16, plus, "More"));
				} else {
					int bx = x + w - 78;
					graphics.fill(bx, drawY + 8, bx + 72, drawY + 24, 0xFF4A3A14);
					graphics.text(font, "Open", bx + 18, drawY + 12, 0xFFFFD54A, false);
					hits.add(new Hit(x, drawY, w, ROW_H - 2, row.action, row.desc));
				}
			}
			if (row.kind == Kind.TOGGLE) {
				toggleIndex++;
			}
			drawY += ROW_H;
		}

		if (tab == 2) {
			graphics.text(font, "Pest name", x, panelY + PANEL_H - 74, 0xFFB8C4A0, false);
			graphics.text(font, "Farm name", x + (w / 2), panelY + PANEL_H - 74, 0xFFB8C4A0, false);
		}
		graphics.disableScissor();

		int contentH = rows.size() * ROW_H + (tab == 1 && PolarPresence.detected() ? 26 : 0);
		int viewH = bottom - y;
		float maxScroll = Math.max(0, contentH - viewH + 8);
		scrollTarget = Mth.clamp(scrollTarget, 0, maxScroll);
	}

	private void drawToggle(GuiGraphicsExtractor graphics, int x, int y, float t) {
		t = Mth.clamp(t, 0f, 1f);
		int track = lerpColor(0xFF5A403C, 0xFF5A9A32, t);
		graphics.fill(x, y + 3, x + 34, y + 13, 0xFF0A0C08);
		graphics.fill(x + 1, y + 4, x + 33, y + 12, track);
		int knobX = x + 2 + Math.round(18 * t);
		graphics.fill(knobX, y + 1, knobX + 12, y + 15, 0xFFFFF3C4);
		graphics.fill(knobX + 1, y + 2, knobX + 11, y + 14, lerpColor(0xFFD7CCC8, 0xFFE8F5C8, t));
	}

	private void drawMini(GuiGraphicsExtractor graphics, int x, int y, int s, String label, int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + s && mouseY >= y && mouseY < y + s;
		graphics.fill(x, y, x + s, y + s, hover ? 0xFF4A5C28 : 0xFF2A341C);
		graphics.text(this.font, label, x + (label.equals("+") ? 5 : 6), y + 4, 0xFFFFF8E1, false);
	}

	private List<Row> rows() {
		List<Row> rows = new ArrayList<>();
		switch (tab) {
			case 0 -> {
				rows.add(toggle("Coins / hour HUD", "Shows the overlay when a crop hoe is in hand", Items.GOLDEN_HOE,
						() -> config.hudEnabled, () -> {
							config.hudEnabled = !config.hudEnabled;
							config.save();
							ping(config.hudEnabled ? "HUD on" : "HUD hidden");
						}));
				rows.add(cycle("Bazaar mode", "OFFER = Cofl sell offer · INSTANT = instant sell", Items.GOLD_INGOT,
						() -> config.useSellOffer() ? "OFFER" : "INSTANT", () -> {
							config.priceMode = config.useSellOffer() ? "INSTANT" : "OFFER";
							config.save();
							ping("Prices: " + config.priceMode);
						}));
				rows.add(toggle("Count seeds", "Adds enchanted seed price to wheat", Items.WHEAT_SEEDS,
						() -> config.includeSeeds, () -> {
							config.includeSeeds = !config.includeSeeds;
							config.save();
						}));
				rows.add(action("Move HUD", "Drag the overlay with the mouse", Items.PAPER, this::openMove));
				rows.add(action("Reset session", "Clears counter, time and profit", Items.CLOCK, () -> {
					tracker.resetSession();
					ping("Session reset");
				}));
				rows.add(action("Refresh Cofl", "Reload bazaar API", Items.ENDER_CHEST, () -> {
					prices.refreshNow();
					ping("Cofl prices…");
				}));
			}
			case 1 -> {
				rows.add(toggle("Crop hitboxes", "1 block when mature, lowest otherwise (cocoa included)", Items.WHEAT,
						() -> config.fullCropHitboxes, () -> {
							config.fullCropHitboxes = !config.fullCropHitboxes;
							config.save();
							ping(config.fullCropHitboxes ? "Custom hitboxes" : "Vanilla hitboxes");
						}));
				rows.add(stepper("AFK timeout", "Pauses the HUD timer after inactivity", Items.CLOCK,
						() -> config.afkTimeoutSeconds + "s",
						() -> stepAfk(-5),
						() -> stepAfk(5)));
			}
			case 2 -> {
				rows.add(toggle("Rod → loadout", "Right-click rod: Pest / Farm", Items.FISHING_ROD,
						() -> config.pestRodLoadout, () -> {
							config.pestRodLoadout = !config.pestRodLoadout;
							config.save();
						}));
				rows.add(toggle("Auto Pest / Farm", "Pest at 2m50, /setspawn, Farm 0.5–1s after spawn", Items.CARROT,
						() -> config.autoPestLoadout, () -> {
							config.autoPestLoadout = !config.autoPestLoadout;
							if (!config.autoPestLoadout) {
								pest.cancelPendingAuto();
							}
							config.save();
						}));
				rows.add(toggle("2m50 alert", "Large title + 5s countdown", Items.NETHER_WART,
						() -> config.pestCooldownAlert, () -> {
							config.pestCooldownAlert = !config.pestCooldownAlert;
							config.save();
						}));
				rows.add(action("Switch Pest / Farm", "Opens /loadout and clicks the other loadout", Items.FISHING_ROD, () -> {
					this.onClose();
					pest.startFromCommand();
				}));
			}
			case 3 -> {
				rows.add(toggle("Auto pickaxe ability", "Right-click when mining cooldown hits 0", Items.IRON_PICKAXE,
						() -> config.autoPickaxeAbility, () -> {
							config.autoPickaxeAbility = !config.autoPickaxeAbility;
							if (!config.autoPickaxeAbility) {
								pickaxe.reset();
							}
							config.save();
						}));
			}
			case 4 -> {
				rows.add(toggle("Server pack last", "Vanilla + your packs override Hypixel", Items.CHEST,
						() -> config.hideServerResourcePack, this::togglePack));
				rows.add(toggle("GitHub updates", "Check Latest 26.1.2 on login", Items.COMPASS,
						() -> config.checkUpdates, () -> {
							config.checkUpdates = !config.checkUpdates;
							config.save();
						}));
				rows.add(toggle("Usage ping", "Sends UUID / username / version (private table)", Items.PAPER,
						() -> config.usagePing, () -> {
							config.usagePing = !config.usagePing;
							config.save();
						}));
				rows.add(action("Check GitHub", "Compare with the latest release", Items.COMPASS, () -> {
					updates.refreshNow();
					ping("GitHub check…");
				}));
				rows.add(action("Install update", "Download the 26.1.2 JAR and close the game", Items.NETHER_STAR, () -> {
					updates.installNow();
					ping("Installing…");
				}));
				rows.add(action("NPC sell", "In-game: /fprofit sell <item> [times]", Items.EMERALD, () -> {
					ping("/fprofit sell <item>");
				}));
			}
			default -> {
			}
		}
		return rows;
	}

	private void togglePack() {
		config.hideServerResourcePack = !config.hideServerResourcePack;
		config.save();
		if (config.hideServerResourcePack && this.minecraft != null) {
			this.minecraft.reloadResourcePacks();
			ping("Hypixel pack last");
		} else {
			ping("Normal pack priority — reconnect");
		}
	}

	private void stepAfk(int delta) {
		config.afkTimeoutSeconds = Mth.clamp(config.afkTimeoutSeconds + delta, 5, 120);
		config.save();
	}

	private void openMove() {
		Minecraft client = this.minecraft;
		client.execute(() -> ClientScreens.set(client, new HudMoveScreen(config, tracker, prices, this)));
	}

	private void selectTab(int next) {
		if (tab == next) {
			return;
		}
		tab = next;
		tabAnim = 0f;
		scroll = 0f;
		scrollTarget = 0f;
		this.rebuildWidgets();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
			return false;
		}
		for (Hit hit : hits) {
			if (hit.contains(event.x(), event.y())) {
				hit.action.run();
				click();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollTarget -= (float) scrollY * 18f;
		return true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public static boolean blockingHud(Minecraft client) {
		var screen = ClientScreens.current(client);
		return screen instanceof SettingsScreen || screen instanceof HudMoveScreen;
	}

	@Override
	public void onClose() {
		config.save();
		super.onClose();
	}

	private void ping(String message) {
		toast = message;
		toastTicks = 60;
	}

	private void click() {
		if (this.minecraft != null) {
			this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
		}
	}

	private static Row toggle(String title, String desc, Item icon, java.util.function.BooleanSupplier on, Runnable action) {
		return new Row(Kind.TOGGLE, title, desc, icon, on, null, action, null, null);
	}

	private static Row cycle(String title, String desc, Item icon, java.util.function.Supplier<String> value, Runnable action) {
		return new Row(Kind.CYCLE, title, desc, icon, null, value, action, null, null);
	}

	private static Row action(String title, String desc, Item icon, Runnable action) {
		return new Row(Kind.ACTION, title, desc, icon, null, null, action, null, null);
	}

	private static Row stepper(String title, String desc, Item icon, java.util.function.Supplier<String> value, Runnable minus, Runnable plus) {
		return new Row(Kind.STEPPER, title, desc, icon, null, value, null, minus, plus);
	}

	private static ItemStack stack(Item item) {
		return new ItemStack(item);
	}

	private static String version() {
		return FabricLoader.getInstance()
				.getModContainer(FarmingProfitMod.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("?");
	}

	private static String nz(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static float easeOut(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return 1f - (1f - t) * (1f - t);
	}

	private static int alpha(int argb, float a) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(a, 0f, 1f));
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	private static int lerpColor(int from, int to, float t) {
		t = Mth.clamp(t, 0f, 1f);
		int a = lerpChan(from, to, t, 24);
		int r = lerpChan(from, to, t, 16);
		int g = lerpChan(from, to, t, 8);
		int b = lerpChan(from, to, t, 0);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int lerpChan(int from, int to, float t, int shift) {
		int a = (from >> shift) & 0xFF;
		int b = (to >> shift) & 0xFF;
		return Math.round(a + (b - a) * t);
	}

	private enum Tab {
		HUD("HUD", "Coins / hour overlay", Items.GOLDEN_HOE),
		GARDEN("Farm", "Hitbox and AFK", Items.WHEAT),
		PEST("Pest", "Loadout and 2m50 alert", Items.FISHING_ROD),
		MINING("Mine", "Pickaxe ability", Items.IRON_PICKAXE),
		SYSTEM("Sys", "Pack, updates, NPC sell", Items.NETHER_STAR);

		final String label;
		final String hint;
		final Item icon;

		Tab(String label, String hint, Item icon) {
			this.label = label;
			this.hint = hint;
			this.icon = icon;
		}
	}

	private enum Kind {
		TOGGLE, CYCLE, ACTION, STEPPER
	}

	private record Row(
			Kind kind,
			String title,
			String desc,
			Item icon,
			java.util.function.BooleanSupplier on,
			java.util.function.Supplier<String> value,
			Runnable action,
			Runnable minus,
			Runnable plus
	) {
	}

	private record Hit(int x, int y, int w, int h, Runnable action, String tooltip) {
		boolean contains(double mx, double my) {
			return mx >= x && my >= y && mx < x + w && my < y + h;
		}
	}

	private static final class Spark {
		float x;
		float y;
		final float speed;
		float phase;

		Spark(float x, float y, float speed, float phase) {
			this.x = x;
			this.y = y;
			this.speed = speed;
			this.phase = phase;
		}
	}
}
