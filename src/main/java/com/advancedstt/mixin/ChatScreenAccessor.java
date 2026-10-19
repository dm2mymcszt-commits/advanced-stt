package com.advancedstt.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the chat box, so dictated words can be written into it. Changes no game code. */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
	@Accessor("input")
	EditBox advancedStt$getInput();
}
