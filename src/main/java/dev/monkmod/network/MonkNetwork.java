package dev.monkmod.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.monkmod.ability.MonkAbility;
import dev.monkmod.ability.PassiveAbilities;
import dev.monkmod.config.MonkConfig;
import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.data.MonkData;
import dev.monkmod.level.LevelTable;
import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.logic.MonkPlayers;
import dev.monkmod.requirement.MonkRequirement;

public final class MonkNetwork {
	private MonkNetwork() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(StatePayload.TYPE, StatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ProgressPayload.TYPE, ProgressPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(BlinkDonePayload.TYPE, BlinkDonePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(BlinkPayload.TYPE, BlinkPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BlinkPayload.TYPE, (payload, context) ->
				handleBlink(context.player(), new Vec3(payload.x(), payload.y(), payload.z()), 3.0));
		PayloadTypeRegistry.playC2S().register(BlinkFarPayload.TYPE, BlinkFarPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BlinkFarPayload.TYPE, (payload, context) ->
				handleFarBlink(context.player(), new Vec3(payload.dx(), payload.dy(), payload.dz())));
		PayloadTypeRegistry.playC2S().register(FlySpeedPayload.TYPE, FlySpeedPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FlySpeedPayload.TYPE, (payload, context) -> handleFlySpeed(context.player(), payload.speed()));
		PayloadTypeRegistry.playC2S().register(StareTogglePayload.TYPE, StareTogglePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(StareTogglePayload.TYPE, (payload, context) -> {
			if (MonkPlayers.get(context.player()).abilities().has("withering_stare")) {
				dev.monkmod.ability.WitheringStare.toggle(context.player());
			}
		});
		PayloadTypeRegistry.playC2S().register(TimeStopPayload.TYPE, TimeStopPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TimeStopPayload.TYPE, (payload, context) ->
				dev.monkmod.ability.CombatAbilities.TimeStop.toggle(context.player()));
		PayloadTypeRegistry.playC2S().register(NightVisionPayload.TYPE, NightVisionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(NightVisionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (MonkPlayers.get(player).abilities().has("night_vision") && player.isAlive()) {
				PassiveAbilities.NightVision.toggle(player);
			}
		});
	}

	public static void sendState(ServerPlayer player, MonkData data, MonkPlayerState state, LevelTable table) {
		if (!ServerPlayNetworking.canSend(player, StatePayload.TYPE)) {
			return; // vanilla client: everything still works server-side
		}
		List<String> abilities = new ArrayList<>();
		for (MonkAbility ability : state.abilities().all()) {
			abilities.add(ability.params().json().toString());
		}
		MonkRequirement next = table.nextRequirement(data.level());
		MonkConfig.Blink blink = MonkConfigManager.config().blink;
		// "type|icon" of the next level, so the client can mirror interactions and draw the HUD icon.
		String nextInfo = next == null ? "" : next.key() + "|" + table.icon(next.levelToGrant());
		MonkConfig.Flight flight = MonkConfigManager.config().flight;
		ServerPlayNetworking.send(player, new StatePayload(data.level(), table.maxLevel(), nextInfo,
				abilities, blink.range, blink.cooldownTicks, blink.allowAirTarget, blink.minRange, blink.groundSearchDepth,
				flight.minSpeed, flight.maxSpeed, runScaleMax(data.level(), table)));
	}

	public static void sendProgress(ServerPlayer player, MonkData data) {
		if (ServerPlayNetworking.canSend(player, ProgressPayload.TYPE)) {
			ServerPlayNetworking.send(player, new ProgressPayload(data.progress(), data.maxProgress()));
		}
	}

	/** Validates and performs a blink. The client only suggests a destination. */
	private static void handleFarBlink(ServerPlayer player, Vec3 aim) {
		MonkPlayerState state = MonkPlayers.get(player);
		MonkConfig.Blink cfg = MonkConfigManager.config().blink;
		if (!cfg.farBlink || !state.abilities().has("blink") || player.level().getGameTime() < state.blinkReadyAt
				|| !Double.isFinite(aim.x) || !Double.isFinite(aim.y) || !Double.isFinite(aim.z)) {
			return;
		}
		double dist = Math.min(aim.length(), cfg.range);
		if (dist < 1.0) {
			return;
		}
		Vec3 dir = aim.normalize();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		BlockPos prev = null;
		BlockPos hit = null;
		// walks along the aim line; reading a block loads its chunk (from the save, or generates it) on the server
		for (double d = 0.5; d <= dist; d += 0.5) {
			Vec3 at = eye.add(dir.scale(d));
			if (at.y < level.getMinY() || at.y > level.getMaxY()) {
				break;
			}
			BlockPos pos = BlockPos.containing(at);
			if (pos.equals(prev)) {
				continue;
			}
			if (!level.getWorldBorder().isWithinBounds(pos)) {
				break;
			}
			if (solid(level, pos)) {
				hit = pos;
				break;
			}
			prev = pos;
		}
		if (hit == null || prev == null) {
			return;
		}
		BlockPos feet = prev;
		for (int i = 0; i < 64 && feet.getY() > level.getMinY() && !solid(level, feet.below()); i++) {
			feet = feet.below();
		}
		for (int up = 0; up < 6; up++, feet = feet.above()) {
			BlockPos below = feet.below();
			net.minecraft.world.phys.shapes.VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
			double y = floor.isEmpty() ? feet.getY() : below.getY() + floor.max(net.minecraft.core.Direction.Axis.Y);
			Vec3 target = new Vec3(feet.getX() + 0.5, y, feet.getZ() + 0.5);
			if (level.noCollision(player, player.getBoundingBox().move(target.subtract(player.position())))) {
				handleBlink(player, target, 72.0);
				return;
			}
		}
	}

	private static boolean solid(ServerLevel level, BlockPos pos) {
		net.minecraft.world.level.block.state.BlockState s = level.getBlockState(pos);
		return !s.getFluidState().isEmpty() || !s.getCollisionShape(level, pos).isEmpty();
	}

	private static void handleBlink(ServerPlayer player, Vec3 target, double slack) {
		MonkPlayerState state = MonkPlayers.get(player);
		MonkConfig.Blink cfg = MonkConfigManager.config().blink;
		if (!state.abilities().has("blink") || player.isSleeping() || player.isSpectator() || !player.isAlive()) {
			return;
		}
		if (cfg.requireUnarmored && !state.unarmored()) {
			return;
		}
		long now = player.level().getGameTime();
		if (now < state.blinkReadyAt) {
			return;
		}
		if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)
				|| target.distanceTo(player.getEyePosition()) > cfg.range + slack) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		AABB moved = player.getBoundingBox().move(target.subtract(player.position()));
		if (!level.noCollision(player, moved)) {
			return;
		}
		if (!cfg.allowAirTarget && !hasGround(player, level, moved)) {
			return;
		}
		if (player.isPassenger()) {
			player.stopRiding();
		}
		// No sound or particles from the server: the blinking player's own client plays them, so nobody else
		// sees or hears the blink (only the player moving).
		Vec3 from = player.position();
		player.teleportTo(target.x, target.y, target.z);
		if (ServerPlayNetworking.canSend(player, BlinkDonePayload.TYPE)) {
			ServerPlayNetworking.send(player, new BlinkDonePayload(from.x, from.y, from.z, target.x, target.y, target.z));
		}
		player.resetFallDistance();
		if (cfg.fallDamage > 0) {
			player.hurtServer(level, player.damageSources().fall(), cfg.fallDamage);
		}
		state.blinkReadyAt = now + cfg.cooldownTicks;
	}

	/** Something to stand on (a block or water) right below the box. */
	public static boolean hasGround(net.minecraft.world.entity.Entity entity, net.minecraft.world.level.Level level, AABB box) {
		AABB below = new AABB(box.minX, box.minY - 0.6, box.minZ, box.maxX, box.minY, box.maxZ);
		return !level.noCollision(entity, below) || level.containsAnyLiquid(below.inflate(0, 0.01, 0));
	}

	private static void handleFlySpeed(ServerPlayer player, float speed) {
		if (!Float.isFinite(speed) || !MonkPlayers.get(player).abilities().has("fly")) {
			return;
		}
		MonkConfig.Flight cfg = MonkConfigManager.config().flight;
		float clamped = Math.max(cfg.minSpeed, Math.min(cfg.maxSpeed, speed));
		if (player.getAbilities().getFlyingSpeed() != clamped) {
			player.getAbilities().setFlyingSpeed(clamped);
			player.onUpdateAbilities();
		}
	}

	/**
	 * Highest running bonus the player may choose with Ctrl + wheel: 100% when Swiftness is first
	 * unlocked, growing level by level up to controls.maxRunScale (200%) at the last level.
	 */
	public static float runScaleMax(int level, LevelTable table) {
		float top = MonkConfigManager.config().controls.maxRunScale;
		int max = table.maxLevel();
		int first = -1;
		for (int l = 0; l <= max; l++) {
			if (table.abilitiesAt(l).has("swift")) {
				first = l;
				break;
			}
		}
		if (first < 0 || level < first) {
			return Math.min(1.0F, top);
		}
		if (max <= first || top <= 1.0F) {
			return top;
		}
		float progress = Math.min(1.0F, (level - first) / (float) (max - first));
		return 1.0F + (top - 1.0F) * progress;
	}
}
