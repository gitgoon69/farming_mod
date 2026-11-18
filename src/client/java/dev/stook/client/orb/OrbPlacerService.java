package dev.stook.client.orb;

import java.util.Locale;

import dev.stook.StookMod;
import dev.stook.client.compat.ClientScreens;
import dev.stook.client.config.ModConfig;
import dev.stook.client.garden.GardenDetector;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Right-clicks the orb in a hotbar slot onto the ground beside the player.
 * Places again after leaving the radius or when the duration runs out.
 */
public final class OrbPlacerService {
	public static final double RADIUS = 30.0;
	public static final long DURATION_MS = 250_000L;
	private static final double RADIUS_SQR = RADIUS * RADIUS;
	private static final int MAX_DROP = 4;
	private static final int ARM_TICKS = 2;
	private static final long RETRY_MS = 1_000L;
	private static final long FAIL_BACKOFF_MS = 8_000L;

	private enum Phase {
		IDLE, ARM, RESTORE
	}

	private final ModConfig config;
	private Phase phase = Phase.IDLE;
	private int ticks;
	private int orbSlot;
	private int returnSlot = -1;
	private Vec3 anchor;
	private long placedAtMs;
	private long nextAttemptAtMs;
	private long lastClickMs;
	private Level watchedLevel;

	public OrbPlacerService(ModConfig config) {
		this.config = config;
	}

	public boolean busy() {
		return phase != Phase.IDLE;
	}

	public void reset() {
		restoreSlot(Minecraft.getInstance());
		anchor = null;
		placedAtMs = 0L;
		nextAttemptAtMs = 0L;
		lastClickMs = 0L;
		watchedLevel = null;
		phase = Phase.IDLE;
		ticks = 0;
	}

	public void onChat(String raw) {
		if (raw == null || raw.isBlank() || anchor == null || lastClickMs <= 0L) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastClickMs > 3_000L) {
			return;
		}
		String text = strip(raw).toLowerCase(Locale.ROOT);
		if (text.startsWith("[stook]")) {
			return;
		}
		if (text.contains("not enough mana")
				|| text.contains("don't have enough mana")
				|| text.contains("do not have enough mana")) {
			anchor = null;
			placedAtMs = 0L;
			nextAttemptAtMs = now + FAIL_BACKOFF_MS;
			StookMod.LOGGER.info("Orb place rejected, retry in {}ms", FAIL_BACKOFF_MS);
		}
	}

	public void tick(Minecraft client, boolean allowStart) {
		if (!config.autoOrb) {
			if (phase != Phase.IDLE || anchor != null) {
				reset();
			}
			return;
		}

		LocalPlayer player = client.player;
		if (player == null || client.gameMode == null || client.level == null) {
			phase = Phase.IDLE;
			returnSlot = -1;
			anchor = null;
			placedAtMs = 0L;
			watchedLevel = null;
			return;
		}

		if (client.level != watchedLevel) {
			watchedLevel = client.level;
			anchor = null;
			placedAtMs = 0L;
		}

		if (phase != Phase.IDLE) {
			if (ClientScreens.isOpen(client)) {
				restoreSlot(client);
				return;
			}
			advance(client, player);
			return;
		}

		if (!allowStart || ClientScreens.isOpen(client)) {
			return;
		}
		if (!GardenDetector.onSkyblock()) {
			return;
		}
		if (!player.isAlive() || player.isSpectator() || player.isUsingItem() || player.isHandsBusy()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < nextAttemptAtMs || !needsReplace(player, now)) {
			return;
		}

		int slot = Mth.clamp(config.orbHotbarSlot, 1, Inventory.getSelectionSize()) - 1;
		ItemStack stack = player.getInventory().getItem(slot);
		if (stack.isEmpty() || player.getCooldowns().isOnCooldown(stack)) {
			return;
		}

		BlockHitResult ground = groundBeside(player);
		if (ground == null) {
			nextAttemptAtMs = now + RETRY_MS;
			return;
		}

		orbSlot = slot;
		returnSlot = player.getInventory().getSelectedSlot();
		select(player, orbSlot);
		phase = Phase.ARM;
		ticks = 0;
	}

	private void advance(Minecraft client, LocalPlayer player) {
		ticks++;
		if (phase == Phase.ARM) {
			select(player, orbSlot);
			if (ticks < ARM_TICKS) {
				return;
			}
			ItemStack stack = player.getInventory().getItem(orbSlot);
			BlockHitResult ground = groundBeside(player);
			if (stack.isEmpty() || ground == null) {
				nextAttemptAtMs = System.currentTimeMillis() + RETRY_MS;
				restoreSlot(client);
				return;
			}
			client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, ground);
			anchor = Vec3.atCenterOf(ground.getBlockPos()).add(0, 0.5, 0);
			placedAtMs = System.currentTimeMillis();
			lastClickMs = placedAtMs;
			nextAttemptAtMs = placedAtMs + RETRY_MS;
			StookMod.LOGGER.info("Orb placed at {}, slot {}", ground.getBlockPos(), orbSlot + 1);
			phase = Phase.RESTORE;
			ticks = 0;
			return;
		}
		restoreSlot(client);
	}

	private boolean needsReplace(LocalPlayer player, long now) {
		if (anchor == null || placedAtMs <= 0L) {
			return true;
		}
		if (now - placedAtMs >= DURATION_MS) {
			return true;
		}
		return player.position().distanceToSqr(anchor) > RADIUS_SQR;
	}

	private void restoreSlot(Minecraft client) {
		LocalPlayer player = client.player;
		if (player != null && returnSlot >= 0) {
			select(player, returnSlot);
		}
		returnSlot = -1;
		phase = Phase.IDLE;
		ticks = 0;
	}

	private static void select(LocalPlayer player, int slot) {
		int max = Inventory.getSelectionSize();
		slot = Mth.clamp(slot, 0, max - 1);
		if (player.getInventory().getSelectedSlot() == slot) {
			return;
		}
		player.getInventory().setSelectedSlot(slot);
		player.connection.send(new ServerboundSetCarriedItemPacket(slot));
	}

	/**
	 * Top face of a solid block beside the player, then under their feet.
	 * The spot stays inside reach so the right-click can land.
	 */
	private static BlockHitResult groundBeside(LocalPlayer player) {
		Level level = player.level();
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos feet = player.blockPosition();
		BlockPos[] columns = {
				feet.relative(facing),
				feet,
				feet.relative(side),
				feet.relative(side.getOpposite()),
				feet.relative(facing.getOpposite())
		};
		for (BlockPos column : columns) {
			BlockPos ground = surfaceBelow(level, column);
			if (ground != null) {
				Vec3 hit = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
				return new BlockHitResult(hit, Direction.UP, ground, false);
			}
		}
		return null;
	}

	private static BlockPos surfaceBelow(Level level, BlockPos column) {
		BlockPos pos = column;
		for (int i = 0; i <= MAX_DROP; i++) {
			BlockState here = level.getBlockState(pos);
			BlockPos below = pos.below();
			BlockState under = level.getBlockState(below);
			if (!here.blocksMotion() && under.blocksMotion()) {
				return below;
			}
			pos = below;
		}
		return null;
	}

	private static String strip(String value) {
		String stripped = ChatFormatting.stripFormatting(value);
		return stripped == null ? "" : stripped.trim();
	}
}
