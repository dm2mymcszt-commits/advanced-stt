package com.advancedstt.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Where the mod keeps the player's API keys and usage estimate: in the user's own settings folder,
 * outside the game folder. That way they are never copied when someone shares or exports their game
 * folder or modpack, and every game instance uses the same keys.
 */
public final class Folders {
	private Folders() {
	}

	public static Path privateFolder() {
		return privateFolder(System.getProperty("os.name", ""), System.getProperty("user.home", "."), System.getenv());
	}

	static Path privateFolder(String osName, String home, Map<String, String> env) {
		String os = osName.toLowerCase(Locale.ROOT);
		if (os.startsWith("windows")) {
			String local = env.get("LOCALAPPDATA");
			Path base = local != null && !local.isBlank() ? Path.of(local) : Path.of(home, "AppData", "Local");
			return base.resolve("AdvancedSTT");
		}
		if (os.contains("mac") || os.contains("darwin")) {
			return Path.of(home, "Library", "Application Support", "AdvancedSTT");
		}
		String xdg = env.get("XDG_CONFIG_HOME");
		Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(home, ".config");
		return base.resolve("advanced-stt");
	}
}
