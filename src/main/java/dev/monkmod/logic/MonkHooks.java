package dev.monkmod.logic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;

import dev.monkmod.ability.EffectAbilities;
import dev.monkmod.ability.MiningAbility;
import dev.monkmod.ability.PassiveAbilities;
import dev.monkmod.ability.SimpleAbility;
import dev.monkmod.ability.MovementAbilities;
import dev.monkmod.ability.ProtectionAbility;
import dev.monkmod.level.AbilitySet;

/** Entry points called from mixins. Each one bails out as early and cheaply as possible. */
public final class MonkHooks {
	private MonkHooks() {
	}

	/** Water walking: lets the vanilla "stand on fluid" collision (used by striders) apply to monks on water. */
	public static boolean canWalkOnWater(LivingEntity entity, FluidState fluid) {
		if (!(entity instanceof Player player) || !fluid.is(FluidTags.WATER)) {
			return false;
		}
		if (player.isShiftKeyDown() || player.fallDistance > 3.0 || player.isEyeInFluid(FluidTags.WATER) || player.getAbilities().flying) {
			return false;
		}
		return MonkAccess.abilities(player).has("water_walking") && MonkAccess.unarmored(player);
	}

	public static float modifyIncomingDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || amount <= 0 || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return amount;
		}
		MonkPlayerState state = MonkPlayers.getIfPresent(player);
		if (state == null || state.abilities().protections().isEmpty()) {
			return amount;
		}
		for (ProtectionAbility protection : state.abilities().protections()) {
			if (protection.handles(source)) {
				amount *= protection.factor(player, state, source);
			}
		}
		return amount;
	}

	public static boolean canHarvestBareHanded(Player player, BlockState state) {
		if (!player.getMainHandItem().isEmpty()) {
			return false;
		}
		MiningAbility mining = MonkAccess.abilities(player).get("mining", MiningAbility.class);
		return mining != null && mining.canHarvest(state);
	}

	public static float modifyDestroySpeed(Player player, float speed) {
		if (!player.getMainHandItem().isEmpty()) {
			return speed;
		}
		MiningAbility mining = MonkAccess.abilities(player).get("mining", MiningAbility.class);
		return mining == null ? speed : mining.modifySpeed(speed);
	}

	public static float modifyExhaustion(Player player, float exhaustion) {
		if (player.level().isClientSide()) {
			return exhaustion;
		}
		AbilitySet set = MonkAccess.abilities(player);
		MovementAbilities.Hunger hunger = set.get("hunger", MovementAbilities.Hunger.class);
		return hunger == null ? exhaustion : exhaustion * hunger.exhaustionMultiplier();
	}

	/** Swiftness: multiplies ground speed (no attribute, so the FOV stays the same). */
	public static float modifySpeed(Player player, float speed) {
		PassiveAbilities.Swift swift = MonkAccess.abilities(player).get("swift", PassiveAbilities.Swift.class);
		if (swift == null || (swift.unarmoredOnly() && !MonkAccess.unarmored(player))) {
			return speed;
		}
		return speed * swift.multiplier(player);
	}

	/** Infinite air from the water breathing abilities. */
	public static boolean infiniteAir(LivingEntity entity) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		AbilitySet set = MonkAccess.abilities(player);
		PassiveAbilities.WaterBreathing full = set.get("water_breathing_full", PassiveAbilities.WaterBreathing.class);
		if (full != null && full.infinite()) {
			return true;
		}
		PassiveAbilities.WaterBreathing partial = set.get("water_breathing_partial", PassiveAbilities.WaterBreathing.class);
		return partial != null && partial.infinite();
	}

	/** Toxin immunity: refuses listed effects before they are applied. */
	public static boolean blocksEffect(LivingEntity entity, Holder<MobEffect> effect) {
		if (!(entity instanceof ServerPlayer player)) {
			return false;
		}
		MonkPlayerState state = MonkPlayers.getIfPresent(player);
		if (state == null) {
			return false;
		}
		EffectAbilities.PotionImmunity immunity = state.abilities().get("potion_immunity", EffectAbilities.PotionImmunity.class);
		return immunity != null && immunity.blocks(effect, state);
	}

	/** Damage that must be ignored completely (dragon breath, creeper explosions). */
	public static boolean cancelDamage(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		MonkPlayerState state = MonkPlayers.getIfPresent(player);
		if (state == null) {
			return false;
		}
		EffectAbilities.PotionImmunity immunity = state.abilities().get("potion_immunity", EffectAbilities.PotionImmunity.class);
		if (immunity == null) {
			return false;
		}
		Entity direct = source.getDirectEntity();
		Entity cause = source.getEntity();
		if (immunity.blocksDragonBreath() && (source.is(DamageTypes.DRAGON_BREATH)
				|| (direct instanceof AreaEffectCloud && cause instanceof EnderDragon))) {
			return true;
		}
		return immunity.blocksCreeperExplosions() && source.is(DamageTypeTags.IS_EXPLOSION)
				&& (cause instanceof Creeper || direct instanceof Creeper);
	}

	/** Gentle plea: creepers leave this player alone (passive part of the kiss_creeper ability). */
	public static boolean creeperIgnores(Entity target) {
		if (!(target instanceof Player player)) {
			return false;
		}
		SimpleAbility ability = MonkAccess.abilities(player).get("kiss_creeper", SimpleAbility.class);
		return ability != null && ability.params().getBool("passive");
	}
}
