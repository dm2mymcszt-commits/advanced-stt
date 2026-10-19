package com.advancedstt.stt;

/**
 * Receives what a session produces. Methods are called from network threads, never from the game
 * thread, so implementations must hand the data over safely.
 *
 * <p>Both services split speech into turns. While a turn is open, its text can still change; once
 * it ends, its text is final (AssemblyAI may send a better-formatted final for the same turn).
 */
public interface SttListener {
	/** The connection is open and the service accepted the key. */
	void onOpen();

	/** The whole text of the open turn so far (not just the new words). */
	void onTurnUpdate(int turn, String text);

	/** The final text of a turn. */
	void onTurnEnd(int turn, String text);

	void onError(SttError error);

	/** Always called exactly once, last, whatever happened. */
	void onClosed(SessionUsage usage);
}
