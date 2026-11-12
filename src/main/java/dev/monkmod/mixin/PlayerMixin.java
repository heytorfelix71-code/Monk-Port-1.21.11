package dev.monkmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import dev.monkmod.logic.MonkHooks;

@Mixin(Player.class)
public abstract class PlayerMixin {
	/** Bare-handed harvesting according to the mining ability's tier. */
	@Inject(method = "hasCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
	private void monk$harvest(BlockState state, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && MonkHooks.canHarvestBareHanded((Player) (Object) this, state)) {
			cir.setReturnValue(true);
		}
	}

	/** Bare-handed mining speed. Runs on both sides so client prediction matches the server. */
	@Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
	private void monk$speed(BlockState state, CallbackInfoReturnable<Float> cir) {
		float vanilla = cir.getReturnValueF();
		float modified = MonkHooks.modifyDestroySpeed((Player) (Object) this, vanilla);
		if (modified != vanilla) {
			cir.setReturnValue(modified);
		}
	}

	/** Swiftness without a speed attribute: the camera FOV does not stretch. */
	@Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true)
	private void monk$swift(CallbackInfoReturnable<Float> cir) {
		float vanilla = cir.getReturnValueF();
		float modified = MonkHooks.modifySpeed((Player) (Object) this, vanilla);
		if (modified != vanilla) {
			cir.setReturnValue(modified);
		}
	}
}
