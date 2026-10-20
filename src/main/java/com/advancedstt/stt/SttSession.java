package com.advancedstt.stt;

/** One live transcription connection. All methods are thread-safe and return immediately. */
public interface SttSession {
	/**
	 * Queues 16 kHz, mono, 16-bit little-endian PCM audio. Audio queued before the connection is open
	 * is sent once it opens, so the first words are not lost.
	 */
	void sendAudio(byte[] pcm);

	/** Asks the service to finish the current turn now, if it supports that. */
	void requestEndpoint();

	/** No more audio: the service sends its last words, then the connection closes. */
	void finish();

	/** Closes at once. Words still being processed are dropped. */
	void abort();
}
