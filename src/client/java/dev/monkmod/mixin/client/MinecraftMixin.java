package dev.monkmod.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import dev.monkmod.client.MobSense;

/** Mob Sense (outline style): the mobs it shows get the game's glowing outline, which is drawn through walls. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true, require = 0)
	private void monk$mobSenseGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && MobSense.glows(entity)) {
			cir.setReturnValue(true);
		}
	}
}
