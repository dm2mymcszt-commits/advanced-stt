package com.advancedstt.client;

import com.advancedstt.Provider;
import com.advancedstt.audio.MicCapture;
import com.advancedstt.audio.Microphone;
import com.advancedstt.client.gui.SettingsScreen;
import com.advancedstt.config.ConfigStore;
import com.advancedstt.config.SttConfig;
import com.advancedstt.secure.CredentialStore;
import com.advancedstt.stt.SessionUsage;
import com.advancedstt.stt.SttError;
import com.advancedstt.stt.SttListener;
import com.advancedstt.stt.SttOptions;
import com.advancedstt.stt.SttSession;
import com.advancedstt.stt.SttSessions;
import com.advancedstt.text.AutoSendPolicy;
import com.advancedstt.text.ChatText;
import com.advancedstt.text.TranscriptAssembler;
import com.advancedstt.usage.UsageFormat;
import com.advancedstt.usage.UsageTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringUtil;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The dictation loop. Runs on the game thread once per client tick: talk key, microphone,
 * connection, live text, chat box and sending. Network and microphone threads never touch game
 * state; they only post events to a queue that the tick reads.
 *
 * <p>Money rule: a connection is open only while the microphone is on. It closes when the talk key
 * is released (or pressed again in toggle mode), when the game window loses focus, when the pause
 * menu opens, when the time limit is reached, and when the player leaves the world.
 */
public final class DictationController {
	/** In hold mode, a press shorter than this opens no connection (Alt+Tab, a slip of the finger). */
	static final long ARM_MS = 150;
	/**
	 * The same while the chat box is open, where Alt is also used to type symbols (Option on a Mac).
	 * The microphone already records during this time, so no words are lost.
	 */
	static final long CHAT_ARM_MS = 300;
	private static final long NOTICE_MS = 6_000;
	private static final long SAME_CHAT_NOTICE_GAP_MS = 4_000;
	private static final long NOT_WAITING = -1;
	private static final long WAITING_FOR_WORDS = Long.MAX_VALUE;

	public enum Phase {
		IDLE, CONNECTING, LISTENING, FINISHING
	}

	enum StopReason {
		KEY, TIME_LIMIT, FOCUS_LOST, PAUSED, SCREEN
	}

	sealed interface Event permits Opened, Update, End, Failed, Closed, MicFailed {
		long session();
	}

	record Opened(long session) implements Event {
	}

	record Update(long session, int turn, String text) implements Event {
	}

	record End(long session, int turn, String text) implements Event {
	}

	record Failed(long session, Provider provider, SttError error) implements Event {
	}

	record Closed(long session, SessionUsage usage) implements Event {
	}

	record MicFailed(long session, String message) implements Event {
	}

	/** Where microphones and connections come from. Players always get {@link #REAL}. */
	interface Devices {
		Devices REAL = new Devices() {
			@Override
			public Microphone microphone(String deviceName, Consumer<byte[]> sink) {
				return new MicCapture(deviceName, sink);
			}

			@Override
			public SttSession connect(Provider provider, String apiKey, SttOptions options, SttListener listener) {
				return SttSessions.open(provider, apiKey, options, listener);
			}
		};

		Microphone microphone(String deviceName, Consumer<byte[]> sink);

		SttSession connect(Provider provider, String apiKey, SttOptions options, SttListener listener);
	}

	/** One press of the talk key: microphone, then (after a short delay in hold mode) a connection. */
	private static final class Live {
		final long id;
		final Provider provider;
		final long armedAtMs;
		final AudioPipe pipe = new AudioPipe();
		Microphone mic;
		SttSession session;
		long startedAtMs;
		boolean opened;
		private String apiKey;

		Live(long id, Provider provider, String apiKey, long armedAtMs) {
			this.id = id;
			this.provider = provider;
			this.apiKey = apiKey;
			this.armedAtMs = armedAtMs;
		}

		/** The key is only kept until the connection is opened. */
		String takeKey() {
			String key = apiKey;
			apiKey = null;
			return key;
		}
	}

	private final ConfigStore configStore;
	private final CredentialStore credentials;
	private final UsageTracker usage;
	private final TranscriptAssembler transcript = new TranscriptAssembler();
	private final AutoSendPolicy autoSend = new AutoSendPolicy();
	private final ChatBridge bridge = new ChatBridge();
	private final Queue<Event> events = new ConcurrentLinkedQueue<>();
	/** Opens and closes microphones in order, off the game thread (opening a device can take a moment). */
	private final ExecutorService micWorker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "advanced-stt-microphone-control");
		t.setDaemon(true);
		return t;
	});

	private Devices devices = Devices.REAL;
	private Live current;
	/** Sessions the player ended, still sending their last words. */
	private final Map<Long, Live> closing = new LinkedHashMap<>();
	/** Sessions whose words must be dropped (the player left the world). */
	private final Set<Long> discarded = new HashSet<>();
	private long sessionCounter;

	private boolean keyWasHeld;
	/** Another key was pressed in the chat box while the talk key was down: it was used to type, not to talk. */
	private boolean talkKeyUsedForTyping;
	private boolean chatTalkKeyReleased;
	private String lastText = "";
	private long lastChangeMs;
	/** When the microphone last went fully off (the last words of every connection arrived). */
	private long micOffAtMs;
	private boolean endpointRequested;
	/**
	 * The chat box holds text the player sends themselves (auto-send handed it to them, or they opened
	 * the chat to check it). Nothing is sent automatically until that chat box closes.
	 */
	private boolean autoSendPaused;
	/** When the waiting message will be sent, or {@link #NOT_WAITING} / {@link #WAITING_FOR_WORDS}. */
	private long sendDueAtMs = NOT_WAITING;
	private long sendDelayMs;
	private volatile boolean openSettingsSoon;

	private Component notice;
	private boolean noticeIsError;
	private long noticeAtMs = Long.MIN_VALUE / 2;
	private String lastChatNotice = "";
	private long lastChatNoticeAtMs = Long.MIN_VALUE / 2;

	DictationController(ConfigStore configStore, CredentialStore credentials, UsageTracker usage) {
		this.configStore = configStore;
		this.credentials = credentials;
		this.usage = usage;
	}

	// ---- What the HUD reads (game thread) ----

	public Phase phase() {
		if (current != null && current.session != null) {
			return current.opened ? Phase.LISTENING : Phase.CONNECTING;
		}
		if (!closing.isEmpty()) {
			return Phase.FINISHING;
		}
		return Phase.IDLE;
	}

	/** Milliseconds since the microphone went live, or 0. */
	public long listeningMs() {
		return current != null && current.session != null ? Util.getMillis() - current.startedAtMs : 0;
	}

	/** Microphone loudness, 0 to 1. */
	public float micLevel() {
		return current != null && current.mic != null ? current.mic.level() : 0f;
	}

	/** Dictated text not handed over yet. */
	public String pendingText() {
		return transcript.text();
	}

	public boolean chatBound() {
		return bridge.isBound();
	}

	/**
	 * In auto-send mode, while a message waits to be sent: the milliseconds left (0: any moment now),
	 * or {@link Long#MAX_VALUE} while its last words are still on their way. -1 when no message waits.
	 */
	public long autoSendInMs() {
		if (sendDueAtMs == NOT_WAITING || sendDueAtMs == WAITING_FOR_WORDS) {
			return sendDueAtMs;
		}
		return Math.max(0, sendDueAtMs - Util.getMillis());
	}

	/** The wait the player chose before a message is sent, in milliseconds. */
	public long autoSendDelayMs() {
		return sendDelayMs;
	}

	/** A short message for the HUD, or null when there is none to show. */
	public Component notice() {
		return Util.getMillis() - noticeAtMs < NOTICE_MS ? notice : null;
	}

	public boolean noticeIsError() {
		return noticeIsError;
	}

	public void openSettingsSoon() {
		openSettingsSoon = true;
	}

	/**
	 * Esc was pressed in game, just before the pause menu opens. In auto-send mode, while words are
	 * being dictated or wait to be sent, Esc drops them instead: the microphone turns off and nothing
	 * is sent. Returns true when it did, so the pause menu stays closed. Game thread only.
	 */
	public boolean cancelOnEscape(Minecraft mc) {
		try {
			SttConfig config = configStore.get();
			if (config.sendMode != SttConfig.SendMode.AUTO || mc.player == null || mc.gui.screen() != null
					|| bridge.isBound()) {
				return false;
			}
			boolean micWasOn = current != null && current.session != null;
			if (!micWasOn && closing.isEmpty() && transcript.isEmpty()) {
				return false; // nothing to cancel: open the pause menu as usual
			}
			stopEverything(true);
			if (micWasOn) {
				beep(mc, config, false);
			}
			AdvancedStt.LOG.info("Dictation cancelled with Esc");
			notify(mc, Component.translatable("advanced_stt.notice.cancelled"), false);
			return true;
		} catch (RuntimeException e) {
			AdvancedStt.LOG.error("Could not cancel the dictation", e);
			return false;
		}
	}

	/** For the in-game tests: a fake microphone and service. Game thread only. */
	void useDevices(Devices devices) {
		this.devices = devices;
	}

	// ---- The tick ----

	void tick(Minecraft mc) {
		try {
			tickUnsafe(mc);
		} catch (RuntimeException e) {
			// A bug here must never crash the game: stop listening and say so.
			AdvancedStt.LOG.error("Dictation stopped after an unexpected error", e);
			stopEverything(false);
			notify(mc, Component.translatable("advanced_stt.notice.internal_error"), true);
		}
	}

	private void tickUnsafe(Minecraft mc) {
		long now = Util.getMillis();
		// Look at the chat box before new words arrive, so words the player never saw are not lost.
		switch (bridge.observe(mc, transcript)) {
			case CLOSED -> autoSendPaused = false; // sent or cancelled: the player dealt with it
			case OPENED -> {
				if (!transcript.isEmpty() && configStore.get().sendMode == SttConfig.SendMode.AUTO) {
					autoSendPaused = true; // opened to check the words: the player sends them with Enter
				}
			}
			default -> {
			}
		}
		drainEvents(mc);

		if (openSettingsSoon) {
			openSettingsSoon = false;
			mc.gui.setScreen(new SettingsScreen(mc.gui.screen()));
		}
		while (Keys.OPEN_SETTINGS.consumeClick()) {
			if (mc.gui.screen() == null) {
				mc.gui.setScreen(new SettingsScreen(null));
			}
		}
		int talkClicks = 0;
		while (Keys.TALK.consumeClick()) {
			talkClicks++; // presses in game, even ones too quick for the check below
		}

		if (mc.player == null || mc.getConnection() == null) {
			// Not in a world: nothing to dictate into.
			if (current != null || !transcript.isEmpty() || bridge.isBound()) {
				stopEverything(true);
			}
			keyWasHeld = false;
			chatTalkKeyReleased = false;
			return;
		}

		SttConfig config = configStore.get();
		handleTalkKey(mc, config, now, talkClicks);
		enforceLimits(mc, config, now);

		bridge.write(transcript);
		trackChanges(now);

		if (config.sendMode == SttConfig.SendMode.AUTO) {
			autoSendTick(mc, config, now);
		} else {
			sendDueAtMs = NOT_WAITING;
			handOverTick(mc);
		}
	}

	private void handleTalkKey(Minecraft mc, SttConfig config, long now, int clicks) {
		Screen screen = mc.gui.screen();
		boolean inChat = screen instanceof ChatScreen;
		boolean focused = mc.isWindowActive();
		boolean allowed = talkKeyAllowed(screen);
		boolean held = focused && allowed && Keys.talkKeyHeld(mc);
		boolean wasHeld = keyWasHeld;
		boolean pressed = held && !wasHeld;
		boolean typing = talkKeyUsedForTyping;
		boolean releasedInChat = chatTalkKeyReleased;
		keyWasHeld = held;
		chatTalkKeyReleased = false;
		if (!held) {
			talkKeyUsedForTyping = false;
		}
		if (config.activation == SttConfig.ActivationMode.HOLD) {
			if (current == null) {
				if (pressed && !typing) {
					begin(mc, config, now, false);
				}
			} else if (!held) {
				end(mc, config, !focused ? StopReason.FOCUS_LOST : !allowed ? StopReason.SCREEN : StopReason.KEY);
			} else if (current.session == null) {
				if (typing) {
					cancelArming(); // the key was held to type a symbol, not to talk
				} else if (now - current.armedAtMs >= (inChat ? CHAT_ARM_MS : ARM_MS)) {
					connect(mc, current, config, now);
				}
			}
			return;
		}
		boolean toggle;
		if (inChat) {
			// In the chat box, act when the key comes back up, and not if it was used to type a symbol.
			boolean released = (!held && wasHeld) || releasedInChat;
			toggle = released && focused && !typing;
		} else {
			// In game, act on the press. A tap too quick for the check above still shows up as a click.
			toggle = focused && allowed && (pressed || (clicks > 0 && !held && !wasHeld));
		}
		if (toggle) {
			if (current == null) {
				begin(mc, config, now, true);
			} else {
				end(mc, config, StopReason.KEY);
			}
		}
	}

	/**
	 * A key was pressed in the chat box. If the talk key is down at the same time, it is being used
	 * to type a symbol (Option on a Mac), not to talk.
	 */
	void keyPressedInChat(Minecraft mc, int key) {
		if (!Keys.isTalkKey(key) && mc.isWindowActive() && Keys.talkKeyHeld(mc)) {
			talkKeyUsedForTyping = true;
		}
	}

	/** The talk key came up in the chat box (kept until the next tick, so a quick tap isn't missed). */
	void keyReleasedInChat(int key) {
		if (Keys.isTalkKey(key)) {
			chatTalkKeyReleased = true;
		}
	}

	/** The talk key works in game and while the chat box is open, not in other screens. */
	private static boolean talkKeyAllowed(Screen screen) {
		return screen == null || screen instanceof ChatScreen;
	}

	private void enforceLimits(Minecraft mc, SttConfig config, long now) {
		if (current == null) {
			return;
		}
		if (!mc.isWindowActive()) {
			end(mc, config, StopReason.FOCUS_LOST);
			return;
		}
		Screen screen = mc.gui.screen();
		if (screen != null && !(screen instanceof ChatScreen) && screen.isPauseScreen()) {
			end(mc, config, StopReason.PAUSED);
			return;
		}
		if (current.session != null && now - current.startedAtMs >= config.maxSessionSeconds * 1000L) {
			end(mc, config, StopReason.TIME_LIMIT);
		}
	}

	private void trackChanges(long now) {
		String text = transcript.text();
		if (!text.equals(lastText)) {
			lastText = text;
			lastChangeMs = now;
			endpointRequested = false;
		}
	}

	// ---- Starting and stopping ----

	private void begin(Minecraft mc, SttConfig config, long now, boolean connectNow) {
		Provider provider = config.provider;
		Optional<String> key;
		try {
			key = credentials.get(provider);
		} catch (RuntimeException e) {
			key = Optional.empty();
		}
		if (key.isEmpty()) {
			notify(mc, Component.translatable("advanced_stt.notice.no_key", provider.displayName()), true);
			return;
		}
		Live live = new Live(++sessionCounter, provider, key.get(), now);
		live.mic = devices.microphone(config.microphone, live.pipe);
		current = live;
		micWorker.execute(() -> {
			try {
				live.mic.start();
			} catch (MicCapture.MicException e) {
				events.add(new MicFailed(live.id, e.getMessage()));
			} catch (RuntimeException | LinkageError e) {
				events.add(new MicFailed(live.id, e.toString()));
			}
		});
		if (connectNow) {
			connect(mc, live, config, now);
		}
	}

	private void connect(Minecraft mc, Live live, SttConfig config, long now) {
		String key = live.takeKey();
		try {
			live.session = devices.connect(live.provider, key, config.sttOptions(), new Listener(live.id, live.provider, key));
		} catch (RuntimeException e) {
			current = null;
			live.pipe.discard();
			micWorker.execute(live.mic::stop);
			notify(mc, Component.translatable("advanced_stt.notice.start_failed", live.provider.displayName(),
					Messages.shorten(Messages.redact(e.toString(), key))), true);
			return;
		}
		live.pipe.attach(live.session);
		live.startedAtMs = now;
		endpointRequested = false;
		beep(mc, config, true);
		AdvancedStt.LOG.info("Microphone on ({})", live.provider.displayName());
	}

	private void end(Minecraft mc, SttConfig config, StopReason reason) {
		Live live = current;
		if (live == null) {
			return;
		}
		if (live.session == null) {
			cancelArming(); // released before the connection opened: nothing was sent, nothing is billed
			return;
		}
		current = null;
		closing.put(live.id, live);
		micWorker.execute(() -> {
			live.mic.stop(); // sends the last bit of audio first
			live.session.finish(); // then asks for the last words and closes
		});
		beep(mc, config, false);
		AdvancedStt.LOG.info("Microphone off ({})", reason.name().toLowerCase(java.util.Locale.ROOT));
		if (reason == StopReason.TIME_LIMIT) {
			notify(mc, Component.translatable("advanced_stt.notice.time_limit",
					UsageFormat.duration(config.maxSessionSeconds)), false);
		}
		if (config.sendMode == SttConfig.SendMode.MANUAL && !transcript.isEmpty() && !bridge.isBound()
				&& mc.gui.screen() == null) {
			openChat(mc); // the last words arrive straight in the chat box
		}
	}

	/** Drops a press whose connection isn't open yet: its audio is thrown away, unsent. */
	private void cancelArming() {
		Live live = current;
		current = null;
		live.pipe.discard();
		micWorker.execute(live.mic::stop);
		if (closing.isEmpty()) {
			micOffAtMs = Util.getMillis(); // a waiting message gets its full wait again
		}
	}

	/** Stops everything at once. With {@code discardText}, dictated words not handed over are dropped. */
	void stopEverything(boolean discardText) {
		List<Live> all = new ArrayList<>(closing.values());
		if (current != null) {
			all.add(current);
			if (current.session != null) {
				closing.put(current.id, current);
			}
			current = null;
		}
		for (Live live : all) {
			if (discardText) {
				discarded.add(live.id);
			}
			live.pipe.discard();
			micWorker.execute(live.mic::stop);
			if (live.session != null) {
				live.session.abort(); // its usage is still recorded when the close event arrives
			}
		}
		if (discardText) {
			transcript.clear();
			bridge.release();
			lastText = "";
			autoSendPaused = false;
			sendDueAtMs = NOT_WAITING;
		}
	}

	/** The game is closing: stop, and save what was used. */
	void shutdown() {
		stopEverything(true);
		drainEvents(Minecraft.getInstance());
		micWorker.shutdown();
	}

	// ---- Events from the network and microphone threads ----

	private void drainEvents(Minecraft mc) {
		Event event;
		while ((event = events.poll()) != null) {
			try {
				handle(mc, event);
			} catch (RuntimeException e) {
				AdvancedStt.LOG.error("Could not handle a dictation event", e);
			}
		}
	}

	private void handle(Minecraft mc, Event event) {
		switch (event) {
			case Opened opened -> {
				if (current != null && current.id == opened.session()) {
					current.opened = true;
				}
			}
			case Update update -> {
				if (!discarded.contains(update.session())) {
					transcript.update(ChatText.turnKey(update.session(), update.turn()), update.text());
				}
			}
			case End end -> {
				if (!discarded.contains(end.session())) {
					transcript.end(ChatText.turnKey(end.session(), end.turn()), end.text());
				}
			}
			case Failed failed -> {
				AdvancedStt.LOG.warn("{} error ({}): {}", failed.provider().displayName(), failed.error().kind(),
						failed.error().detail());
				if (!discarded.contains(failed.session())) {
					notify(mc, Messages.error(failed.error(), failed.provider()), true);
				}
			}
			case MicFailed micFailed -> onMicFailed(mc, micFailed);
			case Closed closed -> onClosed(mc, closed);
		}
	}

	private void onMicFailed(Minecraft mc, MicFailed event) {
		AdvancedStt.LOG.warn("Microphone error: {}", event.message());
		if (current == null || current.id != event.session()) {
			return;
		}
		Live live = current;
		current = null;
		live.pipe.discard();
		if (live.session != null) {
			closing.put(live.id, live);
			live.session.abort();
		}
		notify(mc, Component.translatable("advanced_stt.notice.mic_failed", Messages.shorten(event.message())), true);
	}

	private void onClosed(Minecraft mc, Closed event) {
		Live live = closing.remove(event.session());
		if (live == null && current != null && current.id == event.session()) {
			// Closed while the microphone was still on: the service or the network ended it.
			live = current;
			current = null;
			live.pipe.discard();
			micWorker.execute(live.mic::stop);
			beep(mc, configStore.get(), false);
		}
		if (!discarded.remove(event.session())) {
			transcript.finishOpenTurns(ChatText.firstTurn(event.session()), ChatText.endTurn(event.session()));
		}
		if (current == null && closing.isEmpty()) {
			micOffAtMs = Util.getMillis(); // the last words are in: a waiting message gets its full wait from now
		}
		SessionUsage used = event.usage();
		double cost = usage.record(used, configStore.get());
		try {
			usage.save();
		} catch (IOException e) {
			AdvancedStt.LOG.warn("Could not save the usage estimate: {}", e.toString());
		}
		AdvancedStt.LOG.info("Connection closed: {} s billable, about {} (estimate)",
				String.format(java.util.Locale.ROOT, "%.1f", used.billableSeconds()),
				UsageFormat.amount(used.provider(), cost));
	}

	// ---- Handing the text over ----

	/** Manual mode: once dictation is over, the text goes into the chat box for the player to check. */
	private void handOverTick(Minecraft mc) {
		if (current != null || !closing.isEmpty()) {
			return;
		}
		if (bridge.isBound() || transcript.isEmpty() || mc.gui.screen() != null) {
			return;
		}
		openChat(mc);
	}

	/**
	 * Auto-send mode: the message goes out once the player has been quiet for the wait they chose.
	 * While the microphone is on, the wait starts at the last new word. Once it is off, it starts when
	 * the last words arrive, so the player always has the whole wait to read the text and press Esc.
	 */
	private void autoSendTick(Minecraft mc, SttConfig config, long now) {
		sendDueAtMs = NOT_WAITING;
		if (autoSendPaused || transcript.isEmpty()) {
			return; // nothing new, or the player sends it themselves
		}
		long delayMs = Math.round(config.autoSendSilenceSeconds * 1000);
		sendDelayMs = delayMs;
		boolean live = current != null && current.session != null;
		if (!closing.isEmpty() || (current != null && !live)) {
			sendDueAtMs = WAITING_FOR_WORDS; // the last words are on their way, or the talk key was pressed again
			return;
		}
		// Turning the microphone back on during the wait (to add words) starts it again.
		long quietSince = Math.max(lastChangeMs, live ? current.startedAtMs : micOffAtMs);
		String message = bridge.isBound() ? bridge.value() : transcript.text();
		AutoSendPolicy.Decision decision = autoSend.decide(message, live && transcript.hasOpenTurn(),
				quietSince, delayMs, now);
		if (decision == AutoSendPolicy.Decision.SEND && !mc.computeChatAbilities().canSendMessages()) {
			decision = AutoSendPolicy.Decision.HAND_TO_PLAYER; // chat is restricted: let the chat screen explain
		}
		switch (decision) {
			case WAIT -> sendDueAtMs = quietSince + delayMs;
			case REQUEST_ENDPOINT -> {
				sendDueAtMs = WAITING_FOR_WORDS;
				if (!endpointRequested && live) {
					endpointRequested = true;
					current.session.requestEndpoint();
				}
			}
			case SEND -> send(mc, message, now);
			case HAND_TO_PLAYER -> {
				autoSendPaused = true;
				notify(mc, Messages.notSentAutomatically(message, autoSend, now), false);
				if (!bridge.isBound() && mc.gui.screen() == null) {
					openChat(mc);
				}
			}
		}
	}

	private void send(Minecraft mc, String message, long now) {
		String text = StringUtil.trimChatMessage(StringUtils.normalizeSpace(message.trim()));
		if (!text.isEmpty()) {
			if (bridge.isBound()) {
				bridge.submit(mc, text);
			} else {
				mc.gui.hud.getChat().addRecentChat(text);
				mc.player.connection.sendChat(text);
			}
			autoSend.markSent(text, now);
		}
		transcript.consumeAll();
		lastText = transcript.text();
	}

	private void openChat(Minecraft mc) {
		mc.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
		bridge.observe(mc, transcript); // fill it right away, not on the next tick
		bridge.write(transcript);
	}

	// ---- Feedback ----

	private void beep(Minecraft mc, SttConfig config, boolean on) {
		if (!config.indicator.sound()) {
			return;
		}
		try {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, on ? 1.6f : 1.0f));
		} catch (RuntimeException e) {
			// no sound is not worth an error
		}
	}

	/**
	 * Tells the player something. Errors go in the chat, where they stay readable (only this player
	 * sees them); other notices, and errors when the chat is hidden, show briefly in the HUD.
	 */
	private void notify(Minecraft mc, Component message, boolean error) {
		if (error && chatShown(mc)) {
			String text = message.getString();
			long now = Util.getMillis();
			if (text.equals(lastChatNotice) && now - lastChatNoticeAtMs < SAME_CHAT_NOTICE_GAP_MS) {
				return; // the same error is already right there in the chat
			}
			try {
				mc.gui.hud.getChat().addClientSystemMessage(Component.empty()
						.append(Component.literal("[Advanced STT] ").withStyle(ChatFormatting.GOLD))
						.append(message.copy().withStyle(ChatFormatting.RED)));
				lastChatNotice = text;
				lastChatNoticeAtMs = now;
				return;
			} catch (RuntimeException e) {
				// show it in the HUD instead
			}
		}
		notice = message;
		noticeIsError = error;
		noticeAtMs = Util.getMillis();
	}

	private static boolean chatShown(Minecraft mc) {
		return mc.options.chatVisibility().get() != ChatVisiblity.HIDDEN;
	}

	// ---- Plumbing ----

	/** Receives a session's callbacks on network threads and queues them for the game thread. */
	private final class Listener implements SttListener {
		private final long session;
		private final Provider provider;
		private final String apiKey;

		Listener(long session, Provider provider, String apiKey) {
			this.session = session;
			this.provider = provider;
			this.apiKey = apiKey;
		}

		@Override
		public void onOpen() {
			events.add(new Opened(session));
		}

		@Override
		public void onTurnUpdate(int turn, String text) {
			events.add(new Update(session, turn, text));
		}

		@Override
		public void onTurnEnd(int turn, String text) {
			events.add(new End(session, turn, text));
		}

		@Override
		public void onError(SttError error) {
			events.add(new Failed(session, provider, Messages.redact(error, apiKey)));
		}

		@Override
		public void onClosed(SessionUsage usage) {
			events.add(new Closed(session, usage));
		}
	}

	/**
	 * Carries microphone audio to the connection. Audio recorded before the connection exists (the
	 * first moments of a press) is kept and sent first, so the first words are not cut off.
	 */
	static final class AudioPipe implements Consumer<byte[]> {
		/** Two seconds of 100 ms chunks. */
		private static final int MAX_BUFFERED = 20;

		private final ArrayDeque<byte[]> buffered = new ArrayDeque<>();
		private SttSession target;
		private boolean discarded;

		@Override
		public synchronized void accept(byte[] pcm) {
			if (discarded) {
				return;
			}
			if (target != null) {
				target.sendAudio(pcm);
				return;
			}
			if (buffered.size() == MAX_BUFFERED) {
				buffered.removeFirst();
			}
			buffered.addLast(pcm);
		}

		synchronized void attach(SttSession session) {
			if (discarded) {
				return;
			}
			while (!buffered.isEmpty()) {
				session.sendAudio(buffered.removeFirst());
			}
			target = session;
		}

		/** Drops buffered audio and anything still coming from the microphone. */
		synchronized void discard() {
			discarded = true;
			buffered.clear();
			target = null;
		}
	}
}
