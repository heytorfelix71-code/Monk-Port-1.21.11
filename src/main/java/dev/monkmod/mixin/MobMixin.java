package dev.monkmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import dev.monkmod.data.MonkAttachments;

@Mixin(Mob.class)
public abstract class MobMixin {
	/** A kissed creeper never picks a target again (so it never fuses). */
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void monk$calmed(LivingEntity target, CallbackInfo ci) {
		if (target != null && MonkAttachments.isCalmed((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
