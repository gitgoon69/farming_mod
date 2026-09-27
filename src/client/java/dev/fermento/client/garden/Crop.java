package dev.fermento.client.garden;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * SkyBlock crop → tool → enchanted bazaar item mapping.
 * Inspired by SkyHanni {@code CropType} and Skyblocker {@code FarmingHudWidget.FARMING_TOOLS}.
 * 1 enchanted = 160 normal items, so price/crop = enchanted price / 160.
 */
public enum Crop {
	WHEAT("Wheat", "WHEAT", "ENCHANTED_WHEAT", false, 6),
	CARROT("Carrot", "CARROT_ITEM", "ENCHANTED_CARROT", true, 3),
	POTATO("Potato", "POTATO_ITEM", "ENCHANTED_POTATO", true, 3),
	NETHER_WART("Nether Wart", "NETHER_STALK", "ENCHANTED_NETHER_STALK", true, 4),
	PUMPKIN("Pumpkin", "PUMPKIN", "ENCHANTED_PUMPKIN", false, 10),
	MELON("Melon", "MELON", "ENCHANTED_MELON", false, 2),
	COCOA_BEANS("Cocoa Beans", "INK_SACK:3", "ENCHANTED_COCOA", true, 3),
	SUGAR_CANE("Sugar Cane", "SUGAR_CANE", "ENCHANTED_SUGAR", false, 4),
	CACTUS("Cactus", "CACTUS", "ENCHANTED_CACTUS_GREEN", false, 4),
	MUSHROOM("Mushroom", "RED_MUSHROOM", "ENCHANTED_RED_MUSHROOM", false, 10),
	SUNFLOWER("Sunflower", "DOUBLE_PLANT", "ENCHANTED_SUNFLOWER", true, 4),
	MOONFLOWER("Moonflower", "MOONFLOWER", "ENCHANTED_MOONFLOWER", true, 4),
	WILD_ROSE("Wild Rose", "WILD_ROSE", "ENCHANTED_WILD_ROSE", true, 4);

	public static final String ENCHANTED_SEEDS = "ENCHANTED_SEEDS";
	public static final String ENCHANTED_BROWN_MUSHROOM = "ENCHANTED_BROWN_MUSHROOM";
	public static final int ENCHANTED_RATIO = 160;
	/** NPC sell price of seeds (wheat counter mix). */
	public static final double NPC_SEEDS = 3;

	private static final Map<String, Crop> BY_TOOL = new HashMap<>();
	private static final Set<String> GENERIC_TOOLS = Set.of(
			"BASIC_GARDENING_HOE",
			"ADVANCED_GARDENING_HOE",
			"BASIC_GARDENING_AXE",
			"ADVANCED_GARDENING_AXE",
			"BINGHOE"
	);

	static {
		bindTools("THEORETICAL_HOE_WHEAT", WHEAT);
		bindTools("THEORETICAL_HOE_CARROT", CARROT);
		bindTools("THEORETICAL_HOE_POTATO", POTATO);
		bindTools("THEORETICAL_HOE_CANE", SUGAR_CANE);
		bindTools("THEORETICAL_HOE_WARTS", NETHER_WART);
		bindTools("THEORETICAL_HOE_SUNFLOWER", SUNFLOWER);
		bindTools("THEORETICAL_HOE_WILD_ROSE", WILD_ROSE);
		bindTools("FUNGI_CUTTER", MUSHROOM);
		bindTools("CACTUS_KNIFE", CACTUS);
		bindTools("MELON_DICER", MELON);
		bindTools("PUMPKIN_DICER", PUMPKIN);
		bindTools("COCO_CHOPPER", COCOA_BEANS);
	}

	public final String displayName;
	public final String hypixelItemId;
	public final String enchantedBazaarId;
	public final boolean replenishCrop;
	/** NPC sell price of one harvested item. */
	public final double npcPrice;

	Crop(String displayName, String hypixelItemId, String enchantedBazaarId, boolean replenishCrop, double npcPrice) {
		this.displayName = displayName;
		this.hypixelItemId = hypixelItemId;
		this.enchantedBazaarId = enchantedBazaarId;
		this.replenishCrop = replenishCrop;
		this.npcPrice = npcPrice;
	}

	/**
	 * Coins per Cultivating increment at NPC. Wheat + seeds uses the ~40/60 mix.
	 */
	public double npcUnitPrice(boolean includeSeeds) {
		if (this == WHEAT && includeSeeds) {
			return npcPrice * 0.4 + NPC_SEEDS * 0.6;
		}
		return npcPrice;
	}

	private static void bindTools(String baseId, Crop crop) {
		BY_TOOL.put(baseId, crop);
		BY_TOOL.put(baseId + "_1", crop);
		BY_TOOL.put(baseId + "_2", crop);
		BY_TOOL.put(baseId + "_3", crop);
	}

	public static Crop fromToolId(String skyblockId) {
		if (skyblockId == null || skyblockId.isEmpty()) {
			return null;
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		Crop exact = BY_TOOL.get(key);
		if (exact != null) {
			return exact;
		}
		if (key.startsWith("THEORETICAL_HOE_WHEAT")) {
			return WHEAT;
		}
		if (key.startsWith("THEORETICAL_HOE_CARROT")) {
			return CARROT;
		}
		if (key.startsWith("THEORETICAL_HOE_POTATO")) {
			return POTATO;
		}
		if (key.startsWith("THEORETICAL_HOE_CANE")) {
			return SUGAR_CANE;
		}
		if (key.startsWith("THEORETICAL_HOE_WARTS")) {
			return NETHER_WART;
		}
		if (key.startsWith("THEORETICAL_HOE_SUNFLOWER")) {
			return SUNFLOWER;
		}
		if (key.startsWith("THEORETICAL_HOE_WILD_ROSE")) {
			return WILD_ROSE;
		}
		if (key.startsWith("FUNGI_CUTTER")) {
			return MUSHROOM;
		}
		if (key.startsWith("CACTUS_KNIFE")) {
			return CACTUS;
		}
		if (key.startsWith("MELON_DICER")) {
			return MELON;
		}
		if (key.startsWith("PUMPKIN_DICER")) {
			return PUMPKIN;
		}
		if (key.startsWith("COCO_CHOPPER")) {
			return COCOA_BEANS;
		}
		return null;
	}

	public static boolean isFarmingTool(String skyblockId) {
		if (skyblockId == null || skyblockId.isEmpty()) {
			return false;
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		return fromToolId(key) != null || GENERIC_TOOLS.contains(key);
	}

	public static Crop fromBlock(Block block) {
		if (block == Blocks.WHEAT) {
			return WHEAT;
		}
		if (block == Blocks.CARROTS) {
			return CARROT;
		}
		if (block == Blocks.POTATOES) {
			return POTATO;
		}
		if (block == Blocks.PUMPKIN || block == Blocks.CARVED_PUMPKIN) {
			return PUMPKIN;
		}
		if (block == Blocks.SUGAR_CANE) {
			return SUGAR_CANE;
		}
		if (block == Blocks.MELON) {
			return MELON;
		}
		if (block == Blocks.CACTUS) {
			return CACTUS;
		}
		if (block == Blocks.COCOA) {
			return COCOA_BEANS;
		}
		if (block == Blocks.RED_MUSHROOM || block == Blocks.BROWN_MUSHROOM
				|| block == Blocks.RED_MUSHROOM_BLOCK || block == Blocks.BROWN_MUSHROOM_BLOCK) {
			return MUSHROOM;
		}
		if (block == Blocks.NETHER_WART) {
			return NETHER_WART;
		}
		if (block == Blocks.ROSE_BUSH) {
			return WILD_ROSE;
		}
		if (block == Blocks.SUNFLOWER) {
			return SUNFLOWER;
		}
		return null;
	}

	/**
	 * Sunflower hoe: day = sunflower, night = moonflower (like Skyblocker).
	 */
	public static Crop resolveTimeFlower(Crop crop, long dayTime) {
		if (crop != SUNFLOWER && crop != MOONFLOWER) {
			return crop;
		}
		long timeOfDay = Math.floorMod(dayTime, 24000L);
		return timeOfDay >= 12000L ? MOONFLOWER : SUNFLOWER;
	}
}
