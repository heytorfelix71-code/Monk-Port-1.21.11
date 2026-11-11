package dev.monkmod.requirement;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.data.MonkAttachments;
import dev.monkmod.data.MonkData;
import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.type.Params;

/** Fighting challenges. */
public final class CombatRequirements {
	private CombatRequirements() {
	}

	/** Kill N matching mobs while unarmed (kill_undead, kill_hostile, kill_blind). */
	public static final class Kill extends MonkRequirement {
		private final Predicate<LivingEntity> victims;
		private final boolean cureBlindness;

		public Kill(String key, Params params, Predicate<LivingEntity> victims, boolean cureBlindness) {
			super(key, params, params.getInt("kills"));
			this.victims = victims;
			this.cureBlindness = cureBlindness;
		}

		@Override
		public void onKill(ServerPlayer killer, MonkData data, MonkPlayerState state, LivingEntity victim, DamageSource source) {
			if (state.unarmed() && victims.test(victim)) {
				progress(killer, data, 1);
			}
		}

		@Override
		protected void onGrant(ServerPlayer player) {
			if (cureBlindness) {
				player.removeEffect(MobEffects.BLINDNESS);
			}
		}
	}

	/**
	 * Defeat N mobs of a type in fair melee: you must deal at least a quarter of its health,
	 * without projectiles, weapons or armor.
	 */
	public static final class EnemyDefeat extends MonkRequirement {
		private final EntityType<?> type;
		private final Map<LivingEntity, Map<UUID, Entry>> tracker = new WeakHashMap<>();

		private static final class Entry {
			float damage;
			boolean disqualified;
		}

		public EnemyDefeat(Params params) {
			super("kill_entity_type", params, params.getInt("kills"));
			Identifier id = Identifier.tryParse(params.getString("entity_type"));
			this.type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
			if (type == null) {
				throw new IllegalArgumentException("Unknown entity type '" + params.getString("entity_type") + "'");
			}
		}

		@Override
		public void onDealtDamage(ServerPlayer attacker, MonkData data, MonkPlayerState state, LivingEntity victim, DamageSource source, float amount) {
			if (victim.getType() != type) {
				return;
			}
			Entry entry = tracker.computeIfAbsent(victim, v -> new HashMap<>()).computeIfAbsent(attacker.getUUID(), u -> new Entry());
			boolean fair = !source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() == attacker && state.unarmed() && state.unarmored();
			if (!fair) {
				if (!entry.disqualified) {
					entry.disqualified = true;
					attacker.displayClientMessage(Component.translatable("monk.disqualify").withStyle(ChatFormatting.RED), true);
				}
				return;
			}
			entry.damage += amount;
		}

		@Override
		public void onAnyDeath(LivingEntity victim, DamageSource source) {
			Map<UUID, Entry> entries = tracker.remove(victim);
			if (entries == null || victim.level().getServer() == null) {
				return;
			}
			float needed = victim.getMaxHealth() / 4.0F;
			entries.forEach((uuid, entry) -> {
				if (entry.disqualified || entry.damage < needed) {
					return;
				}
				ServerPlayer player = victim.level().getServer().getPlayerList().getPlayer(uuid);
				if (player == null) {
					return;
				}
				MonkData data = MonkAttachments.data(player);
				if (data.level() == levelToGrant - 1) {
					progress(player, data, 1);
				}
			});
		}

		@Override
		protected Object[] descriptionArgs() {
			return new Object[]{limit, type.getDescription()};
		}
	}

	/** Dodge N skeleton arrows in a row; getting hit by an arrow resets the count. */
	public static final class ArrowDodge extends MonkRequirement {
		public ArrowDodge(Params params) {
			super("arrow_dodge", params, params.getInt("dodges"));
		}

		/** Called when a skeleton aiming at {@code target} fires an arrow. */
		public void track(MonkPlayerState state, Entity arrow) {
			if (state.trackedArrows.size() < 64) {
				state.trackedArrows.add(arrow);
			}
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkData data, MonkPlayerState state) {
			if (state.trackedArrows.isEmpty()) {
				return;
			}
			var it = state.trackedArrows.iterator();
			int dodged = 0;
			while (it.hasNext()) {
				Entity arrow = it.next();
				Entity owner = arrow instanceof Projectile p ? p.getOwner() : null;
				if (!arrow.isAlive() || owner == null || arrow.level() != player.level() || arrow.getDeltaMovement().lengthSqr() < 1.0E-4) {
					it.remove();
					continue;
				}
				Vec3 fromShooter = player.position().subtract(owner.position()).normalize();
				Vec3 fromArrow = player.position().subtract(arrow.position()).normalize();
				if (fromShooter.dot(fromArrow) < -0.1) {
					dodged++;
					it.remove();
				}
			}
			if (dodged > 0) {
				progress(player, data, dodged);
			}
		}

		@Override
		public boolean allowDamage(ServerPlayer player, MonkData data, MonkPlayerState state, DamageSource source, float amount) {
			Entity direct = source.getDirectEntity();
			if (direct != null && direct.getType().is(EntityTypeTags.ARROWS)) {
				data.resetProgress();
				state.trackedArrows.remove(direct);
			}
			return true;
		}

		public static boolean isSkeleton(Entity owner) {
			return owner instanceof Mob mob && mob.getType().is(EntityTypeTags.SKELETONS);
		}
	}
}
