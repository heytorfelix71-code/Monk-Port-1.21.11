package dev.monkmod.logic;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import dev.monkmod.MonkMod;
import dev.monkmod.ability.MonkAbility;
import dev.monkmod.advancement.MonkCriteria;
import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.data.MonkAttachments;
import dev.monkmod.data.MonkData;
import dev.monkmod.level.AbilitySet;
import dev.monkmod.level.LevelTable;
import dev.monkmod.network.MonkNetwork;
import dev.monkmod.requirement.MonkRequirement;

/** Per-tick player update, level changes and client sync. */
public final class MonkLogic {
	private MonkLogic() {
	}

	public static void tickPlayer(ServerPlayer player) {
		if (player.isSpectator() || !player.isAlive()) {
			return;
		}
		MonkData data = MonkAttachments.data(player);
		MonkPlayerState state = MonkPlayers.get(player);
		LevelTable table = MonkConfigManager.table();

		state.refreshEquipment(player);
		if (state.appliedLevel != data.level() || state.appliedTableVersion != table.version()) {
			applyLevel(player, data, state, table);
		}

		for (MonkAbility ability : state.abilities.ticking()) {
			ability.tick(player, state);
		}

		MonkRequirement next = table.nextRequirement(data.level());
		if (next != null && next.isTicking()) {
			next.tick(player, data, state);
		}

		if (data.stateDirty) {
			data.stateDirty = false;
			MonkNetwork.sendState(player, data, MonkPlayers.get(player), table);
		}
		if (data.progressDirty) {
			data.progressDirty = false;
			MonkNetwork.sendProgress(player, data);
			if (MonkConfigManager.config().gameplay.actionBarProgress && data.maxProgress() > 0) {
				player.displayClientMessage(Component.translatable("monk.progress.actionbar", data.level() + 1,
						Math.min(data.progress(), data.maxProgress()), data.maxProgress()).withStyle(ChatFormatting.GRAY), true);
			}
		}
	}

	/** Swaps the applied ability set, letting abilities that are gone clean up after themselves. */
	private static void applyLevel(ServerPlayer player, MonkData data, MonkPlayerState state, LevelTable table) {
		AbilitySet next = table.abilitiesAt(data.level());
		for (MonkAbility old : state.abilities.all()) {
			if (!next.has(old.key())) {
				old.onRemoved(player, state);
			}
		}
		state.abilities = next;
		state.appliedLevel = data.level();
		state.appliedTableVersion = table.version();
		state.resetRequirementScratch();
		data.stateDirty = true;
	}

	public static void grantLevel(ServerPlayer player, MonkData data, int level) {
		data.setLevel(level);
		MonkPlayers.get(player).resetRequirementScratch();
		MonkCriteria.LEVEL_UP.trigger(player, level);
		MonkMod.LOGGER.debug("{} reached monk level {}", player.getName().getString(), level);
	}

	/** Forces a full re-apply and re-sync (join, respawn, dimension change, config reload). */
	public static void resync(ServerPlayer player) {
		MonkData data = MonkAttachments.data(player);
		MonkPlayerState state = MonkPlayers.get(player);
		state.appliedLevel = Integer.MIN_VALUE;
		state.markEquipmentDirty();
		data.stateDirty = true;
		data.progressDirty = true;
		if (data.level() >= 0) {
			MonkCriteria.LEVEL_UP.trigger(player, data.level());
		}
	}

	/** Admin level change: also revokes the monk advancements above the new level. */
	public static void setLevel(ServerPlayer player, int level) {
		MonkData data = MonkAttachments.data(player);
		LevelTable table = MonkConfigManager.table();
		level = Math.max(-1, Math.min(level, table.maxLevel()));
		if (level < data.level()) {
			revokeAbove(player, level, table.maxLevel());
		}
		data.setLevel(level);
		MonkPlayers.get(player).resetRequirementScratch();
		if (level >= 0) {
			MonkCriteria.LEVEL_UP.trigger(player, level);
		}
	}

	private static void revokeAbove(ServerPlayer player, int level, int maxLevel) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}
		for (int l = Math.max(0, level + 1); l <= maxLevel; l++) {
			revoke(player, server, l == 0 ? MonkMod.id("root") : MonkMod.id("level_" + l));
			revoke(player, server, MonkMod.id("level_reward_" + l));
		}
	}

	private static void revoke(ServerPlayer player, MinecraftServer server, net.minecraft.resources.Identifier id) {
		AdvancementHolder holder = server.getAdvancements().get(id);
		if (holder == null) {
			return;
		}
		AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
		java.util.List<String> done = new java.util.ArrayList<>();
		progress.getCompletedCriteria().forEach(done::add);
		for (String criterion : done) {
			player.getAdvancements().revoke(holder, criterion);
		}
	}
}
