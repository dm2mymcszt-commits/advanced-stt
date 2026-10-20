package com.advancedstt.stt;

import com.advancedstt.Provider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SttErrorTest {
	@Test
	void classifiesServiceMessages() {
		assertEquals(SttError.Kind.INVALID_KEY, SttError.classify("Unauthorized: invalid API key"));
		assertEquals(SttError.Kind.NO_CREDITS, SttError.classify("Insufficient credits on this account"));
		assertEquals(SttError.Kind.RATE_LIMITED, SttError.classify("Too many concurrent sessions"));
		assertEquals(SttError.Kind.SERVICE, SttError.classify("Something odd happened"));
		assertEquals(SttError.Kind.SERVICE, SttError.classify(null));
	}

	@Test
	void classifiesHttpStatuses() {
		assertEquals(SttError.Kind.INVALID_KEY, SttError.fromHttpStatus(401));
		assertEquals(SttError.Kind.INVALID_KEY, SttError.fromHttpStatus(403));
		assertEquals(SttError.Kind.NO_CREDITS, SttError.fromHttpStatus(402));
		assertEquals(SttError.Kind.RATE_LIMITED, SttError.fromHttpStatus(429));
		assertEquals(SttError.Kind.SERVICE, SttError.fromHttpStatus(500));
	}

	@Test
	void billableTimeFollowsEachServicesRules() {
		assertEquals(5.0, new SessionUsage(Provider.CARTESIA, 5.0, 9.0, 7.0).billableSeconds());
		assertEquals(7.0, new SessionUsage(Provider.ASSEMBLYAI, 5.0, 9.0, 7.0).billableSeconds());
		assertEquals(9.0, new SessionUsage(Provider.ASSEMBLYAI, 5.0, 9.0, -1).billableSeconds());
	}
}
