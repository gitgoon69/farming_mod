package dev.fermento.client.garden;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.fermento.client.config.ModConfig;
import dev.fermento.client.prices.CoflBazaarService;
import net.minecraft.ChatFormatting;

/**
 * Parses Garden pest loot chat:
 * {@code You received 6x Enchanted Cocoa Beans for killing a Moth!}
 * {@code RARE DROP! Mutant Nether Wart x9 (+134)}
 */
public final class PestDropTracker {
	private static final Pattern KILL = Pattern.compile(
			"(?i)you received\\s+(\\d+)x\\s+(.+?)\\s+for killing an?\\s+(.+?)\\s*!"
	);
	private static final Pattern RARE = Pattern.compile(
			"(?i)(?:rare|pet)\\s+drop!\\s+(.+?)(?:\\s+x([\\d,]+))?(?:\\s+\\(.+\\))?\\s*$"
	);
	private static final long KILL_COIN_DEBOUNCE_MS = 1_500L;

	private final List<Loot> loot = new ArrayList<>();
	private double sessionKillCoins;
	private int sessionKills;
	private long lastKillCoinMs;

	public void onChat(String raw) {
		if (raw == null || raw.isBlank() || !GardenDetector.inGarden()) {
			return;
		}
		String text = clean(raw);
		if (text.isBlank()) {
			return;
		}

		Matcher kill = KILL.matcher(text);
		if (kill.find()) {
			int amount = parseInt(kill.group(1), 1);
			if (amount > 0) {
				loot.add(new Loot(kill.group(2).trim(), amount));
			}
			countKill(kill.group(3));
			return;
		}

		Matcher rare = RARE.matcher(text);
		if (rare.find()) {
			int amount = parseInt(rare.group(2), 1);
			if (amount > 0) {
				loot.add(new Loot(rare.group(1).trim(), amount));
			}
		}
	}

	public void reset() {
		loot.clear();
		sessionKillCoins = 0;
		sessionKills = 0;
		lastKillCoinMs = 0;
	}

	public double sessionProfit(CoflBazaarService prices, ModConfig config) {
		if (config == null || !config.includePestDrops) {
			return 0;
		}
		boolean sellOffer = config.useSellOffer();
		double total = sessionKillCoins;
		for (Loot drop : loot) {
			double unit = PestDrops.unitValue(drop.item(), prices, sellOffer);
			if (unit > 0) {
				total += unit * drop.amount();
			}
		}
		return total;
	}

	public int sessionKills() {
		return sessionKills;
	}

	private void countKill(String pestName) {
		long now = System.currentTimeMillis();
		if (now - lastKillCoinMs < KILL_COIN_DEBOUNCE_MS) {
			return;
		}
		lastKillCoinMs = now;
		sessionKills++;
		sessionKillCoins += PestDrops.killCoins(pestName);
	}

	private static int parseInt(String raw, int fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			return Integer.parseInt(raw.replace(",", "").trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	static String clean(String raw) {
		String stripped = ChatFormatting.stripFormatting(raw);
		if (stripped == null) {
			stripped = raw;
		}
		return stripped
				.replace('\u00A0', ' ')
				.replace('\u202F', ' ')
				.replaceAll("\\s+", " ")
				.trim();
	}

	private record Loot(String item, int amount) {
	}
}
