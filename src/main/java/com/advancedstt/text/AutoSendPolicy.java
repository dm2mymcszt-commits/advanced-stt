package com.advancedstt.text;

/**
 * Decides when a dictated message may be sent on its own, after the player stops talking.
 *
 * <p>Rules: wait for the configured silence after the last change; wait for the service to finish
 * the open turn (with a grace period, in case it never does); never send commands, empty text or
 * text longer than a chat message; never send faster than {@link #MIN_GAP_MS} or the same text
 * twice in a row within {@link #REPEAT_WINDOW_MS}. Text that can't go out on its own is handed to
 * the player in the chat box instead.
 */
public final class AutoSendPolicy {
	/** Minecraft's chat message limit. */
	public static final int MAX_MESSAGE_LENGTH = 256;
	public static final long MIN_GAP_MS = 2_000;
	public static final long REPEAT_WINDOW_MS = 10_000;
	/** Extra wait for a turn the service never closes. */
	public static final long OPEN_TURN_GRACE_MS = 2_500;

	public enum Decision {
		/** Keep waiting. */
		WAIT,
		/** Silence reached but the turn is still open: ask the service to close it. */
		REQUEST_ENDPOINT,
		/** Send it now. */
		SEND,
		/** Can't be sent automatically (command, too long, repeat): let the player decide. */
		HAND_TO_PLAYER
	}

	private long lastSentAtMs = Long.MIN_VALUE / 2;
	private String lastSentText = "";

	/**
	 * @param text         text waiting to be sent
	 * @param openTurn     the service may still change the end of the text
	 * @param lastChangeMs when the text last changed
	 * @param silenceMs    silence the player asked for before sending
	 */
	public Decision decide(String text, boolean openTurn, long lastChangeMs, long silenceMs, long nowMs) {
		String message = text == null ? "" : text.strip();
		if (message.isEmpty()) {
			return Decision.WAIT;
		}
		long quiet = nowMs - lastChangeMs;
		if (quiet < silenceMs) {
			return Decision.WAIT;
		}
		if (openTurn && quiet < silenceMs + OPEN_TURN_GRACE_MS) {
			return Decision.REQUEST_ENDPOINT;
		}
		if (!allowedAutomatically(message, nowMs)) {
			return Decision.HAND_TO_PLAYER;
		}
		if (nowMs - lastSentAtMs < MIN_GAP_MS) {
			return Decision.WAIT; // too soon after the last one: send it a bit later
		}
		return Decision.SEND;
	}

	/** Commands, over-long text and an immediate repeat are never sent without the player. */
	public boolean allowedAutomatically(String message, long nowMs) {
		if (message.startsWith("/") || message.length() > MAX_MESSAGE_LENGTH) {
			return false;
		}
		return !(message.equals(lastSentText) && nowMs - lastSentAtMs < REPEAT_WINDOW_MS);
	}

	public void markSent(String message, long nowMs) {
		lastSentText = message.strip();
		lastSentAtMs = nowMs;
	}
}
