package com.advancedstt.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** A bar showing the estimated balance left, with the numbers written on it. */
final class UsageGauge extends AbstractWidget {
	private static final int BORDER = 0xFF808080;
	private static final int BACK = 0xFF202020;
	private static final int GOOD = 0xFF2E7D32;
	private static final int LOW = 0xFFF9A825;
	private static final int EMPTY = 0xFFC62828;

	/** Share of the balance left, 0 to 1, or below 0 when no balance was typed in. */
	private double fraction = -1;

	UsageGauge() {
		super(0, 0, 310, 20, Component.empty());
		this.active = false;
	}

	void set(Component label, double fraction) {
		setMessage(label);
		this.fraction = fraction;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		graphics.fill(x, y, x + w, y + h, BORDER);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, BACK);
		if (fraction >= 0) {
			double f = Math.clamp(fraction, 0.0, 1.0);
			int filled = (int) Math.round((w - 2) * f);
			int color = f > 0.25 ? GOOD : f > 0.10 ? LOW : EMPTY;
			if (filled > 0) {
				graphics.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
			}
		}
		Font font = Minecraft.getInstance().font;
		graphics.centeredText(font, getMessage(), x + w / 2, y + (h - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage());
	}
}
