package com.advancedstt.secure;

import com.advancedstt.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialStoreTest {
	private static final String KEY = "sk_car_TEST1234567890abcdef";

	@TempDir
	Path dir;

	@Test
	void savesAndReadsBackOneKeyPerService() throws Exception {
		CredentialStore store = CredentialStores.localFile(dir);
		assertEquals(Optional.empty(), store.get(Provider.CARTESIA));
		store.put(Provider.CARTESIA, "  " + KEY + "\n");
		store.put(Provider.ASSEMBLYAI, "aai-key-0987654321");
		assertEquals(Optional.of(KEY), store.get(Provider.CARTESIA));
		assertEquals(Optional.of("aai-key-0987654321"), store.get(Provider.ASSEMBLYAI));
		// A fresh store (next game start) reads the same keys.
		assertEquals(Optional.of(KEY), CredentialStores.localFile(dir).get(Provider.CARTESIA));
	}

	@Test
	void theKeyIsNeverWrittenInPlainText() throws Exception {
		CredentialStore store = CredentialStores.localFile(dir);
		store.put(Provider.CARTESIA, KEY);
		try (var files = Files.list(dir)) {
			for (Path file : files.toList()) {
				byte[] bytes = Files.readAllBytes(file);
				String asText = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
				assertFalse(asText.contains(KEY), "plain key found in " + file.getFileName());
				assertFalse(asText.contains(java.util.Base64.getEncoder().encodeToString(KEY.getBytes())),
						"base64 key found in " + file.getFileName());
			}
		}
	}

	@Test
	void onlyTheOwnerCanReadTheFiles() throws Exception {
		if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
			return;
		}
		CredentialStores.localFile(dir).put(Provider.CARTESIA, KEY);
		assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve("keys-local.json"))));
		assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir.resolve(".keys-local.secret"))));
	}

	@Test
	void removeAndBlankDeleteTheKey() throws Exception {
		CredentialStore store = CredentialStores.localFile(dir);
		store.put(Provider.CARTESIA, KEY);
		store.remove(Provider.CARTESIA);
		assertEquals(Optional.empty(), store.get(Provider.CARTESIA));
		store.put(Provider.CARTESIA, KEY);
		store.put(Provider.CARTESIA, "   ");
		assertEquals(Optional.empty(), store.get(Provider.CARTESIA));
	}

	@Test
	void aLostSecretMeansTypingTheKeyAgainNotACrash() throws Exception {
		CredentialStore store = CredentialStores.localFile(dir);
		store.put(Provider.CARTESIA, KEY);
		Files.delete(dir.resolve(".keys-local.secret"));
		assertEquals(Optional.empty(), CredentialStores.localFile(dir).get(Provider.CARTESIA));
	}

	@Test
	void aDamagedFileIsHandled() throws Exception {
		Files.writeString(dir.resolve("keys-local.json"), "garbage");
		CredentialStore store = CredentialStores.localFile(dir);
		assertEquals(Optional.empty(), store.get(Provider.CARTESIA));
		store.put(Provider.CARTESIA, KEY);
		assertEquals(Optional.of(KEY), store.get(Provider.CARTESIA));
	}

	@Test
	void keyFormatCheck() {
		assertTrue(ApiKeys.looksValid(KEY));
		assertFalse(ApiKeys.looksValid("short"));
		assertFalse(ApiKeys.looksValid("has a space in it"));
		assertFalse(ApiKeys.looksValid("line\nbreak12345"));
		assertFalse(ApiKeys.looksValid("ünïcödé-key-123"));
		assertFalse(ApiKeys.looksValid(null));
	}
}
