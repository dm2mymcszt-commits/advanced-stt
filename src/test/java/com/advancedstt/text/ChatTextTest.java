package com.advancedstt.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTextTest {
	@Test
	void joinsWithOneSpace() {
		assertEquals("hey how are you", ChatText.join("hey", "how are you"));
		assertEquals("hey how are you", ChatText.join("hey ", "how are you"));
		assertEquals("how are you", ChatText.join("", " how are you "));
		assertEquals("hey", ChatText.join("hey", ""));
		assertEquals("", ChatText.join(null, null));
	}

	@Test
	void commandSlashGetsNoSpace() {
		assertEquals("/gamemode creative", ChatText.join("/", "gamemode creative"));
		assertEquals("/msg Steve hello", ChatText.join("/msg Steve", "hello"));
	}

	@Test
	void turnKeysKeepSessionsApartAndInOrder() {
		assertTrue(ChatText.turnKey(1, Integer.MAX_VALUE) < ChatText.turnKey(2, 0));
		assertTrue(ChatText.turnKey(3, 5) < ChatText.turnKey(3, 6));
		assertTrue(ChatText.firstTurn(3) <= ChatText.turnKey(3, 0));
		assertTrue(ChatText.endTurn(3) > ChatText.turnKey(3, Integer.MAX_VALUE));
		assertEquals(ChatText.endTurn(3), ChatText.firstTurn(4));
	}
}
