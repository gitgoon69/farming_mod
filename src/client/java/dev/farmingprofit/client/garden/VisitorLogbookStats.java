package dev.farmingprofit.client.garden;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.farmingprofit.client.compat.ClientScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Visitor's Logbook overlay: unique served = visitors with Offers Accepted &gt; 0.
 * Same source as SkyHanni {@code LogBookStats} (GUI lore), equivalent to API
 * {@code garden.commission_data.unique_npcs_served} without a Hypixel key.
 */
public final class VisitorLogbookStats {
	private static final Pattern PAGE_TITLE = Pattern.compile("\\((\\d+)/(\\d+)\\)");
	private static final Pattern VISITED = Pattern.compile("Times Visited:\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern ACCEPTED = Pattern.compile("(?:Offers|Times) Accepted:\\s*([0-9,]+)", Pattern.CASE_INSENSITIVE);

	private static final int TITLE = 0xFFFFD54A;
	private static final int VALUE = 0xFFFFF8E1;
	private static final int MUTED = 0xFFBDBDBD;

	private static final Map<String, VisitorEntry> visitors = new LinkedHashMap<>();
	private static final Set<Integer> seenPages = new HashSet<>();
	private static final Field LEFT_POS = field("leftPos");
	private static final Field TOP_POS = field("topPos");
	private static final Field IMAGE_WIDTH = field("imageWidth");
	private static boolean open;
	private static int totalPages;

	private VisitorLogbookStats() {
	}

	public static void tick(Minecraft client) {
		if (!(ClientScreens.current(client) instanceof AbstractContainerScreen<?> screen)) {
			open = false;
			return;
		}
		String title = screen.getTitle().getString();
		if (!isLogbook(title)) {
			open = false;
			return;
		}
		open = true;
		updatePages(title);
		scan(screen.getMenu());
	}

	public static void reset() {
		visitors.clear();
		seenPages.clear();
		open = false;
		totalPages = 0;
	}

	public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
		render(screen, graphics, intField(screen, LEFT_POS, 0), intField(screen, TOP_POS, 0), intField(screen, IMAGE_WIDTH, 176));
	}

	public static void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int leftPos, int topPos, int imageWidth) {
		if (!open) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Font font = client.font;
		int unique = uniqueServed();
		long offers = totalAccepted();
		boolean complete = totalPages > 0 && seenPages.size() >= totalPages;

		String[] lines = complete
				? new String[] {
						"Visitor's Logbook",
						"Unique served: " + unique,
						"Offers accepted: " + format(offers)
				}
				: new String[] {
						"Visitor's Logbook",
						"Unique served: " + unique,
						"Offers accepted: " + format(offers),
						"Open every page"
				};

		int x = leftPos + imageWidth + 8;
		int y = topPos + 6;
		int width = 0;
		for (String line : lines) {
			width = Math.max(width, font.width(line));
		}
		int height = lines.length * 10 + 6;
		graphics.fill(x - 4, y - 4, x + width + 4, y + height, 0x90000000);

		int drawY = y;
		for (int i = 0; i < lines.length; i++) {
			int color = i == 0 ? TITLE : (i == lines.length - 1 && !complete ? MUTED : VALUE);
			graphics.text(font, lines[i], x, drawY, color, true);
			drawY += 10;
		}
	}

	private static void scan(AbstractContainerMenu menu) {
		for (Slot slot : menu.slots) {
			if (slot.container instanceof Inventory) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			long visited = 0;
			long accepted = 0;
			boolean visitorItem = false;
			ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
			for (Component line : lore.lines()) {
				String text = line.getString();
				Matcher vis = VISITED.matcher(text);
				if (vis.find()) {
					visited = parseCount(vis.group(1));
					visitorItem = true;
				}
				Matcher acc = ACCEPTED.matcher(text);
				if (acc.find()) {
					accepted = parseCount(acc.group(1));
					visitorItem = true;
				}
			}
			if (!visitorItem) {
				continue;
			}
			String name = stack.getHoverName().getString();
			if (name.isBlank()) {
				continue;
			}
			visitors.put(name, new VisitorEntry(visited, accepted));
		}
	}

	private static void updatePages(String title) {
		Matcher matcher = PAGE_TITLE.matcher(title);
		if (matcher.find()) {
			seenPages.add(Integer.parseInt(matcher.group(1)));
			totalPages = Math.max(totalPages, Integer.parseInt(matcher.group(2)));
		} else if (totalPages == 0) {
			seenPages.add(1);
			totalPages = 1;
		}
	}

	private static Field field(String name) {
		try {
			Field field = AbstractContainerScreen.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException e) {
			return null;
		}
	}

	private static int intField(AbstractContainerScreen<?> screen, Field field, int fallback) {
		if (field == null) {
			return fallback;
		}
		try {
			return field.getInt(screen);
		} catch (IllegalAccessException e) {
			return fallback;
		}
	}

	private static boolean isLogbook(String title) {
		String lower = title.toLowerCase(Locale.ROOT);
		return lower.contains("visitor") && (lower.contains("logbook") || lower.contains("log book"));
	}

	private static int uniqueServed() {
		int count = 0;
		for (VisitorEntry entry : visitors.values()) {
			if (entry.accepted > 0) {
				count++;
			}
		}
		return count;
	}

	private static long totalAccepted() {
		long sum = 0;
		for (VisitorEntry entry : visitors.values()) {
			sum += entry.accepted;
		}
		return sum;
	}

	private static long parseCount(String raw) {
		try {
			return Long.parseLong(raw.replace(",", "").replace(" ", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String format(long value) {
		return String.format(Locale.US, "%,d", value);
	}

	private record VisitorEntry(long visited, long accepted) {
	}
}
