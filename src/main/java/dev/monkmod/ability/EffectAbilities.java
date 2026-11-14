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
			if (dev.monkmod.config.MonkConfigManager.config().gameplay.noBlindness) {
				if (current != null && !current.isVisible() && current.getDuration() <= 100) {
					player.removeEffect(MobEffects.BLINDNESS); // ours: hidden particles, short
				}
				return;
			}
			if (current == null || (!current.isInfiniteDuration() && current.getDuration() < 40)) {
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false, true));
			}
		}

		@Override
		public void onRemoved(ServerPlayer player, MonkPlayerState state) {
			player.removeEffect(MobEffects.BLINDNESS);
		}
	}

	/**
	 * Immunity to every effect listed in "ignored_effects" (all harmful vanilla effects by default).
	 * The list may also hold two special entries that block damage instead of an effect:
	 * monk:dragon_breath and monk:creeper_explosion.
	 */
	public static final class PotionImmunity extends MonkAbility {
		public static final String DRAGON_BREATH = "monk:dragon_breath";
		public static final String CREEPER_EXPLOSION = "monk:creeper_explosion";
		public static final List<String> DEFAULT_IMMUNITIES = List.of(
				"minecraft:poison", "minecraft:wither", "minecraft:slowness", "minecraft:mining_fatigue",
				"minecraft:instant_damage", "minecraft:nausea", "minecraft:blindness", "minecraft:hunger",
				"minecraft:weakness", "minecraft:levitation", "minecraft:unluck", "minecraft:darkness",
				"minecraft:wind_charged", "minecraft:weaving", "minecraft:oozing", "minecraft:infested",
				DRAGON_BREATH, CREEPER_EXPLOSION);

		private final Set<Identifier> immune = new HashSet<>();
		private final boolean allHarmful;
		private final boolean dragonBreath;
		private final boolean creeperExplosion;

		public PotionImmunity(Params params) {
			super("potion_immunity", params);
			boolean dragon = false;
			boolean creeper = false;
			boolean all = false;
			for (String raw : params.getList("ignored_effects")) {
				String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
				if (s.isEmpty()) {
					continue;
				}
				switch (s) {
					case DRAGON_BREATH, "dragon_breath" -> dragon = true;
					case CREEPER_EXPLOSION, "creeper_explosion" -> creeper = true;
					case "*", "all", "monk:all_harmful" -> all = true;
					default -> {
						Identifier id = Identifier.tryParse(s);
						if (id != null) {
							immune.add(id);
						}
					}
				}
			}
			this.dragonBreath = dragon;
			this.creeperExplosion = creeper;
			this.allHarmful = all;
		}

		public boolean blocksDragonBreath() {
			return dragonBreath;
		}

		public boolean blocksCreeperExplosions() {
			return creeperExplosion;
		}

		/** True if the effect must not be applied to this player. */
		public boolean blocks(Holder<MobEffect> effect, MonkPlayerState state) {
			// Blindness granted by the monk path itself is never blocked.
			if (effect.equals(MobEffects.BLINDNESS) && state != null && state.abilities().has("blindness")) {
				return false;
			}
			if (allHarmful && effect.value().getCategory() == MobEffectCategory.HARMFUL) {
				return true;
			}
			Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(effect.value());
			return id != null && immune.contains(id);
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		/** Removes listed effects that slipped through (commands, forced effects). */
		@Override
		public void tick(ServerPlayer player, MonkPlayerState state) {
			if (player.getActiveEffects().isEmpty()) {
				return;
			}
			List<Holder<MobEffect>> toRemove = null;
			for (MobEffectInstance instance : player.getActiveEffects()) {
				if (blocks(instance.getEffect(), state)) {
					if (toRemove == null) {
						toRemove = new ArrayList<>(2);
					}
					toRemove.add(instance.getEffect());
				}
			}
			if (toRemove != null) {
				toRemove.forEach(player::removeEffect);
			}
		}
	}

}
