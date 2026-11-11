package dev.monkmod.logic;

import java.util.function.Function;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import dev.monkmod.level.AbilitySet;

/**
 * Side-agnostic ability lookup used by mixins that run on both client and server
 * (mining speed, water walking). The client entrypoint installs the client lookups.
 */
public final class MonkAccess {
	private static Function<Player, AbilitySet> clientAbilities = p -> AbilitySet.EMPTY;
	private static Predicate<Player> clientUnarmored = p -> false;

	private MonkAccess() {
	}

	public static void installClient(Function<Player, AbilitySet> abilities, Predicate<Player> unarmored) {
		clientAbilities = abilities;
		clientUnarmored = unarmored;
	}

	public static AbilitySet abilities(Player player) {
		if (player.level().isClientSide()) {
			return clientAbilities.apply(player);
		}
		if (player instanceof ServerPlayer sp) {
			MonkPlayerState state = MonkPlayers.getIfPresent(sp);
			return state == null ? AbilitySet.EMPTY : state.abilities();
		}
		return AbilitySet.EMPTY;
	}

	public static boolean unarmored(Player player) {
		if (player.level().isClientSide()) {
			return clientUnarmored.test(player);
		}
		if (player instanceof ServerPlayer sp) {
			MonkPlayerState state = MonkPlayers.getIfPresent(sp);
			return state != null && state.unarmored();
		}
		return false;
	}
}
