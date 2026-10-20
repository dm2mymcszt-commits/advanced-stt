package com.advancedstt.stt;

import java.util.List;

/**
 * How a session should listen: the words to listen for, plus two AssemblyAI-only settings. Cartesia
 * uses the words only.
 *
 * @param keyterms   names and game words the service should spell right
 * @param mode       AssemblyAI's speed/accuracy preset
 * @param voiceFocus AssemblyAI's background-noise removal
 */
public record SttOptions(List<String> keyterms, Mode mode, VoiceFocus voiceFocus) {
	public SttOptions {
		keyterms = keyterms == null ? List.of() : List.copyOf(keyterms);
		mode = mode == null ? Mode.BALANCED : mode;
		voiceFocus = voiceFocus == null ? VoiceFocus.OFF : voiceFocus;
	}

	/** Just the words, with AssemblyAI's defaults. */
	public static SttOptions of(List<String> keyterms) {
		return new SttOptions(keyterms, Mode.BALANCED, VoiceFocus.OFF);
	}

	/**
	 * AssemblyAI's {@code mode} connection parameter. It sets how quickly a pause ends a sentence and
	 * how often the live text updates; the model and the price stay the same.
	 */
	public enum Mode {
		BALANCED("balanced"),
		MIN_LATENCY("min_latency"),
		MAX_ACCURACY("max_accuracy");

		private final String apiValue;

		Mode(String apiValue) {
			this.apiValue = apiValue;
		}

		public String apiValue() {
			return apiValue;
		}
	}

	/**
	 * AssemblyAI's {@code voice_focus} connection parameter: removes the noise around the speaker's
	 * voice before the model hears it. Billed as an add-on.
	 */
	public enum VoiceFocus {
		OFF(null),
		/** Headsets and other microphones close to the mouth. */
		NEAR_FIELD("near-field"),
		/** Laptop, desk and other distant microphones. */
		FAR_FIELD("far-field");

		private final String apiValue;

		VoiceFocus(String apiValue) {
			this.apiValue = apiValue;
		}

		/** The value to send, or null to leave it off. */
		public String apiValue() {
			return apiValue;
		}
	}
}
