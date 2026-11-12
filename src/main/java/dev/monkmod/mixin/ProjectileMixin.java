package dev.monkmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import dev.monkmod.logic.MonkProjectiles;

/** Catch / dodge thrown things (arrows, tridents, snowballs, pearls, rockets...) before they hit a monk. */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {
	@Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
	private void monk$catch(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> cir) {
		if (hit instanceof EntityHitResult entityHit && MonkProjectiles.intercept((Projectile) (Object) this, entityHit.getEntity())) {
			cir.setReturnValue(ProjectileDeflection.NONE);
		}
	}
}
