package com.advancedstt.text;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeytermsTest {
	@Test
	void splitsTrimsAndDropsRepeats() {
		assertEquals(List.of("Steve", "Nether Fortress", "elytra"),
				Keyterms.parse(" Steve ,Nether   Fortress;\nelytra,steve,, Elytra"));
		assertEquals(List.of(), Keyterms.parse(null));
		assertEquals(List.of(), Keyterms.parse(" , ;"));
	}

	@Test
	void keepsAtMostOneHundred() {
		String many = IntStream.range(0, 150).mapToObj(i -> "word" + i).collect(Collectors.joining(","));
		List<String> terms = Keyterms.parse(many);
		assertEquals(Keyterms.MAX_TERMS, terms.size());
		assertEquals("word0", terms.getFirst());
		assertEquals("Steve, Alex", Keyterms.format(List.of("Steve", "Alex")));
	}
}
