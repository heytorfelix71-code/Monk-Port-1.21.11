package dev.monkmod.command;

import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import dev.monkmod.ability.MonkAbility;
import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.data.MonkAttachments;
import dev.monkmod.data.MonkData;
import dev.monkmod.level.LevelTable;
import dev.monkmod.logic.MonkLogic;
import dev.monkmod.requirement.MonkRequirement;

/**
 * /monk info [player]          - level, next challenge, progress and abilities
 * /monk setlevel <level> [players]
 * /monk reload                 - re-read config/monk/*.json and rebuild advancements
 */
public final class MonkCommand {
	private MonkCommand() {
	}

	private static boolean isAdmin(CommandSourceStack source) {
		return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
				Commands.literal("monk")
						.then(Commands.literal("info")
								.requires(src -> MonkConfigManager.config().gameplay.infoCommandForEveryone || isAdmin(src))
								.executes(ctx -> info(ctx, ctx.getSource().getPlayerOrException()))
								.then(Commands.argument("player", EntityArgument.player())
										.requires(MonkCommand::isAdmin)
										.executes(ctx -> info(ctx, EntityArgument.getPlayer(ctx, "player")))))
						.then(Commands.literal("setlevel")
								.requires(MonkCommand::isAdmin)
								.then(Commands.argument("level", IntegerArgumentType.integer(-1))
										.executes(ctx -> setLevel(ctx, List.of(ctx.getSource().getPlayerOrException())))
										.then(Commands.argument("players", EntityArgument.players())
												.executes(ctx -> setLevel(ctx, EntityArgument.getPlayers(ctx, "players"))))))
						.then(Commands.literal("reload")
								.requires(MonkCommand::isAdmin)
								.executes(MonkCommand::reload))));
	}

	private static int info(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		MonkData data = MonkAttachments.data(player);
		LevelTable table = MonkConfigManager.table();
		MutableComponent text = Component.translatable("monk.command.info.header", player.getDisplayName(), data.level(), table.maxLevel())
				.withStyle(ChatFormatting.GOLD);
		MonkRequirement next = table.nextRequirement(data.level());
		if (next != null) {
			text.append("\n").append(Component.translatable("monk.command.info.next", data.level() + 1).withStyle(ChatFormatting.YELLOW))
					.append(next.description());
			if (data.maxProgress() > 0) {
				text.append("\n").append(Component.translatable("monk.command.info.progress",
						Math.min(data.progress(), data.maxProgress()), data.maxProgress()).withStyle(ChatFormatting.GRAY));
			}
		} else {
			text.append("\n").append(Component.translatable("monk.command.info.max").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		Collection<MonkAbility> abilities = table.abilitiesAt(data.level()).all();
		if (!abilities.isEmpty()) {
			text.append("\n").append(Component.translatable("monk.command.info.abilities").withStyle(ChatFormatting.YELLOW));
			for (MonkAbility ability : abilities) {
				text.append("\n - ").append(ability.describe());
			}
		}
		ctx.getSource().sendSuccess(() -> text, false);
		return data.level();
	}

	private static int setLevel(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players) throws CommandSyntaxException {
		int level = IntegerArgumentType.getInteger(ctx, "level");
		int clamped = Math.min(level, MonkConfigManager.table().maxLevel());
		for (ServerPlayer player : players) {
			MonkLogic.setLevel(player, clamped);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("monk.command.setlevel", players.size(), clamped), true);
		return players.size();
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		List<String> errors = MonkConfigManager.loadAll();
		MinecraftServer server = ctx.getSource().getServer();
		applyReload(server);
		if (errors.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.translatable("monk.command.reload.ok", MonkConfigManager.table().maxLevel() + 1), true);
		} else {
			ctx.getSource().sendFailure(Component.translatable("monk.command.reload.errors", errors.size()));
			errors.stream().limit(10).forEach(e -> ctx.getSource().sendFailure(Component.literal(" - " + e)));
		}
		return errors.isEmpty() ? 1 : 0;
	}

	/** Rebuilds advancements (datapack reload) and re-syncs every player. */
	public static void applyReload(MinecraftServer server) {
		server.reloadResources(server.getPackRepository().getSelectedIds()).thenRun(() -> server.execute(() -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				MonkLogic.resync(player);
			}
		}));
	}
}
