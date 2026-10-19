package com.advancedstt.secure;

import java.nio.file.Path;
import java.util.Locale;

/** Picks the best key storage for this computer. */
public final class CredentialStores {
	private CredentialStores() {
	}

	public static CredentialStore create(Path folder) {
		boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
		if (windows && WindowsDpapiProtector.available()) {
			return new ProtectedFileCredentialStore(folder.resolve("keys.json"), new WindowsDpapiProtector());
		}
		return localFile(folder);
	}

	/** The fallback store; also used by the tests. */
	public static CredentialStore localFile(Path folder) {
		return new ProtectedFileCredentialStore(folder.resolve("keys-local.json"),
				new AesFileProtector(folder.resolve(".keys-local.secret")));
	}
}
