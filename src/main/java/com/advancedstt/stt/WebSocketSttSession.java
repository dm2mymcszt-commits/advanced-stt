package com.advancedstt.stt;

import com.advancedstt.Provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared plumbing for the two streaming services: one WebSocket, audio sent in order by a single
 * sender thread, and a clean close that always reports what was used.
 *
 * <p>The API key only ever travels in a request header, never in the URL, so it can't end up in an
 * error message or a log line.
 */
abstract class WebSocketSttSession implements SttSession {
	static final int SAMPLE_RATE = 16_000;
	static final int BYTES_PER_SECOND = SAMPLE_RATE * 2;

	/** More than this much unsent audio means the connection can't keep up. */
	private static final long MAX_QUEUED_AUDIO_BYTES = BYTES_PER_SECOND * 20L;
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
	private static final long SEND_TIMEOUT_SECONDS = 10;
	/** How long the service gets to send its last words after we say we're done. */
	static final long FINISH_TIMEOUT_MS = 6_000;

	private static final Object FINISH = new Object();

	private static final ExecutorService NETWORK_THREADS = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "advanced-stt-network");
		t.setDaemon(true);
		return t;
	});
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.executor(NETWORK_THREADS)
			.build();

	protected final Provider provider;
	protected final SttListener listener;
	private final URI uri;
	private final Map<String, String> headers;

	private final LinkedBlockingQueue<Object> outgoing = new LinkedBlockingQueue<>();
	private final AtomicLong queuedAudioBytes = new AtomicLong();
	private final AtomicLong sentAudioBytes = new AtomicLong();
	private final AtomicBoolean started = new AtomicBoolean();
	private final AtomicBoolean closeReported = new AtomicBoolean();
	private final CountDownLatch closed = new CountDownLatch(1);

	private volatile boolean finishing;
	private volatile boolean aborted;
	private volatile CompletableFuture<WebSocket> connecting;
	private volatile WebSocket socket;
	private volatile Thread sender;
	private volatile long openedAtNanos;
	private volatile long closedAtNanos;

	protected WebSocketSttSession(Provider provider, URI uri, Map<String, String> headers, SttListener listener) {
		this.provider = provider;
		this.uri = uri;
		this.headers = Map.copyOf(headers);
		this.listener = listener;
	}

	/** Opens the connection. Called once, right after construction. */
	final void start() {
		if (!started.compareAndSet(false, true)) {
			return;
		}
		WebSocket.Builder builder = CLIENT.newWebSocketBuilder().connectTimeout(CONNECT_TIMEOUT);
		headers.forEach(builder::header);
		connecting = builder.buildAsync(uri, new Handler());
		connecting.whenComplete((ws, error) -> {
			if (error != null) {
				connectFailed(error);
			}
		});
		Thread t = new Thread(this::sendLoop, "advanced-stt-sender");
		t.setDaemon(true);
		sender = t;
		t.start();
	}

	/** Text to send when no more audio will come, or null to just close the connection. */
	protected abstract String finishMessage();

	/** Text that asks the service to end the current turn now, or null if it has none. */
	protected abstract String endpointMessage();

	/** One complete text message from the service. */
	protected abstract void handleMessage(String message) throws Exception;

	/** Session length the service reported, or -1. */
	protected double reportedSeconds() {
		return -1;
	}

	@Override
	public void sendAudio(byte[] pcm) {
		if (pcm == null || pcm.length == 0 || finishing || aborted) {
			return;
		}
		if (queuedAudioBytes.addAndGet(pcm.length) > MAX_QUEUED_AUDIO_BYTES) {
			fail(new SttError(SttError.Kind.AUDIO_BACKLOG, "The connection is too slow to send the audio in time."));
			return;
		}
		outgoing.offer(pcm.clone());
	}

	@Override
	public void requestEndpoint() {
		String message = endpointMessage();
		if (message != null && !finishing && !aborted) {
			outgoing.offer(message);
		}
	}

	@Override
	public void finish() {
		if (finishing || aborted) {
			return;
		}
		finishing = true;
		outgoing.offer(FINISH);
	}

	@Override
	public void abort() {
		aborted = true;
		WebSocket ws = socket;
		if (ws != null) {
			ws.abort();
		} else if (connecting != null) {
			connecting.cancel(true);
		}
		Thread t = sender;
		if (t != null && t != Thread.currentThread()) {
			t.interrupt();
		}
		reportClosed();
	}

	private void sendLoop() {
		WebSocket ws;
		try {
			ws = connecting.get(CONNECT_TIMEOUT.toSeconds() + 5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			return;
		} catch (Exception e) {
			return; // connectFailed() reports it
		}
		try {
			while (!aborted) {
				Object item = outgoing.poll(200, TimeUnit.MILLISECONDS);
				if (item == null) {
					continue;
				}
				if (item instanceof byte[] audio) {
					queuedAudioBytes.addAndGet(-audio.length);
					ws.sendBinary(ByteBuffer.wrap(audio), true).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
					sentAudioBytes.addAndGet(audio.length);
				} else if (item instanceof String text) {
					ws.sendText(text, true).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
				} else if (item == FINISH) {
					String message = finishMessage();
					if (message != null) {
						ws.sendText(message, true).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
					} else {
						ws.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
					}
					// Wait for the last words, but never keep a paid connection open for long.
					if (!closed.await(FINISH_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
						aborted = true;
						ws.abort();
						reportClosed();
					}
					return;
				}
			}
		} catch (InterruptedException e) {
			// abort() was called
		} catch (ExecutionException | java.util.concurrent.TimeoutException e) {
			if (!aborted) {
				fail(new SttError(SttError.Kind.NETWORK, describe(e.getCause() != null ? e.getCause() : e)));
			}
		} catch (RuntimeException e) {
			if (!aborted) {
				fail(new SttError(SttError.Kind.NETWORK, describe(e)));
			}
		}
	}

	private void connectFailed(Throwable error) {
		if (aborted) {
			reportClosed();
			return;
		}
		Throwable cause = unwrap(error);
		if (cause instanceof WebSocketHandshakeException handshake) {
			HttpResponse<?> response = handshake.getResponse();
			int status = response == null ? -1 : response.statusCode();
			Object body = response == null ? null : response.body();
			String text = body instanceof String s ? s : "";
			listener.onError(new SttError(SttError.fromHttpStatus(status), describeHttp(status, text)));
		} else {
			listener.onError(new SttError(SttError.Kind.NETWORK, describe(cause)));
		}
		reportClosed();
	}

	/** Reports an error once, then closes. */
	protected final void fail(SttError error) {
		if (closeReported.get()) {
			return;
		}
		safely(() -> listener.onError(error));
		abort();
	}

	private void reportClosed() {
		if (!closeReported.compareAndSet(false, true)) {
			return;
		}
		if (closedAtNanos == 0) {
			closedAtNanos = System.nanoTime();
		}
		aborted = true;
		closed.countDown();
		double audioSeconds = sentAudioBytes.get() / (double) BYTES_PER_SECOND;
		double connectionSeconds = openedAtNanos == 0 ? 0 : (closedAtNanos - openedAtNanos) / 1e9;
		SessionUsage usage = new SessionUsage(provider, audioSeconds, connectionSeconds, reportedSeconds());
		safely(() -> listener.onClosed(usage));
	}

	private static void safely(Runnable r) {
		try {
			r.run();
		} catch (RuntimeException ignored) {
			// A listener bug must never take the network thread down with it.
		}
	}

	private static Throwable unwrap(Throwable t) {
		while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
			t = t.getCause();
		}
		return t;
	}

	static String describe(Throwable t) {
		t = unwrap(t);
		String message = t.getMessage();
		String name = t.getClass().getSimpleName();
		return message == null || message.isBlank() ? name : name + ": " + message;
	}

	static String describeHttp(int status, String body) {
		String text = body == null ? "" : body.strip();
		if (text.length() > 200) {
			text = text.substring(0, 200) + "…";
		}
		return text.isEmpty() ? "HTTP " + status : "HTTP " + status + ": " + text;
	}

	/** Error for a connection the service closed with a non-normal code. */
	protected SttError closeError(int code, String reason) {
		String text = reason == null || reason.isBlank() ? "closed with code " + code : reason.strip();
		SttError.Kind kind = SttError.classify(text);
		return new SttError(kind, text);
	}

	private final class Handler implements WebSocket.Listener {
		private final StringBuilder text = new StringBuilder();

		@Override
		public void onOpen(WebSocket webSocket) {
			socket = webSocket;
			openedAtNanos = System.nanoTime();
			webSocket.request(1);
			if (aborted) {
				webSocket.abort();
				return;
			}
			safely(listener::onOpen);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			text.append(data);
			if (last) {
				String message = text.toString();
				text.setLength(0);
				if (!closeReported.get()) {
					try {
						handleMessage(message);
					} catch (Exception ignored) {
						// One odd message must not end the session.
					}
				}
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			closedAtNanos = System.nanoTime();
			boolean normal = statusCode == WebSocket.NORMAL_CLOSURE || statusCode == 1005;
			if (!normal && !aborted && !closeReported.get()) {
				safely(() -> listener.onError(closeError(statusCode, reason)));
			}
			reportClosed();
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			closedAtNanos = System.nanoTime();
			if (!aborted && !closeReported.get()) {
				safely(() -> listener.onError(new SttError(SttError.Kind.NETWORK, describe(error))));
			}
			reportClosed();
		}
	}
}
