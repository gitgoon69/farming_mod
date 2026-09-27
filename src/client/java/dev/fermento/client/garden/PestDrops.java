package dev.fermento.client.garden;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.fermento.client.prices.CoflBazaarService;

/**
 * Maps Hypixel pest loot names to NPC crop prices or Cofl Bazaar IDs.
 */
public final class PestDrops {
	public static final Set<String> BAZAAR_IDS = Set.of(
			"DUNG",
			"COMPOST",
			"HONEY_JAR",
			"PLANT_MATTER",
			"CHEESE_FUEL",
			"FINE_FLOUR",
			"ENCHANTED_COOKIE"
	);

	private static final Map<String, Entry> BY_NAME = new HashMap<>();

	static {
		npc("enchanted wheat", Crop.WHEAT.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted hay bale", 153_600);
		npc("enchanted seed", Crop.NPC_SEEDS * Crop.ENCHANTED_RATIO);
		npc("enchanted seeds", Crop.NPC_SEEDS * Crop.ENCHANTED_RATIO);
		npc("box of seeds", 76_800);
		npc("box of seed", 76_800);
		npc("enchanted carrot", Crop.CARROT.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted golden carrot", 81_920);
		npc("enchanted potato", Crop.POTATO.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted baked potato", 102_400);
		npc("enchanted pumpkin", Crop.PUMPKIN.npcPrice * Crop.ENCHANTED_RATIO);
		npc("polished pumpkin", 256_000);
		npc("enchanted melon", Crop.MELON.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted melon block", 51_200);
		npc("enchanted sugar", Crop.SUGAR_CANE.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted sugar cane", 102_400);
		npc("enchanted cactus green", Crop.CACTUS.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted cactus", 102_400);
		npc("enchanted cocoa", Crop.COCOA_BEANS.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted cocoa beans", Crop.COCOA_BEANS.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted red mushroom", Crop.MUSHROOM.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted brown mushroom", Crop.MUSHROOM.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted red mushroom block", 51_200);
		npc("enchanted brown mushroom block", 51_200);
		npc("enchanted nether wart", Crop.NETHER_WART.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted nether stalk", Crop.NETHER_WART.npcPrice * Crop.ENCHANTED_RATIO);
		npc("mutant nether wart", 102_400);
		npc("enchanted sunflower", Crop.SUNFLOWER.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted moonflower", Crop.MOONFLOWER.npcPrice * Crop.ENCHANTED_RATIO);
		npc("enchanted wild rose", Crop.WILD_ROSE.npcPrice * Crop.ENCHANTED_RATIO);
		bazaar("dung", "DUNG");
		bazaar("compost", "COMPOST");
		bazaar("honey jar", "HONEY_JAR");
		bazaar("plant matter", "PLANT_MATTER");
		bazaar("tasty cheese", "CHEESE_FUEL");
		bazaar("fine flour", "FINE_FLOUR");
		bazaar("enchanted cookie", "ENCHANTED_COOKIE");
	}

	private PestDrops() {
	}

	public static double unitValue(String itemName, CoflBazaarService prices, boolean sellOffer) {
		Entry entry = BY_NAME.get(normalize(itemName));
		if (entry == null) {
			return 0;
		}
		if (entry.npcPrice > 0) {
			return entry.npcPrice;
		}
		if (entry.bazaarId == null || prices == null) {
			return 0;
		}
		return prices.price(entry.bazaarId, sellOffer);
	}

	public static int killCoins(String pestName) {
		String name = normalize(pestName);
		if (name.contains("field mouse") || name.contains("mouse")) {
			return 10_000;
		}
		return 1_000;
	}

	static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.toLowerCase(Locale.ROOT)
				.replace('§', ' ')
				.replaceAll("[^a-z0-9 +]", " ")
				.replaceAll("\\s+", " ")
				.trim();
	}

	private static void npc(String name, double price) {
		BY_NAME.put(name, new Entry(price, null));
	}

	private static void bazaar(String name, String id) {
		BY_NAME.put(name, new Entry(0, id));
	}

	private record Entry(double npcPrice, String bazaarId) {
	}
}
