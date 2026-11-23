package dev.monkmod.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;

import dev.monkmod.client.MobSense;

/** Mob Sense (outline style): the color of the outline (red hostile, blue passive, or what the player picked). */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true, require = 0)
	private void monk$mobSenseColor(CallbackInfoReturnable<Integer> cir) {
		Integer color = MobSense.color((Entity) (Object) this);
		if (color != null) {
			cir.setReturnValue(color);
		}
	}
}
