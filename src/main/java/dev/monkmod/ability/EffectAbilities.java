package dev.monkmod.ability;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.type.Params;

/** Abilities built around status effects and air. */
public final class EffectAbilities {
	private EffectAbilities() {
	}

	/** Permanent blindness (removed by a later level). Re-applied only when about to run out. */
	public static final class Blindness extends MonkAbility {
		public Blindness(Params params) {
			super("blindness", params);
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkPlayerState state) {
			MobEffectInstance current = player.getEffect(MobEffects.BLINDNESS);
			if (current == null || (!current.isInfiniteDuration() && current.getDuration() < 40)) {
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false, true));
			}
		}

		@Override
		public void onRemoved(ServerPlayer player, MonkPlayerState state) {
			player.removeEffect(MobEffects.BLINDNESS);
		}
	}

	/** Purges harmful effects. */
	public static final class PotionImmunity extends MonkAbility {
		private final Set<Identifier> keep = new HashSet<>();

		public PotionImmunity(Params params) {
			super("potion_immunity", params);
			for (String s : params.getList("ignored_effects")) {
				Identifier id = Identifier.tryParse(s);
				if (id != null && !s.isBlank()) {
					keep.add(id);
				}
			}
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkPlayerState state) {
			if (player.getActiveEffects().isEmpty()) {
				return;
			}
			List<Holder<MobEffect>> toRemove = null;
			for (MobEffectInstance instance : player.getActiveEffects()) {
				Holder<MobEffect> effect = instance.getEffect();
				if (effect.value().getCategory() != MobEffectCategory.HARMFUL) {
					continue;
				}
				// Blindness granted by the monk path is not a "toxin".
				if (effect.equals(MobEffects.BLINDNESS) && state.abilities().has("blindness")) {
					continue;
				}
				Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect.value());
				if (id != null && keep.contains(id)) {
					continue;
				}
				if (toRemove == null) {
					toRemove = new ArrayList<>(2);
				}
				toRemove.add(effect);
			}
			if (toRemove != null) {
				toRemove.forEach(player::removeEffect);
			}
		}
	}

	/** Never runs out of air. */
	public static final class FullWaterBreathing extends MonkAbility {
		public FullWaterBreathing(Params params) {
			super("water_breathing_full", params);
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkPlayerState state) {
			if (player.getAirSupply() < player.getMaxAirSupply()) {
				player.setAirSupply(player.getMaxAirSupply());
			}
		}
	}
}
