package com.advancedstt.secure;

import com.advancedstt.Provider;
import com.advancedstt.config.JsonFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Keys encrypted one by one with a {@link Protector}, stored in their own JSON file. */
final class ProtectedFileCredentialStore implements CredentialStore {
	static final class FileData {
		int version = 1;
		String protection;
		Map<String, String> keys = new LinkedHashMap<>();
	}

	private final Path file;
	private final Protector protector;

	ProtectedFileCredentialStore(Path file, Protector protector) {
		this.file = file;
		this.protector = protector;
	}

	@Override
	public synchronized Optional<String> get(Provider provider) {
		FileData data = read();
		String sealed = data.keys.get(provider.id());
		if (sealed == null) {
			return Optional.empty();
		}
		try {
			byte[] plain = protector.unprotect(Base64.getDecoder().decode(sealed));
			String key = new String(plain, StandardCharsets.UTF_8);
			java.util.Arrays.fill(plain, (byte) 0);
			return key.isEmpty() ? Optional.empty() : Optional.of(key);
		} catch (IOException | IllegalArgumentException e) {
			return Optional.empty(); // made on another account or machine: the player types it again
		}
	}

	@Override
	public synchronized void put(Provider provider, String apiKey) throws IOException {
		String key = apiKey == null ? "" : apiKey.strip();
		if (key.isEmpty()) {
			remove(provider);
			return;
		}
		FileData data = read();
		byte[] plain = key.getBytes(StandardCharsets.UTF_8);
		try {
			data.keys.put(provider.id(), Base64.getEncoder().encodeToString(protector.protect(plain)));
		} finally {
			java.util.Arrays.fill(plain, (byte) 0);
		}
		write(data);
	}

	@Override
	public synchronized void remove(Provider provider) throws IOException {
		FileData data = read();
		if (data.keys.remove(provider.id()) != null) {
			write(data);
		}
	}

	@Override
	public Protection protection() {
		return protector.protection();
	}

	private FileData read() {
		FileData data = JsonFiles.read(file, FileData.class);
		if (data == null || data.keys == null || !protector.id().equals(data.protection)) {
			data = new FileData();
		}
		data.protection = protector.id();
		return data;
	}

	private void write(FileData data) throws IOException {
		JsonFiles.write(file, data);
		if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
			Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
		}
	}
}
