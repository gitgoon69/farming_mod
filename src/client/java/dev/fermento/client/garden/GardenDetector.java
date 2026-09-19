package dev.fermento.client.garden;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/**
 * Detects Hypixel SkyBlock / Garden from the scoreboard, like Skyblocker {@code Utils}.
 */
public final class GardenDetector {
	private static boolean onHypixel;
	private static boolean onSkyblock;
	private static boolean inGarden;
	private static int gardenMissTicks;

	private GardenDetector() {
	}

	public static boolean inGarden() {
		return inGarden;
	}

	public static boolean onSkyblock() {
		return onSkyblock;
	}

	public static boolean onHypixel() {
		return onHypixel || isConnectedToHypixel(Minecraft.getInstance());
	}

	public static void tick(Minecraft client) {
		onHypixel = isConnectedToHypixel(client);

		if (client.level == null || client.player == null) {
			onSkyblock = false;
			inGarden = false;
			gardenMissTicks = 0;
			return;
		}

		if (!onHypixel && !client.hasSingleplayerServer()) {
			onSkyblock = false;
			inGarden = false;
			return;
		}

		StringBuilder sidebar = new StringBuilder();
		readSidebar(client.level, sidebar);
		String text = sidebar.toString();

		onSkyblock = text.toUpperCase().contains("SKYBLOCK") || text.contains("Purse:") || text.contains("Piggy:");
		boolean detectedGarden = onSkyblock && containsGarden(text);
		if (detectedGarden) {
			gardenMissTicks = 0;
			inGarden = true;
		} else if (inGarden) {
			gardenMissTicks++;
			if (gardenMissTicks > 60) {
				inGarden = false;
			}
		} else {
			inGarden = false;
		}
	}

	public static void reset() {
		onHypixel = false;
		onSkyblock = false;
		inGarden = false;
		gardenMissTicks = 0;
	}

	private static boolean containsGarden(String text) {
		String stripped = ChatFormatting.stripFormatting(text);
		if (stripped == null) {
			stripped = text;
		}
		return stripped.contains("The Garden") || stripped.contains("Garden")
				|| stripped.contains("Plot -") || stripped.contains("Plot —");
	}

	private static boolean isConnectedToHypixel(Minecraft client) {
		String address = client.getCurrentServer() != null ? client.getCurrentServer().ip.toLowerCase() : "";
		LocalPlayer player = client.player;
		String brand = "";
		if (player != null && player.connection != null && player.connection.serverBrand() != null) {
			brand = player.connection.serverBrand();
		}
		return address.contains("hypixel.net") || address.contains("hypixel.io") || brand.contains("Hypixel");
	}

	private static void readSidebar(ClientLevel level, StringBuilder out) {
		Scoreboard scoreboard = level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (objective == null) {
			objective = scoreboard.getDisplayObjective(DisplaySlot.BY_ID.apply(1));
		}
		if (objective == null) {
			return;
		}

		out.append(objective.getDisplayName().getString()).append('\n');

		for (ScoreHolder holder : scoreboard.getTrackedPlayers()) {
			if (!scoreboard.listPlayerScores(holder).containsKey(objective)) {
				continue;
			}
			PlayerTeam team = scoreboard.getPlayersTeam(holder.getScoreboardName());
			if (team == null) {
				continue;
			}
			Component line = Component.empty().append(team.getPlayerPrefix()).append(team.getPlayerSuffix());
			String str = ChatFormatting.stripFormatting(line.getString());
			if (str != null && !str.isBlank()) {
				out.append(str).append('\n');
			}
		}
	}
}
