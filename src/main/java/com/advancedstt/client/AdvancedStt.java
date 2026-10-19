package com.advancedstt.client;

import com.advancedstt.config.ConfigStore;
import com.advancedstt.config.Folders;
import com.advancedstt.secure.CredentialStore;
import com.advancedstt.secure.CredentialStores;
import com.advancedstt.usage.UsageTracker;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** Holds the mod's parts and wires them into the game. */
public final class AdvancedStt {
	public static final String MOD_ID = "advanced_stt";
	public static final Logger LOG = LoggerFactory.getLogger("Advanced STT");

	private static AdvancedStt instance;

	private final ConfigStore config;
	private final CredentialStore credentials;
	private final UsageTracker usage;
	private final DictationController dictation;

	private AdvancedStt(ConfigStore config, CredentialStore credentials, UsageTracker usage) {
		this.config = config;
		this.credentials = credentials;
		this.usage = usage;
		this.dictation = new DictationController(config, credentials, usage);
	}

	public static void init() {
		if (instance != null) {
			return;
		}
		Path settings = FabricLoader.getInstance().getConfigDir().resolve("advanced-stt.json");
		Path privateFolder = privateFolder();
		ConfigStore config = new ConfigStore(settings);
		config.load();
		UsageTracker usage = new UsageTracker(privateFolder.resolve("usage.json")).load();
		CredentialStore credentials = CredentialStores.create(privateFolder);
		instance = new AdvancedStt(config, credentials, usage);
		instance.register();
		LOG.info("Advanced STT ready. API keys are stored {}, outside the game folder.",
				credentials.protection() == CredentialStore.Protection.WINDOWS_ACCOUNT
						? "encrypted for this Windows account" : "encrypted in the user's settings folder");
	}

	/**
	 * The folder for keys and usage. The in-game tests point it at a throwaway folder so they never
	 * touch a developer's real keys; that setting is ignored outside a development environment.
	 */
	private static Path privateFolder() {
		String testFolder = System.getProperty("advanced_stt.testPrivateFolder");
		if (testFolder != null && !testFolder.isBlank() && FabricLoader.getInstance().isDevelopmentEnvironment()) {
			return Path.of(testFolder);
		}
		return Folders.privateFolder();
	}

	public static AdvancedStt get() {
		return instance;
	}

	public ConfigStore config() {
		return config;
	}

	public CredentialStore credentials() {
		return credentials;
	}

	public UsageTracker usage() {
		return usage;
	}

	public DictationController dictation() {
		return dictation;
	}

	private void register() {
		Keys.register();
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "dictation"), new HudOverlay(this));
		ClientTickEvents.END_CLIENT_TICK.register(dictation::tick);
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ChatScreen) {
				ScreenKeyboardEvents.beforeKeyPress(screen).register((chat, key) ->
						dictation.keyPressedInChat(client, key.key()));
				ScreenKeyboardEvents.afterKeyRelease(screen).register((chat, key) ->
						dictation.keyReleasedInChat(key.key()));
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> dictation.shutdown());
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
				dispatcher.register(ClientCommands.literal("astt").executes(command -> {
					// The chat screen closes right after a command runs, so open the settings on the next tick.
					dictation.openSettingsSoon();
					return 1;
				})));
	}
}
