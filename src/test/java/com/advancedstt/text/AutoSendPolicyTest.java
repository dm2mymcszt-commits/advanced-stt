package com.advancedstt.text;

import org.junit.jupiter.api.Test;

import static com.advancedstt.text.AutoSendPolicy.Decision.HAND_TO_PLAYER;
import static com.advancedstt.text.AutoSendPolicy.Decision.REQUEST_ENDPOINT;
import static com.advancedstt.text.AutoSendPolicy.Decision.SEND;
import static com.advancedstt.text.AutoSendPolicy.Decision.WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoSendPolicyTest {
	private static final long SILENCE = 1_500;

	@Test
	void waitsForTheSilence() {
		AutoSendPolicy p = new AutoSendPolicy();
		assertEquals(WAIT, p.decide("hello", false, 10_000, SILENCE, 11_000));
		assertEquals(SEND, p.decide("hello", false, 10_000, SILENCE, 11_500));
	}

	@Test
	void nothingToSend() {
		AutoSendPolicy p = new AutoSendPolicy();
		assertEquals(WAIT, p.decide("   ", false, 0, SILENCE, 100_000));
		assertEquals(WAIT, p.decide(null, false, 0, SILENCE, 100_000));
	}

	@Test
	void asksTheServiceToCloseAnOpenTurnThenSendsAnyway() {
		AutoSendPolicy p = new AutoSendPolicy();
		assertEquals(REQUEST_ENDPOINT, p.decide("hello", true, 10_000, SILENCE, 11_600));
		long afterGrace = 10_000 + SILENCE + AutoSendPolicy.OPEN_TURN_GRACE_MS;
		assertEquals(SEND, p.decide("hello", true, 10_000, SILENCE, afterGrace));
	}

	@Test
	void neverSendsCommandsOrTooLongText() {
		AutoSendPolicy p = new AutoSendPolicy();
		assertEquals(HAND_TO_PLAYER, p.decide("/kill @e", false, 0, SILENCE, 100_000));
		assertEquals(HAND_TO_PLAYER, p.decide("a".repeat(257), false, 0, SILENCE, 100_000));
		assertEquals(SEND, p.decide("a".repeat(256), false, 0, SILENCE, 100_000));
	}

	@Test
	void noDoubleSendsAndNoSpam() {
		AutoSendPolicy p = new AutoSendPolicy();
		p.markSent("hello", 100_000);
		assertEquals(HAND_TO_PLAYER, p.decide("hello", false, 0, 0, 105_000));
		assertEquals(WAIT, p.decide("something else", false, 0, 0, 101_000));
		assertEquals(SEND, p.decide("something else", false, 0, 0, 102_000));
		assertEquals(SEND, p.decide("hello", false, 0, 0, 100_000 + AutoSendPolicy.REPEAT_WINDOW_MS));
	}
}
