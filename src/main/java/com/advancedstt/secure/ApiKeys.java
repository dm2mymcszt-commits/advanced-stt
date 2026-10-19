package com.advancedstt.secure;

/** Simple checks on what the player pastes as an API key. */
public final class ApiKeys {
	private ApiKeys() {
	}

	/** API keys are one word of visible characters: no spaces, no line breaks, nothing exotic. */
	public static boolean looksValid(String key) {
		if (key == null || key.length() < 8 || key.length() > 512) {
			return false;
		}
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (c < 0x21 || c > 0x7e) {
				return false;
			}
		}
		return true;
	}
}
