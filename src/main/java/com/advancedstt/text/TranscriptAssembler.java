package com.advancedstt.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the text to show from the turns a service sends.
 *
 * <p>Each update replaces a turn's text instead of adding to it, so revised words never appear
 * twice. Text already handed to the player (sent, or left in the chat box where they edited it) is
 * "consumed" and not shown again, even if an open turn keeps growing.
 *
 * <p>Turns are identified by a {@code long} so that each session can use its own range: a session
 * that is still sending its last words never mixes with the next one.
 *
 * <p>Not thread-safe: use it from the game thread only.
 */
public final class TranscriptAssembler {
	private final TreeMap<Long, String> finals = new TreeMap<>();
	private final TreeMap<Long, String> open = new TreeMap<>();

	/** Turns before this one are consumed. */
	private long consumedTurn = Long.MIN_VALUE;
	/** Words of {@link #consumedTurn} already consumed ({@link Integer#MAX_VALUE} = the whole turn). */
	private int consumedWords;

	/** The whole text of an open turn so far. */
	public void update(long turn, String text) {
		if (finals.containsKey(turn) || turn < consumedTurn) {
			return; // a late partial for a turn that already ended
		}
		open.put(turn, clean(text));
	}

	/** The final text of a turn. A later final for the same turn replaces it. */
	public void end(long turn, String text) {
		open.remove(turn);
		if (turn < consumedTurn) {
			return;
		}
		finals.put(turn, clean(text));
	}

	/** A connection closed: whatever its open turns say now is final. Turns in [from, to). */
	public void finishOpenTurns(long from, long to) {
		List<Map.Entry<Long, String>> closing = new ArrayList<>(open.subMap(from, to).entrySet());
		for (Map.Entry<Long, String> entry : closing) {
			end(entry.getKey(), entry.getValue());
		}
	}

	/** True while an open turn with words in it can still change. */
	public boolean hasOpenTurn() {
		for (String text : open.values()) {
			if (!text.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Text not consumed yet: all turns in order, open ones included. */
	public String text() {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<Long, String> entry : turnsInOrder().entrySet()) {
			String visible = visiblePart(entry.getKey(), entry.getValue());
			if (!visible.isEmpty()) {
				parts.add(visible);
			}
		}
		return String.join(" ", parts);
	}

	/** Everything shown so far counts as handed over: it won't be shown again. */
	public void consumeAll() {
		TreeMap<Long, String> turns = turnsInOrder();
		if (turns.isEmpty()) {
			return;
		}
		long last = turns.lastKey();
		consumedTurn = last;
		consumedWords = finals.containsKey(last) ? Integer.MAX_VALUE : wordCount(turns.get(last));
		// Forget consumed turns so the maps don't grow during long sessions.
		finals.headMap(last).clear();
		open.headMap(last).clear();
	}

	public boolean isEmpty() {
		return text().isEmpty();
	}

	public void clear() {
		finals.clear();
		open.clear();
		consumedTurn = Long.MIN_VALUE;
		consumedWords = 0;
	}

	private TreeMap<Long, String> turnsInOrder() {
		TreeMap<Long, String> all = new TreeMap<>(finals);
		for (Map.Entry<Long, String> entry : open.entrySet()) {
			all.putIfAbsent(entry.getKey(), entry.getValue());
		}
		return all;
	}

	private String visiblePart(long turn, String text) {
		if (turn < consumedTurn) {
			return "";
		}
		if (turn > consumedTurn) {
			return text;
		}
		if (consumedWords == Integer.MAX_VALUE) {
			return "";
		}
		String[] words = text.isEmpty() ? new String[0] : text.split(" ");
		if (consumedWords >= words.length) {
			return "";
		}
		return String.join(" ", Arrays.copyOfRange(words, consumedWords, words.length));
	}

	private static int wordCount(String text) {
		return text.isEmpty() ? 0 : text.split(" ").length;
	}

	static String clean(String text) {
		return text == null ? "" : text.strip().replaceAll("\\s+", " ");
	}
}
