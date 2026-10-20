package com.advancedstt.stt;

import com.advancedstt.Provider;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * AssemblyAI Universal-3.6 Pro over the v3 streaming WebSocket.
 *
 * <p>The key goes in the {@code Authorization} header (no "Bearer"). The service sends
 * {@code Begin}, then {@code Turn} messages (the whole text of the turn so far, with
 * {@code end_of_turn} on the final one), and {@code Termination} with the session length after we
 * send {@code Terminate}. Billed for the time the connection stays open. The model detects the
 * language itself and handles French and English in one sentence.
 */
public final class AssemblyAiSession extends WebSocketSttSession {
	public static final String DEFAULT_ENDPOINT = "wss://streaming.assemblyai.com/v3/ws";
	public static final String MODEL = "universal-3-6-pro";
	public static final int MAX_KEYTERMS = 100;
	public static final int MAX_KEYTERM_LENGTH = 50;

	private volatile double reportedSeconds = -1;

	private AssemblyAiSession(URI uri, String apiKey, SttListener listener) {
		super(Provider.ASSEMBLYAI, uri, Map.of("Authorization", apiKey), listener);
	}

	public static SttSession open(String endpoint, String apiKey, List<String> keyterms, SttListener listener) {
		AssemblyAiSession session = new AssemblyAiSession(buildUri(endpoint, keyterms), apiKey, listener);
		session.start();
		return session;
	}

	static URI buildUri(String endpoint, List<String> keyterms) {
		StringBuilder url = new StringBuilder(endpoint)
				.append("?sample_rate=").append(SAMPLE_RATE)
				.append("&encoding=pcm_s16le")
				.append("&speech_model=").append(MODEL);
		JsonArray terms = new JsonArray();
		for (String term : keyterms) {
			if (terms.size() >= MAX_KEYTERMS) {
				break;
			}
			if (term.length() <= MAX_KEYTERM_LENGTH) {
				terms.add(term);
			}
		}
		if (!terms.isEmpty()) {
			url.append("&keyterms_prompt=").append(URLEncoder.encode(terms.toString(), StandardCharsets.UTF_8));
		}
		return URI.create(url.toString());
	}

	@Override
	protected String finishMessage() {
		return "{\"type\":\"Terminate\"}";
	}

	@Override
	protected String endpointMessage() {
		return "{\"type\":\"ForceEndpoint\"}";
	}

	@Override
	protected double reportedSeconds() {
		return reportedSeconds;
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
			case "Turn" -> {
				int order = event.has("turn_order") ? event.get("turn_order").getAsInt() : 0;
				String transcript = string(event, "transcript");
				boolean end = event.has("end_of_turn") && event.get("end_of_turn").getAsBoolean();
				if (end) {
					listener.onTurnEnd(order, transcript);
				} else {
					listener.onTurnUpdate(order, transcript);
				}
			}
			case "Termination" -> {
				if (event.has("session_duration_seconds")) {
					reportedSeconds = event.get("session_duration_seconds").getAsDouble();
				}
			}
			case "Begin" -> {
				// Session id and expiry: nothing to show.
			}
			default -> {
				String error = string(event, "error");
				if (!error.isEmpty()) {
					fail(new SttError(SttError.classify(error), "AssemblyAI: " + error));
				}
			}
		}
	}

	@Override
	protected SttError closeError(int code, String reason) {
		SttError error = super.closeError(code, reason);
		if (error.kind() != SttError.Kind.SERVICE) {
			return error;
		}
		// Older AssemblyAI streaming close codes, used only when the reason text says nothing clearer.
		SttError.Kind kind = switch (code) {
			case 1008, 4001 -> SttError.Kind.INVALID_KEY;
			case 4002 -> SttError.Kind.NO_CREDITS;
			case 4029 -> SttError.Kind.RATE_LIMITED;
			default -> SttError.Kind.SERVICE;
		};
		return new SttError(kind, "AssemblyAI: " + error.detail());
	}

	private static String string(JsonObject object, String key) {
		JsonElement value = object.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}
}
