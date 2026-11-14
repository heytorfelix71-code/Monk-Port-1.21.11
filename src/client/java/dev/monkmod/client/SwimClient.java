package dev.monkmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.ability.PassiveAbilities;
import dev.monkmod.level.AbilitySet;

/**
 * Monk swimming (water breathing abilities): faster swimming and no sinking. Sneak makes you go down
 * until you press jump once; then you stay at that depth again. Pure movement, no potion effect.
 */
final class SwimClient {
	private static boolean sinking;
	private static double anchorY = Double.NaN;
	private static boolean jumpWasDown;

	private SwimClient() {
	}

	/** After a high dive: keep going down until jump is pressed. */
	static void startSinking() {
		sinking = true;
	}

	private static PassiveAbilities.WaterBreathing ability() {
		AbilitySet set = ClientMonkState.abilities();
		PassiveAbilities.WaterBreathing full = set.get("water_breathing_full", PassiveAbilities.WaterBreathing.class);
		return full != null ? full : set.get("water_breathing_partial", PassiveAbilities.WaterBreathing.class);
	}

	static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.isPaused()) {
			return;
		}
		boolean jump = client.options.keyJump.isDown();
		boolean jumpPressed = jump && !jumpWasDown;
		jumpWasDown = jump;
		PassiveAbilities.WaterBreathing ability = ability();
		if (ability == null || !player.isInWater() || player.isPassenger() || player.getAbilities().flying || player.isSpectator()) {
			sinking = false;
			anchorY = Double.NaN;
			return;
		}
		Vec3 motion = player.getDeltaMovement();

		// Faster swimming: a small push in the direction you are already moving (bounded, it never runs away).
		float boost = ability.params().getFloat("swim_boost");
		if (boost > 0 && (motion.horizontalDistanceSqr() > 4.0E-4 || player.isSwimming())) {
			double k = 1.0 + boost;
			motion = new Vec3(motion.x * k, player.isSwimming() ? motion.y * k : motion.y, motion.z * k);
		}

		// Faster ascent while holding jump.
		float up = ability.params().getFloat("swim_up");
		if (up > 0 && jump && !player.isShiftKeyDown() && !player.isSwimming()) {
			motion = new Vec3(motion.x, Math.min(0.6, motion.y + up), motion.z);
		}

		if (ability.params().getBool("hold_depth")) {
			if (player.isShiftKeyDown()) {
				sinking = true;
			} else if (jumpPressed) {
				sinking = false;
			}
			if (sinking || jump || player.isSwimming() || player.onGround()) {
				anchorY = player.getY(); // moving on purpose: follow
			} else {
				if (Double.isNaN(anchorY)) {
					anchorY = player.getY();
				}
				double dy = Math.max(-0.1, Math.min(0.1, anchorY - player.getY()));
				motion = new Vec3(motion.x, dy, motion.z);
			}
		}
		player.setDeltaMovement(motion);
	}
}
