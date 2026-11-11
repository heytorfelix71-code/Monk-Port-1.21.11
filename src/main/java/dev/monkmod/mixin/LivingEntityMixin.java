package dev.monkmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;

import dev.monkmod.logic.MonkHooks;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** Water walking reuses the vanilla strider mechanic. */
	@Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
	private void monk$walkOnWater(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
		if (MonkHooks.canWalkOnWater((LivingEntity) (Object) this, fluid)) {
			cir.setReturnValue(true);
		}
	}

	/** Fire / explosion / lava protection multiply the incoming damage before anything else. */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float monk$protect(float amount, ServerLevel level, DamageSource source) {
		return MonkHooks.modifyIncomingDamage((LivingEntity) (Object) this, source, amount);
	}
}
