package dev.farmingprofit.client.garden;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;

/**
 * Hypixel tab lines (widgets = team prefix/suffix, unlisted fake players).
 */
public final class TabList {
	private TabList() {
	}

	public static List<String> lines(Minecraft client) {
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		if (client.player == null || client.player.connection == null) {
			return List.of();
		}
		ClientPacketListener connection = client.player.connection;
		Set<PlayerInfo> entries = new LinkedHashSet<>();
		addAll(entries, connection.getListedOnlinePlayers());
		addAll(entries, connection.getOnlinePlayers());
		for (PlayerInfo info : entries) {
			for (String line : lineVariants(info)) {
				if (!line.isBlank()) {
					unique.add(line);
				}
			}
		}
		return new ArrayList<>(unique);
	}

	private static void addAll(Set<PlayerInfo> target, Collection<PlayerInfo> source) {
		if (source != null) {
			target.addAll(source);
		}
	}

	private static List<String> lineVariants(PlayerInfo info) {
		List<String> variants = new ArrayList<>(3);
		Component display = info.getTabListDisplayName();
		if (display != null) {
			addClean(variants, display.getString());
		}
		PlayerTeam team = info.getTeam();
		String name = info.getProfile().name();
		if (team != null) {
			addClean(variants, team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString());
			addClean(variants, team.getPlayerPrefix().getString() + name + team.getPlayerSuffix().getString());
		} else {
			addClean(variants, name);
		}
		return variants;
	}

	private static void addClean(List<String> out, String raw) {
		String cleaned = clean(raw);
		if (!cleaned.isBlank()) {
			out.add(cleaned);
		}
	}

	static String clean(String text) {
		if (text == null) {
			return "";
		}
		String stripped = ChatFormatting.stripFormatting(text);
		if (stripped == null) {
			stripped = text;
		}
		stripped = stripped
				.replace('\u00A0', ' ')
				.replace('\u202F', ' ')
				.replace('\u2007', ' ')
				.replace('\u200B', ' ')
				.replaceAll("![A-Za-z0-9._-]{1,24}", " ")
				.replaceAll("\\s+", " ")
				.trim();
		return stripped;
	}
}
