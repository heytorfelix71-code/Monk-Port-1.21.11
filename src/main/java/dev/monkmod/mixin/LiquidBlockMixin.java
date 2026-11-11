package dev.monkmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.monkmod.logic.MonkHooks;

/**
 * Water walking: the water surface is solid ground for a monk (a full block, so you stand on top of the
 * water instead of half inside it, which made the player bob up and down and get stuck).
 */
@Mixin(LiquidBlock.class)
public abstract class LiquidBlockMixin {
	@Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true)
	private void monk$waterFloor(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
		VoxelShape floor = MonkHooks.waterFloor(state, level, pos, context);
		if (floor != null) {
			cir.setReturnValue(floor);
		}
	}
}
