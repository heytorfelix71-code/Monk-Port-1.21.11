package dev.monkmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.ability.SimpleAbility;
import dev.monkmod.logic.MonkHooks;

/**
 * Water walking entry rules. Only the moment you enter the water decides:
 * walking in from the shore or dropping from max_fall blocks or less puts you on the surface;
 * a higher fall dives in (you sink until you press jump). While swimming you are never pulled up:
 * jump out of the water and the surface catches you like ground.
 */
final class WaterWalkClient {
	/** Fall distance of the last tick spent out of the water (vanilla resets it on entering water). */
	private static double airFall;
	private static boolean wasInWater;
	/** Ticks left in which the player may still be put on the surface after a gentle entry. */
	private static int liftWindow;

	private WaterWalkClient() {
	}

	static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.isPaused()) {
			return;
		}
		boolean inWater = player.isInWater();
		if (!inWater) {
			airFall = player.fallDistance;
			wasInWater = false;
			liftWindow = 0;
			return;
		}
		if (!wasInWater) {
			wasInWater = true;
			onEnterWater(player);
		}
		if (liftWindow <= 0) {
			return;
		}
		liftWindow--;
		lift(player);
	}

	private static void onEnterWater(LocalPlayer player) {
		SimpleAbility ability = ClientMonkState.abilities().get("water_walking", SimpleAbility.class);
		if (ability == null || player.isPassenger() || player.getAbilities().flying || player.isShiftKeyDown()) {
			return;
		}
		if (airFall > ability.params().getFloat("max_fall")) {
			SwimClient.startSinking(); // dive: go down until jump is pressed
			return;
		}
		if (ability.params().getBool("lift")) {
			liftWindow = 4;
		}
	}

	private static void lift(LocalPlayer player) {
		if (player.isEyeInFluid(FluidTags.WATER) || player.isPassenger()) {
			liftWindow = 0;
			return;
		}
		SimpleAbility ability = MonkHooks.waterWalker(player);
		if (ability == null) {
			return;
		}
		float height = ability.params().getFloat("height");
		BlockPos pos = BlockPos.containing(player.getX(), player.getY(), player.getZ());
		if (!MonkHooks.isSurfaceWater(player.level().getBlockState(pos), player.level(), pos)) {
			return;
		}
		double surface = pos.getY() + height;
		double below = surface - player.getY();
		if (below <= 0.0 || !player.level().noCollision(player, player.getBoundingBox().move(0.0, below + 0.001, 0.0))) {
			return;
		}
		player.setPos(player.getX(), surface + 0.001, player.getZ());
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x, Math.max(0.0, motion.y), motion.z);
		player.resetFallDistance();
		liftWindow = 0;
	}
}
