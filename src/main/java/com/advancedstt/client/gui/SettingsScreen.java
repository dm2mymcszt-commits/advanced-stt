package com.advancedstt.client.gui;

import com.advancedstt.Provider;
import com.advancedstt.audio.MicCapture;
import com.advancedstt.client.AdvancedStt;
import com.advancedstt.client.KeyTester;
import com.advancedstt.client.Keys;
import com.advancedstt.client.Messages;
import com.advancedstt.config.SttConfig;
import com.advancedstt.secure.ApiKeys;
import com.advancedstt.secure.CredentialStore;
import com.advancedstt.text.Keyterms;
import com.advancedstt.usage.UsageFormat;
import com.advancedstt.usage.UsageTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;

/** The settings screen: service and key, microphone, sending, words to listen for, usage. */
public final class SettingsScreen extends OptionsSubScreen {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int MUTED = 0xFFA0A0A0;
	private static final int GOOD = 0xFF55FF55;
	private static final int WARN = 0xFFFFFF55;
	private static final int BAD = 0xFFFF5555;
	private static final int FULL = 310;
	private static final int HALF = 150;

	private final AdvancedStt mod;
	private final SttConfig config;
	private final KeyTester tester = new KeyTester();

	private SecretEditBox keyBox;
	private Button removeKeyButton;
	private InfoLine keyStatus;
	private InfoLine actionStatus;
	private ValueSlider silenceSlider;
	private InfoLine sendInfo;
	private EditBox keytermsBox;
	private UsageGauge gauge;
	private InfoLine usageLine;
	private InfoLine usageNote;
	private InfoLine languageInfo;
	private EditBox balanceBox;
	private Button clearBalanceButton;
	private boolean keytermsEdited;
	/** Shown in the action line until something else replaces it. */
	private Component actionMessage = Component.empty();
	private int actionColor = MUTED;

	public SettingsScreen(Screen parent) {
		super(parent, Minecraft.getInstance().options, Component.translatable("advanced_stt.settings.title"));
		this.mod = AdvancedStt.get();
		this.config = mod.config().get();
	}

	@Override
	protected void addOptions() {
		addServiceRows();
		addMicrophoneRows();
		addSendingRows();
		addWordRows();
		addUsageRows();
		list.addHeader(Component.translatable("advanced_stt.settings.section.language"));
		languageInfo = new InfoLine(Component.empty(), MUTED);
		list.addBig(languageInfo);
		refresh();
	}

	private void addServiceRows() {
		list.addHeader(Component.translatable("advanced_stt.settings.section.service"));
		CycleButton<Provider> provider = CycleButton.builder(SettingsScreen::providerName, config.provider)
				.withValues(Provider.values())
				.create(Component.translatable("advanced_stt.settings.provider"), (button, value) -> {
					config.provider = value;
					saveConfig();
					tester.cancel();
					keyBox.setValue("");
					setAction(Component.empty(), MUTED);
					refresh();
				});
		list.addBig(provider);

		keyBox = new SecretEditBox(font, FULL, Component.translatable("advanced_stt.settings.key"));
		list.addBig(keyBox);

		Button saveKey = Button.builder(Component.translatable("advanced_stt.settings.key.save"), b -> saveKey())
				.width(HALF).build();
		removeKeyButton = Button.builder(Component.translatable("advanced_stt.settings.key.remove"), b -> removeKey())
				.width(HALF).build();
		list.addSmall(saveKey, removeKeyButton);

		keyStatus = new InfoLine(Component.empty(), WHITE);
		list.addBig(keyStatus);

		Button test = Button.builder(Component.translatable("advanced_stt.settings.key.test"), b -> testKey())
				.width(HALF)
				.tooltip(Tooltip.create(Component.translatable("advanced_stt.settings.key.test.tooltip")))
				.build();
		Button getKey = Button.builder(Component.translatable("advanced_stt.settings.key.get"),
				b -> openLink(config.provider.keysUrl())).width(HALF).build();
		list.addSmall(test, getKey);

		actionStatus = new InfoLine(Component.empty(), MUTED);
		list.addBig(actionStatus);
	}

	private void addMicrophoneRows() {
		list.addHeader(Component.translatable("advanced_stt.settings.section.microphone"));
		List<String> microphones = new ArrayList<>();
		microphones.add("");
		microphones.addAll(MicCapture.listDevices());
		if (!microphones.contains(config.microphone)) {
			microphones.add(config.microphone); // unplugged right now, but keep the choice
		}
		CycleButton<String> microphone = CycleButton.builder(SettingsScreen::microphoneName, config.microphone)
				.withValues(microphones)
				.create(Component.translatable("advanced_stt.settings.microphone"), (button, value) -> {
					config.microphone = value;
					saveConfig();
				});
		list.addBig(microphone);

		CycleButton<SttConfig.ActivationMode> activation = CycleButton
				.builder((SttConfig.ActivationMode v) -> Component.translatable("advanced_stt.settings.activation." + lower(v)),
						config.activation)
				.withValues(SttConfig.ActivationMode.values())
				.withTooltip(v -> Tooltip.create(
						Component.translatable("advanced_stt.settings.activation." + lower(v) + ".tooltip")))
				.create(Component.translatable("advanced_stt.settings.activation"), (button, value) -> {
					config.activation = value;
					saveConfig();
				});
		CycleButton<SttConfig.Indicator> indicator = CycleButton
				.builder((SttConfig.Indicator v) -> Component.translatable("advanced_stt.settings.indicator." + lower(v)),
						config.indicator)
				.withValues(SttConfig.Indicator.values())
				.create(Component.translatable("advanced_stt.settings.indicator"), (button, value) -> {
					config.indicator = value;
					saveConfig();
				});
		list.addSmall(activation, indicator);

		ValueSlider maxSession = new ValueSlider(FULL, SttConfig.MIN_SESSION_SECONDS, SttConfig.MAX_SESSION_SECONDS, 15,
				config.maxSessionSeconds,
				v -> Component.translatable("advanced_stt.settings.max_session", UsageFormat.duration(v)),
				v -> {
					config.maxSessionSeconds = (int) Math.round(v);
					saveConfig();
				});
		maxSession.setTooltip(Tooltip.create(Component.translatable("advanced_stt.settings.max_session.tooltip")));
		list.addBig(maxSession);

		Button talkKey = Button.builder(
				Component.translatable("advanced_stt.settings.talk_key", Keys.TALK.getTranslatedKeyMessage()),
				b -> minecraft.gui.setScreen(new KeyBindsScreen(this, options))).width(FULL).build();
		list.addBig(talkKey);
	}

	private void addSendingRows() {
		list.addHeader(Component.translatable("advanced_stt.settings.section.sending"));
		CycleButton<SttConfig.SendMode> sendMode = CycleButton
				.builder((SttConfig.SendMode v) -> Component.translatable("advanced_stt.settings.send." + lower(v)),
						config.sendMode)
				.withValues(SttConfig.SendMode.values())
				.create(Component.translatable("advanced_stt.settings.send"), (button, value) -> {
					config.sendMode = value;
					saveConfig();
					refresh();
				});
		silenceSlider = new ValueSlider(HALF, SttConfig.MIN_SILENCE_SECONDS, SttConfig.MAX_SILENCE_SECONDS, 0.1,
				config.autoSendSilenceSeconds,
				v -> Component.translatable("advanced_stt.settings.silence", local(String.format(Locale.ROOT, "%.1f", v))),
				v -> {
					config.autoSendSilenceSeconds = v;
					saveConfig();
				});
		silenceSlider.setTooltip(Tooltip.create(Component.translatable("advanced_stt.settings.silence.tooltip")));
		list.addSmall(sendMode, silenceSlider);
		sendInfo = new InfoLine(Component.empty(), MUTED);
		list.addBig(sendInfo);
	}

	private void addWordRows() {
		list.addHeader(Component.translatable("advanced_stt.settings.section.words"));
		keytermsBox = new EditBox(font, 0, 0, FULL, 20, Component.translatable("advanced_stt.settings.words"));
		keytermsBox.setMaxLength(4000);
		keytermsBox.setValue(Keyterms.format(config.keyterms));
		keytermsBox.setHint(Component.translatable("advanced_stt.settings.words.hint"));
		keytermsBox.setTooltip(Tooltip.create(Component.translatable("advanced_stt.settings.words.tooltip")));
		keytermsBox.setResponder(value -> keytermsEdited = true);
		list.addBig(keytermsBox);
	}

	private void addUsageRows() {
		list.addHeader(Component.translatable("advanced_stt.settings.section.usage"));
		gauge = new UsageGauge();
		list.addBig(gauge);
		usageLine = new InfoLine(Component.empty(), WHITE);
		list.addBig(usageLine);

		balanceBox = new EditBox(font, 0, 0, HALF, 20, Component.translatable("advanced_stt.settings.balance"));
		balanceBox.setMaxLength(20);
		Button setBalance = Button.builder(Component.translatable("advanced_stt.settings.balance.set"), b -> setBalance())
				.width(HALF).build();
		list.addSmall(balanceBox, setBalance);

		clearBalanceButton = Button.builder(Component.translatable("advanced_stt.settings.balance.clear"),
				b -> clearBalance()).width(HALF).build();
		Button dashboard = Button.builder(Component.translatable("advanced_stt.settings.dashboard"),
				b -> openLink(config.provider.dashboardUrl())).width(HALF).build();
		list.addSmall(clearBalanceButton, dashboard);

		usageNote = new InfoLine(Component.empty(), MUTED);
		list.addBig(usageNote);
	}

	// ---- Actions ----

	private void saveKey() {
		String key = keyBox.getValue().strip();
		if (key.isEmpty()) {
			setAction(Component.translatable("advanced_stt.settings.key.empty"), WARN);
			return;
		}
		if (!ApiKeys.looksValid(key)) {
			setAction(Component.translatable("advanced_stt.settings.key.invalid"), BAD);
			return;
		}
		try {
			mod.credentials().put(config.provider, key);
			keyBox.setValue("");
			tester.cancel();
			setAction(Component.translatable("advanced_stt.settings.key.saved_now"), GOOD);
		} catch (IOException | RuntimeException e) {
			setAction(Component.translatable("advanced_stt.settings.key.save_failed",
					Messages.shorten(String.valueOf(e.getMessage()))), BAD);
		}
		refresh();
	}

	private void removeKey() {
		try {
			mod.credentials().remove(config.provider);
			tester.cancel();
			setAction(Component.translatable("advanced_stt.settings.key.removed"), WHITE);
		} catch (IOException | RuntimeException e) {
			setAction(Component.translatable("advanced_stt.settings.key.save_failed",
					Messages.shorten(String.valueOf(e.getMessage()))), BAD);
		}
		refresh();
	}

	/** Tests the key typed in the box, or the saved key if the box is empty. */
	private void testKey() {
		String typed = keyBox.getValue().strip();
		Optional<String> key = typed.isEmpty() ? savedKey(config.provider) : Optional.of(typed);
		if (key.isEmpty()) {
			setAction(Component.translatable("advanced_stt.settings.key.none", config.provider.displayName()), WARN);
			return;
		}
		if (!ApiKeys.looksValid(key.get())) {
			setAction(Component.translatable("advanced_stt.settings.key.invalid"), BAD);
			return;
		}
		setAction(Component.empty(), MUTED);
		tester.test(config.provider, key.get());
	}

	private void setBalance() {
		OptionalDouble amount = UsageFormat.parseAmount(config.provider, balanceBox.getValue());
		if (amount.isEmpty()) {
			setAction(Component.translatable("advanced_stt.settings.balance.invalid",
					config.provider == Provider.CARTESIA ? "100000" : local("25.50")), BAD);
			return;
		}
		mod.usage().setStartingBalance(config.provider, amount.getAsDouble(), System.currentTimeMillis());
		saveUsage();
		balanceBox.setValue("");
		setAction(Component.translatable("advanced_stt.settings.balance.saved"), GOOD);
		refresh();
	}

	private void clearBalance() {
		mod.usage().clearStartingBalance(config.provider);
		saveUsage();
		setAction(Component.translatable("advanced_stt.settings.balance.cleared"), WHITE);
		refresh();
	}

	private void openLink(String url) {
		ConfirmLinkScreen.confirmLinkNow(this, url);
	}

	// ---- Screen ----

	@Override
	public void tick() {
		super.tick();
		if (tester.state() != KeyTester.State.IDLE) {
			int color = switch (tester.state()) {
				case OK -> GOOD;
				case FAILED -> BAD;
				default -> WARN;
			};
			actionStatus.set(tester.message(), color);
		}
	}

	@Override
	public void removed() {
		saveKeyterms();
		super.removed();
	}

	@Override
	public void onClose() {
		tester.cancel();
		saveKeyterms();
		super.onClose();
	}

	private void refresh() {
		Provider provider = config.provider;
		boolean hasKey = savedKey(provider).isPresent();
		keyBox.setHint(Component.translatable(hasKey ? "advanced_stt.settings.key.hint_replace"
				: "advanced_stt.settings.key.hint_new"));
		removeKeyButton.active = hasKey;
		if (hasKey) {
			String where = mod.credentials().protection() == CredentialStore.Protection.WINDOWS_ACCOUNT
					? "advanced_stt.settings.key.saved.windows" : "advanced_stt.settings.key.saved.file";
			keyStatus.set(Component.translatable(where), GOOD);
		} else {
			keyStatus.set(Component.translatable("advanced_stt.settings.key.none", provider.displayName()), WARN);
		}
		keyStatus.setTooltip(Tooltip.create(Component.translatable("advanced_stt.settings.key.privacy")));
		actionStatus.set(actionMessage, actionColor);

		silenceSlider.active = config.sendMode == SttConfig.SendMode.AUTO;
		sendInfo.set(Component.translatable("advanced_stt.settings.send." + lower(config.sendMode) + ".info"), MUTED);

		UsageTracker usage = mod.usage();
		UsageTracker.ProviderUsage used = usage.get(provider);
		OptionalDouble remaining = usage.estimatedRemaining(provider);
		if (remaining.isPresent()) {
			double fraction = used.startingBalance > 0 ? remaining.getAsDouble() / used.startingBalance : 0;
			gauge.set(Component.translatable("advanced_stt.settings.usage.remaining",
					amount(provider, remaining.getAsDouble()), amount(provider, used.startingBalance)), fraction);
		} else {
			gauge.set(Component.translatable("advanced_stt.settings.usage.no_balance"), -1);
		}
		clearBalanceButton.active = remaining.isPresent();
		usageLine.set(Component.translatable("advanced_stt.settings.usage.total", amount(provider, used.totalUsed),
				used.sessions, UsageFormat.duration(used.totalSeconds)), WHITE);
		balanceBox.setHint(Component.translatable("advanced_stt.settings.balance.hint." + provider.id()));
		usageNote.set(provider == Provider.CARTESIA
				? Component.translatable("advanced_stt.settings.usage.note.cartesia",
						local(UsageFormat.plain(config.cartesiaCreditsPerSecond)))
				: Component.translatable("advanced_stt.settings.usage.note.assemblyai",
						local(UsageFormat.plain(config.assemblyAiDollarsPerHour))), MUTED);
		usageNote.setTooltip(Tooltip.create(Component.translatable("advanced_stt.settings.usage.note.tooltip")));

		// Ink-2 was tested by the owner: fine in English, weak in French, and mixed sentences fail.
		languageInfo.set(Component.translatable("advanced_stt.settings.language." + provider.id()),
				provider == Provider.CARTESIA ? WARN : MUTED);
	}

	private void setAction(Component message, int color) {
		actionMessage = message;
		actionColor = color;
		if (actionStatus != null) {
			actionStatus.set(message, color);
		}
	}

	private void saveKeyterms() {
		if (!keytermsEdited || keytermsBox == null) {
			return;
		}
		keytermsEdited = false;
		config.keyterms = new ArrayList<>(Keyterms.parse(keytermsBox.getValue()));
		saveConfig();
	}

	private void saveConfig() {
		try {
			mod.config().save();
		} catch (IOException e) {
			AdvancedStt.LOG.warn("Could not save the settings: {}", e.toString());
		}
	}

	private void saveUsage() {
		try {
			mod.usage().save();
		} catch (IOException e) {
			AdvancedStt.LOG.warn("Could not save the usage estimate: {}", e.toString());
		}
	}

	private Optional<String> savedKey(Provider provider) {
		try {
			return mod.credentials().get(provider);
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	private static Component amount(Provider provider, double value) {
		String number = local(UsageFormat.number(provider, value));
		return provider == Provider.CARTESIA
				? Component.translatable("advanced_stt.unit.credits", number)
				: Component.literal(number);
	}

	/** Numbers written the way the game's language writes them. */
	private static String local(String number) {
		return UsageFormat.localize(number, Minecraft.getInstance().options.languageCode);
	}

	private static Component providerName(Provider provider) {
		return Component.literal(provider.displayName());
	}

	private static Component microphoneName(String device) {
		return device == null || device.isEmpty()
				? Component.translatable("advanced_stt.settings.microphone.default")
				: Component.literal(device);
	}

	private static String lower(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}
}
