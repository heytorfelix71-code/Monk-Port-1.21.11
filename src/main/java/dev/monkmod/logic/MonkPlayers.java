package dev.monkmod.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

/** Server-side registry of transient per-player state. Only touched from the server thread. */
public final class MonkPlayers {
	private static final Map<UUID, MonkPlayerState> STATES = new HashMap<>();

	private MonkPlayers() {
	}

	public static MonkPlayerState get(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), u -> new MonkPlayerState());
	}

	public static MonkPlayerState getIfPresent(ServerPlayer player) {
		return STATES.get(player.getUUID());
	}

	public static void remove(ServerPlayer player) {
		STATES.remove(player.getUUID());
	}

	public static void clear() {
		STATES.clear();
	}
}
