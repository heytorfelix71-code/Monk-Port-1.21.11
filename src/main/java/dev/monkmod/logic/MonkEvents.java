package dev.monkmod.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import dev.monkmod.ability.MonkAbility;
import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.data.MonkAttachments;
import dev.monkmod.data.MonkData;
import dev.monkmod.level.AbilitySet;
import dev.monkmod.requirement.CombatRequirements;
import dev.monkmod.requirement.MonkRequirement;

/**
 * Registers one listener per game event. Each listener resolves the player's current requirement and
 * ability set once and dispatches to them, instead of every ability/requirement listening to everything.
 */
public final class MonkEvents {
	private MonkEvents() {
	}

	private static boolean frozen(Player player) {
		return !player.level().isClientSide() && dev.monkmod.ability.CombatAbilities.TimeStop.isFrozen(player);
	}

	private static MonkRequirement next(ServerPlayer player, MonkData data) {
		return MonkConfigManager.table().nextRequirement(data.level());
	}

	public static void register() {
		// Stopped time: everyone except the monk who stopped it is frozen and can't act.
		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				frozen(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				frozen(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) ->
				frozen(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> frozen(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> frozen(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !frozen(player));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				MonkLogic.tickPlayer(player);
			}
			dev.monkmod.ability.CombatAbilities.TimeStop.serverTick(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(dev.monkmod.ability.CombatAbilities.TimeStop::end);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MonkPlayers.clear());
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MonkPlayers.remove(handler.getPlayer()));
		ServerPlayerEvents.JOIN.register(MonkLogic::resync);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> MonkLogic.resync(newPlayer));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> MonkLogic.resync(player));
		ServerEntityEvents.EQUIPMENT_CHANGE.register((entity, slot, previous, current) -> {
			if (entity instanceof ServerPlayer player) {
				MonkPlayers.get(player).markEquipmentDirty();
			}
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp) {
				onBlockBreak(sp, pos, state);
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register(MonkEvents::onDeath);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(MonkEvents::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (victim instanceof ServerPlayer hurt && taken > 0 && source.getEntity() != null) {
				MonkPlayers.get(hurt).lastCombatTick = hurt.level().getServer().getTickCount();
			}
			if (source.getEntity() instanceof ServerPlayer fighter && victim != fighter) {
				MonkPlayers.get(fighter).lastCombatTick = fighter.level().getServer().getTickCount();
			}
			if (source.getEntity() instanceof ServerPlayer attacker && victim != attacker && taken > 0) {
				MonkData data = MonkAttachments.data(attacker);
				MonkRequirement req = next(attacker, data);
				if (req != null) {
					req.onDealtDamage(attacker, data, MonkPlayers.get(attacker), victim, source, taken);
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player) {
				MonkData data = MonkAttachments.data(player);
				MonkRequirement req = next(player, data);
				return req == null || req.allowDeath(player, data, MonkPlayers.get(player), source, amount);
			}
			return true;
		});

		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator()) {
				return InteractionResult.PASS;
			}
			return onUseEntity(sp, entity, hand);
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator() || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			MonkData data = MonkAttachments.data(sp);
			MonkRequirement req = next(sp, data);
			if (req != null) {
				InteractionResult result = req.onUseBlock(sp, data, MonkPlayers.get(sp), hit, hand);
				if (result != null) {
					return result;
				}
			}
			return InteractionResult.PASS;
		});

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.getType().is(EntityTypeTags.ARROWS)) {
				trackSkeletonArrow(entity);
			}
		});

		EntitySleepEvents.ALLOW_BED.register((entity, sleepingPos, state, vanillaResult) -> {
			if (!vanillaResult && entity instanceof ServerPlayer player) {
				MonkRequirement req = next(player, MonkAttachments.data(player));
				if (req != null && req.isValidSleepSpot(player, sleepingPos)) {
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});
		EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.register((entity, sleepingPos, direction) -> {
			if (direction == null && isMonkBed(entity.level().getBlockState(sleepingPos.below()))) {
				return entity.getDirection();
			}
			return direction;
		});
	}

	public static boolean isMonkBed(BlockState state) {
		return state.is(Blocks.BEDROCK) || state.is(Blocks.CACTUS);
	}

	private static void onBlockBreak(ServerPlayer player, BlockPos pos, BlockState state) {
		MonkData data = MonkAttachments.data(player);
		MonkPlayerState ps = MonkPlayers.get(player);
		MonkRequirement req = next(player, data);
		if (req != null) {
			req.onBlockBreak(player, data, ps, pos, state);
		}
		if (!player.isCreative() && ps.abilities().has("glass_touch") && player.getMainHandItem().isEmpty()
				&& (state.is(ConventionalBlockTags.GLASS_BLOCKS) || state.is(ConventionalBlockTags.GLASS_PANES))) {
			Block.popResource(player.level(), pos, new ItemStack(state.getBlock()));
		}
	}

	private static void onDeath(LivingEntity victim, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer killer && killer != victim) {
			MonkData data = MonkAttachments.data(killer);
			MonkRequirement req = next(killer, data);
			if (req != null) {
				req.onKill(killer, data, MonkPlayers.get(killer), victim, source);
			}
		}
		for (MonkRequirement listener : MonkConfigManager.table().deathListeners()) {
			listener.onAnyDeath(victim, source);
		}
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		MonkData data = MonkAttachments.data(player);
		MonkPlayerState state = MonkPlayers.get(player);
		MonkRequirement req = next(player, data);
		if (req != null && !req.allowDamage(player, data, state, source, amount)) {
			return false;
		}
		dev.monkmod.ability.CombatAbilities.CounterAttack counter =
				state.abilities().get("counter_attack", dev.monkmod.ability.CombatAbilities.CounterAttack.class);
		if (counter != null && counter.tryCounter(player, state, source)) {
			return false;
		}
		return true;
	}

	private static InteractionResult onUseEntity(ServerPlayer player, Entity target, InteractionHand hand) {
		MonkData data = MonkAttachments.data(player);
		MonkPlayerState state = MonkPlayers.get(player);
		MonkRequirement req = next(player, data);
		if (req != null) {
			InteractionResult result = req.onUseEntity(player, data, state, target, hand);
			if (result != null) {
				return result;
			}
		}
		if (!player.getItemInHand(hand).isEmpty()) {
			return InteractionResult.PASS;
		}
		AbilitySet abilities = state.abilities();
		if (target.getType() == EntityType.CREEPER && abilities.has("kiss_creeper") && !MonkAttachments.isCalmed(target)) {
			MonkInteractions.say(player, target, "monk.blush");
			MonkInteractions.calmCreeper(target);
			return InteractionResult.SUCCESS;
		}
		if (target instanceof Animal animal && abilities.has("tame_animals")) {
			boolean fleeing = animal.getLastHurtByMob() != null;
			if (fleeing) {
				animal.setLastHurtByMob(null);
				animal.getNavigation().stop();
				MonkInteractions.say(player, target, "monk.heart");
				return InteractionResult.SUCCESS;
			}
			if (!animal.isInLove() && animal.getAge() == 0 && animal.canFallInLove()) {
				animal.setInLove(player);
				MonkInteractions.say(player, target, "monk.heart");
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

	private static void trackSkeletonArrow(Entity arrow) {
		if (!(arrow instanceof Projectile projectile)) {
			return;
		}
		Entity owner = projectile.getOwner();
		if (!CombatRequirements.ArrowDodge.isSkeleton(owner) || !(((Mob) owner).getTarget() instanceof ServerPlayer target)) {
			return;
		}
		MonkData data = MonkAttachments.data(target);
		if (next(target, data) instanceof CombatRequirements.ArrowDodge dodge) {
			dodge.track(MonkPlayers.get(target), arrow);
		}
	}

	/** True for players other code should treat as not participating (spectators). */
	static boolean ignored(Player player) {
		return player.isSpectator();
	}
}
