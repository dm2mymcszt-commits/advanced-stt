package com.advancedstt.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/** A text box for API keys: shows stars, can't be copied from, and the narrator never reads it. */
final class SecretEditBox extends EditBox {
	SecretEditBox(Font font, int width, Component label) {
		super(font, 0, 0, width, 20, label);
		setMaxLength(512);
		addFormatter((text, firstIndex) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY));
	}

	/** Copy and cut get nothing, so the key can't be copied back out. */
	@Override
	public String getHighlighted() {
		return "";
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage());
	}
}
