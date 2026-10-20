package com.advancedstt.config;

import java.io.IOException;
import java.nio.file.Path;

/** Loads and saves {@link SttConfig}. */
public final class ConfigStore {
	private final Path file;
	private SttConfig config;

	public ConfigStore(Path file) {
		this.file = file;
	}

	public SttConfig load() {
		SttConfig loaded = JsonFiles.read(file, SttConfig.class);
		config = (loaded == null ? new SttConfig() : loaded).sanitize();
		return config;
	}

	public SttConfig get() {
		return config == null ? load() : config;
	}

	public void save() throws IOException {
		JsonFiles.write(file, get().sanitize());
	}
}
