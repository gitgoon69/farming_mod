package dev.fermento.client.garden;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.fermento.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Reads {@code Cooldown: 2m 50s} from the tab Pests widget, then shows
 * a 5s countdown.
 */
public final class PestCooldownTracker {
	private static final Pattern COOLDOWN = Pattern.compile("(?i)cooldown:\\s*(.+)$");
	private static final Pattern HOURS = Pattern.compile("(\\d+)\\s*h", Pattern.CASE_INSENSITIVE);
	private static final Pattern MINUTES = Pattern.compile("(\\d+)\\s*m(?!s)", Pattern.CASE_INSENSITIVE);
	private static final Pattern SECONDS = Pattern.compile("(\\d+)\\s*s", Pattern.CASE_INSENSITIVE);
	private static final Pattern CLOCK = Pattern.compile("(\\d{1,2}):(\\d{2})");
	private static final Pattern BARE_NUMBER = Pattern.compile("^\\d{1,3}$");

	private long endsAtMs;
	private long countdownEndsAtMs;
	private State state = State.UNKNOWN;
	private boolean alertArmed = true;
	private boolean lastParseCoarse;
	private int lastBeepSecond = -1;
	private int previousRemaining = -1;
	private int awayTicks;
	private boolean seenCooldownLine;
	private boolean pendingAlertTrigger;
	private boolean firedThisCycle;

	public enum State {
		UNKNOWN, TIMING, READY, MAX
	}

	public void tick(Minecraft client, ModConfig config) {
		if (client.player == null) {
			return;
		}
		if (!config.pestCooldownAlert && !config.autoPestLoadout) {
			return;
		}

		boolean parsed = parseTab(client);
		if (parsed || GardenDetector.inGarden()) {
			awayTicks = 0;
		} else {
			awayTicks++;
			if (awayTicks > 80) {
				reset();
			}
			return;
		}

		int remaining = remainingSeconds();
		int triggerAt = Math.max(1, config.pestCooldownAlertAtSeconds);
		int windowLow = Math.max(1, triggerAt - 20);
		if (state == State.TIMING && !lastParseCoarse && remaining > Math.max(triggerAt + 30, 200)) {
			alertArmed = true;
			firedThisCycle = false;
		}
		if (state == State.READY || state == State.MAX) {
			alertArmed = true;
			firedThisCycle = false;
			countdownEndsAtMs = 0;
		}

		boolean inWindow = remaining > 0 && remaining <= triggerAt && remaining >= windowLow;
		boolean crossed = previousRemaining > triggerAt && remaining <= triggerAt && remaining >= windowLow;
		boolean joinedInWindow = previousRemaining < 0 && inWindow;
		if (alertArmed && !firedThisCycle && state == State.TIMING && !lastParseCoarse && (crossed || joinedInWindow)) {
			alertArmed = false;
			firedThisCycle = true;
			pendingAlertTrigger = true;
			int length = Math.max(1, config.pestCooldownAlertCountdown);
			countdownEndsAtMs = System.currentTimeMillis() + length * 1000L;
			lastBeepSecond = -1;
		}
		previousRemaining = remaining;

		if (!config.pestCooldownAlert) {
			return;
		}

		int countdown = alertCountdownSeconds();
		if (countdown <= 0) {
			return;
		}
		if (lastBeepSecond != countdown) {
			lastBeepSecond = countdown;
			if (countdown == Math.max(1, config.pestCooldownAlertCountdown)) {
				playLoud(client, SoundEvents.PLAYER_LEVELUP, 0.9f, 3.0f);
				playLoud(client, SoundEvents.NOTE_BLOCK_PLING.value(), 1.4f, 3.0f);
				playLoud(client, SoundEvents.NOTE_BLOCK_BELL.value(), 1.1f, 2.5f);
			} else {
				playLoud(client, SoundEvents.NOTE_BLOCK_PLING.value(), countdown <= 2 ? 1.8f : 1.35f, 2.8f);
			}
		}
	}

	public boolean alerting(ModConfig config) {
		return config.pestCooldownAlert && alertCountdownSeconds() > 0;
	}

	public int alertCountdownSeconds() {
		if (countdownEndsAtMs <= 0) {
			return -1;
		}
		long left = countdownEndsAtMs - System.currentTimeMillis();
		if (left <= 0) {
			return 0;
		}
		return (int) Math.ceil(left / 1000.0);
	}

	public int remainingSeconds() {
		if (state != State.TIMING || endsAtMs <= 0) {
			return -1;
		}
		long left = endsAtMs - System.currentTimeMillis();
		if (left <= 0) {
			return 0;
		}
		return (int) Math.ceil(left / 1000.0);
	}

	public State state() {
		return state;
	}

	public boolean seenCooldownLine() {
		return seenCooldownLine;
	}

	public boolean consumeAlertTrigger() {
		if (!pendingAlertTrigger) {
			return false;
		}
		pendingAlertTrigger = false;
		return true;
	}

	public void reset() {
		endsAtMs = 0;
		countdownEndsAtMs = 0;
		state = State.UNKNOWN;
		alertArmed = true;
		lastParseCoarse = false;
		lastBeepSecond = -1;
		previousRemaining = -1;
		awayTicks = 0;
		seenCooldownLine = false;
		pendingAlertTrigger = false;
		firedThisCycle = false;
	}

	private boolean parseTab(Minecraft client) {
		List<String> lines = TabList.lines(client);
		for (int i = 0; i < lines.size(); i++) {
			Matcher matcher = COOLDOWN.matcher(lines.get(i));
			if (!matcher.find()) {
				continue;
			}
			if (!pestContext(lines, i)) {
				continue;
			}
			seenCooldownLine = true;
			String value = matcher.group(1).trim();
			String upper = value.toUpperCase(Locale.ROOT);
			if (upper.contains("READY")) {
				applyParsed(State.READY, 0, false);
				return true;
			}
			if (upper.contains("MAX")) {
				applyParsed(State.MAX, 0, false);
				return true;
			}
			ParsedTime time = parseDuration(value);
			if (time.seconds() >= 0) {
				applyParsed(State.TIMING, time.seconds(), time.coarse());
				return true;
			}
		}
		return seenCooldownLine && state != State.UNKNOWN;
	}

	private void applyParsed(State parsedState, int seconds, boolean coarse) {
		if (parsedState != State.TIMING) {
			state = parsedState;
			endsAtMs = 0;
			lastParseCoarse = false;
			return;
		}
		lastParseCoarse = coarse;
		long now = System.currentTimeMillis();
		long candidate = now + seconds * 1000L;
		int current = remainingSeconds();

		if (coarse) {
			state = State.TIMING;
			if (current < 0 || seconds > current + 60) {
				endsAtMs = candidate;
				if (seconds > 200) {
					alertArmed = true;
					firedThisCycle = false;
				}
			} else {
				endsAtMs = candidate;
			}
			return;
		}

		if (state != State.TIMING || endsAtMs <= 0 || current < 0) {
			endsAtMs = candidate;
			state = State.TIMING;
			if (seconds > 200) {
				alertArmed = true;
				firedThisCycle = false;
			}
			return;
		}
		if (seconds > current + 60) {
			endsAtMs = candidate;
			state = State.TIMING;
			alertArmed = true;
			firedThisCycle = false;
			lastBeepSecond = -1;
			return;
		}
		if (seconds < current) {
			endsAtMs = candidate;
		}
		state = State.TIMING;
	}

	private static boolean pestContext(List<String> lines, int index) {
		int from = Math.max(0, index - 8);
		int to = Math.min(lines.size() - 1, index + 3);
		for (int i = from; i <= to; i++) {
			String line = lines.get(i).toLowerCase(Locale.ROOT);
			if (line.contains("pest") || line.contains("alive:") || line.contains("infested")
					|| line.contains("max pests") || line.contains("plots:") || line.contains("cooldown:")) {
				return true;
			}
		}
		return GardenDetector.inGarden();
	}

	static ParsedTime parseDuration(String raw) {
		if (raw == null || raw.isBlank()) {
			return ParsedTime.invalid();
		}
		String value = raw.trim();
		int hours = firstInt(HOURS, value);
		int minutes = firstInt(MINUTES, value);
		int secs = firstInt(SECONDS, value);
		boolean hasSeconds = secs >= 0 || CLOCK.matcher(value).find();
		if (hours < 0 && minutes < 0 && secs < 0) {
			Matcher clock = CLOCK.matcher(value);
			if (clock.find()) {
				int total = Integer.parseInt(clock.group(1)) * 60 + Integer.parseInt(clock.group(2));
				return new ParsedTime(total, false);
			}
			if (BARE_NUMBER.matcher(value).matches()) {
				return new ParsedTime(Integer.parseInt(value), false);
			}
			return ParsedTime.invalid();
		}
		int total = Math.max(0, hours) * 3600 + Math.max(0, minutes) * 60 + Math.max(0, secs);
		return new ParsedTime(total, !hasSeconds);
	}

	private static int firstInt(Pattern pattern, String raw) {
		Matcher matcher = pattern.matcher(raw);
		if (!matcher.find()) {
			return -1;
		}
		try {
			return Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static void playLoud(Minecraft client, SoundEvent sound, float pitch, float volume) {
		if (client.getSoundManager() == null || sound == null) {
			return;
		}
		client.getSoundManager().play(new SimpleSoundInstance(
				sound.location(),
				SoundSource.MASTER,
				volume,
				pitch,
				RandomSource.create(),
				false,
				0,
				SoundInstance.Attenuation.NONE,
				0.0,
				0.0,
				0.0,
				true
		));
	}

	record ParsedTime(int seconds, boolean coarse) {
		static ParsedTime invalid() {
			return new ParsedTime(-1, false);
		}
	}
}
