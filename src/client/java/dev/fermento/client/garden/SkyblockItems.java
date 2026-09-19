package dev.fermento.client.garden;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Reads Hypixel ExtraAttributes (id, cultivating, replenish) like Skyblocker {@code ItemUtils}/{@code FarmingHud}.
 */
public final class SkyblockItems {
	public static final String ID_KEY = "id";
	public static final String CULTIVATING_KEY = "farmed_cultivating";
	public static final String COUNTER_KEY = "counter";

	private SkyblockItems() {
	}

	public static CompoundTag extraAttributes(ItemStack stack) {
		CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		CompoundTag tag = customData.copyTag();
		if (tag.contains("ExtraAttributes")) {
			return tag.getCompoundOrEmpty("ExtraAttributes");
		}
		return tag;
	}

	public static String skyblockId(ItemStack stack) {
		return extraAttributes(stack).getStringOr(ID_KEY, "");
	}

	public static long cultivatingOrCounter(ItemStack stack) {
		CompoundTag extra = extraAttributes(stack);
		Long cultivating = numeric(extra, CULTIVATING_KEY);
		if (cultivating != null) {
			return cultivating;
		}
		Long counter = numeric(extra, COUNTER_KEY);
		return counter != null ? counter : -1L;
	}

	public static boolean hasReplenish(ItemStack stack) {
		CompoundTag extra = extraAttributes(stack);
		CompoundTag enchants = extra.getCompoundOrEmpty("enchantments");
		return enchants.contains("replenish");
	}

	private static Long numeric(CompoundTag tag, String key) {
		if (tag.get(key) instanceof NumericTag) {
			return tag.getLongOr(key, 0L);
		}
		return null;
	}
}
