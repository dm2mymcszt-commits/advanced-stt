package com.advancedstt.client;

import com.advancedstt.Provider;
import com.advancedstt.client.gui.SettingsScreen;
import com.advancedstt.config.SttConfig;
import com.advancedstt.mixin.ChatScreenAccessor;
import com.advancedstt.stt.SttError;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Plays the mod in a real game: settings screen, then a singleplayer world where the talk key is
 * held and released, with a fake microphone and a fake speech service. Takes screenshots along the
 * way (build/run/clientGameTest/screenshots).
 *
 * <p>Run with {@code ./gradlew runClientGameTest} (on a machine without a screen:
 * {@code xvfb-run -a ./gradlew runClientGameTest}).
 */
public final class AdvancedSttGameTest implements FabricClientGameTest {
	private static final int TALK_KEY = GLFW.GLFW_KEY_LEFT_ALT;
	private static final String FAKE_KEY = "fake-key-for-the-in-game-test-0000";
	private static final int TIMEOUT_TICKS = 400;

	/** Chat messages the server received. */
	private static final List<String> SERVER_CHAT = new CopyOnWriteArrayList<>();

	private final FakeDevices devices = new FakeDevices();

	@Override
	public void runTest(ClientGameTestContext context) {
		requireThrowawayKeyFolder();
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> SERVER_CHAT.add(message.signedContent()));
		context.runOnClient(mc -> AdvancedStt.get().dictation().useDevices(devices));

		settingsScreenSavesAKey(context);

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			check(context.computeOnClient(Minecraft::isWindowActive), "the game window counts as focused");

			noKeyForTheChosenService(context);
			holdToTalkThenPressEnter(context);
			dictateAfterTypedText(context);
			altUsedToTypeASymbol(context);
			pressOnPressOff(context);
			autoSendAfterSilence(context);
			autoSendNeverSendsCommands(context);
			serviceRefusesTheKey(context);
			microphoneProblem(context);
			pauseMenuTurnsTheMicrophoneOff(context);
			timeLimitTurnsTheMicrophoneOff(context);

			// Leave the world while still talking: everything stops and nothing is kept.
			FakeDevices.FakeSession leaving = startTalking(context);
			leaving.say(0, "this is never sent");
			waitUntil(context, "the words show", mc -> dictation().pendingText().equals("this is never sent"));
		}
		FakeDevices.FakeSession left = last();
		waitUntil(context, "leaving the world closes the connection", mc -> left.aborted);
		context.getInput().releaseKey(TALK_KEY);
		waitUntil(context, "every microphone is off", mc -> allMicrophonesOff());
		check(context.computeOnClient(mc -> dictation().pendingText().isEmpty()), "no words are kept after leaving");
		check(!SERVER_CHAT.contains("this is never sent"), "nothing was sent while leaving");
		check(devices.sessions.stream().allMatch(FakeDevices.FakeSession::isClosed), "every connection is closed");
		check(devices.sessions.stream().allMatch(s -> s.apiKey.equals(FAKE_KEY)), "sessions got the saved key");

		settingsScreenShowsUsage(context);
		settingsScreenInFrench(context);
		context.setScreen(() -> new TitleScreen());
	}

	// ---- Settings screen ----

	private void settingsScreenSavesAKey(ClientGameTestContext context) {
		setConfig(context, config -> config.provider = Provider.ASSEMBLYAI); // the default on a first launch
		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(2);
		context.takeScreenshot("settings-no-key");

		// The rest of the test uses Cartesia, picked with the Service button.
		pressSetting(context, "advanced_stt.settings.provider");
		check(context.computeOnClient(mc -> AdvancedStt.get().config().get().provider) == Provider.CARTESIA,
				"the Service button switches to Cartesia");

		click(context, editBox("advanced_stt.settings.key"));
		context.getInput().typeChars(FAKE_KEY);
		context.waitTick();
		String shown = context.computeOnClient(mc -> find(mc.gui.screen(), editBox("advanced_stt.settings.key"))
				.map(w -> ((EditBox) w).getValue()).orElse(""));
		check(shown.equals(FAKE_KEY), "the key box holds what was typed");
		context.takeScreenshot("settings-key-typed");

		press(context, "advanced_stt.settings.key.save");
		check(context.computeOnClient(mc -> AdvancedStt.get().credentials().get(Provider.CARTESIA))
				.filter(FAKE_KEY::equals).isPresent(), "Save key stores the key");
		check(context.computeOnClient(mc -> find(mc.gui.screen(), editBox("advanced_stt.settings.key"))
				.map(w -> ((EditBox) w).getValue()).orElse("x")).isEmpty(), "the key box is emptied after saving");
		context.takeScreenshot("settings-key-saved");

		// A starting balance for the gauge.
		scrollToBottom(context);
		click(context, editBox("advanced_stt.settings.balance"));
		context.getInput().typeChars("100000");
		press(context, "advanced_stt.settings.balance.set");
		check(context.computeOnClient(mc -> AdvancedStt.get().usage().estimatedRemaining(Provider.CARTESIA))
				.orElse(-1) == 100_000, "Set balance stores the balance");
		context.takeScreenshot("settings-bottom");
		context.setScreen(() -> null);
	}

	private void settingsScreenShowsUsage(ClientGameTestContext context) {
		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(2);
		scrollToBottom(context);
		context.takeScreenshot("settings-usage-after-playing");
		double used = context.computeOnClient(mc -> AdvancedStt.get().usage().get(Provider.CARTESIA).totalUsed);
		check(used > 0, "usage was recorded");
		context.setScreen(() -> null);
	}

	private void settingsScreenInFrench(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			mc.options.languageCode = "fr_fr";
			mc.getLanguageManager().setSelected("fr_fr");
			mc.reloadResourcePacks();
		});
		context.waitFor(mc -> mc.gui.overlay() == null, 2_000);
		context.setScreen(() -> new SettingsScreen(null));
		context.waitTicks(2);
		context.takeScreenshot("settings-french");
		scrollToBottom(context);
		context.takeScreenshot("settings-french-bottom");
		context.setScreen(() -> null);
	}

	// ---- Dictation in a world ----

	private void noKeyForTheChosenService(ClientGameTestContext context) {
		setConfig(context, config -> config.provider = Provider.ASSEMBLYAI);
		int sessions = devices.sessions.size();
		context.getInput().holdKey(TALK_KEY);
		waitUntil(context, "a 'no key' message in the chat",
				mc -> chatHas(mc, "No AssemblyAI Universal-3.6 Pro API key yet"));
		check(context.computeOnClient(mc -> dictation().notice() == null), "the error isn't repeated in the HUD");
		context.getInput().releaseKey(TALK_KEY);
		context.waitTicks(10);
		check(devices.sessions.size() == sessions, "no connection without a key");
		check(devices.mics.isEmpty(), "no microphone without a key");
		context.takeScreenshot("no-key-in-chat");

		// With the chat hidden, errors show in the HUD instead.
		context.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
		context.waitTicks(100); // past the gap that stops the same error from repeating
		context.getInput().holdKey(TALK_KEY);
		waitUntil(context, "the error shows in the HUD", mc -> noticeContains("AssemblyAI") && dictation().noticeIsError());
		context.getInput().releaseKey(TALK_KEY);
		context.takeScreenshot("no-key-chat-hidden");
		context.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.FULL));
		setConfig(context, config -> config.provider = Provider.CARTESIA);
		context.waitTicks(2);
	}

	private void holdToTalkThenPressEnter(ClientGameTestContext context) {
		FakeDevices.FakeSession session = startTalking(context);
		session.say(0, "hello from");
		session.say(0, "hello from the mod");
		waitUntil(context, "the words show", mc -> dictation().pendingText().equals("hello from the mod"));
		context.takeScreenshot("listening");
		check(session.audioChunks.get() >= 3, "audio recorded before the connection opened is sent first");

		session.lastWords(0, "Hello from the mod.");
		context.getInput().releaseKey(TALK_KEY);
		waitUntil(context, "the chat box opens with the final words",
				mc -> "Hello from the mod.".equals(chatValue(mc)));
		check(session.finished && !session.aborted, "letting go finishes the connection");
		waitUntil(context, "the microphone is off", mc -> allMicrophonesOff());
		check(SERVER_CHAT.isEmpty(), "nothing is sent before Enter");
		context.takeScreenshot("chat-box-filled");

		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		waitUntil(context, "the server gets the message", mc -> SERVER_CHAT.contains("Hello from the mod."));
		context.waitTicks(2);
		check(context.computeOnClient(mc -> mc.gui.screen() == null), "the chat box closes after Enter");
		check(context.computeOnClient(mc -> dictation().pendingText().isEmpty()), "sent words are not shown again");
	}

	private void dictateAfterTypedText(ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyChat);
		context.waitForScreen(ChatScreen.class);
		context.getInput().typeChars("gg");
		FakeDevices.FakeSession session = startTalking(context);
		session.say(0, "well played");
		waitUntil(context, "dictated words go after the typed text", mc -> "gg well played".equals(chatValue(mc)));
		context.takeScreenshot("chat-typed-then-dictated");

		session.lastWords(0, "Well played.");
		context.getInput().releaseKey(TALK_KEY);
		waitUntil(context, "the final words replace the draft words", mc -> "gg Well played.".equals(chatValue(mc)));

		context.getInput().typeChars("!");
		context.waitTicks(2);
		check("gg Well played.!".equals(context.computeOnClient(AdvancedSttGameTest::chatValue)),
				"the player's own edits are kept");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
		waitUntil(context, "the server gets the edited message", mc -> SERVER_CHAT.contains("gg Well played.!"));
	}

	private void altUsedToTypeASymbol(ClientGameTestContext context) {
		context.getInput().pressKey(options -> options.keyChat);
		context.waitForScreen(ChatScreen.class);
		int sessions = devices.sessions.size();
		context.getInput().holdKey(TALK_KEY);
		context.waitTick();
		context.getInput().pressKey(GLFW.GLFW_KEY_9); // Option+9 types a symbol on a Mac
		waitMillis(context, 3 * DictationController.CHAT_ARM_MS);
		check(devices.sessions.size() == sessions, "Alt used to type a symbol opens no connection");
		context.getInput().releaseKey(TALK_KEY);
		context.waitTicks(2); // a person can't let go and press again within one tick
		waitUntil(context, "the microphone is off", mc -> allMicrophonesOff());

		// Holding it again, without typing, talks as usual.
		FakeDevices.FakeSession session = startTalking(context);
		session.lastWords(0, "still works");
		context.getInput().releaseKey(TALK_KEY);
		waitUntil(context, "the words arrive in the chat box", mc -> "still works".equals(chatValue(mc)));
		closeChatWithoutSending(context);
	}

	private void pressOnPressOff(ClientGameTestContext context) {
		setConfig(context, config -> config.activation = SttConfig.ActivationMode.TOGGLE);
		int before = devices.sessions.size();
		context.getInput().pressKey(TALK_KEY); // a quick tap
		waitUntil(context, "a tap turns the microphone on", mc -> devices.sessions.size() > before);
		FakeDevices.FakeSession session = devices.sessions.get(before);
		session.open();
		session.say(0, "toggle mode works");
		waitUntil(context, "the words show", mc -> dictation().pendingText().equals("toggle mode works"));
		waitMillis(context, 500);
		check(!session.finished && context.computeOnClient(mc -> dictation().phase()) == DictationController.Phase.LISTENING,
				"the microphone stays on after letting go");

		// Open the chat box while talking: the words go in it. A tap there turns the microphone off.
		context.getInput().pressKey(options -> options.keyChat);
		context.waitForScreen(ChatScreen.class);
		waitUntil(context, "the words go in the chat box", mc -> "toggle mode works".equals(chatValue(mc)));
		session.lastWords(0, "Toggle mode works.");
		context.getInput().pressKey(TALK_KEY);
		waitUntil(context, "a second tap turns it off", mc -> session.finished);
		waitUntil(context, "the final words replace the draft words", mc -> "Toggle mode works.".equals(chatValue(mc)));

		// Alt used to type a symbol in the chat box doesn't turn it on.
		int sessions = devices.sessions.size();
		context.getInput().holdKey(TALK_KEY);
		context.waitTick();
		context.getInput().pressKey(GLFW.GLFW_KEY_9);
		context.getInput().releaseKey(TALK_KEY);
		waitMillis(context, 300);
		check(devices.sessions.size() == sessions, "typing a symbol doesn't turn the microphone on");
		closeChatWithoutSending(context);
		setConfig(context, config -> config.activation = SttConfig.ActivationMode.HOLD);
	}

	private void autoSendAfterSilence(ClientGameTestContext context) {
		setConfig(context, config -> {
			config.sendMode = SttConfig.SendMode.AUTO;
			config.autoSendSilenceSeconds = 0.5;
		});
		FakeDevices.FakeSession session = startTalking(context);
		session.say(0, "on my");
		session.end(0, "On my way.");
		waitUntil(context, "the message is sent after a short silence, while still talking",
				mc -> SERVER_CHAT.contains("On my way."));
		check(context.computeOnClient(mc -> mc.gui.screen() == null), "auto-send doesn't open the chat box");
		context.getInput().releaseKey(TALK_KEY);
		waitUntil(context, "the connection closes", mc -> session.isClosed());
		context.waitTicks(5);
		check(SERVER_CHAT.stream().filter("On my way."::equals).count() == 1, "the message is sent once");
	}

	private void autoSendNeverSendsCommands(ClientGameTestContext context) {
		FakeDevices.FakeSession session = startTalking(context);
		session.lastWords(0, "/time set night");
		context.getInput().releaseKey(TALK_KEY);
		waitUntil(context, "a command is handed to the player instead",
				mc -> "/time set night".equals(chatValue(mc)));
		check(noticeContainsKey(context, "advanced_stt.notice.not_sent.command"), "the player is told why");
		context.takeScreenshot("command-not-sent");
		context.waitTicks(40);
		check("/time set night".equals(context.computeOnClient(AdvancedSttGameTest::chatValue)),
				"the command waits in the chat box");
		closeChatWithoutSending(context);
		setConfig(context, config -> config.sendMode = SttConfig.SendMode.MANUAL);
		check(session.isClosed(), "the connection is closed");
	}

	private void serviceRefusesTheKey(ClientGameTestContext context) {
		FakeDevices.FakeSession session = startTalking(context);
		session.fail(new SttError(SttError.Kind.INVALID_KEY, "HTTP 401 for " + FAKE_KEY));
		waitUntil(context, "the error is shown in the chat", mc -> chatHas(mc, "Cartesia Ink-2 refused the API key")
				&& dictation().phase() == DictationController.Phase.IDLE);
		check(!context.computeOnClient(mc -> chatHas(mc, FAKE_KEY)), "the key never appears in the chat");
		waitUntil(context, "the microphone is off", mc -> allMicrophonesOff());
		context.takeScreenshot("service-refused-key");
		context.getInput().releaseKey(TALK_KEY);
		context.waitTicks(2);
	}

	private void microphoneProblem(ClientGameTestContext context) {
		devices.micBroken = true;
		int sessions = devices.sessions.size();
		context.getInput().holdKey(TALK_KEY);
		waitUntil(context, "the microphone problem is shown",
				mc -> chatHas(mc, "Microphone problem: No microphone could be opened."));
		context.waitTicks(10);
		check(context.computeOnClient(mc -> dictation().phase()) == DictationController.Phase.IDLE, "nothing is running");
		check(devices.sessions.stream().skip(sessions).allMatch(FakeDevices.FakeSession::isClosed),
				"no connection stays open without a microphone");
		context.getInput().releaseKey(TALK_KEY);
		devices.micBroken = false;
		context.waitTicks(2);
	}

	private void pauseMenuTurnsTheMicrophoneOff(ClientGameTestContext context) {
		FakeDevices.FakeSession session = startTalking(context);
		session.say(0, "before the pause");
		waitUntil(context, "the words show", mc -> dictation().pendingText().equals("before the pause"));
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitForScreen(PauseScreen.class);
		waitUntil(context, "the pause menu ends the connection", mc -> session.finished);
		context.getInput().releaseKey(TALK_KEY);
		context.setScreen(() -> null);
		waitUntil(context, "the words wait in the chat box", mc -> "before the pause".equals(chatValue(mc)));
		closeChatWithoutSending(context);
	}

	private void timeLimitTurnsTheMicrophoneOff(ClientGameTestContext context) {
		int limit = context.computeOnClient(mc -> AdvancedStt.get().config().get().maxSessionSeconds);
		setConfig(context, config -> config.maxSessionSeconds = 1); // the settings screen allows 15 s minimum
		FakeDevices.FakeSession session = startTalking(context);
		waitUntil(context, "the time limit ends the connection", mc -> session.finished);
		check(noticeContainsKey(context, "advanced_stt.notice.time_limit"), "the player is told why");
		context.takeScreenshot("time-limit");
		context.getInput().releaseKey(TALK_KEY);
		context.waitTicks(2);
		check(devices.sessions.size() == devices.sessions.indexOf(session) + 1, "holding on doesn't reconnect");
		setConfig(context, config -> config.maxSessionSeconds = limit);
	}

	// ---- Helpers ----

	/** Holds the talk key until the fake service is connected and listening. */
	private FakeDevices.FakeSession startTalking(ClientGameTestContext context) {
		int before = devices.sessions.size();
		context.getInput().holdKey(TALK_KEY);
		waitUntil(context, "a connection opens", mc -> devices.sessions.size() > before);
		FakeDevices.FakeSession session = devices.sessions.get(before);
		session.open();
		waitUntil(context, "the microphone indicator shows",
				mc -> dictation().phase() == DictationController.Phase.LISTENING);
		return session;
	}

	private FakeDevices.FakeSession last() {
		return devices.sessions.get(devices.sessions.size() - 1);
	}

	private boolean allMicrophonesOff() {
		return devices.mics.stream().allMatch(mic -> !mic.started || mic.stopped);
	}

	private static void closeChatWithoutSending(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof ChatScreen chat) {
				((ChatScreenAccessor) chat).advancedStt$getInput().setValue(""); // no draft left behind
			}
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		context.waitFor(mc -> mc.gui.screen() == null);
	}

	private static DictationController dictation() {
		return AdvancedStt.get().dictation();
	}

	private static String chatValue(Minecraft mc) {
		return mc.gui.screen() instanceof ChatScreen chat ? ((ChatScreenAccessor) chat).advancedStt$getInput().getValue() : null;
	}

	private static boolean noticeContains(String text) {
		Component notice = dictation().notice();
		return notice != null && notice.getString().contains(text);
	}


	private static boolean noticeContainsKey(ClientGameTestContext context, String key) {
		return context.computeOnClient(mc -> {
			Component notice = dictation().notice();
			return notice != null && notice.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
					&& t.getKey().equals(key);
		});
	}

	/** Whether a line of the chat history contains this text. */
	@SuppressWarnings("unchecked")
	private static boolean chatHas(Minecraft mc, String text) {
		try {
			java.lang.reflect.Field field = ChatComponent.class.getDeclaredField("allMessages");
			field.setAccessible(true);
			List<GuiMessage> messages = (List<GuiMessage>) field.get(mc.gui.hud.getChat());
			return messages.stream().anyMatch(message -> message.content().getString().contains(text));
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Can't read the chat history", e);
		}
	}

	/** Lets the game run for a while in real time (timers in the mod use the clock, not ticks). */
	private static void waitMillis(ClientGameTestContext context, long millis) {
		long until = net.minecraft.util.Util.getMillis() + millis;
		context.waitFor(mc -> net.minecraft.util.Util.getMillis() >= until, TIMEOUT_TICKS);
	}

	private static void setConfig(ClientGameTestContext context, java.util.function.Consumer<SttConfig> change) {
		context.runOnClient(mc -> change.accept(AdvancedStt.get().config().get()));
	}

	private static void waitUntil(ClientGameTestContext context, String what, Predicate<Minecraft> condition) {
		try {
			context.waitFor(condition, TIMEOUT_TICKS);
		} catch (Throwable e) {
			String state = context.computeOnClient(mc -> "phase=" + dictation().phase() + ", text='"
					+ dictation().pendingText() + "', chat=" + chatValue(mc) + ", screen=" + mc.gui.screen()
					+ ", notice=" + (dictation().notice() == null ? null : dictation().notice().getString()));
			throw new AssertionError("Timed out waiting until " + what + " (" + state + ")", e);
		}
	}

	private static void check(boolean condition, String what) {
		if (!condition) {
			throw new AssertionError("Expected: " + what);
		}
	}

	/** A text box, found by its (narrated) title. */
	private static Predicate<GuiEventListener> editBox(String titleKey) {
		return w -> w instanceof EditBox box
				&& box.getMessage().getString().equals(Component.translatable(titleKey).getString());
	}

	/** Presses a setting button such as "Service: Cartesia Ink-2" once, which moves it to its next value. */
	private static void pressSetting(ClientGameTestContext context, String captionKey) {
		context.runOnClient(mc -> {
			String caption = Component.translatable(captionKey).getString() + ": ";
			AbstractButton button = (AbstractButton) find(mc.gui.screen(),
					w -> w instanceof AbstractButton b && b.getMessage().getString().startsWith(caption))
					.orElseThrow(() -> new AssertionError("No '" + caption + "...' button on " + mc.gui.screen()));
			button.onPress(new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
		});
		context.waitTick();
	}

	/** Presses the button with this label, wherever it is in the screen's lists. */
	private static void press(ClientGameTestContext context, String translationKey) {
		context.runOnClient(mc -> {
			String label = Component.translatable(translationKey).getString();
			AbstractButton button = (AbstractButton) find(mc.gui.screen(),
					w -> w instanceof AbstractButton b && b.getMessage().getString().equals(label))
					.orElseThrow(() -> new AssertionError("No button '" + label + "' on " + mc.gui.screen()));
			check(button.active, "the button '" + label + "' can be pressed");
			button.onPress(new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
		});
		context.waitTick();
	}

	/** Clicks the middle of the first widget on screen that matches. */
	private static void click(ClientGameTestContext context, Predicate<GuiEventListener> which) {
		double[] at = context.computeOnClient(mc -> {
			AbstractWidget widget = (AbstractWidget) find(mc.gui.screen(), which)
					.orElseThrow(() -> new AssertionError("No such widget on " + mc.gui.screen()));
			double scale = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
			return new double[]{(widget.getX() + widget.getWidth() / 2.0) * scale,
					(widget.getY() + widget.getHeight() / 2.0) * scale};
		});
		context.getInput().setCursorPos(at[0], at[1]);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		context.waitTick();
	}

	private static java.util.Optional<GuiEventListener> find(GuiEventListener root, Predicate<GuiEventListener> which) {
		if (root == null) {
			return java.util.Optional.empty();
		}
		if (which.test(root)) {
			return java.util.Optional.of(root);
		}
		if (root instanceof ContainerEventHandler container) {
			for (GuiEventListener child : container.children()) {
				java.util.Optional<GuiEventListener> found = find(child, which);
				if (found.isPresent()) {
					return found;
				}
			}
		}
		return java.util.Optional.empty();
	}

	private static void scrollToBottom(ClientGameTestContext context) {
		double[] middle = context.computeOnClient(mc -> new double[]{mc.getWindow().getScreenWidth() / 2.0,
				mc.getWindow().getScreenHeight() / 2.0});
		context.getInput().setCursorPos(middle[0], middle[1]);
		context.getInput().scroll(-50);
		context.waitTicks(2);
	}

	/** Refuses to run against a developer's real key folder. */
	private static void requireThrowawayKeyFolder() {
		String folder = System.getProperty("advanced_stt.testPrivateFolder");
		if (folder == null || folder.isBlank()) {
			throw new AssertionError("Run the in-game tests with ./gradlew runClientGameTest, which keeps test keys"
					+ " away from your real ones.");
		}
		try {
			Files.createDirectories(Path.of(folder));
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

}
