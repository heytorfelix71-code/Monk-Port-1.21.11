package dev.monkmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.logic.MonkHooks;

/** Magma-block bubble columns pull a monk down less and less as the water breathing ability grows. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "onInsideBubbleColumn", at = @At("HEAD"), cancellable = true, require = 0)
	private void monk$insideColumn(boolean down, CallbackInfo ci) {
		if (down && monk$resist(0.3, 0.03)) {
			ci.cancel();
		}
	}

	@Inject(method = "onAboveBubbleColumn", at = @At("HEAD"), cancellable = true, require = 0)
	private void monk$aboveColumn(boolean down, BlockPos pos, CallbackInfo ci) {
		if (down && monk$resist(0.9, 0.03)) {
			ci.cancel();
		}
	}

	/** Applies the weakened downward pull and returns true when vanilla's version should be skipped. */
	private boolean monk$resist(double maxFall, double pull) {
		if (!((Object) this instanceof Player player)) {
			return false;
		}
		float resist = MonkHooks.columnResist(player);
		if (resist <= 0.0F) {
			return false;
		}
		Vec3 v = player.getDeltaMovement();
		double y = Math.max(-maxFall * (1.0 - resist), v.y - pull * (1.0 - resist));
		player.setDeltaMovement(v.x, Math.max(y, Math.min(v.y, 0.0)), v.z);
		player.resetFallDistance();
		return true;
	}
}
