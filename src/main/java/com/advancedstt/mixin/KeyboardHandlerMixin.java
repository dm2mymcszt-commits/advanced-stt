package com.advancedstt.mixin;

import com.advancedstt.client.AdvancedStt;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Esc in game: with automatic sending, a message being dictated or waiting to be sent is cancelled
 * first, and the pause menu only opens on the next Esc. Nothing changes when nothing is waiting.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@WrapWithCondition(method = "keyPress",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pauseGame(Z)V"))
	private boolean advancedStt$escapeCancelsDictation(Minecraft minecraft, boolean pauseOnly) {
		AdvancedStt mod = AdvancedStt.get();
		return mod == null || !mod.dictation().cancelOnEscape(minecraft);
	}
}
