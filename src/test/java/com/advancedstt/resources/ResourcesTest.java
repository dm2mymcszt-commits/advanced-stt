package com.advancedstt.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the files the game reads: translations, mod metadata, mixin config. */
class ResourcesTest {
	private static final Path RESOURCES = Path.of("src/main/resources");
	private static final Path SOURCES = Path.of("src/main/java");
	private static final Pattern KEY = Pattern.compile("\"((?:key\\.(?:category\\.)?)?advanced_stt\\.[a-z0-9_.]+)\"");

	private static JsonObject json(Path file) throws IOException {
		return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
	}

	@Test
	void englishAndFrenchHaveTheSameTexts() throws IOException {
		Set<String> en = json(RESOURCES.resolve("assets/advanced_stt/lang/en_us.json")).keySet();
		Set<String> fr = json(RESOURCES.resolve("assets/advanced_stt/lang/fr_fr.json")).keySet();
		Set<String> missingInFrench = new TreeSet<>(en);
		missingInFrench.removeAll(fr);
		Set<String> missingInEnglish = new TreeSet<>(fr);
		missingInEnglish.removeAll(en);
		assertEquals(Set.of(), missingInFrench);
		assertEquals(Set.of(), missingInEnglish);
	}

	@Test
	void everyTextTheCodeUsesExists() throws IOException {
		Set<String> en = json(RESOURCES.resolve("assets/advanced_stt/lang/en_us.json")).keySet();
		Set<String> used = new HashSet<>();
		try (Stream<Path> files = Files.walk(SOURCES)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				Matcher m = KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
				while (m.find()) {
					used.add(m.group(1));
				}
			}
		}
		// Texts whose last part comes from a setting's value.
		for (String mode : List.of("hold", "toggle")) {
			used.add("advanced_stt.settings.activation." + mode);
			used.add("advanced_stt.settings.activation." + mode + ".tooltip");
		}
		for (String indicator : List.of("sound_and_visual", "visual", "sound", "none")) {
			used.add("advanced_stt.settings.indicator." + indicator);
		}
		for (String send : List.of("manual", "auto")) {
			used.add("advanced_stt.settings.send." + send);
			used.add("advanced_stt.settings.send." + send + ".info");
		}
		for (com.advancedstt.Provider provider : com.advancedstt.Provider.values()) {
			used.add("advanced_stt.settings.balance.hint." + provider.id());
			used.add("advanced_stt.settings.language." + provider.id());
		}
		for (com.advancedstt.stt.SttOptions.Mode mode : com.advancedstt.stt.SttOptions.Mode.values()) {
			String name = mode.name().toLowerCase(java.util.Locale.ROOT);
			used.add("advanced_stt.settings.mode." + name);
			used.add("advanced_stt.settings.mode." + name + ".tooltip");
		}
		for (com.advancedstt.stt.SttOptions.VoiceFocus focus : com.advancedstt.stt.SttOptions.VoiceFocus.values()) {
			String name = focus.name().toLowerCase(java.util.Locale.ROOT);
			used.add("advanced_stt.settings.voice_focus." + name);
			used.add("advanced_stt.settings.voice_focus." + name + ".tooltip");
		}
		used.add("key.category.advanced_stt.main");
		Set<String> missing = new TreeSet<>();
		for (String key : used) {
			if (!key.endsWith(".") && !en.contains(key)) {
				missing.add(key);
			}
		}
		assertEquals(Set.of(), missing);
		assertTrue(used.size() > 50);
	}

	@Test
	void modMetadataPointsAtRealClasses() throws IOException {
		JsonObject mod = json(RESOURCES.resolve("fabric.mod.json"));
		assertEquals("advanced_stt", mod.get("id").getAsString());
		assertEquals("client", mod.get("environment").getAsString());
		JsonObject entrypoints = mod.getAsJsonObject("entrypoints");
		for (String group : List.of("client", "modmenu")) {
			String className = entrypoints.getAsJsonArray(group).get(0).getAsString();
			assertTrue(Files.exists(SOURCES.resolve(className.replace('.', '/') + ".java")), className);
		}
		String mixins = mod.getAsJsonArray("mixins").get(0).getAsString();
		JsonObject mixinConfig = json(RESOURCES.resolve(mixins));
		String pkg = mixinConfig.get("package").getAsString();
		for (var entry : mixinConfig.getAsJsonArray("client")) {
			Path mixin = SOURCES.resolve((pkg + "." + entry.getAsString()).replace('.', '/') + ".java");
			assertTrue(Files.exists(mixin), mixin.toString());
		}
	}

	@Test
	void noSecretsInTheResources() throws IOException {
		Pattern secret = Pattern.compile("(sk_car_|sk-|Bearer [A-Za-z0-9])");
		try (Stream<Path> files = Files.walk(RESOURCES)) {
			for (Path file : files.filter(Files::isRegularFile).toList()) {
				String text = Files.readString(file, StandardCharsets.UTF_8);
				assertTrue(!secret.matcher(text).find(), "possible secret in " + file);
			}
		}
	}
}
