package dev.monkmod.ability;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Abilities;

import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.logic.MonkPlayers;
import dev.monkmod.type.Params;

/** Iron body, out-of-combat regeneration and the time stop. */
public final class CombatAbilities {
	private CombatAbilities() {
	}

	/**
	 * Iron Body: takes only a fraction of every kind of damage and gains armor toughness while wearing no
	 * armor (the old "Iron Skin" and "Netherite Skin" are part of it now).
	 */
	public static final class IronBody extends ProtectionAbility {
		private final float multiplier;
		private final float toughness;
		private final AttributeAbility toughnessMod;

		public IronBody(Params params) {
			super("iron_body", params, s -> !s.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !s.is(DamageTypeTags.BYPASSES_EFFECTS),
					params.getFloat("multiplier"));
			this.multiplier = params.getFloat("multiplier");
			this.toughness = params.getFloat("armor_toughness");
			this.toughnessMod = new AttributeAbility("iron_body", params, AttributeAbility.Condition.UNARMORED,
					java.util.List.of(new AttributeAbility.Mod(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS, "toughness",
							net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE, (pl, s) -> toughness)),
					a -> new Object[0]);
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkPlayerState state) {
			toughnessMod.tick(player, state);
		}

		@Override
		public void onRemoved(ServerPlayer player, MonkPlayerState state) {
			toughnessMod.onRemoved(player, state);
		}

		@Override
		public float factor(ServerPlayer player, MonkPlayerState state, DamageSource source) {
			return state.unarmored() ? multiplier : 1.0F;
		}

		@Override
		protected Object[] descriptionArgs() {
			return new Object[]{percent(1.0 - multiplier), number(toughness)};
		}
	}

	/** Heals slowly once the player has been out of combat for a while. No potion effect, no hunger cost. */
	public static final class Regeneration extends MonkAbility {
		private final int delay;
		private final int interval;
		private final float amount;

		public Regeneration(Params params) {
			super("regeneration", params);
			this.delay = params.getInt("delay");
			this.interval = Math.max(1, params.getInt("interval"));
			this.amount = params.getFloat("amount");
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkPlayerState state) {
			int now = player.level().getServer().getTickCount();
			if ((now + state.tickOffset) % interval != 0 || now - state.lastCombatTick < delay) {
				return;
			}
			if (player.isAlive() && player.getHealth() < player.getMaxHealth()) {
				player.heal(amount);
			}
		}

		@Override
		protected Object[] descriptionArgs() {
			return new Object[]{number(delay / 20.0)};
		}
	}

	/**
	 * Stops time with a key (J): uses the vanilla tick freeze, so mobs, projectiles, blocks and the
	 * day cycle stand still while players keep moving. Ends by itself, then needs a cooldown.
	 */
	public static final class TimeStop extends MonkAbility {
		private static UUID owner;
		/** Positions other players are held at while time is stopped. */
		private static final java.util.Map<UUID, net.minecraft.world.phys.Vec3> frozen = new java.util.HashMap<>();
		private static boolean freezeOthers;
		private static int endsAt;
		private static boolean grantedFlight;

		private final int duration;
		private final int cooldown;
		private final boolean flight;
		private final boolean freezePlayersParam;

		public TimeStop(Params params) {
			super("time_stop", params);
			this.duration = params.getInt("duration");
			this.cooldown = params.getInt("cooldown");
			this.flight = params.getBool("flight");
			this.freezePlayersParam = params.getBool("freeze_players");
		}

		public static boolean active() {
			return owner != null;
		}

		/** True for every player caught in someone else's stopped time. */
		public static boolean isFrozen(net.minecraft.world.entity.player.Player player) {
			return owner != null && freezeOthers && !owner.equals(player.getUUID()) && !player.isSpectator()
					&& !player.isCreative();
		}

		/** Key press: start, or end early when it is already yours. */
		public static void toggle(ServerPlayer player) {
			MinecraftServer server = player.level().getServer();
			MonkPlayerState state = MonkPlayers.get(player);
			TimeStop ability = state.abilities().get("time_stop", TimeStop.class);
			if (ability == null || !player.isAlive()) {
				return;
			}
			if (owner != null) {
				if (owner.equals(player.getUUID())) {
					end(server);
				}
				return;
			}
			ServerTickRateManager ticks = server.tickRateManager();
			if (ticks.isFrozen()) {
				return; // already frozen by /tick freeze: not ours to touch
			}
			int now = server.getTickCount();
			if (now < state.timeStopReadyAt) {
				player.displayClientMessage(Component.translatable("monk.time_stop.cooldown",
						(state.timeStopReadyAt - now + 19) / 20).withStyle(ChatFormatting.GRAY), true);
				return;
			}
			ticks.setFrozen(true);
			owner = player.getUUID();
			freezeOthers = ability.freezePlayersParam;
			frozen.clear();
			endsAt = now + ability.duration;
			grantedFlight = false;
			if (ability.flight && !player.getAbilities().mayfly) {
				player.getAbilities().mayfly = true;
				player.onUpdateAbilities();
				grantedFlight = true;
			}
			player.displayClientMessage(Component.translatable("monk.time_stop.start", ability.duration / 20).withStyle(ChatFormatting.AQUA), true);
		}

		/** Called every server tick (server ticks keep running while the game is frozen). */
		public static void serverTick(MinecraftServer server) {
			if (owner == null) {
				return;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			int now = server.getTickCount();
			boolean lost = player == null || !player.isAlive() || !MonkPlayers.get(player).abilities().has("time_stop");
			if (lost || now >= endsAt || !server.tickRateManager().isFrozen()) {
				end(server);
				return;
			}
			if (freezeOthers) {
				for (ServerPlayer other : server.getPlayerList().getPlayers()) {
					if (!isFrozen(other)) {
						continue;
					}
					net.minecraft.world.phys.Vec3 hold = frozen.computeIfAbsent(other.getUUID(), id -> other.position());
					other.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
					if (other.position().distanceToSqr(hold) > 0.0025) {
						other.teleportTo(hold.x, hold.y, hold.z);
					}
				}
			}
			int left = endsAt - now;
			if (left % 20 == 0) {
				player.displayClientMessage(Component.translatable("monk.time_stop.left", left / 20).withStyle(ChatFormatting.AQUA), true);
			}
		}

		public static void end(MinecraftServer server) {
			if (owner == null) {
				return;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			owner = null;
			frozen.clear();
			if (server.tickRateManager().isFrozen()) {
				server.tickRateManager().setFrozen(false);
			}
			if (player == null) {
				return;
			}
			MonkPlayerState state = MonkPlayers.get(player);
			TimeStop ability = state.abilities().get("time_stop", TimeStop.class);
			state.timeStopReadyAt = server.getTickCount() + (ability == null ? 0 : ability.cooldown);
			if (grantedFlight && !state.flyGranted && !player.isCreative() && !player.isSpectator()) {
				Abilities abilities = player.getAbilities();
				abilities.mayfly = false;
				abilities.flying = false;
				player.onUpdateAbilities();
			}
			grantedFlight = false;
			player.displayClientMessage(Component.translatable("monk.time_stop.end").withStyle(ChatFormatting.GRAY), true);
		}

		@Override
		protected Object[] descriptionArgs() {
			return new Object[]{number(duration / 20.0)};
		}
	}

	/** Parry a melee hit and strike back, with a chance that grows with the levels. */
	public static final class CounterAttack extends MonkAbility {
		private final float chance;
		private final float damage;
		private final boolean parry;
		private final boolean emptyHand;

		public CounterAttack(Params params) {
			super("counter_attack", params);
			this.chance = params.getFloat("chance");
			this.damage = params.getFloat("damage_multiplier");
			this.parry = params.getBool("parry");
			this.emptyHand = params.getBool("require_empty_hand");
		}

		/** Called when a player is about to take damage; true = the hit is parried (cancelled). */
		public boolean tryCounter(ServerPlayer player, MonkPlayerState state, DamageSource source) {
			if (!(source.getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) || source.getEntity() != attacker
					|| attacker == player || !attacker.isAlive() || player.distanceToSqr(attacker) > 25.0
					|| (emptyHand && !state.unarmed()) || player.getRandom().nextFloat() >= chance) {
				return false;
			}
			net.minecraft.server.level.ServerLevel level = player.level();
			float amount = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * damage;
			if (amount > 0) {
				attacker.hurtServer(level, player.damageSources().playerAttack(player), amount);
				attacker.knockback(0.6, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
			}
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, attacker.getX(), attacker.getY(0.5), attacker.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,
					net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.2F);
			return parry;
		}

		@Override
		protected Object[] descriptionArgs() {
			return new Object[]{percent(chance)};
		}
	}
}
