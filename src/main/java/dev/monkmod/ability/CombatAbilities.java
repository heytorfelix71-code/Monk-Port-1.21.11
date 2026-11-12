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

	/** Takes only a fraction of every kind of damage while wearing no armor (on top of the natural armor). */
	public static final class IronBody extends ProtectionAbility {
		private final float multiplier;

		public IronBody(Params params) {
			super("iron_body", params, s -> !s.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !s.is(DamageTypeTags.BYPASSES_EFFECTS),
					params.getFloat("multiplier"));
			this.multiplier = params.getFloat("multiplier");
		}

		@Override
		public float factor(ServerPlayer player, MonkPlayerState state, DamageSource source) {
			return state.unarmored() ? multiplier : 1.0F;
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
		private static int endsAt;
		private static boolean grantedFlight;

		private final int duration;
		private final int cooldown;
		private final boolean flight;

		public TimeStop(Params params) {
			super("time_stop", params);
			this.duration = params.getInt("duration");
			this.cooldown = params.getInt("cooldown");
			this.flight = params.getBool("flight");
		}

		public static boolean active() {
			return owner != null;
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
}
