package dev.monkmod.requirement;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import dev.monkmod.data.MonkData;
import dev.monkmod.logic.MonkLogic;
import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.type.Params;

/**
 * The challenge that unlocks one level. Only the requirement of a player's <em>next</em> level is ever
 * consulted, so each hook runs for at most one requirement per event (the 1.12 version ran every
 * requirement for every event).
 */
public abstract class MonkRequirement {
	protected final String key;
	protected final Params params;
	/** Target amount shown in the HUD; -1 for one-shot requirements. */
	protected final int limit;
	int levelToGrant = -1;

	protected MonkRequirement(String key, Params params, int limit) {
		this.key = key;
		this.params = params;
		this.limit = limit;
	}

	public final String key() {
		return key;
	}

	public final Params params() {
		return params;
	}

	public final int levelToGrant() {
		return levelToGrant;
	}

	public void setLevelToGrant(int level) {
		this.levelToGrant = level;
	}

	public boolean isTicking() {
		return false;
	}

	public void tick(ServerPlayer player, MonkData data, MonkPlayerState state) {
	}

	public void onBlockBreak(ServerPlayer player, MonkData data, MonkPlayerState state, BlockPos pos, BlockState blockState) {
	}

	public void onKill(ServerPlayer killer, MonkData data, MonkPlayerState state, LivingEntity victim, DamageSource source) {
	}

	/** Called when the player damaged {@code victim}. */
	public void onDealtDamage(ServerPlayer attacker, MonkData data, MonkPlayerState state, LivingEntity victim, DamageSource source, float amount) {
	}

	/** Any living entity died (for requirements that track several attackers). */
	public void onAnyDeath(LivingEntity victim, DamageSource source) {
	}

	/** @return null to pass, otherwise the interaction result. */
	public InteractionResult onUseEntity(ServerPlayer player, MonkData data, MonkPlayerState state, Entity target, InteractionHand hand) {
		return null;
	}

	/** @return null to pass, otherwise the interaction result. */
	public InteractionResult onUseBlock(ServerPlayer player, MonkData data, MonkPlayerState state, BlockHitResult hit, InteractionHand hand) {
		return null;
	}

	/** @return false to cancel the damage. */
	public boolean allowDamage(ServerPlayer player, MonkData data, MonkPlayerState state, DamageSource source, float amount) {
		return true;
	}

	/** @return false to cancel the death. */
	public boolean allowDeath(ServerPlayer player, MonkData data, MonkPlayerState state, DamageSource source, float amount) {
		return true;
	}

	/** Whether the player may keep sleeping on this non-bed block. */
	public boolean isValidSleepSpot(ServerPlayer player, BlockPos sleepingPos) {
		return false;
	}

	public final void grant(ServerPlayer player, MonkData data) {
		MonkLogic.grantLevel(player, data, levelToGrant);
		onGrant(player);
	}

	protected void onGrant(ServerPlayer player) {
	}

	/** Adds progress and grants the level once the limit is reached. */
	protected final void progress(ServerPlayer player, MonkData data, int amount) {
		if (data.increase(amount, limit)) {
			grant(player, data);
		}
	}

	public String translationKey() {
		return "monk.advancements.requirement." + key;
	}

	protected Object[] descriptionArgs() {
		return limit < 0 ? new Object[0] : new Object[]{limit};
	}

	public Component description() {
		return Component.translatable(translationKey(), descriptionArgs());
	}
}
