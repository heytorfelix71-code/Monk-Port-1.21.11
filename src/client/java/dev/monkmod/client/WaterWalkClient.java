package dev.monkmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.logic.MonkEquipment;

/** Lifts a water-walking monk who is ankle deep back onto the surface (movement is client-driven). */
final class WaterWalkClient {
	private WaterWalkClient() {
	}

	static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.isPaused() || player.isShiftKeyDown() || !player.isInWater()) {
			return;
		}
		if (!ClientMonkState.abilities().has("water_walking") || player.fallDistance > 3.0 || player.getAbilities().flying) {
			return;
		}
		double depth = player.getFluidHeight(FluidTags.WATER);
		if (depth > 0.0 && depth < 0.6 && MonkEquipment.computeUnarmored(player)) {
			Vec3 motion = player.getDeltaMovement();
			if (motion.y < 0.1) {
				player.setDeltaMovement(motion.x, Math.max(motion.y, 0.0) + 0.05, motion.z);
			}
		}
	}
}
