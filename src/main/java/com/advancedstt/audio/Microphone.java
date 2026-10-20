package com.advancedstt.audio;

/** A microphone that delivers 100 ms chunks of 16 kHz mono 16-bit little-endian PCM while it is on. */
public interface Microphone {
	/** Starts recording. Throws if the microphone can't be opened. */
	void start() throws MicCapture.MicException;

	/** Stops recording. The last partial chunk is still delivered. */
	void stop();

	/** Loudness of the last chunk, 0 to 1. */
	float level();
}
