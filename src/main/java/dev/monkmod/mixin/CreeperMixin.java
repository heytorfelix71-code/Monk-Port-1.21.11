package dev.monkmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;

import dev.monkmod.data.MonkAttachments;
import dev.monkmod.logic.MonkHooks;

/**
 * "Gentle plea": a creeper that was asked not to explode never explodes, and creepers ignore monks that
 * have the ability (passive option). Covers every path: targeting, swelling and the explosion itself.
 */
@Mixin(Creeper.class)
public abstract class CreeperMixin {
	@Shadow
	private int swell;

	@Shadow
	public abstract void setSwellDir(int dir);

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void monk$noTarget(LivingEntity target, CallbackInfo ci) {
		Creeper self = (Creeper) (Object) this;
		if (target != null && (MonkAttachments.isCalmed(self) || MonkHooks.creeperIgnores(target))) {
			ci.cancel();
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void monk$calm(CallbackInfo ci) {
		Creeper self = (Creeper) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}
		LivingEntity target = self.getTarget();
		boolean calmed = MonkAttachments.isCalmed(self);
		if (calmed || (target != null && MonkHooks.creeperIgnores(target))) {
			if (target != null) {
				self.setTarget(null);
				self.getNavigation().stop();
			}
			if (calmed || !self.isIgnited()) {
				setSwellDir(-1);
			}
		}
	}

	@Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
	private void monk$noBoom(CallbackInfo ci) {
		Creeper self = (Creeper) (Object) this;
		if (MonkAttachments.isCalmed(self)) {
			swell = 0;
			setSwellDir(-1);
			ci.cancel();
		}
	}
}
