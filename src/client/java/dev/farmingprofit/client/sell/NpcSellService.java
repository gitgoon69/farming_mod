package dev.farmingprofit.client.sell;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.farmingprofit.FarmingProfitMod;
import dev.farmingprofit.client.compat.ClientScreens;
import dev.farmingprofit.client.garden.SkyblockItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Vide un sack via {@code /gfs}, ouvre {@code /boostercookiemenu}, puis vend
 * uniquement l'item ciblé (1 middle-click par slot, 100 ms d'écart).
 * Répète le cycle N fois ; stop après 3 tours d'affilée sans vente.
 */
public final class NpcSellService {
	private static final int CLICK_DELAY_TICKS = 2;
	private static final int WAIT_AFTER_GFS_TICKS = 20;
	private static final int WAIT_BETWEEN_ROUNDS_TICKS = 10;
	private static final int WAIT_MENU_TIMEOUT_TICKS = 80;
	private static final int MAX_RECHECKS = 8;
	private static final int MAX_EMPTY_ROUNDS = 3;

	private enum Phase {
		IDLE, WAIT_GFS, OPEN_MENU, WAIT_MENU, SELLING, BETWEEN_ROUNDS
	}

	private Phase phase = Phase.IDLE;
	private String rawQuery = "";
	private String targetId = "";
	private String gfsName = "";
	private int ticksInPhase;
	private final List<Integer> slotsToClick = new ArrayList<>();
	private int nextSlotIndex;
	private int soldCount;
	private int soldThisRound;
	private int recheckCount;
	private int repeatCount = 1;
	private int currentRound = 1;
	private int emptyRounds;

	public boolean running() {
		return phase != Phase.IDLE;
	}

	public void start(String itemQuery) {
		if (itemQuery == null || itemQuery.isBlank()) {
			chat("Donne un nom d'item. Exemple: /fprofit sell enchanted_wheat 5", ChatFormatting.RED);
			return;
		}
		if (running()) {
			chat("Vente déjà en cours. /fprofit sell cancel pour arrêter.", ChatFormatting.RED);
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			chat("Tu dois être en jeu.", ChatFormatting.RED);
			return;
		}

		ParsedStart parsed = parseQuery(itemQuery.trim());
		if (parsed.item().isBlank()) {
			chat("Donne un nom d'item. Exemple: /fprofit sell enchanted_wheat 5", ChatFormatting.RED);
			return;
		}

		rawQuery = parsed.item();
		targetId = normalize(rawQuery);
		gfsName = rawQuery.replace(' ', '_');
		repeatCount = parsed.repeats();
		currentRound = 1;
		emptyRounds = 0;
		soldCount = 0;
		soldThisRound = 0;
		chat("Vente de « " + rawQuery + " » (" + repeatCount + " tour(s))…", ChatFormatting.YELLOW);
		beginRound(client, player, true);
		FarmingProfitMod.LOGGER.info("NPC sell start item={} repeats={}", targetId, repeatCount);
	}

	public void cancel() {
		if (!running()) {
			chat("Aucune vente en cours.", ChatFormatting.GRAY);
			return;
		}
		chat("Vente annulée. Total : " + soldCount + " slot(s).", ChatFormatting.RED);
		reset();
	}

	public void tick(Minecraft client) {
		if (!running()) {
			return;
		}
		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null) {
			chat("Joueur introuvable, vente stoppée.", ChatFormatting.RED);
			reset();
			return;
		}

		ticksInPhase++;
		switch (phase) {
			case WAIT_GFS -> {
				if (ticksInPhase >= WAIT_AFTER_GFS_TICKS) {
					phase = Phase.OPEN_MENU;
					ticksInPhase = 0;
					player.connection.sendCommand("boostercookiemenu");
				}
			}
			case OPEN_MENU -> {
				if (ticksInPhase >= 2) {
					phase = Phase.WAIT_MENU;
					ticksInPhase = 0;
				}
			}
			case WAIT_MENU -> {
				if (isNpcMenuOpen(client)) {
					collectSlots(player);
					if (slotsToClick.isEmpty()) {
						finishRound(client, 0);
						return;
					}
					phase = Phase.SELLING;
					ticksInPhase = 0;
					FarmingProfitMod.LOGGER.debug("NPC sell round {}/{} slots={}", currentRound, repeatCount, slotsToClick.size());
				} else if (ticksInPhase >= WAIT_MENU_TIMEOUT_TICKS) {
					chat("Le menu cookie ne s'est pas ouvert. Cookie actif ? Vente annulée.", ChatFormatting.RED);
					reset();
				}
			}
			case SELLING -> {
				if (!isNpcMenuOpen(client)) {
					chat("Menu fermé, vente interrompue après " + soldCount + " slot(s).", ChatFormatting.RED);
					reset();
					return;
				}
				if (ticksInPhase < CLICK_DELAY_TICKS) {
					return;
				}
				ticksInPhase = 0;
				clickNext(client, player);
			}
			case BETWEEN_ROUNDS -> {
				if (ticksInPhase >= WAIT_BETWEEN_ROUNDS_TICKS) {
					beginRound(client, player, false);
				}
			}
			case IDLE -> {
			}
		}
	}

	private void beginRound(Minecraft client, LocalPlayer player, boolean first) {
		ticksInPhase = 0;
		nextSlotIndex = 0;
		soldThisRound = 0;
		recheckCount = 0;
		slotsToClick.clear();
		ClientScreens.close(client);
		phase = Phase.WAIT_GFS;
		FarmingProfitMod.LOGGER.debug("NPC sell /gfs {} round {}/{} first={}", gfsName, currentRound, repeatCount, first);
		player.connection.sendCommand("gfs " + gfsName + " 9999");
	}

	private void clickNext(Minecraft client, LocalPlayer player) {
		if (nextSlotIndex >= slotsToClick.size()) {
			recheckRemaining(client, player);
			return;
		}

		int slotId = slotsToClick.get(nextSlotIndex++);
		AbstractContainerMenu menu = player.containerMenu;
		if (!menu.isValidSlotIndex(slotId)) {
			return;
		}

		Slot slot = menu.getSlot(slotId);
		ItemStack stack = slot.getItem();
		if (stack.isEmpty() || !matches(stack, targetId)) {
			return;
		}

		client.gameMode.handleContainerInput(menu.containerId, slotId, 2, ContainerInput.CLONE, player);
		soldCount++;
		soldThisRound++;
	}

	private void recheckRemaining(Minecraft client, LocalPlayer player) {
		collectSlots(player);
		if (slotsToClick.isEmpty()) {
			finishRound(client, soldThisRound);
			return;
		}
		recheckCount++;
		if (recheckCount > MAX_RECHECKS) {
			FarmingProfitMod.LOGGER.debug("NPC sell leftover slots={} after rechecks", slotsToClick.size());
			finishRound(client, soldThisRound);
			return;
		}
		nextSlotIndex = 0;
		ticksInPhase = 0;
	}

	private void finishRound(Minecraft client, int soldInRound) {
		if (soldInRound == 0) {
			emptyRounds++;
		} else {
			emptyRounds = 0;
		}

		if (emptyRounds >= MAX_EMPTY_ROUNDS) {
			chat("Vente stoppée : plus rien à vendre. Total : " + soldCount + " slot(s).", ChatFormatting.YELLOW);
			reset();
			return;
		}
		if (currentRound >= repeatCount) {
			chat("Vente terminée : " + soldCount + " slot(s).", ChatFormatting.GREEN);
			reset();
			return;
		}

		currentRound++;
		ClientScreens.close(client);
		phase = Phase.BETWEEN_ROUNDS;
		ticksInPhase = 0;
	}

	private void collectSlots(LocalPlayer player) {
		slotsToClick.clear();
		AbstractContainerMenu menu = player.containerMenu;
		Inventory inventory = player.getInventory();
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.getSlot(i);
			if (!isPlayerInvSlot(slot, inventory)) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || !matches(stack, targetId)) {
				continue;
			}
			slotsToClick.add(i);
		}
	}

	private static boolean isPlayerInvSlot(Slot slot, Inventory inventory) {
		if (slot.container != inventory) {
			return false;
		}
		int invSlot = slot.getContainerSlot();
		return invSlot >= 0 && invSlot < 36;
	}

	private static boolean isNpcMenuOpen(Minecraft client) {
		Screen screen = ClientScreens.current(client);
		return screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen);
	}

	static boolean matches(ItemStack stack, String targetId) {
		if (stack.isEmpty()) {
			return false;
		}
		String skyblockId = normalize(SkyblockItems.skyblockId(stack));
		if (!skyblockId.isEmpty() && skyblockId.equals(targetId)) {
			return true;
		}
		String hover = ChatFormatting.stripFormatting(stack.getHoverName().getString());
		return hover != null && normalize(hover).equals(targetId);
	}

	static String normalize(String value) {
		return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
	}

	static ParsedStart parseQuery(String raw) {
		String[] parts = raw.trim().split("\\s+");
		if (parts.length >= 2) {
			try {
				int repeats = Integer.parseInt(parts[parts.length - 1]);
				if (repeats >= 1 && repeats <= 999) {
					String item = String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
					return new ParsedStart(item, repeats);
				}
			} catch (NumberFormatException ignored) {
			}
		}
		return new ParsedStart(raw.trim(), 1);
	}

	private void reset() {
		phase = Phase.IDLE;
		ticksInPhase = 0;
		nextSlotIndex = 0;
		recheckCount = 0;
		soldThisRound = 0;
		emptyRounds = 0;
		slotsToClick.clear();
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

	record ParsedStart(String item, int repeats) {
	}
}
