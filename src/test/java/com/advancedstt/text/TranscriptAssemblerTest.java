package com.advancedstt.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptAssemblerTest {
	@Test
	void updatesReplaceTheOpenTurnInsteadOfAddingToIt() {
		TranscriptAssembler t = new TranscriptAssembler();
		t.update(0, "hello");
		t.update(0, "hello there");
		t.update(0, "Hello there,");
		assertEquals("Hello there,", t.text());
		assertTrue(t.hasOpenTurn());
	}

	@Test
	void finishedTurnsComeFirstThenTheOpenOne() {
		TranscriptAssembler t = new TranscriptAssembler();
		t.update(0, "first");
		t.end(0, "First.");
		t.update(1, "second");
		assertEquals("First. second", t.text());
		t.end(1, "Second.");
		assertEquals("First. Second.", t.text());
		assertFalse(t.hasOpenTurn());
	}

	@Test
	void latePartialForAnEndedTurnIsIgnored() {
		TranscriptAssembler t = new TranscriptAssembler();
		t.end(0, "Done.");
		t.update(0, "do");
		assertEquals("Done.", t.text());
	}

	@Test
	void consumedTextIsNeverShownAgainEvenIfTheOpenTurnGrows() {
		TranscriptAssembler t = new TranscriptAssembler();
		t.end(0, "One.");
		t.update(1, "two three");
		t.consumeAll();
		assertEquals("", t.text());
		assertTrue(t.isEmpty());
		t.update(1, "two three four");
		assertEquals("four", t.text());
		t.end(1, "Two three four five.");
		assertEquals("four five.", t.text(), "only the two consumed words stay hidden");
		t.update(2, "six");
		assertEquals("four five. six", t.text());
	}

	@Test
	void aFinalTurnConsumedWholeStaysHiddenWhenAFormattedVersionArrives() {
		TranscriptAssembler t = new TranscriptAssembler();
		t.end(4, "hello how are you");
		t.consumeAll();
		t.end(4, "Hello, how are you?");
		assertEquals("", t.text());
	}

	@Test
	void revisionWithFewerWordsDoesNotRepeatConsumedWords() {
		TranscriptAssembler t = new TranscriptAssembler();
		t.update(0, "um so I want");
		t.consumeAll();
		t.end(0, "So I want");
		assertEquals("", t.text());
	}

	@Test
	void whitespaceIsCleaned() {
		TranscriptAssembler t = new TranscriptAssembler();
		t.update(0, "  lots   of\nspace  ");
		assertEquals("lots of space", t.text());
	}

	@Test
	void twoSessionsKeepTheirOwnTurnsInOrder() {
		TranscriptAssembler t = new TranscriptAssembler();
		long a0 = ChatText.turnKey(1, 0);
		long b0 = ChatText.turnKey(2, 0);
		t.update(a0, "end of the old");
		t.update(b0, "start of the new");
		// The old session's open turn is still there; the new one did not overwrite it.
		assertEquals("end of the old start of the new", t.text());
		t.end(a0, "End of the old.");
		assertEquals("End of the old. start of the new", t.text());
	}

	@Test
	void closingASessionFinishesOnlyItsOwnOpenTurns() {
		TranscriptAssembler t = new TranscriptAssembler();
		long a0 = ChatText.turnKey(1, 0);
		long b0 = ChatText.turnKey(2, 0);
		t.update(a0, "old words");
		t.update(b0, "new words");
		t.finishOpenTurns(ChatText.firstTurn(1), ChatText.endTurn(1));
		assertTrue(t.hasOpenTurn(), "the new session's turn is still open");
		t.finishOpenTurns(ChatText.firstTurn(2), ChatText.endTurn(2));
		assertFalse(t.hasOpenTurn());
		assertEquals("old words new words", t.text());
	}

	@Test
	void emptyOpenTurnDoesNotCountAsSpeech() {
		TranscriptAssembler t = new TranscriptAssembler();
		t.update(0, "");
		assertFalse(t.hasOpenTurn());
		assertTrue(t.isEmpty());
	}

	@Test
	void clearForgetsEverything() {
		TranscriptAssembler t = new TranscriptAssembler();
		t.end(0, "x");
		t.consumeAll();
		t.clear();
		t.update(0, "again");
		assertEquals("again", t.text());
	}
}
