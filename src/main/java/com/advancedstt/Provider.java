package com.advancedstt;

/** The two speech-to-text services the mod supports. There will be no others. */
public enum Provider {
	CARTESIA("cartesia", "Cartesia Ink-2", "https://play.cartesia.ai/keys", "https://play.cartesia.ai"),
	ASSEMBLYAI("assemblyai", "AssemblyAI Universal-3.6 Pro", "https://www.assemblyai.com/dashboard",
			"https://www.assemblyai.com/dashboard");

	private final String id;
	private final String displayName;
	private final String keysUrl;
	private final String dashboardUrl;

	Provider(String id, String displayName, String keysUrl, String dashboardUrl) {
		this.id = id;
		this.displayName = displayName;
		this.keysUrl = keysUrl;
		this.dashboardUrl = dashboardUrl;
	}

	/** Where the player creates an API key (from the service's own docs). */
	public String keysUrl() {
		return keysUrl;
	}

	/** Where the player sees their real balance. */
	public String dashboardUrl() {
		return dashboardUrl;
	}

	/** Stable id used in files. Never change it. */
	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public static Provider fromId(String id) {
		for (Provider p : values()) {
			if (p.id.equals(id)) {
				return p;
			}
		}
		return null;
	}
}
