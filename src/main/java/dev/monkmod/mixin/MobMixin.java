package dev.monkmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import net.minecraft.world.entity.monster.Creeper;

import dev.monkmod.data.MonkAttachments;
import dev.monkmod.logic.MonkHooks;

@Mixin(Mob.class)
public abstract class MobMixin implements dev.monkmod.logic.Paralyzable {
	@org.spongepowered.asm.mixin.Unique
	private long monk$paralyzedUntil;

	@Override
	public void monk$paralyze(long untilGameTime) {
		monk$paralyzedUntil = Math.max(monk$paralyzedUntil, untilGameTime);
		Mob self = (Mob) (Object) this;
		self.getNavigation().stop();
		if (self instanceof Creeper creeper) {
			creeper.setSwellDir(-1);
		}
	}

	@Override
	public boolean monk$isParalyzed() {
		return monk$paralyzedUntil != 0 && ((Mob) (Object) this).level().getGameTime() < monk$paralyzedUntil;
	}

	/** Withering stare: a paralyzed mob thinks and moves nothing (it still falls and can be pushed). */
	@Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true, require = 0)
	private void monk$paralyzed(CallbackInfo ci) {
		if (monk$isParalyzed()) {
			Mob self = (Mob) (Object) this;
			self.getNavigation().stop();
			self.setZza(0.0F);
			self.setXxa(0.0F);
			self.setYya(0.0F);
			self.setJumping(false);
			ci.cancel();
		}
	}

	/** A kissed creeper never picks a target again (so it never fuses). */
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void monk$calmed(LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (target != null && (MonkAttachments.isCalmed(self) || (self instanceof Creeper && MonkHooks.creeperIgnores(target)))) {
			ci.cancel();
		}
	}
}
