package dev.monkmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.ability.SimpleAbility;
import dev.monkmod.logic.MonkHooks;

/**
 * Water walking safety net: if a monk ends up in the top layer of water (walking in from the shore,
 * landing from a jump, coming up after sneaking), put them back on the surface floor. Only while the
 * head is still out of the water, so swimming and diving keep working.
 */
final class WaterWalkClient {
	private WaterWalkClient() {
	}

	static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.isPaused() || player.isPassenger() || !player.isInWater()
				|| player.isEyeInFluid(FluidTags.WATER)) {
			return;
		}
		SimpleAbility ability = MonkHooks.waterWalker(player);
		if (ability == null || !ability.params().getBool("lift")) {
			return;
		}
		float height = ability.params().getFloat("height");
		BlockPos pos = BlockPos.containing(player.getX(), player.getY(), player.getZ());
		if (!MonkHooks.isSurfaceWater(player.level().getBlockState(pos), player.level(), pos)) {
			return;
		}
		double surface = pos.getY() + height;
		double below = surface - player.getY();
		if (below <= 0.0) {
			return;
		}
		// Only if the spot on the surface is free.
		if (!player.level().noCollision(player, player.getBoundingBox().move(0.0, below + 0.001, 0.0))) {
			return;
		}
		player.setPos(player.getX(), surface + 0.001, player.getZ());
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x, Math.max(0.0, motion.y), motion.z);
		player.resetFallDistance();
	}
}
