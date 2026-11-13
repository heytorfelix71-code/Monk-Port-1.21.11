package dev.monkmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.ability.PassiveAbilities;
import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.logic.MonkHooks;

/**
 * Double jump (Swiftness): press jump again in the air for a second jump. Its height follows the running
 * bonus chosen with Ctrl + mouse wheel. Movement is client side, so this runs on the client only.
 */
final class DoubleJumpClient {
	private static boolean jumpWasDown;
	private static boolean used;
	private static int airTicks;

	private DoubleJumpClient() {
	}

	static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.isPaused()) {
			return;
		}
		boolean jump = client.options.keyJump.isDown();
		boolean pressed = jump && !jumpWasDown;
		jumpWasDown = jump;
		if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()
				|| player.getAbilities().flying || player.isFallFlying() || player.isPassenger()) {
			used = false;
			airTicks = 0;
			return;
		}
		airTicks++;
		// With creative-style flight, a quick double tap starts flying: only later presses double jump.
		int minTicks = player.getAbilities().mayfly ? 8 : 3;
		if (!pressed || used || airTicks < minTicks) {
			return;
		}
		PassiveAbilities.Swift swift = MonkHooks.activeSwift(player);
		if (swift == null) {
			return;
		}
		float extra = swift.doubleJumpHeight(PassiveAbilities.Swift.clientScale(), MonkConfigManager.config().controls.maxRunScale);
		if (extra < 0.3F) {
			return;
		}
		used = true;
		Vec3 motion = player.getDeltaMovement();
		player.setDeltaMovement(motion.x, PassiveAbilities.jumpPowerFor(extra), motion.z);
		player.resetFallDistance();
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			player.level().addParticle(ParticleTypes.CLOUD, player.getX() + Math.cos(a) * 0.4, player.getY(), player.getZ() + Math.sin(a) * 0.4,
					Math.cos(a) * 0.05, -0.05, Math.sin(a) * 0.05);
		}
		player.playSound(SoundEvents.WOOL_STEP, 0.6F, 1.6F);
	}
}
