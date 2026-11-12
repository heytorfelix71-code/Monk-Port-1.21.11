package dev.monkmod.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.level.AbilitySet;

/**
 * Transient, server-side per-player state (never saved). Holds caches so the expensive checks
 * run once per interval instead of once per event, plus scratch data for requirements.
 */
public final class MonkPlayerState {
	/** Level whose abilities are currently applied; MIN_VALUE forces a full re-apply. */
	int appliedLevel = Integer.MIN_VALUE;
	int appliedTableVersion = -1;
	AbilitySet abilities = AbilitySet.EMPTY;

	private long equipmentCheckedAt = Long.MIN_VALUE;
	boolean equipmentDirty = true;
	private boolean unarmed = true;
	private boolean unarmored = true;

	/** Random phase so periodic checks of different players don't all land on the same tick. */
	public final int tickOffset = ThreadLocalRandom.current().nextInt(40);

	public boolean flyGranted;
	public long lavaReadyAt;
	public long lavaImmuneUntil;
	public long blinkReadyAt;
	/** Server tick of the last time this player dealt or took damage (out-of-combat regeneration). */
	public int lastCombatTick;
	/** Server tick from which the time stop can be used again. */
	public int timeStopReadyAt;
	/** Ids of projectiles this player is dodging right now (they pass through for a few ticks). */
	public final java.util.Set<Integer> dodgedProjectiles = new java.util.HashSet<>();

	// Requirement scratch space (only the requirement of the next level uses it).
	public boolean tracking;
	public double trackX;
	public double trackZ;
	public final List<Entity> trackedArrows = new ArrayList<>();
	public int lastFireTick;

	public AbilitySet abilities() {
		return abilities;
	}

	public boolean unarmed() {
		return unarmed;
	}

	public boolean unarmored() {
		return unarmored;
	}

	public void markEquipmentDirty() {
		equipmentDirty = true;
	}

	void refreshEquipment(ServerPlayer player) {
		long now = player.level().getGameTime();
		int interval = MonkConfigManager.config().performance.equipmentCheckInterval;
		if (equipmentDirty || now - equipmentCheckedAt >= interval) {
			unarmed = MonkEquipment.computeUnarmed(player);
			unarmored = MonkEquipment.computeUnarmored(player);
			equipmentCheckedAt = now;
			equipmentDirty = false;
		}
	}

	public void resetRequirementScratch() {
		tracking = false;
		trackedArrows.clear();
	}
}
