package dev.farmingprofit.client.garden;

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
	WHEAT("Wheat", "WHEAT", "ENCHANTED_WHEAT", false),
	CARROT("Carrot", "CARROT_ITEM", "ENCHANTED_CARROT", true),
	POTATO("Potato", "POTATO_ITEM", "ENCHANTED_POTATO", true),
	NETHER_WART("Nether Wart", "NETHER_STALK", "ENCHANTED_NETHER_STALK", true),
	PUMPKIN("Pumpkin", "PUMPKIN", "ENCHANTED_PUMPKIN", false),
	MELON("Melon", "MELON", "ENCHANTED_MELON", false),
	COCOA_BEANS("Cocoa Beans", "INK_SACK:3", "ENCHANTED_COCOA", true),
	SUGAR_CANE("Sugar Cane", "SUGAR_CANE", "ENCHANTED_SUGAR", false),
	CACTUS("Cactus", "CACTUS", "ENCHANTED_CACTUS_GREEN", false),
	MUSHROOM("Mushroom", "RED_MUSHROOM", "ENCHANTED_RED_MUSHROOM", false),
	SUNFLOWER("Sunflower", "DOUBLE_PLANT", "ENCHANTED_SUNFLOWER", true),
	MOONFLOWER("Moonflower", "MOONFLOWER", "ENCHANTED_MOONFLOWER", true),
	WILD_ROSE("Wild Rose", "WILD_ROSE", "ENCHANTED_WILD_ROSE", true);

	public static final String ENCHANTED_SEEDS = "ENCHANTED_SEEDS";
	public static final String ENCHANTED_BROWN_MUSHROOM = "ENCHANTED_BROWN_MUSHROOM";
	public static final int ENCHANTED_RATIO = 160;

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

	Crop(String displayName, String hypixelItemId, String enchantedBazaarId, boolean replenishCrop) {
		this.displayName = displayName;
		this.hypixelItemId = hypixelItemId;
		this.enchantedBazaarId = enchantedBazaarId;
		this.replenishCrop = replenishCrop;
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
