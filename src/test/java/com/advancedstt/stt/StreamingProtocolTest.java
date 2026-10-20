package com.advancedstt.stt;

import com.advancedstt.Provider;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Talks to local fake services to check the exact protocol: where the key goes, what the URL holds,
 * that audio arrives in order, how each service's events are read, and how the session ends.
 */
class StreamingProtocolTest {
	private static final String KEY = "test-key-0123456789";
	private static final byte[] CHUNK = new byte[3200]; // 100 ms of 16 kHz 16-bit mono

	private FakeSttServer server;
	private HttpServer http;

	@AfterEach
	void stopServers() throws InterruptedException {
		if (server != null) {
			server.stop(1000);
		}
		if (http != null) {
			http.stop(0);
		}
	}

	@Test
	void cartesiaSessionFromStartToFinish() throws Exception {
		server = FakeSttServer.start(new FakeSttServer.Script() {
			@Override
			public void onBinary(WebSocket conn, FakeSttServer s) {
				if (s.audioBytes.get() == CHUNK.length) {
					conn.send("{\"type\":\"turn.start\"}");
					conn.send("{\"type\":\"turn.update\",\"transcript\":\"hello\"}");
					conn.send("{\"type\":\"turn.eager_end\",\"transcript\":\"hello world\"}");
				}
			}

			@Override
			public void onText(WebSocket conn, String text, FakeSttServer s) {
				if (text.contains("\"close\"")) {
					conn.send("{\"type\":\"turn.end\",\"transcript\":\"Hello world.\"}");
					conn.close(1000, "done");
				}
			}
		});
		RecordingListener listener = new RecordingListener();
		SttSession session = CartesiaInkSession.open(server.url("/stt/turns/websocket"), KEY,
				List.of("Steve", "Nether Fortress"), listener);
		session.sendAudio(CHUNK); // queued before the connection opens: must not be lost
		session.sendAudio(CHUNK);
		session.finish();
		assertTrue(listener.awaitClosed(10));

		assertEquals("Bearer " + KEY, server.authorization);
		assertEquals(CartesiaInkSession.API_VERSION, server.cartesiaVersion);
		Map<String, List<String>> query = query(server.resource);
		assertEquals("/stt/turns/websocket", URI.create(server.resource).getPath());
		assertEquals(List.of("ink-2"), query.get("model"));
		assertEquals(List.of("pcm_s16le"), query.get("encoding"));
		assertEquals(List.of("16000"), query.get("sample_rate"));
		assertEquals(List.of("Steve", "Nether Fortress"), query.get("keyterm"));
		assertFalse(server.resource.contains(KEY), "the key must never be in the URL");

		assertEquals(2L * CHUNK.length, server.audioBytes.get());
		assertEquals(List.of("{\"type\":\"close\"}"), server.texts);
		assertEquals(List.of("open", "update 0 hello", "update 0 hello world", "end 0 Hello world."), listener.events);
		assertEquals(List.of(), listener.errors);
		assertEquals(1, listener.closeCount.get());
		assertEquals(Provider.CARTESIA, listener.usage.provider());
		assertEquals(0.2, listener.usage.audioSeconds(), 1e-9);
		assertEquals(0.2, listener.usage.billableSeconds(), 1e-9);
	}

	@Test
	void cartesiaTurnsAreNumberedInOrder() throws Exception {
		server = FakeSttServer.start(new FakeSttServer.Script() {
			@Override
			public void onText(WebSocket conn, String text, FakeSttServer s) {
				conn.send("{\"type\":\"turn.update\",\"transcript\":\"one\"}");
				conn.send("{\"type\":\"turn.end\",\"transcript\":\"One.\"}");
				conn.send("{\"type\":\"turn.update\",\"transcript\":\"two\"}");
				conn.send("{\"type\":\"turn.end\",\"transcript\":\"Two.\"}");
				conn.close(1000, "done");
			}
		});
		RecordingListener listener = new RecordingListener();
		SttSession session = CartesiaInkSession.open(server.url("/"), KEY, List.of(), listener);
		session.finish();
		assertTrue(listener.awaitClosed(10));
		assertEquals(List.of("open", "update 0 one", "end 0 One.", "update 1 two", "end 1 Two."), listener.events);
		assertFalse(server.resource.contains("keyterm"), "no empty word list in the URL");
	}

	@Test
	void cartesiaErrorMessageEndsTheSessionWithAClearKind() throws Exception {
		server = FakeSttServer.start(new FakeSttServer.Script() {
			@Override
			public void onOpen(WebSocket conn, FakeSttServer s) {
				conn.send("{\"type\":\"error\",\"message\":\"Insufficient credits\"}");
			}
		});
		RecordingListener listener = new RecordingListener();
		CartesiaInkSession.open(server.url("/"), KEY, List.of(), listener);
		assertTrue(listener.awaitClosed(10));
		assertEquals(1, listener.errors.size());
		assertEquals(SttError.Kind.NO_CREDITS, listener.errors.getFirst().kind());
		assertTrue(listener.errors.getFirst().detail().contains("Insufficient credits"));
		assertEquals(1, listener.closeCount.get());
	}

	@Test
	void assemblyAiSessionFromStartToFinish() throws Exception {
		server = FakeSttServer.start(new FakeSttServer.Script() {
			@Override
			public void onOpen(WebSocket conn, FakeSttServer s) {
				conn.send("{\"type\":\"Begin\",\"id\":\"abc\",\"expires_at\":1700000000}");
			}

			@Override
			public void onBinary(WebSocket conn, FakeSttServer s) {
				if (s.audioBytes.get() == CHUNK.length) {
					conn.send("{\"type\":\"Turn\",\"turn_order\":0,\"transcript\":\"bonjour tout\",\"end_of_turn\":false}");
				}
			}

			@Override
			public void onText(WebSocket conn, String text, FakeSttServer s) {
				if (text.contains("ForceEndpoint")) {
					conn.send("{\"type\":\"Turn\",\"turn_order\":0,\"transcript\":\"Bonjour tout le monde.\","
							+ "\"end_of_turn\":true,\"turn_is_formatted\":true}");
				} else if (text.contains("Terminate")) {
					conn.send("{\"type\":\"Termination\",\"audio_duration_seconds\":0.2,\"session_duration_seconds\":1.5}");
					conn.close(1000, "done");
				}
			}
		});
		RecordingListener listener = new RecordingListener();
		SttSession session = AssemblyAiSession.open(server.url("/v3/ws"), KEY, List.of("Steve", "Nether"), listener);
		session.sendAudio(CHUNK);
		session.sendAudio(CHUNK);
		session.requestEndpoint();
		session.finish();
		assertTrue(listener.awaitClosed(10));

		assertEquals(KEY, server.authorization, "AssemblyAI takes the bare key, no 'Bearer'");
		Map<String, List<String>> query = query(server.resource);
		assertEquals(List.of("16000"), query.get("sample_rate"));
		assertEquals(List.of("pcm_s16le"), query.get("encoding"));
		assertEquals(List.of("universal-3-6-pro"), query.get("speech_model"));
		assertEquals(List.of("balanced"), query.get("mode"), "AssemblyAI's own default, sent explicitly");
		assertNull(query.get("voice_focus"), "Voice Focus costs extra, so it's left off unless chosen");
		assertEquals(JsonParser.parseString("[\"Steve\",\"Nether\"]"),
				JsonParser.parseString(query.get("keyterms_prompt").getFirst()));
		assertFalse(server.resource.contains(KEY));

		assertEquals(2L * CHUNK.length, server.audioBytes.get());
		assertEquals(List.of("{\"type\":\"ForceEndpoint\"}", "{\"type\":\"Terminate\"}"), server.texts);
		assertEquals(List.of("open", "update 0 bonjour tout", "end 0 Bonjour tout le monde."), listener.events);
		assertEquals(List.of(), listener.errors);
		assertEquals(1, listener.closeCount.get());
		assertEquals(1.5, listener.usage.reportedSeconds(), 1e-9);
		assertEquals(1.5, listener.usage.billableSeconds(), 1e-9, "billed on the session length the service reports");
	}

	@Test
	void assemblyAiSettingsGoOnTheConnection() throws Exception {
		server = FakeSttServer.start(new FakeSttServer.Script() {
		});
		RecordingListener listener = new RecordingListener();
		SttSession session = AssemblyAiSession.open(server.url("/v3/ws"), KEY,
				new SttOptions(List.of(), SttOptions.Mode.MAX_ACCURACY, SttOptions.VoiceFocus.FAR_FIELD), listener);
		assertTrue(listener.opened.await(10, java.util.concurrent.TimeUnit.SECONDS));
		session.abort();
		assertTrue(listener.awaitClosed(10));
		Map<String, List<String>> query = query(server.resource);
		assertEquals(List.of("max_accuracy"), query.get("mode"));
		assertEquals(List.of("far-field"), query.get("voice_focus"));
		assertNull(query.get("keyterms_prompt"), "no words to listen for, so none sent");

		assertEquals("min_latency", SttOptions.Mode.MIN_LATENCY.apiValue());
		assertEquals("near-field", SttOptions.VoiceFocus.NEAR_FIELD.apiValue());
		assertNull(SttOptions.VoiceFocus.OFF.apiValue());
	}

	@Test
	void assemblyAiCloseCodeForABadKey() throws Exception {
		server = FakeSttServer.start(new FakeSttServer.Script() {
			@Override
			public void onOpen(WebSocket conn, FakeSttServer s) {
				conn.close(4001, "Not authorized");
			}
		});
		RecordingListener listener = new RecordingListener();
		AssemblyAiSession.open(server.url("/v3/ws"), KEY, List.of(), listener);
		assertTrue(listener.awaitClosed(10));
		assertEquals(1, listener.errors.size());
		assertEquals(SttError.Kind.INVALID_KEY, listener.errors.getFirst().kind());
		assertEquals(1, listener.closeCount.get());
	}

	@Test
	void refusedHandshakeIsReportedAsABadKey() throws Exception {
		http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		http.createContext("/", exchange -> {
			byte[] body = "{\"error\":\"Invalid API key\"}".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(401, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		http.start();
		RecordingListener listener = new RecordingListener();
		CartesiaInkSession.open("ws://127.0.0.1:" + http.getAddress().getPort() + "/stt/turns/websocket", KEY,
				List.of(), listener);
		assertTrue(listener.awaitClosed(10));
		assertEquals(1, listener.errors.size());
		SttError error = listener.errors.getFirst();
		assertEquals(SttError.Kind.INVALID_KEY, error.kind());
		assertTrue(error.detail().contains("401"), error.detail());
		assertFalse(error.detail().contains(KEY));
		assertFalse(listener.events.contains("open"));
		assertEquals(1, listener.closeCount.get());
		assertEquals(0, listener.usage.billableSeconds(), 1e-9);
	}

	@Test
	void unreachableServiceIsANetworkError() throws Exception {
		int port;
		try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
			port = probe.getLocalPort(); // free port: nothing listens there once closed
		}
		RecordingListener listener = new RecordingListener();
		AssemblyAiSession.open("ws://127.0.0.1:" + port + "/v3/ws", KEY, List.of(), listener);
		assertTrue(listener.awaitClosed(15));
		assertEquals(1, listener.errors.size());
		assertEquals(SttError.Kind.NETWORK, listener.errors.getFirst().kind());
		assertEquals(1, listener.closeCount.get());
	}

	@Test
	void abortClosesAtOnceAndReportsOnce() throws Exception {
		server = FakeSttServer.start(new FakeSttServer.Script() {
		});
		RecordingListener listener = new RecordingListener();
		SttSession session = CartesiaInkSession.open(server.url("/"), KEY, List.of(), listener);
		assertTrue(listener.opened.await(10, java.util.concurrent.TimeUnit.SECONDS));
		session.abort();
		assertTrue(listener.awaitClosed(5));
		session.sendAudio(CHUNK); // ignored after the end
		session.finish();
		session.abort();
		Thread.sleep(200);
		assertEquals(1, listener.closeCount.get());
		assertEquals(List.of(), listener.errors);
	}

	@Test
	void aServiceThatNeverClosesIsCutOffSoItCannotKeepBilling() throws Exception {
		server = FakeSttServer.start(new FakeSttServer.Script() {
			// Ignores the close message and keeps the connection open.
		});
		RecordingListener listener = new RecordingListener();
		SttSession session = CartesiaInkSession.open(server.url("/"), KEY, List.of(), listener);
		session.sendAudio(CHUNK);
		long start = System.nanoTime();
		session.finish();
		assertTrue(listener.awaitClosed(15));
		double waited = (System.nanoTime() - start) / 1e9;
		assertTrue(waited < WebSocketSttSession.FINISH_TIMEOUT_MS / 1000.0 + 3, "waited " + waited + " s");
		assertEquals(List.of(), listener.errors);
		assertEquals(1, listener.closeCount.get());
	}

	@Test
	void tooMuchUnsentAudioStopsTheSession() throws Exception {
		server = FakeSttServer.start(new FakeSttServer.Script() {
		}, 500);
		RecordingListener listener = new RecordingListener();
		SttSession session = CartesiaInkSession.open(server.url("/"), KEY, List.of(), listener);
		for (int i = 0; i < 210; i++) { // 21 s of audio while the connection is still opening
			session.sendAudio(CHUNK);
		}
		assertTrue(listener.awaitClosed(10));
		assertEquals(1, listener.errors.size());
		assertEquals(SttError.Kind.AUDIO_BACKLOG, listener.errors.getFirst().kind());
		assertEquals(1, listener.closeCount.get());
	}

	private static Map<String, List<String>> query(String resource) {
		Map<String, List<String>> result = new LinkedHashMap<>();
		String raw = URI.create(resource).getRawQuery();
		if (raw == null) {
			return result;
		}
		for (String pair : raw.split("&")) {
			int eq = pair.indexOf('=');
			String name = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
			String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
			result.computeIfAbsent(name, k -> new java.util.ArrayList<>()).add(value);
		}
		return result;
	}
}
