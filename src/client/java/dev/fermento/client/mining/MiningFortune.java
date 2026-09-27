package dev.fermento.client.mining;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mining Fortune and Block Fortune.
 * Drop average follows the SkyBlock formula used by SkyHanni and the wiki:
 * {@code base * (1 + fortune / 100)}. Block Fortune is added to Mining Fortune
 * for End Stone (same way gemstone fortune is added for gemstones).
 */
public final class MiningFortune {
	/** Hypixel icon shared by Mining Fortune and Block Fortune (SkyHanni {@code SkyblockStat}). */
	static final char FORTUNE_ICON = '\uE053';
	private static final char LEGACY_CLOVER = '\u2618';

	private static final Pattern NAMED = Pattern.compile(
			"(?i)\\b(mining fortune|block fortune)\\b\\s*:?\\s*[^\\d]{0,6}([\\d,]{1,9})"
	);
	private static final Pattern ICON_NUMBER = Pattern.compile(
			"([\\d,]{1,9})\\s*(?:" + FORTUNE_ICON + "|" + LEGACY_CLOVER + ")"
	);

	private MiningFortune() {
	}

	public record Reading(int mining, int block, boolean miningKnown, boolean blockKnown) {
		public static final Reading NONE = new Reading(0, 0, false, false);

		public boolean known() {
			return miningKnown || blockKnown;
		}

		public int total() {
			return (miningKnown ? mining : 0) + (blockKnown ? block : 0);
		}

		/** Average End Stone items per broken block. Base amount is 1. */
		public double dropsPerBlock() {
			if (!known()) {
				return 0;
			}
			return 1.0 + total() / 100.0;
		}

		Reading mergeMissing(Reading previous) {
			if (previous == null) {
				return this;
			}
			int miningValue = miningKnown ? mining : previous.mining;
			int blockValue = blockKnown ? block : previous.block;
			return new Reading(
					miningValue,
					blockValue,
					miningKnown || previous.miningKnown,
					blockKnown || previous.blockKnown
			);
		}
	}

	public static Reading read(String actionBar, List<String> tabLines) {
		int mining = 0;
		int block = 0;
		boolean miningKnown = false;
		boolean blockKnown = false;

		if (tabLines != null) {
			for (String line : tabLines) {
				if (line == null || line.isEmpty()) {
					continue;
				}
				Matcher named = NAMED.matcher(line);
				while (named.find()) {
					int value = parse(named.group(2));
					if (value < 0) {
						continue;
					}
					if (named.group(1).toLowerCase().startsWith("block")) {
						block = value;
						blockKnown = true;
					} else {
						mining = value;
						miningKnown = true;
					}
				}
			}
		}

		if (!miningKnown && actionBar != null && !actionBar.isEmpty()) {
			Matcher named = NAMED.matcher(actionBar);
			while (named.find()) {
				int value = parse(named.group(2));
				if (value < 0) {
					continue;
				}
				if (named.group(1).toLowerCase().startsWith("block")) {
					block = value;
					blockKnown = true;
				} else {
					mining = value;
					miningKnown = true;
				}
			}
			if (!miningKnown) {
				Matcher icon = ICON_NUMBER.matcher(actionBar);
				int last = -1;
				while (icon.find()) {
					last = parse(icon.group(1));
				}
				if (last >= 0) {
					mining = last;
					miningKnown = true;
				}
			}
		}

		if (!miningKnown && !blockKnown) {
			return Reading.NONE;
		}
		return new Reading(mining, block, miningKnown, blockKnown);
	}

	private static int parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return -1;
		}
		try {
			int value = Integer.parseInt(raw.replace(",", ""));
			if (value < 0 || value > 100_000) {
				return -1;
			}
			return value;
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
