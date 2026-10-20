package com.advancedstt.config;

import com.advancedstt.Provider;
import com.advancedstt.stt.SttOptions;

import java.util.ArrayList;
import java.util.List;

/** Player settings, saved as JSON. API keys are NOT here: see the secure package. */
public final class SttConfig {
	public enum ActivationMode { HOLD, TOGGLE }

	public enum SendMode { MANUAL, AUTO }

	public enum Indicator {
		SOUND_AND_VISUAL, VISUAL, SOUND, NONE;

		public boolean sound() {
			return this == SOUND_AND_VISUAL || this == SOUND;
		}

		public boolean visual() {
			return this == SOUND_AND_VISUAL || this == VISUAL;
		}
	}

	public static final double MIN_SILENCE_SECONDS = 0.5;
	public static final double MAX_SILENCE_SECONDS = 5.0;
	public static final int MIN_SESSION_SECONDS = 15;
	public static final int MAX_SESSION_SECONDS = 600;

	/** Cartesia's published rate for Ink-2: 3 credits per second of audio. */
	public static final double DEFAULT_CARTESIA_CREDITS_PER_SECOND = 3.0;
	/** AssemblyAI's list price for Universal-3.6 Pro streaming: $0.45 per hour. */
	public static final double DEFAULT_ASSEMBLYAI_DOLLARS_PER_HOUR = 0.45;
	/** AssemblyAI's list price for the Voice Focus add-on on Universal-3.6 Pro streaming: +$0.10 per hour. */
	public static final double DEFAULT_VOICE_FOCUS_DOLLARS_PER_HOUR = 0.10;

	/** AssemblyAI by default: it handles French and English, even mixed in one sentence. */
	public Provider provider = Provider.ASSEMBLYAI;
	public ActivationMode activation = ActivationMode.HOLD;
	public SendMode sendMode = SendMode.MANUAL;
	public double autoSendSilenceSeconds = 1.5;
	public Indicator indicator = Indicator.SOUND_AND_VISUAL;
	/** OpenAL capture device name; empty = the system default. */
	public String microphone = "";
	/** The microphone turns itself off after this long, so a session is never left open by mistake. */
	public int maxSessionSeconds = 120;
	/** Words to listen for: names and game words the service should spell right. */
	public List<String> keyterms = new ArrayList<>();
	/** AssemblyAI only: speed/accuracy preset. Balanced is AssemblyAI's own default. */
	public SttOptions.Mode assemblyAiMode = SttOptions.Mode.BALANCED;
	/** AssemblyAI only: background-noise removal. Off by default, as it costs extra. */
	public SttOptions.VoiceFocus voiceFocus = SttOptions.VoiceFocus.OFF;
	/** Prices used for the estimate; editable in the file if a service changes them. */
	public double cartesiaCreditsPerSecond = DEFAULT_CARTESIA_CREDITS_PER_SECOND;
	public double assemblyAiDollarsPerHour = DEFAULT_ASSEMBLYAI_DOLLARS_PER_HOUR;
	public double voiceFocusDollarsPerHour = DEFAULT_VOICE_FOCUS_DOLLARS_PER_HOUR;

	/** What a new session should be told: the words to listen for and AssemblyAI's settings. */
	public SttOptions sttOptions() {
		return new SttOptions(keyterms, assemblyAiMode, voiceFocus);
	}

	/** AssemblyAI's price per hour with the current settings: the base rate, plus Voice Focus when it's on. */
	public double assemblyAiRate() {
		return assemblyAiDollarsPerHour + (voiceFocus == SttOptions.VoiceFocus.OFF ? 0 : voiceFocusDollarsPerHour);
	}

	/** Puts every value back in range after loading a hand-edited or older file. */
	public SttConfig sanitize() {
		if (provider == null) {
			provider = Provider.ASSEMBLYAI;
		}
		if (activation == null) {
			activation = ActivationMode.HOLD;
		}
		if (sendMode == null) {
			sendMode = SendMode.MANUAL;
		}
		if (indicator == null) {
			indicator = Indicator.SOUND_AND_VISUAL;
		}
		if (microphone == null) {
			microphone = "";
		}
		if (assemblyAiMode == null) {
			assemblyAiMode = SttOptions.Mode.BALANCED;
		}
		if (voiceFocus == null) {
			voiceFocus = SttOptions.VoiceFocus.OFF;
		}
		if (keyterms == null) {
			keyterms = new ArrayList<>();
		}
		keyterms = new ArrayList<>(com.advancedstt.text.Keyterms.parse(String.join(",", keyterms)));
		if (!Double.isFinite(autoSendSilenceSeconds)) {
			autoSendSilenceSeconds = 1.5;
		}
		autoSendSilenceSeconds = Math.clamp(autoSendSilenceSeconds, MIN_SILENCE_SECONDS, MAX_SILENCE_SECONDS);
		maxSessionSeconds = Math.clamp(maxSessionSeconds, MIN_SESSION_SECONDS, MAX_SESSION_SECONDS);
		if (!(cartesiaCreditsPerSecond > 0) || !Double.isFinite(cartesiaCreditsPerSecond)) {
			cartesiaCreditsPerSecond = DEFAULT_CARTESIA_CREDITS_PER_SECOND;
		}
		if (!(assemblyAiDollarsPerHour > 0) || !Double.isFinite(assemblyAiDollarsPerHour)) {
			assemblyAiDollarsPerHour = DEFAULT_ASSEMBLYAI_DOLLARS_PER_HOUR;
		}
		if (!(voiceFocusDollarsPerHour >= 0) || !Double.isFinite(voiceFocusDollarsPerHour)) {
			voiceFocusDollarsPerHour = DEFAULT_VOICE_FOCUS_DOLLARS_PER_HOUR;
		}
		return this;
	}
}
