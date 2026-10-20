package com.advancedstt.usage;

import com.advancedstt.Provider;
import com.advancedstt.config.SttConfig;
import com.advancedstt.stt.SessionUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsageTrackerTest {
	@TempDir
	Path dir;

	@Test
	void cartesiaIsBilledPerSecondOfAudio() {
		SttConfig config = new SttConfig();
		UsageTracker tracker = new UsageTracker(dir.resolve("usage.json"));
		double cost = tracker.record(new SessionUsage(Provider.CARTESIA, 10.0, 12.0, -1), config);
		assertEquals(30.0, cost, 1e-9); // 3 credits per second of audio
		assertEquals(10.0, tracker.get(Provider.CARTESIA).totalSeconds, 1e-9);
		assertEquals(1, tracker.get(Provider.CARTESIA).sessions);
	}

	@Test
	void assemblyAiIsBilledForTheConnectionTime() {
		SttConfig config = new SttConfig();
		UsageTracker tracker = new UsageTracker(dir.resolve("usage.json"));
		// Reported session length wins over the local measurement.
		assertEquals(0.45, tracker.record(new SessionUsage(Provider.ASSEMBLYAI, 100, 3500, 3600), config), 1e-9);
		// Without a reported length, the locally measured connection time is used.
		assertEquals(0.45, tracker.record(new SessionUsage(Provider.ASSEMBLYAI, 100, 3600, -1), config), 1e-9);
	}

	@Test
	void voiceFocusAddsItsPriceToAssemblyAi() {
		SttConfig config = new SttConfig();
		config.voiceFocus = com.advancedstt.stt.SttOptions.VoiceFocus.NEAR_FIELD;
		UsageTracker tracker = new UsageTracker(dir.resolve("usage.json"));
		assertEquals(0.55, tracker.record(new SessionUsage(Provider.ASSEMBLYAI, 100, 3600, 3600), config), 1e-9);
		assertEquals(30.0, tracker.record(new SessionUsage(Provider.CARTESIA, 10.0, 12.0, -1), config), 1e-9,
				"Voice Focus is AssemblyAI's: Cartesia's price doesn't change");
		config.assemblyAiMode = com.advancedstt.stt.SttOptions.Mode.MAX_ACCURACY;
		assertEquals(0.55, UsageTracker.cost(Provider.ASSEMBLYAI, 3600, config), 1e-9, "the mode is free");
	}

	@Test
	void remainingIsAnEstimateFromTheTypedBalance() {
		SttConfig config = new SttConfig();
		UsageTracker tracker = new UsageTracker(dir.resolve("usage.json"));
		assertFalse(tracker.estimatedRemaining(Provider.CARTESIA).isPresent(), "no balance typed in: nothing shown");
		tracker.record(new SessionUsage(Provider.CARTESIA, 100, 100, -1), config); // before the balance: not counted
		tracker.setStartingBalance(Provider.CARTESIA, 1000, 1);
		tracker.record(new SessionUsage(Provider.CARTESIA, 100, 100, -1), config);
		assertEquals(700, tracker.estimatedRemaining(Provider.CARTESIA).getAsDouble(), 1e-9);
		tracker.record(new SessionUsage(Provider.CARTESIA, 1000, 1000, -1), config);
		assertEquals(0, tracker.estimatedRemaining(Provider.CARTESIA).getAsDouble(), 1e-9, "never below zero");
		tracker.clearStartingBalance(Provider.CARTESIA);
		assertFalse(tracker.estimatedRemaining(Provider.CARTESIA).isPresent());
	}

	@Test
	void survivesARestart() throws Exception {
		SttConfig config = new SttConfig();
		Path file = dir.resolve("sub").resolve("usage.json");
		UsageTracker tracker = new UsageTracker(file);
		tracker.setStartingBalance(Provider.ASSEMBLYAI, 50, 1);
		tracker.record(new SessionUsage(Provider.ASSEMBLYAI, 0, 0, 360), config);
		tracker.save();
		UsageTracker again = new UsageTracker(file).load();
		assertEquals(50 - 0.045, again.estimatedRemaining(Provider.ASSEMBLYAI).getAsDouble(), 1e-9);
		assertEquals(1, again.get(Provider.ASSEMBLYAI).sessions);
	}

	@Test
	void aBrokenFileIsSetAsideNotFatal() throws Exception {
		Path file = dir.resolve("usage.json");
		java.nio.file.Files.writeString(file, "{ not json");
		UsageTracker tracker = new UsageTracker(file).load();
		assertEquals(0, tracker.get(Provider.CARTESIA).sessions);
		assertTrue(java.nio.file.Files.exists(dir.resolve("usage.json.bad")));
	}
}
