package com.advancedstt.stt;

import com.advancedstt.Provider;

/** Opens a session with the chosen service. */
public final class SttSessions {
	private SttSessions() {
	}

	public static SttSession open(Provider provider, String apiKey, SttOptions options, SttListener listener) {
		return switch (provider) {
			case CARTESIA -> CartesiaInkSession.open(CartesiaInkSession.DEFAULT_ENDPOINT, apiKey, options.keyterms(),
					listener);
			case ASSEMBLYAI -> AssemblyAiSession.open(AssemblyAiSession.DEFAULT_ENDPOINT, apiKey, options, listener);
		};
	}
}
