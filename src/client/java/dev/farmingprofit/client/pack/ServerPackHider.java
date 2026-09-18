package dev.farmingprofit.client.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.farmingprofit.client.FarmingProfitClient;
import dev.farmingprofit.client.compat.PolarPresence;
import dev.farmingprofit.client.config.ModConfig;
import dev.farmingprofit.client.garden.GardenDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

/**
 * Keeps the Hypixel server pack (SkyBlock item textures) at the lowest priority.
 */
public final class ServerPackHider {
	private ServerPackHider() {
	}

	public static boolean enabled() {
		if (PolarPresence.detected()) {
			return false;
		}
		ModConfig config = FarmingProfitClient.config();
		return config != null && config.hideServerResourcePack && isHypixelPack();
	}

	public static boolean shouldHide(ClientboundResourcePackPushPacket packet) {
		if (PolarPresence.detected()) {
			return false;
		}
		ModConfig config = FarmingProfitClient.config();
		if (config == null || !config.hideServerResourcePack) {
			return false;
		}
		if (isHypixelPack()) {
			return true;
		}
		String prompt = packet.prompt().map(component -> component.getString()).orElse("");
		String blob = (packet.url() + " " + prompt).toLowerCase(Locale.ROOT);
		return blob.contains("hypixel") || blob.contains("skyblock");
	}

	public static boolean isHypixelPack() {
		Minecraft client = Minecraft.getInstance();
		if (client.getCurrentServer() != null) {
			String ip = client.getCurrentServer().ip.toLowerCase(Locale.ROOT);
			if (ip.contains("hypixel.net") || ip.contains("hypixel.io")) {
				return true;
			}
		}
		return GardenDetector.onHypixel();
	}

	/**
	 * Puts server packs at the bottom: vanilla and personal packs take priority,
	 * SkyBlock item textures stay as fallback.
	 */
	public static List<Pack> withServerPacksLowest(List<Pack> selected) {
		List<Pack> serverPacks = new ArrayList<>();
		List<Pack> others = new ArrayList<>();
		for (Pack pack : selected) {
			if (pack.getPackSource() == PackSource.SERVER) {
				serverPacks.add(pack);
			} else {
				others.add(pack);
			}
		}
		if (serverPacks.isEmpty()) {
			return null;
		}
		others.addAll(0, serverPacks);
		return List.copyOf(others);
	}
}
