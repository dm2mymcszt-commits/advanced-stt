package com.advancedstt.client;

import com.advancedstt.Provider;
import com.advancedstt.audio.MicCapture;
import com.advancedstt.audio.Microphone;
import com.advancedstt.stt.SessionUsage;
import com.advancedstt.stt.SttError;
import com.advancedstt.stt.SttListener;
import com.advancedstt.stt.SttSession;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** A microphone and a speech service that the in-game tests control. Nothing leaves the computer. */
final class FakeDevices implements DictationController.Devices {
	/** 100 ms of 16 kHz mono 16-bit audio. */
	static final int CHUNK_BYTES = 3_200;

	final List<FakeSession> sessions = new CopyOnWriteArrayList<>();
	final List<FakeMic> mics = new CopyOnWriteArrayList<>();
	volatile boolean micBroken;

	@Override
	public Microphone microphone(String deviceName, Consumer<byte[]> sink) {
		FakeMic mic = new FakeMic(sink, micBroken);
		mics.add(mic);
		return mic;
	}

	@Override
	public SttSession connect(Provider provider, String apiKey, List<String> keyterms, SttListener listener) {
		FakeSession session = new FakeSession(provider, apiKey, keyterms, listener);
		sessions.add(session);
		return session;
	}

	static final class FakeMic implements Microphone {
		private final Consumer<byte[]> sink;
		private final boolean broken;
		volatile boolean started;
		volatile boolean stopped;

		FakeMic(Consumer<byte[]> sink, boolean broken) {
			this.sink = sink;
			this.broken = broken;
		}

		@Override
		public void start() throws MicCapture.MicException {
			if (broken) {
				throw new MicCapture.MicException("No microphone could be opened.");
			}
			started = true;
			// The first words, recorded before the connection exists.
			for (int i = 0; i < 3; i++) {
				sink.accept(new byte[CHUNK_BYTES]);
			}
		}

		@Override
		public void stop() {
			if (started && !stopped) {
				stopped = true;
				sink.accept(new byte[CHUNK_BYTES]); // the last bit of audio
			}
		}

		@Override
		public float level() {
			return started && !stopped ? 0.6f : 0f;
		}
	}

	static final class FakeSession implements SttSession {
		final Provider provider;
		final String apiKey;
		final List<String> keyterms;
		private final SttListener listener;
		private final AtomicBoolean closed = new AtomicBoolean();
		final AtomicInteger audioChunks = new AtomicInteger();
		volatile boolean endpointRequested;
		volatile boolean finished;
		volatile boolean aborted;
		/** What the service sends as the last words when the player stops talking. */
		volatile String lastWords;
		volatile int lastTurn;

		FakeSession(Provider provider, String apiKey, List<String> keyterms, SttListener listener) {
			this.provider = provider;
			this.apiKey = apiKey;
			this.keyterms = keyterms;
			this.listener = listener;
		}

		@Override
		public void sendAudio(byte[] pcm) {
			if (!closed.get()) {
				audioChunks.incrementAndGet();
			}
		}

		@Override
		public void requestEndpoint() {
			endpointRequested = true;
		}

		@Override
		public void finish() {
			finished = true;
			if (lastWords != null) {
				listener.onTurnEnd(lastTurn, lastWords);
			}
			close();
		}

		@Override
		public void abort() {
			aborted = true;
			close();
		}

		void open() {
			listener.onOpen();
		}

		void say(int turn, String text) {
			listener.onTurnUpdate(turn, text);
		}

		void end(int turn, String text) {
			listener.onTurnEnd(turn, text);
		}

		void lastWords(int turn, String text) {
			lastTurn = turn;
			lastWords = text;
		}

		void fail(SttError error) {
			listener.onError(error);
			close();
		}

		boolean isClosed() {
			return closed.get();
		}

		private void close() {
			if (closed.compareAndSet(false, true)) {
				listener.onClosed(new SessionUsage(provider, audioChunks.get() * 0.1, 1.0, -1));
			}
		}
	}
}
