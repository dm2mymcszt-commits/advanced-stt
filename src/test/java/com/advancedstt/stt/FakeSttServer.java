package com.advancedstt.stt;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** A local stand-in for a speech service, to check exactly what the mod sends and how it reacts. */
final class FakeSttServer extends WebSocketServer {
	interface Script {
		default void onOpen(WebSocket conn, FakeSttServer server) {
		}

		default void onBinary(WebSocket conn, FakeSttServer server) {
		}

		default void onText(WebSocket conn, String text, FakeSttServer server) {
		}
	}

	private final Script script;
	private final long handshakeDelayMs;
	private final CountDownLatch started = new CountDownLatch(1);
	final AtomicLong audioBytes = new AtomicLong();
	final List<String> texts = new CopyOnWriteArrayList<>();
	volatile String resource;
	volatile String authorization;
	volatile String cartesiaVersion;

	private FakeSttServer(Script script, long handshakeDelayMs) {
		super(new InetSocketAddress("127.0.0.1", 0));
		this.script = script;
		this.handshakeDelayMs = handshakeDelayMs;
		setReuseAddr(true);
	}

	static FakeSttServer start(Script script) throws InterruptedException {
		return start(script, 0);
	}

	static FakeSttServer start(Script script, long handshakeDelayMs) throws InterruptedException {
		FakeSttServer server = new FakeSttServer(script, handshakeDelayMs);
		server.start();
		if (!server.started.await(5, TimeUnit.SECONDS)) {
			throw new IllegalStateException("fake server did not start");
		}
		return server;
	}

	String url(String path) {
		return "ws://127.0.0.1:" + getPort() + path;
	}

	@Override
	public ServerHandshakeBuilder onWebsocketHandshakeReceivedAsServer(WebSocket conn, Draft draft,
			ClientHandshake request) throws InvalidDataException {
		if (handshakeDelayMs > 0) {
			try {
				Thread.sleep(handshakeDelayMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		return super.onWebsocketHandshakeReceivedAsServer(conn, draft, request);
	}

	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		resource = handshake.getResourceDescriptor();
		authorization = handshake.getFieldValue("Authorization");
		cartesiaVersion = handshake.getFieldValue("Cartesia-Version");
		script.onOpen(conn, this);
	}

	@Override
	public void onMessage(WebSocket conn, ByteBuffer message) {
		audioBytes.addAndGet(message.remaining());
		script.onBinary(conn, this);
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		texts.add(message);
		script.onText(conn, message, this);
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
	}

	@Override
	public void onStart() {
		started.countDown();
	}
}
