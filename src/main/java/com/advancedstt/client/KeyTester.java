package com.advancedstt.client;

import com.advancedstt.Provider;
import com.advancedstt.stt.SessionUsage;
import com.advancedstt.stt.SttError;
import com.advancedstt.stt.SttListener;
import com.advancedstt.stt.SttOptions;
import com.advancedstt.stt.SttSession;
import com.advancedstt.stt.SttSessions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

/**
 * Checks a key by opening a connection and closing it as soon as the service accepts it. No audio
 * is sent. AssemblyAI bills the second or so the connection is open; Cartesia bills audio only.
 */
public final class KeyTester {
	public enum State {
		IDLE, TESTING, OK, FAILED
	}

	private volatile State state = State.IDLE;
	private volatile Component message = Component.empty();
	private volatile SttSession session;
	private volatile boolean opened;
	private volatile long run;

	public State state() {
		return state;
	}

	public Component message() {
		return message;
	}

	/** Tests the key with the same AssemblyAI settings dictation uses, so a refused setting shows up here too. */
	public void test(Provider provider, String apiKey, SttOptions options) {
		cancel();
		long thisRun = ++run;
		opened = false;
		state = State.TESTING;
		message = Component.translatable("advanced_stt.test.testing", provider.displayName());
		SttListener listener = new SttListener() {
			@Override
			public void onOpen() {
				if (thisRun != run) {
					return;
				}
				opened = true;
				state = State.OK;
				message = Component.translatable("advanced_stt.test.ok", provider.displayName());
				SttSession s = session;
				if (s != null) {
					s.finish();
				}
			}

			@Override
			public void onTurnUpdate(int turn, String text) {
			}

			@Override
			public void onTurnEnd(int turn, String text) {
			}

			@Override
			public void onError(SttError error) {
				if (thisRun != run || state == State.OK) {
					return;
				}
				state = State.FAILED;
				message = Messages.error(Messages.redact(error, apiKey), provider);
			}

			@Override
			public void onClosed(SessionUsage usage) {
				if (thisRun == run && state == State.TESTING) {
					state = State.FAILED;
					message = Component.translatable("advanced_stt.test.no_answer", provider.displayName());
				}
				recordUsage(usage);
			}
		};
		try {
			session = SttSessions.open(provider, apiKey,
					new SttOptions(List.of(), options.mode(), options.voiceFocus()), listener);
			if (opened) {
				session.finish();
			}
		} catch (RuntimeException e) {
			state = State.FAILED;
			message = Component.translatable("advanced_stt.notice.start_failed", provider.displayName(),
					Messages.shorten(Messages.redact(e.toString(), apiKey)));
		}
	}

	public void cancel() {
		run++;
		SttSession s = session;
		session = null;
		if (s != null) {
			s.finish();
		}
		state = State.IDLE;
		message = Component.empty();
	}

	private static void recordUsage(SessionUsage usage) {
		Minecraft.getInstance().execute(() -> {
			AdvancedStt mod = AdvancedStt.get();
			mod.usage().record(usage, mod.config().get());
			try {
				mod.usage().save();
			} catch (IOException e) {
				AdvancedStt.LOG.warn("Could not save the usage estimate: {}", e.toString());
			}
		});
	}
}
