package dev.monkmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

import dev.monkmod.logic.MonkHooks;

/**
 * Fasting: every source of exhaustion is reduced, including the one from natural healing (which is
 * added inside FoodData itself and is the biggest hunger drain in practice).
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
	@Unique
	private ServerPlayer monk$player;

	@Inject(method = "tick", at = @At("HEAD"))
	private void monk$remember(ServerPlayer player, CallbackInfo ci) {
		monk$player = player;
	}

	@ModifyVariable(method = "addExhaustion", at = @At("HEAD"), argsOnly = true)
	private float monk$fasting(float exhaustion) {
		return monk$player == null ? exhaustion : MonkHooks.modifyExhaustion(monk$player, exhaustion);
	}
}
