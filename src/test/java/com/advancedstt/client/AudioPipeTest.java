package com.advancedstt.client;

import com.advancedstt.stt.SttSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AudioPipeTest {
	/** Records the audio a session would receive. */
	private static final class FakeSession implements SttSession {
		final List<Byte> firstBytes = new ArrayList<>();

		@Override
		public void sendAudio(byte[] pcm) {
			firstBytes.add(pcm[0]);
		}

		@Override
		public void requestEndpoint() {
		}

		@Override
		public void finish() {
		}

		@Override
		public void abort() {
		}
	}

	private static byte[] chunk(int marker) {
		return new byte[] {(byte) marker, 0};
	}

	@Test
	void audioFromBeforeTheConnectionIsSentFirstAndInOrder() {
		DictationController.AudioPipe pipe = new DictationController.AudioPipe();
		pipe.accept(chunk(1));
		pipe.accept(chunk(2));
		FakeSession session = new FakeSession();
		pipe.attach(session);
		pipe.accept(chunk(3));
		assertEquals(List.of((byte) 1, (byte) 2, (byte) 3), session.firstBytes);
	}

	@Test
	void keepsOnlyTheLastTwoSecondsWhileWaiting() {
		DictationController.AudioPipe pipe = new DictationController.AudioPipe();
		for (int i = 0; i < 25; i++) {
			pipe.accept(chunk(i));
		}
		FakeSession session = new FakeSession();
		pipe.attach(session);
		assertEquals(20, session.firstBytes.size());
		assertEquals((byte) 5, session.firstBytes.getFirst());
	}

	@Test
	void nothingGetsThroughOnceDiscarded() {
		DictationController.AudioPipe pipe = new DictationController.AudioPipe();
		pipe.accept(chunk(1));
		pipe.discard();
		FakeSession session = new FakeSession();
		pipe.attach(session);
		pipe.accept(chunk(2));
		assertEquals(List.of(), session.firstBytes);
	}
}
