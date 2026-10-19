package com.advancedstt.stt;

import com.advancedstt.Provider;

import java.util.List;

/** Opens a session with the chosen service. */
public final class SttSessions {
	private SttSessions() {
	}

	public static SttSession open(Provider provider, String apiKey, List<String> keyterms, SttListener listener) {
		return switch (provider) {
			case CARTESIA -> CartesiaInkSession.open(CartesiaInkSession.DEFAULT_ENDPOINT, apiKey, keyterms, listener);
			case ASSEMBLYAI -> AssemblyAiSession.open(AssemblyAiSession.DEFAULT_ENDPOINT, apiKey, keyterms, listener);
		};
	}
}
