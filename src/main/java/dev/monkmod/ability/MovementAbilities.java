package dev.monkmod.ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;

import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.type.Params;

/** Flight, hunger and lava bathing. */
public final class MovementAbilities {
	private MovementAbilities() {
	}

	/** Creative-style flight while unarmored. Cleanly revoked when lost. */
	public static final class Fly extends MonkAbility {
		public Fly(Params params) {
			super("fly", params);
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkPlayerState state) {
			if (player.isCreative() || player.isSpectator()) {
				state.flyGranted = false;
				return;
			}
			Abilities abilities = player.getAbilities();
			boolean allowed = state.unarmored();
			if (allowed && !abilities.mayfly) {
				abilities.mayfly = true;
				state.flyGranted = true;
				player.onUpdateAbilities();
			} else if (!allowed && abilities.mayfly && state.flyGranted) {
				abilities.mayfly = false;
				abilities.flying = false;
				state.flyGranted = false;
				player.onUpdateAbilities();
			}
		}

		@Override
		public void onRemoved(ServerPlayer player, MonkPlayerState state) {
			if (state.flyGranted && !player.isCreative() && !player.isSpectator()) {
				Abilities abilities = player.getAbilities();
				abilities.mayfly = false;
				abilities.flying = false;
				abilities.setFlyingSpeed(0.05F);
				player.onUpdateAbilities();
			}
			state.flyGranted = false;
		}
	}

	/** Food drains slower: exhaustion is multiplied by (1 - hunger_chance). Applied in PlayerMixin. */
	public static final class Hunger extends MonkAbility {
		private final float reduction;

		public Hunger(Params params) {
			super("hunger", params);
			this.reduction = params.getFloat("hunger_chance");
			this.movement = params.getFloat("movement_reduction");
		}

		private final float movement;

		/** Multiplier for exhaustion caused while sprinting or swimming. */
		public float movementMultiplier() {
			return 1.0F - movement;
		}

		public float exhaustionMultiplier() {
			return 1.0F - reduction;
		}

		@Override
		protected Object[] descriptionArgs() {
			return new Object[]{percent(reduction)};
		}
	}

	/**
	 * Entering lava makes you immune to fire and lava for a while (or always, with "infinite").
	 * Works without the Fire Resistance effect: no icon, no particles. Cooldown so it can't be chained.
	 */
	public static final class LavaProtection extends ProtectionAbility {
		private final int duration;
		private final int cooldown;
		private final boolean infinite;

		public LavaProtection(Params params) {
			super("lava_protection", params, s -> s.is(DamageTypeTags.IS_FIRE), 1.0F);
			this.duration = params.getInt("duration");
			this.cooldown = params.getInt("cooldown");
			this.infinite = params.getBool("infinite");
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		private boolean immune(ServerPlayer player, MonkPlayerState state) {
			return infinite || player.level().getGameTime() < state.lavaImmuneUntil;
		}

		@Override
		public void tick(ServerPlayer player, MonkPlayerState state) {
			long now = player.level().getGameTime();
			if (!infinite && player.isInLava() && now >= state.lavaReadyAt) {
				state.lavaImmuneUntil = now + duration;
				state.lavaReadyAt = now + duration + cooldown;
			}
			if (player.isOnFire() && immune(player, state)) {
				player.clearFire();
			}
		}

		@Override
		public float factor(ServerPlayer player, MonkPlayerState state, DamageSource source) {
			if (infinite) {
				return 0.0F;
			}
			if (player.isInLava() && player.level().getGameTime() >= state.lavaReadyAt) {
				tick(player, state);
			}
			return immune(player, state) ? 0.0F : 1.0F;
		}

		@Override
		protected Object[] descriptionArgs() {
			return new Object[0];
		}
	}
}
