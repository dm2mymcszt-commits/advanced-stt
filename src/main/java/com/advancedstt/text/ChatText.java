package com.advancedstt.text;

/** Small text rules for putting dictated words into the chat box. */
public final class ChatText {
	private ChatText() {
	}

	/**
	 * What the chat box should hold: what the player had there, then the dictated words. A space is
	 * added between them unless the box is empty, already ends with a space, or holds only the "/"
	 * that starts a command.
	 */
	public static String join(String base, String dictated) {
		String before = base == null ? "" : base;
		String words = dictated == null ? "" : dictated.strip();
		if (words.isEmpty()) {
			return before;
		}
		if (before.isEmpty() || before.equals("/") || Character.isWhitespace(before.charAt(before.length() - 1))) {
			return before + words;
		}
		return before + " " + words;
	}

	/** Turn number unique across sessions: each session gets its own range, in start order. */
	public static long turnKey(long session, int turn) {
		return (session << 32) | (turn & 0xffff_ffffL);
	}

	/** First turn number of a session's range. */
	public static long firstTurn(long session) {
		return session << 32;
	}

	/** First turn number after a session's range. */
	public static long endTurn(long session) {
		return (session + 1) << 32;
	}
}
