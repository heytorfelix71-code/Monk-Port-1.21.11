package dev.monkmod.logic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import dev.monkmod.ability.MiningAbility;
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
}
