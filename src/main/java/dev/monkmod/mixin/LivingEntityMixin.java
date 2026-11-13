package dev.monkmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;

import dev.monkmod.logic.MonkHooks;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** Fire / explosion / lava protection multiply the incoming damage before anything else. */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float monk$protect(float amount, ServerLevel level, DamageSource source) {
		return MonkHooks.modifyIncomingDamage((LivingEntity) (Object) this, source, amount);
	}

	/** Dragon breath / creeper explosions listed in the toxin immunity are ignored completely. */
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void monk$immune(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (MonkHooks.cancelDamage((LivingEntity) (Object) this, source)) {
			cir.setReturnValue(false);
		}
	}

	/** Toxin immunity: listed effects are refused before they are applied. */
	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void monk$refuseEffect(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		if (MonkHooks.blocksEffect((LivingEntity) (Object) this, effect.getEffect())) {
			cir.setReturnValue(false);
		}
	}

	/** Infinite air for the water breathing abilities (no bubbles lost, no flicker). */
	@Inject(method = "decreaseAirSupply", at = @At("HEAD"), cancellable = true)
	private void monk$infiniteAir(int air, CallbackInfoReturnable<Integer> cir) {
		if (MonkHooks.infiniteAir((LivingEntity) (Object) this)) {
			cir.setReturnValue(air);
		}
	}

	/** Higher jumps together with the running bonus. */
	@Inject(method = "getJumpPower()F", at = @At("RETURN"), cancellable = true)
	private void monk$jump(CallbackInfoReturnable<Float> cir) {
		float vanilla = cir.getReturnValueF();
		float modified = MonkHooks.modifyJumpPower((LivingEntity) (Object) this, vanilla);
		if (modified != vanilla) {
			cir.setReturnValue(modified);
		}
	}

	/** No fall damage from your own super jumps. */
	@ModifyVariable(method = "calculateFallDamage", at = @At("HEAD"), argsOnly = true)
	private double monk$safeJump(double distance) {
		return MonkHooks.modifyFallDistance((LivingEntity) (Object) this, distance);
	}
}
