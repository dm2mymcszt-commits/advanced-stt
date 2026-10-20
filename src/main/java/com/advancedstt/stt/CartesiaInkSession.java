package com.advancedstt.stt;

import com.advancedstt.Provider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Cartesia Ink-2 over the turn-based streaming endpoint.
 *
 * <p>Protocol (same as the owner's working voice-note app): send raw PCM as binary frames; the
 * service answers with {@code turn.update} / {@code turn.eager_end} (the whole text of the open
 * turn so far) and {@code turn.end} (the final text of that turn). Sending {@code {"type":"close"}}
 * makes it process what it has, send the last words, then close. Billed per second of audio sent,
 * silence included. Ink-2 detects the language itself; there is no language parameter.
 */
public final class CartesiaInkSession extends WebSocketSttSession {
	public static final String DEFAULT_ENDPOINT = "wss://api.cartesia.ai/stt/turns/websocket";
	public static final String MODEL = "ink-2";
	public static final String API_VERSION = "2026-08-14";
	/** Ink's limits for the words-to-listen-for list. */
	public static final int MAX_KEYTERMS = 100;
	public static final int MAX_KEYTERM_CHARS = 1200;

	/** Index of the open turn. Only touched by the WebSocket's listener, which runs one message at a time. */
	private int turn;

	private CartesiaInkSession(URI uri, String apiKey, SttListener listener) {
		super(Provider.CARTESIA, uri, Map.of(
				"Authorization", "Bearer " + apiKey,
				"Cartesia-Version", API_VERSION), listener);
	}

	public static SttSession open(String endpoint, String apiKey, List<String> keyterms, SttListener listener) {
		CartesiaInkSession session = new CartesiaInkSession(buildUri(endpoint, keyterms), apiKey, listener);
		session.start();
		return session;
	}

	static URI buildUri(String endpoint, List<String> keyterms) {
		StringBuilder url = new StringBuilder(endpoint)
				.append("?model=").append(MODEL)
				.append("&encoding=pcm_s16le")
				.append("&sample_rate=").append(SAMPLE_RATE);
		int count = 0;
		int chars = 0;
		for (String term : keyterms) {
			if (count >= MAX_KEYTERMS || chars + term.length() > MAX_KEYTERM_CHARS) {
				break;
			}
			url.append("&keyterm=").append(URLEncoder.encode(term, StandardCharsets.UTF_8));
			count++;
			chars += term.length();
		}
		return URI.create(url.toString());
	}

	@Override
	protected String finishMessage() {
		return "{\"type\":\"close\"}";
	}

	@Override
	protected String endpointMessage() {
		return null; // Ink decides where turns end by itself.
	}

	@Override
	protected void handleMessage(String message) {
		JsonElement parsed = JsonParser.parseString(message);
		if (!parsed.isJsonObject()) {
			return;
		}
		JsonObject event = parsed.getAsJsonObject();
		String type = string(event, "type");
		switch (type) {
			case "turn.update", "turn.eager_end" -> listener.onTurnUpdate(turn, string(event, "transcript"));
			case "turn.end" -> {
				listener.onTurnEnd(turn, string(event, "transcript"));
				turn++;
			}
			case "error" -> {
				String text = string(event, "message");
				if (text.isEmpty()) {
					text = message;
				}
				fail(new SttError(SttError.classify(text), "Cartesia: " + text));
			}
			default -> {
				// turn.start, turn.resume and anything new: nothing to show.
			}
		}
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}
}
