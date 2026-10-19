package com.advancedstt.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads and writes small JSON files safely: a broken file is set aside, never crashes the game. */
public final class JsonFiles {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private JsonFiles() {
	}

	/** The file's content, or null if it is missing or unreadable (a broken file is renamed *.bad). */
	public static <T> T read(Path file, Class<T> type) {
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try {
			T value = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
			if (value != null) {
				return value;
			}
		} catch (IOException | JsonParseException | IllegalStateException e) {
			// fall through: set the broken file aside
		}
		try {
			Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// nothing more we can do
		}
		return null;
	}

	/** Writes to a temporary file first, so a crash never leaves a half-written file. */
	public static void write(Path file, Object value) throws IOException {
		writeString(file, GSON.toJson(value));
	}

	public static void writeString(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, content, StandardCharsets.UTF_8);
		try {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
