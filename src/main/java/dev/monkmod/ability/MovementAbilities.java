package dev.monkmod.ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
	 * Entering lava grants a few seconds of fire resistance (with a cooldown so it can't be chained forever,
	 * unlike the 1.12 version which could be abused).
	 */
	public static final class LavaProtection extends ProtectionAbility {
		private final int duration;
		private final int cooldown;

		public LavaProtection(Params params) {
			super("lava_protection", params, s -> s.is(DamageTypeTags.IS_FIRE), 1.0F);
			this.duration = params.getInt("duration");
			this.cooldown = params.getInt("cooldown");
		}

		@Override
		public float factor(ServerPlayer player, MonkPlayerState state, DamageSource source) {
			if (!player.isInLava()) {
				return 1.0F;
			}
			long now = player.level().getGameTime();
			if (player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
				return 0.0F;
			}
			if (now >= state.lavaReadyAt) {
				player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, duration, 2));
				player.clearFire();
				state.lavaReadyAt = now + duration + cooldown;
				return 0.0F;
			}
			return 1.0F;
		}

		@Override
		protected Object[] descriptionArgs() {
			return new Object[0];
		}
	}
}
