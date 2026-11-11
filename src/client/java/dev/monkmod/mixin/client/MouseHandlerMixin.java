package dev.monkmod.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MouseHandler;

import dev.monkmod.client.MonkControls;

/** Mouse wheel + modifier key (Blink key / Ctrl / Space) adjusts monk abilities instead of the hotbar. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void monk$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (MonkControls.onScroll(yOffset)) {
			ci.cancel();
		}
	}
}
