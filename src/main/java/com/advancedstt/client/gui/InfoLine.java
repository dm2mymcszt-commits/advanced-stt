package com.advancedstt.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** One line of text in the settings list. Too-long text is cut, with the full text as a tooltip. */
final class InfoLine extends AbstractWidget {
	private int color;

	InfoLine(Component text, int color) {
		super(0, 0, 310, 20, text);
		this.color = color;
		this.active = false;
		updateTooltip();
	}

	void set(Component text, int color) {
		this.color = color;
		if (!text.equals(getMessage())) {
			setMessage(text);
			updateTooltip();
		}
	}

	private void updateTooltip() {
		Font font = Minecraft.getInstance().font;
		setTooltip(font.width(getMessage()) > getWidth() ? Tooltip.create(getMessage()) : null);
	}

	@Override
	public void setWidth(int width) {
		super.setWidth(width);
		updateTooltip();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Font font = Minecraft.getInstance().font;
		List<FormattedCharSequence> lines = font.split(getMessage(), getWidth());
		if (lines.isEmpty()) {
			return;
		}
		graphics.text(font, lines.getFirst(), getX(), getY() + (getHeight() - font.lineHeight) / 2 + 1, color, true);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage());
	}
}
