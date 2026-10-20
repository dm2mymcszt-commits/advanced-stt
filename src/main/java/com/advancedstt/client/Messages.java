package com.advancedstt.client;

import com.advancedstt.Provider;
import com.advancedstt.stt.SttError;
import com.advancedstt.text.AutoSendPolicy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Texts shown to the player about errors and dictation. */
public final class Messages {
	private static final int MAX_DETAIL = 160;

	private Messages() {
	}

	/** A clear sentence for an error, with the service's own words after it in gray. */
	public static Component error(SttError error, Provider provider) {
		String name = provider.displayName();
		MutableComponent text = switch (error.kind()) {
			case INVALID_KEY -> Component.translatable("advanced_stt.error.invalid_key", name);
			case NO_CREDITS -> Component.translatable("advanced_stt.error.no_credits", name);
			case RATE_LIMITED -> Component.translatable("advanced_stt.error.rate_limited", name);
			case NETWORK -> Component.translatable("advanced_stt.error.network", name);
			case AUDIO_BACKLOG -> Component.translatable("advanced_stt.error.audio_backlog");
			case SERVICE -> Component.translatable("advanced_stt.error.service", name);
		};
		String detail = shorten(error.detail());
		if (!detail.isEmpty()) {
			text.append(Component.literal(" (" + detail + ")").withStyle(ChatFormatting.GRAY));
		}
		return text;
	}

	/** Why auto-send left a message for the player to send. */
	static Component notSentAutomatically(String message, AutoSendPolicy policy, long nowMs) {
		String text = message == null ? "" : message.strip();
		if (text.startsWith("/")) {
			return Component.translatable("advanced_stt.notice.not_sent.command");
		}
		if (text.length() > AutoSendPolicy.MAX_MESSAGE_LENGTH) {
			return Component.translatable("advanced_stt.notice.not_sent.too_long", text.length(),
					AutoSendPolicy.MAX_MESSAGE_LENGTH);
		}
		if (!policy.allowedAutomatically(text, nowMs)) {
			return Component.translatable("advanced_stt.notice.not_sent.repeat");
		}
		return Component.translatable("advanced_stt.notice.not_sent.restricted");
	}

	/** Removes the API key from an error, in case a service ever repeats it back. */
	public static SttError redact(SttError error, String apiKey) {
		return new SttError(error.kind(), redact(error.detail(), apiKey));
	}

	public static String redact(String text, String apiKey) {
		if (text == null || apiKey == null || apiKey.length() < 4) {
			return text == null ? "" : text;
		}
		return text.replace(apiKey, "***");
	}

	public static String shorten(String text) {
		if (text == null) {
			return "";
		}
		String flat = text.strip().replaceAll("\\s+", " ");
		return flat.length() > MAX_DETAIL ? flat.substring(0, MAX_DETAIL) + "…" : flat;
	}
}
