package dev.farmingprofit.client.loadout;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import dev.farmingprofit.FarmingProfitMod;
import dev.farmingprofit.client.compat.ClientMissTime;
import dev.farmingprofit.client.compat.ClientScreens;
import dev.farmingprofit.client.config.ModConfig;
import dev.farmingprofit.client.garden.GardenDetector;
import dev.farmingprofit.client.garden.SkyblockItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Right-click rod → {@code /loadout} → Pest. Right-click again in Pest Mode → Farm.
 */
public final class PestLoadoutService {
	private static final int WAIT_MENU_TIMEOUT_TICKS = 80;
	private static final int CLICK_DELAY_TICKS = 4;
	private static final int CLOSE_DELAY_TICKS = 6;
	private static final long RETRIGGER_COOLDOWN_MS = 2500L;
	private static final long FARM_AFTER_SPAWN_MIN_MS = 500L;
	private static final long FARM_AFTER_SPAWN_MAX_MS = 1000L;
	private static final Pattern PEST_SPAWN = Pattern.compile(
			"(?i)(?:eww|yuck|gross|ew).{0,32}pest|(?i)pests? (?:have )?spawned"
	);

	private enum Phase {
		IDLE, OPEN_MENU, WAIT_MENU, CLICK, CLOSING
	}

	private final ModConfig config;
	private Phase phase = Phase.IDLE;
	private int ticksInPhase;
	private boolean pestMode;
	private boolean wasUseDown;
	private long lastTriggerMs;
	private String pendingTarget = "Pest";
	private boolean quiet;
	private Boolean queuedToPest;
	private boolean expectFarmAfterSpawn;
	private long farmSwitchAtMs;
	private int resumeAttackTicks;

	public PestLoadoutService(ModConfig config) {
		this.config = config;
	}

	public boolean pestMode() {
		return pestMode;
	}

	public void setPestMode(boolean pestMode) {
		this.pestMode = pestMode;
	}

	public boolean running() {
		return phase != Phase.IDLE;
	}

	public void cancel() {
		queuedToPest = null;
		reset();
	}

	public void resetSession() {
		reset();
		pestMode = false;
		wasUseDown = false;
		queuedToPest = null;
		expectFarmAfterSpawn = false;
		farmSwitchAtMs = 0;
		resumeAttackTicks = 0;
	}

	public void cancelPendingAuto() {
		queuedToPest = null;
		expectFarmAfterSpawn = false;
		farmSwitchAtMs = 0;
	}

	public void onPestAlert() {
		if (!config.autoPestLoadout || !GardenDetector.inGarden()) {
			return;
		}
		expectFarmAfterSpawn = true;
		requestSwitch(true);
	}

	public void onChat(String raw) {
		if (raw == null || raw.isBlank()) {
			return;
		}
		String text = strip(raw);
		if (text.startsWith("[Farming Profit]")) {
			return;
		}
		if (!PEST_SPAWN.matcher(text).find()) {
			return;
		}
		boolean inGarden = GardenDetector.inGarden();
		if (!inGarden && !expectFarmAfterSpawn && !pestMode) {
			return;
		}
		if (inGarden) {
			sendSetSpawn();
		}
		if (!config.autoPestLoadout) {
			return;
		}
		if (!expectFarmAfterSpawn && !pestMode) {
			return;
		}
		long delayMs = farmAfterSpawnDelayMs();
		farmSwitchAtMs = System.currentTimeMillis() + delayMs;
		FarmingProfitMod.LOGGER.info("Pest spawn detected, Farm loadout in {}ms", delayMs);
	}

	public void requestSwitch(boolean toPest) {
		if (!GardenDetector.inGarden()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		if (toPest == pestMode && !running()) {
			return;
		}
		if (running()) {
			queuedToPest = toPest;
			return;
		}
		quiet = true;
		start(client, player, toPest, true);
	}

	/** Rod toggle: Pest if inactive, Farm if Pest Mode. */
	public void startFromCommand() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		if (running()) {
			chat("Loadout switch already running.", ChatFormatting.RED);
			return;
		}
		quiet = false;
		start(client, client.player, !pestMode, true);
	}

	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			if (running()) {
				reset();
			}
			wasUseDown = false;
			resumeAttackTicks = 0;
			return;
		}

		if (!GardenDetector.inGarden()) {
			if (running() && quiet) {
				cancel();
			}
			cancelPendingAuto();
			resumeAttackTicks = 0;
		}

		tickResumeAttack(client);

		if (config.autoPestLoadout && farmSwitchAtMs > 0 && System.currentTimeMillis() >= farmSwitchAtMs) {
			farmSwitchAtMs = 0;
			expectFarmAfterSpawn = false;
			if (GardenDetector.inGarden()) {
				requestSwitch(false);
			}
		}

		boolean useDown = client.options.keyUse.isDown();
		boolean rising = useDown && !wasUseDown;
		wasUseDown = useDown;
		if (config.pestRodLoadout && rising && phase == Phase.IDLE && !ClientScreens.isOpen(client)) {
			if (isFishingRod(player.getMainHandItem()) || isFishingRod(player.getOffhandItem())) {
				quiet = false;
				start(client, player, !pestMode, false);
			}
		}

		if (phase == Phase.IDLE) {
			scanOpenLoadout(client);
		}

		if (!running()) {
			return;
		}

		ticksInPhase++;
		switch (phase) {
			case OPEN_MENU -> {
				if (ticksInPhase >= 2) {
					phase = Phase.WAIT_MENU;
					ticksInPhase = 0;
				}
			}
			case WAIT_MENU -> {
				if (isLoadoutMenu(client) && findTargetSlot(player.containerMenu) >= 0) {
					if (ticksInPhase >= CLICK_DELAY_TICKS) {
						phase = Phase.CLICK;
						ticksInPhase = 0;
					}
				} else if (ticksInPhase >= WAIT_MENU_TIMEOUT_TICKS) {
					chat("Loadout menu / item \"" + pendingTarget + "\" not found.", ChatFormatting.RED);
					reset();
				}
			}
			case CLICK -> {
				if (!isLoadoutMenu(client)) {
					chat("Loadout menu closed too early.", ChatFormatting.RED);
					reset();
					return;
				}
				int slotId = findTargetSlot(player.containerMenu);
				if (slotId < 0) {
					chat("Loadout \"" + pendingTarget + "\" not found in the menu.", ChatFormatting.RED);
					reset();
					return;
				}
				client.gameMode.handleContainerInput(
						player.containerMenu.containerId,
						slotId,
						0,
						ContainerInput.PICKUP,
						player
				);
				pestMode = isPestName(pendingTarget);
				FarmingProfitMod.LOGGER.info("Loadout {} left-click slot {}", pendingTarget, slotId);
				phase = Phase.CLOSING;
				ticksInPhase = 0;
			}
			case CLOSING -> {
				if (ticksInPhase >= CLOSE_DELAY_TICKS) {
					ClientScreens.close(client);
					if (!quiet) {
						chat("Loadout " + pendingTarget + " equipped.", ChatFormatting.GREEN);
					}
					Boolean next = queuedToPest;
					queuedToPest = null;
					boolean resumeAttack = next == null;
					reset();
					if (next != null) {
						requestSwitch(next);
					} else if (resumeAttack) {
						scheduleResumeAttack();
					}
				}
			}
			case IDLE -> {
			}
		}
	}

	private void start(Minecraft client, LocalPlayer player, boolean toPest, boolean ignoreCooldown) {
		long now = System.currentTimeMillis();
		if (!ignoreCooldown && now - lastTriggerMs < RETRIGGER_COOLDOWN_MS) {
			return;
		}
		lastTriggerMs = now;
		pendingTarget = toPest ? pestName() : farmName();
		ticksInPhase = 0;
		resumeAttackTicks = 0;
		phase = Phase.OPEN_MENU;
		ClientScreens.close(client);
		player.connection.sendCommand("loadout");
	}

	private static void sendSetSpawn() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.connection == null) {
			return;
		}
		client.player.connection.sendCommand("setspawn");
		FarmingProfitMod.LOGGER.info("Pest spawn detected, /setspawn");
	}

	private static long farmAfterSpawnDelayMs() {
		return ThreadLocalRandom.current().nextLong(FARM_AFTER_SPAWN_MIN_MS, FARM_AFTER_SPAWN_MAX_MS + 1);
	}

	private void scanOpenLoadout(Minecraft client) {
		if (!isLoadoutMenu(client) || client.player == null) {
			return;
		}
		String selected = findSelectedLoadoutName(client.player.containerMenu);
		if (selected == null) {
			return;
		}
		pestMode = isPestName(selected);
	}

	private int findTargetSlot(AbstractContainerMenu menu) {
		Inventory inventory = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.getSlot(i);
			if (inventory != null && slot.container instanceof Inventory) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			if (matchesName(hoverName(stack), pendingTarget)) {
				return slot.index;
			}
		}
		return -1;
	}

	private String findSelectedLoadoutName(AbstractContainerMenu menu) {
		Inventory inventory = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
		for (Slot slot : menu.slots) {
			if (inventory != null && slot.container instanceof Inventory) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !looksSelected(stack)) {
				continue;
			}
			String name = hoverName(stack);
			if (!name.isEmpty()) {
				return name;
			}
		}
		return null;
	}

	private static boolean looksSelected(ItemStack stack) {
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		StringBuilder text = new StringBuilder();
		for (Component line : lore.lines()) {
			text.append(line.getString().toLowerCase(Locale.ROOT)).append('\n');
		}
		String loreText = text.toString();
		if (loreText.contains("click to select") && !loreText.contains("currently")) {
			return false;
		}
		return loreText.contains("selected")
				|| loreText.contains("currently equipped")
				|| loreText.contains("currently selected")
				|| loreText.contains("active loadout")
				|| loreText.contains("equipped");
	}

	private boolean isPestName(String name) {
		return matchesName(name, pestName());
	}

	private static boolean matchesName(String actual, String expected) {
		return strip(actual).equalsIgnoreCase(strip(expected));
	}

	private String pestName() {
		String name = config.pestLoadoutName;
		return name == null || name.isBlank() ? "Pest" : name.trim();
	}

	private String farmName() {
		String name = config.farmLoadoutName;
		return name == null || name.isBlank() ? "Farm" : name.trim();
	}

	private static boolean isLoadoutMenu(Minecraft client) {
		Screen screen = ClientScreens.current(client);
		if (!(screen instanceof AbstractContainerScreen<?>) || screen instanceof InventoryScreen) {
			return false;
		}
		String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
		return title.contains("loadout");
	}

	private static boolean isFishingRod(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.getItem() instanceof FishingRodItem) {
			return true;
		}
		String id = SkyblockItems.skyblockId(stack).toUpperCase(Locale.ROOT);
		if (id.contains("FISHING_ROD")) {
			return true;
		}
		String name = strip(stack.getHoverName().getString()).toLowerCase(Locale.ROOT);
		return name.contains("fishing rod");
	}

	private static String hoverName(ItemStack stack) {
		return strip(stack.getHoverName().getString());
	}

	private static String strip(String value) {
		String stripped = ChatFormatting.stripFormatting(value);
		return stripped == null ? "" : stripped.trim();
	}

	private void scheduleResumeAttack() {
		resumeAttackTicks = 3;
	}

	/**
	 * One click (vanilla Attack/Destroy toggle) after the loadout GUI.
	 * The menu sets {@code missTime} to 10000 and releases the mouse toggle, which is not restored.
	 */
	private void tickResumeAttack(Minecraft client) {
		if (resumeAttackTicks <= 0 || running()) {
			return;
		}
		if (ClientScreens.isOpen(client) || !client.mouseHandler.isMouseGrabbed()) {
			return;
		}
		resumeAttackTicks--;
		if (resumeAttackTicks > 0) {
			return;
		}
		if (client.options.keyAttack.isDown()) {
			return;
		}
		ClientMissTime.clear(client);
		client.options.keyAttack.setDown(true);
	}

	private void reset() {
		phase = Phase.IDLE;
		ticksInPhase = 0;
	}

	private static void chat(String message, ChatFormatting color) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		client.player.sendSystemMessage(
				Component.literal("[Farming Profit] " + message).withStyle(color)
		);
	}
}
