package com.advancedstt.config;

import com.advancedstt.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {
	@TempDir
	Path dir;

	@Test
	void defaultsWhenThereIsNoFile() {
		SttConfig config = new ConfigStore(dir.resolve("advanced-stt.json")).load();
		assertEquals(Provider.CARTESIA, config.provider);
		assertEquals(SttConfig.ActivationMode.HOLD, config.activation);
		assertEquals(SttConfig.SendMode.MANUAL, config.sendMode);
		assertEquals(SttConfig.Indicator.SOUND_AND_VISUAL, config.indicator);
		assertEquals(120, config.maxSessionSeconds);
		assertEquals(3.0, config.cartesiaCreditsPerSecond);
		assertEquals(0.45, config.assemblyAiDollarsPerHour);
	}

	@Test
	void savesAndLoadsAndHoldsNoKey() throws Exception {
		Path file = dir.resolve("advanced-stt.json");
		ConfigStore store = new ConfigStore(file);
		SttConfig config = store.load();
		config.provider = Provider.ASSEMBLYAI;
		config.sendMode = SttConfig.SendMode.AUTO;
		config.keyterms = new java.util.ArrayList<>(List.of("Steve", "Alex"));
		store.save();
		String json = Files.readString(file);
		assertFalse(json.toLowerCase(java.util.Locale.ROOT).contains("apikey"), "keys never go in the settings file");
		SttConfig again = new ConfigStore(file).load();
		assertEquals(Provider.ASSEMBLYAI, again.provider);
		assertEquals(SttConfig.SendMode.AUTO, again.sendMode);
		assertEquals(List.of("Steve", "Alex"), again.keyterms);
	}

	@Test
	void handEditedValuesArePutBackInRange() throws Exception {
		Path file = dir.resolve("advanced-stt.json");
		Files.writeString(file, """
				{"provider": "nonsense", "autoSendSilenceSeconds": 99, "maxSessionSeconds": 1,
				 "cartesiaCreditsPerSecond": -1, "keyterms": null, "microphone": null}
				""");
		SttConfig config = new ConfigStore(file).load();
		assertEquals(Provider.CARTESIA, config.provider);
		assertEquals(SttConfig.MAX_SILENCE_SECONDS, config.autoSendSilenceSeconds);
		assertEquals(SttConfig.MIN_SESSION_SECONDS, config.maxSessionSeconds);
		assertEquals(SttConfig.DEFAULT_CARTESIA_CREDITS_PER_SECOND, config.cartesiaCreditsPerSecond);
		assertEquals(List.of(), config.keyterms);
		assertEquals("", config.microphone);
	}

	@Test
	void aBrokenFileIsSetAsideAndDefaultsAreUsed() throws Exception {
		Path file = dir.resolve("advanced-stt.json");
		Files.writeString(file, "this is not json {");
		SttConfig config = new ConfigStore(file).load();
		assertEquals(Provider.CARTESIA, config.provider);
		assertTrue(Files.exists(dir.resolve("advanced-stt.json.bad")));
	}

	@Test
	void privateFolderIsOutsideTheGameFolder() {
		assertEquals(Path.of("C:\\Users\\a\\AppData\\Local").resolve("AdvancedSTT"),
				Folders.privateFolder("Windows 11", "C:\\Users\\a", Map.of("LOCALAPPDATA", "C:\\Users\\a\\AppData\\Local")));
		assertEquals(Path.of("/Users/a/Library/Application Support/AdvancedSTT"),
				Folders.privateFolder("Mac OS X", "/Users/a", Map.of()));
		assertEquals(Path.of("/home/a/.config/advanced-stt"), Folders.privateFolder("Linux", "/home/a", Map.of()));
		assertEquals(Path.of("/xdg/advanced-stt"),
				Folders.privateFolder("Linux", "/home/a", Map.of("XDG_CONFIG_HOME", "/xdg")));
	}
}
