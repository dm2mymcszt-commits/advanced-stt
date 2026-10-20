package com.advancedstt.client;

import com.advancedstt.config.SttConfig;
import com.advancedstt.text.AutoSendPolicy;
import com.advancedstt.usage.UsageFormat;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * What only this player sees: a small "microphone on" indicator in the top-left corner, short
 * notices under it, and the live text at the bottom, where the chat box appears.
 */
final class HudOverlay implements HudElement {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int PANEL = 0x90000000;
	private static final int DOT_LISTENING = 0xFFE53935;
	private static final int DOT_WAITING = 0xFFFFB300;
	private static final int LEVEL_BACK = 0xFF404040;
	private static final int LEVEL_FILL = 0xFF4CAF50;
	private static final int ERROR = 0xFFFF5555;
	private static final int INFO = 0xFFFFFF55;
	private static final int PREVIEW_TEXT = 0xFFE0E0E0;
	private static final int COUNTDOWN = 0xFF64B5F6;
	private static final int LEVEL_WIDTH = 24;
	private static final int NOTICE_WIDTH = 300;

	private final AdvancedStt mod;

	HudOverlay(AdvancedStt mod) {
		this.mod = mod;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null) {
				return;
			}
			DictationController dictation = mod.dictation();
			SttConfig config = mod.config().get();
			int y = 4;
			if (config.indicator.visual()) {
				y = drawIndicator(graphics, mc.font, dictation, y);
			}
			drawNotice(graphics, mc.font, dictation, y);
			if (mc.gui.screen() == null) {
				drawLiveText(graphics, mc, dictation);
			}
			if (mc.gui.screen() == null || mc.gui.screen() instanceof ChatScreen) {
				drawAutoSend(graphics, mc, dictation);
			}
		} catch (RuntimeException e) {
			// Drawing must never break the game's HUD.
		}
	}

	/** The microphone indicator in the top-left corner. Returns where the next line goes. */
	private static int drawIndicator(GuiGraphicsExtractor g, Font font, DictationController dictation, int y) {
		DictationController.Phase phase = dictation.phase();
		if (phase == DictationController.Phase.IDLE) {
			return y;
		}
		int x = 4;
		boolean listening = phase == DictationController.Phase.LISTENING;
		Component label = switch (phase) {
			case CONNECTING -> Component.translatable("advanced_stt.hud.connecting");
			case LISTENING -> Component.translatable("advanced_stt.hud.listening",
					UsageFormat.clock(dictation.listeningMs()));
			default -> Component.translatable("advanced_stt.hud.finishing");
		};
		int textWidth = font.width(label);
		int width = 10 + textWidth + (listening ? 4 + LEVEL_WIDTH : 0);
		g.fill(x - 2, y - 2, x + width + 2, y + 10, PANEL);
		g.fill(x, y + 1, x + 6, y + 7, listening ? DOT_LISTENING : DOT_WAITING);
		g.text(font, label, x + 10, y, WHITE, true);
		if (listening) {
			int meterX = x + 10 + textWidth + 4;
			g.fill(meterX, y + 2, meterX + LEVEL_WIDTH, y + 6, LEVEL_BACK);
			int filled = Math.round(Math.min(1f, dictation.micLevel()) * LEVEL_WIDTH);
			if (filled > 0) {
				g.fill(meterX, y + 2, meterX + filled, y + 6, LEVEL_FILL);
			}
		}
		return y + 14;
	}

	/** A short message under the indicator, shown whatever the indicator setting is. */
	private static void drawNotice(GuiGraphicsExtractor g, Font font, DictationController dictation, int y) {
		Component notice = dictation.notice();
		if (notice == null) {
			return;
		}
		int x = 4;
		int color = dictation.noticeIsError() ? ERROR : INFO;
		List<FormattedCharSequence> lines = font.split(notice, NOTICE_WIDTH);
		for (FormattedCharSequence line : lines) {
			g.fill(x - 2, y - 2, x + font.width(line) + 2, y + 10, PANEL);
			g.text(font, line, x, y, color, true);
			y += 12;
		}
	}

	/** The words so far, in a strip like the chat box, while the chat box itself is closed. */
	private static void drawLiveText(GuiGraphicsExtractor g, Minecraft mc, DictationController dictation) {
		String text = dictation.pendingText();
		if (text.isEmpty()) {
			return;
		}
		Font font = mc.font;
		int width = g.guiWidth();
		int top = g.guiHeight() - 14;
		g.fill(2, top, width - 2, top + 12, mc.options.getBackgroundColor(Integer.MIN_VALUE));
		String counter = text.length() > AutoSendPolicy.MAX_MESSAGE_LENGTH
				? text.length() + "/" + AutoSendPolicy.MAX_MESSAGE_LENGTH : "";
		int counterWidth = counter.isEmpty() ? 0 : font.width(counter) + 6;
		int room = Math.max(20, width - 8 - counterWidth);
		String shown = text;
		if (font.width(text) > room) {
			// Keep the newest words in view.
			shown = "…" + font.plainSubstrByWidth(text, room - font.width("…"), true);
		}
		g.text(font, shown, 4, top + 2, PREVIEW_TEXT, false);
		if (!counter.isEmpty()) {
			g.text(font, counter, width - 4 - font.width(counter), top + 2, ERROR, false);
		}
	}

	/**
	 * With automatic sending, above the words (or the chat box): how long before the message is sent,
	 * a bar that empties until then, and that Esc cancels it.
	 */
	private static void drawAutoSend(GuiGraphicsExtractor g, Minecraft mc, DictationController dictation) {
		long left = dictation.autoSendInMs();
		if (left < 0) {
			return;
		}
		Font font = mc.font;
		int width = g.guiWidth();
		int top = g.guiHeight() - 14; // where the words, or the chat box, are
		Component label;
		if (left == Long.MAX_VALUE) {
			label = Component.translatable("advanced_stt.hud.esc_cancels");
		} else {
			double seconds = Math.ceil(left / 100.0) / 10.0;
			String number = UsageFormat.localize(String.format(java.util.Locale.ROOT, "%.1f", seconds),
					mc.options.languageCode);
			label = Component.translatable("advanced_stt.hud.send_in", number);
			long delay = Math.max(1, dictation.autoSendDelayMs());
			int bar = (int) Math.round((width - 4) * Math.clamp((double) left / delay, 0.0, 1.0));
			g.fill(2, top - 1, 2 + bar, top, COUNTDOWN);
		}
		int labelWidth = font.width(label);
		int x = width - 4 - labelWidth;
		int y = top - 13;
		g.fill(x - 2, y - 2, x + labelWidth + 2, y + 10, PANEL);
		g.text(font, label, x, y, WHITE, true);
	}
}
