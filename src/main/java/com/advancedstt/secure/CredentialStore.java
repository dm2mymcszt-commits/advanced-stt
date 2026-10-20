package com.advancedstt.secure;

import com.advancedstt.Provider;

import java.io.IOException;
import java.util.Optional;

/** Where the player's API keys live. Keys never go in the settings file, the logs or the chat. */
public interface CredentialStore {
	Optional<String> get(Provider provider);

	void put(Provider provider, String apiKey) throws IOException;

	void remove(Provider provider) throws IOException;

	/** How the keys are protected, for the settings screen. */
	Protection protection();

	enum Protection {
		/** Encrypted by Windows for this Windows account only (DPAPI). */
		WINDOWS_ACCOUNT,
		/** Encrypted with a key kept in a separate file readable only by this user. */
		LOCAL_FILE
	}
}
