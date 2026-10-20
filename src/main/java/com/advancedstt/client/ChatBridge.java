package com.advancedstt.client;

import com.advancedstt.mixin.ChatScreenAccessor;
import com.advancedstt.text.ChatText;
import com.advancedstt.text.TranscriptAssembler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.StringUtil;

/**
 * Writes dictated words into the open chat box, after whatever the player had typed there.
 *
 * <p>The box always belongs to the player: as soon as they type, delete or close it, everything
 * dictated so far counts as theirs and is never written again. Only new words are added after that.
 * Nothing is ever sent from here unless auto-send asks for it.
 */
final class ChatBridge {
	enum Result {
		NONE,
		/** A chat box was opened: the words not handed over yet go in it. */
		OPENED,
		/** The player changed the box. */
		USER_EDIT,
		/** The chat box was closed (sent, cancelled, or replaced by another screen). */
		CLOSED
	}

	private ChatScreen bound;
	/** What the box held before the dictated words. */
	private String base = "";
	/** What the box held after our last write, to notice the player's own changes. */
	private String lastWritten;
	/** What we last tried to write. The box may have cut it to its length limit. */
	private String lastDesired;

	boolean isBound() {
		return bound != null;
	}

	/** Looks at the chat box before new words are added: did the player edit or close it? */
	Result observe(Minecraft mc, TranscriptAssembler transcript) {
		Screen screen = mc.gui.screen();
		Result result = Result.NONE;
		if (bound != null && screen != bound) {
			transcript.consumeAll(); // sent, cancelled, or kept as a draft: the player dealt with it
			release();
			result = Result.CLOSED;
		}
		if (bound == null) {
			if (!(screen instanceof ChatScreen chat)) {
				return result;
			}
			EditBox input = input(chat);
			if (input == null) {
				return result;
			}
			bound = chat;
			base = input.getValue();
			lastWritten = base;
			lastDesired = null;
			return result == Result.CLOSED ? result : Result.OPENED;
		}
		EditBox input = input(bound);
		if (input == null) {
			return result;
		}
		String value = input.getValue();
		if (!value.equals(lastWritten)) {
			transcript.consumeAll();
			base = value;
			lastWritten = value;
			result = Result.USER_EDIT;
		}
		return result;
	}

	/** Puts the dictated words that are not handed over yet after what the player had in the box. */
	void write(TranscriptAssembler transcript) {
		if (bound == null) {
			return;
		}
		EditBox input = input(bound);
		if (input == null) {
			return;
		}
		String desired = ChatText.join(base, StringUtil.filterText(transcript.text()));
		if (desired.equals(lastDesired)) {
			return; // nothing new: leave the box (and the cursor) alone
		}
		lastDesired = desired;
		if (!desired.equals(input.getValue())) {
			input.setValue(desired); // also moves the cursor to the end
		}
		lastWritten = input.getValue();
	}

	/** What the chat box holds now. */
	String value() {
		EditBox input = bound == null ? null : input(bound);
		return input == null ? "" : input.getValue();
	}

	/** Sends the message the way pressing Enter does, then closes the chat box. */
	void submit(Minecraft mc, String message) {
		ChatScreen screen = bound;
		release();
		if (screen == null) {
			return;
		}
		EditBox input = input(screen);
		if (input != null) {
			input.setValue(""); // so the closed chat keeps no draft of a message that was sent
		}
		screen.handleChatInput(message, true);
		if (mc.gui.screen() == screen) {
			mc.gui.setScreen(null);
		}
	}

	void release() {
		bound = null;
		base = "";
		lastWritten = null;
		lastDesired = null;
	}

	private static EditBox input(ChatScreen screen) {
		try {
			return ((ChatScreenAccessor) screen).advancedStt$getInput();
		} catch (ClassCastException | LinkageError e) {
			return null;
		}
	}
}
