package dev.monkmod.logic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Creeper;

import dev.monkmod.ability.EffectAbilities;
import dev.monkmod.config.MonkConfigManager;
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

	private static float floorHeight = -1;
	private static VoxelShape floorShape = Shapes.block();

	private static VoxelShape floor(float height) {
		if (height != floorHeight) {
			floorShape = Shapes.box(0, 0, 0, 1, height, 1);
			floorHeight = height;
		}
		return floorShape;
	}

	/** The water walking ability if the player can use it right now (unarmored, not sneaking or flying), else null. */
	public static SimpleAbility waterWalker(Player player) {
		if (player.isShiftKeyDown() || player.getAbilities().flying || player.isSpectator()) {
			return null;
		}
		SimpleAbility ability = MonkAccess.abilities(player).get("water_walking", SimpleAbility.class);
		if (ability == null || player.fallDistance > ability.params().getFloat("max_fall") || !MonkAccess.unarmored(player)) {
			return null; // a high fall dives into the water
		}
		return ability;
	}

	/** Top water block (no water above it): the one that becomes a floor. */
	public static boolean isSurfaceWater(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getFluidState().is(FluidTags.WATER) && !level.getFluidState(pos.above()).is(FluidTags.WATER);
	}

	/**
	 * Water walking: a solid floor at the water surface for monks that are above it. Returns null to keep
	 * the vanilla (empty) shape.
	 */
	public static VoxelShape waterFloor(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (!(context instanceof EntityCollisionContext ec) || !(ec.getEntity() instanceof Player player)) {
			return null;
		}
		// Player movement is decided by the client. A floor on the server too made it pull divers back up
		// onto the surface; the server accepts the client's position without it.
		if (!player.level().isClientSide()) {
			return null;
		}
		if (!isSurfaceWater(state, level, pos)) {
			return null;
		}
		SimpleAbility ability = waterWalker(player);
		if (ability == null) {
			return null;
		}
		VoxelShape shape = floor(ability.params().getFloat("height"));
		return context.isAbove(shape, pos, true) ? shape : null;
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

	/** Monk harvesting: works with empty hands and also while holding an item, block or the wrong tool. */
	public static boolean canHarvestBareHanded(Player player, BlockState state) {
		if (heavyToolBlocked(player)) {
			return false;
		}
		MiningAbility mining = MonkAccess.abilities(player).get("mining", MiningAbility.class);
		return mining != null && mining.canHarvest(state);
	}

	/**
	 * Monk mining speed, whatever is in the hand: the ability speed is used whenever it is faster than
	 * the held item (a pickaxe only helps when it is quicker than your hands).
	 */
	public static float modifyDestroySpeed(Player player, BlockState state, float speed) {
		if (heavyToolBlocked(player)) {
			return 0.0F;
		}
		MiningAbility mining = MonkAccess.abilities(player).get("mining", MiningAbility.class);
		if (mining == null) {
			return speed;
		}
		ItemStack held = player.getMainHandItem();
		float toolSpeed = held.isEmpty() ? 1.0F : Math.max(1.0F, held.getDestroySpeed(state));
		// Remove the tool's own multiplier, keep the penalties (underwater, in the air, mining fatigue).
		float hands = Math.min(1.0F, speed / toolSpeed);
		if (player.isInWater() && MonkConfigManager.config().water.underwaterMining && hasWaterBreathing(player)) {
			// Vanilla: 5x slower with the head underwater (no Aqua Affinity) and 5x slower again when floating.
			if (player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
				double submerged = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SUBMERGED_MINING_SPEED);
				if (submerged > 0.0 && submerged < 1.0) {
					hands /= (float) submerged;
				}
			}
			if (!player.onGround()) {
				hands *= 5.0F;
			}
			hands = Math.min(1.0F, hands);
		}
		return Math.max(speed, mining.modifySpeed(hands));
	}

	/** Heavy armor pieces worn (configurable list). */
	public static int heavyPieces(Player player) {
		java.util.List<String> heavy = MonkConfigManager.config().gameplay.heavyArmor;
		if (heavy.isEmpty()) {
			return 0;
		}
		int count = 0;
		for (net.minecraft.world.entity.EquipmentSlot slot : HEAVY_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty() && heavy.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
				count++;
			}
		}
		return count;
	}

	private static final net.minecraft.world.entity.EquipmentSlot[] HEAVY_SLOTS = {
			net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
			net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};

	/** A monk in heavy armor can't break blocks with a pickaxe or an axe. */
	private static boolean heavyToolBlocked(Player player) {
		if (!MonkConfigManager.config().gameplay.heavyArmorNoTools || MonkAccess.abilities(player).isEmpty()) {
			return false;
		}
		ItemStack held = player.getMainHandItem();
		return (held.is(net.minecraft.tags.ItemTags.PICKAXES) || held.is(net.minecraft.tags.ItemTags.AXES)) && heavyPieces(player) > 0;
	}

	public static float modifyExhaustion(Player player, float exhaustion) {
		if (player.level().isClientSide()) {
			return exhaustion;
		}
		AbilitySet set = MonkAccess.abilities(player);
		MovementAbilities.Hunger hunger = set.get("hunger", MovementAbilities.Hunger.class);
		if (hunger == null) {
			return exhaustion;
		}
		exhaustion *= hunger.exhaustionMultiplier();
		if (player.isSprinting() || player.isSwimming() || player.isInWater()) {
			exhaustion *= hunger.movementMultiplier();
			PassiveAbilities.Swift swift = set.get("swift", PassiveAbilities.Swift.class);
			if (swift != null && player.isSprinting() && !player.isInWater()) {
				exhaustion /= Math.max(1.0F, swift.multiplier(player)); // more distance per second, same cost
			}
		}
		return exhaustion;
	}

	/** Swiftness: multiplies ground speed (no attribute, so the FOV stays the same). */
	public static float modifySpeed(Player player, float speed) {
		AbilitySet set = MonkAccess.abilities(player);
		if (set.isEmpty()) {
			return speed;
		}
		float slow = MonkConfigManager.config().gameplay.heavyArmorSlowdown;
		if (slow > 0) {
			int heavy = heavyPieces(player);
			if (heavy > 0) {
				return speed * Math.max(0.25F, 1.0F - slow * heavy); // heavy armor: no monk speed, and slower
			}
		}
		PassiveAbilities.Swift swift = set.get("swift", PassiveAbilities.Swift.class);
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

	/** Natural diet ability of a server player, or null. */
	public static dev.monkmod.ability.DietAbility diet(ServerPlayer player) {
		MonkPlayerState state = MonkPlayers.getIfPresent(player);
		return state == null ? null : state.abilities().get("natural_diet", dev.monkmod.ability.DietAbility.class);
	}

	/** Swiftness active right now (unarmored if required, no heavy armor), or null. */
	public static PassiveAbilities.Swift activeSwift(Player player) {
		AbilitySet set = MonkAccess.abilities(player);
		PassiveAbilities.Swift swift = set.get("swift", PassiveAbilities.Swift.class);
		if (swift == null || (swift.unarmoredOnly() && !MonkAccess.unarmored(player)) || heavyPieces(player) > 0) {
			return null;
		}
		return swift;
	}

	private static float cachedHeight = -1;
	private static float cachedPower;

	/** Higher jumps that follow the running bonus chosen with Ctrl + wheel (client side: jumps are client movement). */
	// The double jump itself is done by DoubleJumpClient.
	public static float modifyJumpPower(LivingEntity entity, float vanilla) {
		if (!(entity instanceof Player player) || !player.level().isClientSide()) {
			return vanilla;
		}
		PassiveAbilities.Swift swift = activeSwift(player);
		if (swift == null) {
			return vanilla;
		}
		float height = swift.jumpHeight(PassiveAbilities.Swift.clientScale(), MonkConfigManager.config().controls.maxRunScale);
		if (height <= PassiveAbilities.VANILLA_JUMP_HEIGHT + 0.01F) {
			return vanilla;
		}
		if (height != cachedHeight) {
			cachedPower = PassiveAbilities.jumpPowerFor(height);
			cachedHeight = height;
		}
		return Math.max(vanilla, cachedPower);
	}

	/** Falls up to the highest jump the player can currently do cause no damage. */
	public static double modifyFallDistance(LivingEntity entity, double distance) {
		if (!(entity instanceof ServerPlayer player) || distance <= 3.0) {
			return distance;
		}
		PassiveAbilities.Swift swift = activeSwift(player);
		if (swift == null) {
			return distance;
		}
		float cap = dev.monkmod.network.MonkNetwork.runScaleMax(dev.monkmod.data.MonkAttachments.data(player).level(), MonkConfigManager.table());
		float top = MonkConfigManager.config().controls.maxRunScale;
		float safe = swift.jumpHeight(cap, top) + swift.doubleJumpHeight(cap, top);
		return safe <= PassiveAbilities.VANILLA_JUMP_HEIGHT + 0.01F ? distance : Math.max(0.0, distance - safe);
	}

	public static boolean hasWaterBreathing(Player player) {
		AbilitySet set = MonkAccess.abilities(player);
		return set.has("water_breathing_partial") || set.has("water_breathing_full");
	}

	/** River currents don't push a monk who holds their depth in the water (vanilla flow and Streams-style mods). */
	public static boolean ignoresCurrents(Player player) {
		if (!MonkConfigManager.config().water.ignoreCurrents || player.isShiftKeyDown()) {
			return false;
		}
		AbilitySet set = MonkAccess.abilities(player);
		for (String key : new String[]{"water_breathing_full", "water_breathing_partial"}) {
			PassiveAbilities.WaterBreathing w = set.get(key, PassiveAbilities.WaterBreathing.class);
			if (w != null && w.params().getBool("hold_depth")) {
				return true;
			}
		}
		return false;
	}

	/** 0..1: how much of a magma bubble column's downward pull this monk resists (0 while sneaking on purpose). */
	public static float columnResist(Player player) {
		if (player.isShiftKeyDown()) {
			return 0.0F;
		}
		AbilitySet set = MonkAccess.abilities(player);
		float best = 0.0F;
		for (String key : new String[]{"water_breathing_full", "water_breathing_partial"}) {
			PassiveAbilities.WaterBreathing w = set.get(key, PassiveAbilities.WaterBreathing.class);
			if (w != null) {
				best = Math.max(best, w.params().getFloat("column_resist"));
			}
		}
		return Math.min(1.0F, best);
	}
}
