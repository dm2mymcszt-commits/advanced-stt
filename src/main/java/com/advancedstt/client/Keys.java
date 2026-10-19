package com.advancedstt.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** The mod's keys, listed under "Advanced STT" in Options > Controls > Key Binds. */
public final class Keys {
	public static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(AdvancedStt.MOD_ID, "main"));

	/** Talk key. Left Alt by default: it types nothing, so it also works while the chat box is open. */
	public static final KeyMapping TALK = new KeyMapping("key.advanced_stt.talk",
			InputConstants.Type.KEYSYM, InputConstants.KEY_LALT, CATEGORY);

	/** Opens the settings. Not bound by default. */
	public static final KeyMapping OPEN_SETTINGS = new KeyMapping("key.advanced_stt.settings",
			InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);

	private Keys() {
	}

	static void register() {
		KeyMappingHelper.registerKeyMapping(TALK);
		KeyMappingHelper.registerKeyMapping(OPEN_SETTINGS);
	}

	/**
	 * Whether the talk key is physically held right now. Read straight from the keyboard or mouse,
	 * because Minecraft stops tracking key bindings while the chat box is open.
	 */
	static boolean talkKeyHeld(Minecraft mc) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(TALK);
		if (key == null || key.equals(InputConstants.UNKNOWN) || key.getValue() < 0) {
			return false;
		}
		return switch (key.getType()) {
			case KEYSYM -> InputConstants.isKeyDown(mc.getWindow(), key.getValue());
			case MOUSE -> GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
			case SCANCODE -> false;
		};
	}

	/** Whether a keyboard key is the talk key (it repeats while held). */
	static boolean isTalkKey(int keyCode) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(TALK);
		return key != null && key.getType() == InputConstants.Type.KEYSYM && key.getValue() == keyCode;
	}

	/** The talk key's name as shown in the controls screen, for messages. */
	static net.minecraft.network.chat.Component talkKeyName() {
		return TALK.getTranslatedKeyMessage();
	}
}
