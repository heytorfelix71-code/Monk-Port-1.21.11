package dev.monkmod.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import dev.monkmod.MonkMod;
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
				handleClientBlink(context.player(), new Vec3(payload.x(), payload.y(), payload.z())));
		PayloadTypeRegistry.playC2S().register(BlinkFarPayload.TYPE, BlinkFarPayload.CODEC);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(MonkNetwork::tickJobs);
		ServerPlayNetworking.registerGlobalReceiver(BlinkFarPayload.TYPE, (payload, context) ->
				handleFarBlink(context.player(), new Vec3(payload.dx(), payload.dy(), payload.dz())));
		PayloadTypeRegistry.playC2S().register(BlinkPreloadPayload.TYPE, BlinkPreloadPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(BlinkPreloadPayload.TYPE, (payload, context) ->
				handlePreload(context.player(), new Vec3(payload.dx(), payload.dy(), payload.dz())));
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

	/**
	 * A blink to a spot the CLIENT saw. With mods that keep chunks on the client past what the server has loaded (a chunk cache, LODs),
	 * the spot can be in a chunk the server does not have: reading it there looks like empty air, so the old check said "no ground".
	 * Then the arrival chunk is loaded first (like a far blink) and the landing is found there.
	 */
	private static void handleClientBlink(ServerPlayer player, Vec3 target) {
		if (Double.isFinite(target.x) && Double.isFinite(target.y) && Double.isFinite(target.z)) {
			ServerLevel level = (ServerLevel) player.level();
			BlockPos at = BlockPos.containing(target);
			MonkPlayerState state = MonkPlayers.get(player);
			if (state.abilities().has("blink") && level.getWorldBorder().isWithinBounds(at) && !level.hasChunkAt(at)
					&& player.level().getGameTime() >= state.blinkReadyAt) {
				handleFarBlink(player, target.subtract(player.getEyePosition()));
				return;
			}
		}
		handleBlink(player, target, 3.0);
	}

	/** Validates and performs a blink. The client only suggests a destination. */
	private static void handleFarBlink(ServerPlayer player, Vec3 aim) {
		MonkPlayerState state = MonkPlayers.get(player);
		MonkConfig.Blink cfg = MonkConfigManager.config().blink;
		if (!state.abilities().has("blink") || player.level().getGameTime() < state.blinkReadyAt
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
		dist = Math.min(dist, Math.max(64F, cfg.syncRange)); // like the Ender Skills teleport: its range is the limit
		// one design for both mods: the loaded part with a ray, the rest from the terrain height, one chunk loaded where you land
		Vec3 spot = dev.monkmod.logic.FarTeleport.find(level, player, eye, dir, dist, !cfg.allowAirTarget);
		if (spot == null) {
			say(player, "monk.blink.no_ground");
			return;
		}
		handleBlink(player, spot, 72.0);
	}

	/** A far blink in progress: the arrival chunk is being loaded (the way an ender pearl does it). */
	private static final class FarJob {
		final ServerLevel level;
		final double x;
		final double z;
		final int cx;
		final int cz;
		/** Height of the aim line where it ends: a landing near this height (an island in the sky) is preferred. */
		final double y;
		int waited;

		FarJob(ServerLevel level, double x, double y, double z) {
			this.level = level;
			this.x = x;
			this.y = y;
			this.z = z;
			this.cx = (int) Math.floor(x) >> 4;
			this.cz = (int) Math.floor(z) >> 4;
		}
	}

	private static final java.util.Map<java.util.UUID, FarJob> JOBS = new java.util.HashMap<>();

	private static void say(ServerPlayer player, String key) {
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
	}

	/**
	 * The end of the blink distance, kept inside the world and its border. No terrain is searched on the way: nothing
	 * between you and there is loaded or generated (that is what made a million-block blink take minutes, or nothing).
	 */
	private static Vec3 farEnd(ServerLevel level, Vec3 eye, Vec3 dir, double dist) {
		double d = dist;
		Vec3 end = eye.add(dir.scale(d));
		for (int i = 0; i < 300 && (Math.abs(end.x) > 29_999_000.0 || Math.abs(end.z) > 29_999_000.0
				|| !level.getWorldBorder().isWithinBounds(BlockPos.containing(end.x, 0, end.z))); i++) {
			d *= 0.97;
			end = eye.add(dir.scale(d));
		}
		if (Math.abs(end.x) > 29_999_000.0 || Math.abs(end.z) > 29_999_000.0
				|| !level.getWorldBorder().isWithinBounds(BlockPos.containing(end.x, 0, end.z))) {
			return null;
		}
		return end;
	}

	/**
	 * While the key is held on a far target, the arrival chunks start loading (3x3 around it, enough to land): by the time the key
	 * is released they are usually ready and the blink is almost instant. The ticket expires by itself if you change your mind.
	 */
	private static void handlePreload(ServerPlayer player, Vec3 aim) {
		MonkConfig.Blink cfg = MonkConfigManager.config().blink;
		if (!cfg.farBlink || !MonkPlayers.get(player).abilities().has("blink") || !Double.isFinite(aim.x) || !Double.isFinite(aim.y)
				|| !Double.isFinite(aim.z)) {
			return;
		}
		double dist = Math.min(aim.length(), cfg.range);
		if (dist <= 512) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 end = farEnd(level, player.getEyePosition(), aim.normalize(), dist);
		if (end != null) {
			level.getChunkSource().addTicketAndLoadWithRadius(net.minecraft.server.level.TicketType.PORTAL,
					new net.minecraft.world.level.ChunkPos((int) Math.floor(end.x) >> 4, (int) Math.floor(end.z) >> 4), 1);
		}
	}

	/**
	 * Where the aim line first goes under the terrain, WITHOUT generating anything: the world generator is asked for the height of the
	 * ground at points along the line (cheap compared with generating the chunks), so the blink goes to what you aimed at (the mountain,
	 * the coast) and not to the end of the configured distance. Null when the line stays above the terrain all the way.
	 */
	private static Vec3 marchTerrain(ServerLevel level, Vec3 eye, Vec3 dir, double dist) {
		try {
			net.minecraft.world.level.chunk.ChunkGenerator gen = level.getChunkSource().getGenerator();
			net.minecraft.world.level.levelgen.RandomState random = level.getChunkSource().randomState();
			double step = Math.max(16.0, dist / 500.0);
			double prev = 0;
			for (double d = step; d <= dist; d += step) {
				Vec3 at = eye.add(dir.scale(d));
				if (Math.abs(at.x) > 29_999_000.0 || Math.abs(at.z) > 29_999_000.0 || !level.getWorldBorder().isWithinBounds(BlockPos.containing(at.x, 0, at.z))) {
					return null;
				}
				if (at.y <= gen.getBaseHeight((int) Math.floor(at.x), (int) Math.floor(at.z),
						net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random)) {
					double lo = prev;
					double hi = d;
					for (int i = 0; i < 7; i++) { // narrow down where the line goes under the ground
						double mid = (lo + hi) / 2;
						Vec3 m = eye.add(dir.scale(mid));
						if (m.y <= gen.getBaseHeight((int) Math.floor(m.x), (int) Math.floor(m.z),
								net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random)) {
							hi = mid;
						} else {
							lo = mid;
						}
					}
					return eye.add(dir.scale(hi));
				}
				prev = d;
			}
		} catch (RuntimeException e) {
			MonkMod.LOGGER.warn("Could not read the terrain height along the blink line: {}", e.toString());
		}
		return null;
	}

	private static void startFarJob(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 dir, double dist) {
		Vec3 end = marchTerrain(level, eye, dir, dist);
		if (end == null) {
			end = farEnd(level, eye, dir, dist); // the aim never goes under the terrain: the end of the distance, on the ground there
		}
		if (end == null) {
			say(player, "monk.blink.no_ground");
			return;
		}
		double d = eye.distanceTo(end);
		FarJob job = new FarJob(level, end.x, end.y, end.z);
		level.getChunkSource().addTicketAndLoadWithRadius(net.minecraft.server.level.TicketType.PORTAL,
				new net.minecraft.world.level.ChunkPos(job.cx, job.cz), 0);
		JOBS.put(player.getUUID(), job);
		say(player, "monk.blink.loading");
		MonkMod.LOGGER.info("Far blink of {}: {} blocks, loading the chunk at {} {}", player.getName().getString(), (long) d, job.cx, job.cz);
	}

	private static void tickJobs(net.minecraft.server.MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}
		for (java.util.Iterator<java.util.Map.Entry<java.util.UUID, FarJob>> it = JOBS.entrySet().iterator(); it.hasNext();) {
			java.util.Map.Entry<java.util.UUID, FarJob> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			FarJob job = e.getValue();
			if (player == null || !player.isAlive() || player.level() != job.level) {
				it.remove();
				continue;
			}
			job.waited++;
			if (job.level.getChunkSource().getChunkNow(job.cx, job.cz) != null) {
				it.remove();
				Vec3 spot = landSpot(player, job.level, job.x, job.y, job.z);
				if (spot == null || !handleBlink(player, spot, 4096.0)) {
					say(player, "monk.blink.no_ground");
				}
			} else if (job.waited > 20 * 60) {
				it.remove();
				say(player, "monk.blink.timeout");
			} else if (job.waited % 40 == 0) {
				say(player, "monk.blink.loading");
			}
		}
	}

	/** Somewhere to stand in the loaded arrival chunk: the column itself, or the closest one around it. */
	private static Vec3 landSpot(ServerPlayer player, ServerLevel level, double x, double y, double z) {
		// only inside the loaded chunk (never read its neighbors: that would generate them one by one and freeze for a long time)
		int bx = Math.max(((int) Math.floor(x) & ~15) + 3, Math.min(((int) Math.floor(x) & ~15) + 12, (int) Math.floor(x)));
		int bz = Math.max(((int) Math.floor(z) & ~15) + 3, Math.min(((int) Math.floor(z) & ~15) + 12, (int) Math.floor(z)));
		if (!level.dimensionType().hasCeiling()) {
			// first: ground near the height you aimed at (an island in the sky, a ledge), highest spot below the aim line
			int top = (int) Math.floor(y) + 12;
			for (int r = 0; r <= 2; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						for (int yy = Math.min(level.getMaxY() - 2, top); yy >= Math.max(level.getMinY() + 1, top - 36); yy--) {
							Vec3 at = new Vec3(bx + dx + 0.5, yy, bz + dz + 0.5);
							if (fitsAt(player, level, at)) {
								return at;
							}
						}
					}
				}
			}
		}
		for (int r = 0; r <= 2; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					Vec3 spot = standing(player, level, bx + dx, bz + dz);
					if (spot != null) {
						return spot;
					}
				}
			}
		}
		return null;
	}

	private static boolean fitsAt(ServerPlayer player, ServerLevel level, Vec3 at) {
		AABB box = player.getBoundingBox().move(at.subtract(player.position()));
		return level.noCollision(player, box) && (MonkConfigManager.config().blink.allowAirTarget || hasGround(player, level, box));
	}

	private static Vec3 standing(ServerPlayer player, ServerLevel level, int x, int z) {
		int min = level.getMinY() + 1;
		int max = level.getMaxY() - 2;
		if (level.dimensionType().hasCeiling()) {
			// Nether-like: no open sky, so the first room found going down from your own height, then up
			int from = Math.max(min, Math.min(max, player.getBlockY()));
			for (int y = from; y >= min; y--) {
				Vec3 at = new Vec3(x + 0.5, y, z + 0.5);
				if (fitsAt(player, level, at)) {
					return at;
				}
			}
			for (int y = from + 1; y <= max; y++) {
				Vec3 at = new Vec3(x + 0.5, y, z + 0.5);
				if (fitsAt(player, level, at)) {
					return at;
				}
			}
			return null;
		}
		int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
		for (int y = Math.max(min, top); y <= Math.min(max, top + 8); y++) {
			Vec3 at = new Vec3(x + 0.5, y, z + 0.5);
			if (fitsAt(player, level, at)) {
				return at;
			}
		}
		return null;
	}

	private static boolean solid(ServerLevel level, BlockPos pos) {
		net.minecraft.world.level.block.state.BlockState s = level.getBlockState(pos);
		return !s.getFluidState().isEmpty() || !s.getCollisionShape(level, pos).isEmpty();
	}

	private static boolean handleBlink(ServerPlayer player, Vec3 target, double slack) {
		MonkPlayerState state = MonkPlayers.get(player);
		MonkConfig.Blink cfg = MonkConfigManager.config().blink;
		if (!state.abilities().has("blink") || player.isSleeping() || player.isSpectator() || !player.isAlive()) {
			return false;
		}
		if (cfg.requireUnarmored && !state.unarmored()) {
			say(player, "monk.blink.armor");
			return false;
		}
		long now = player.level().getGameTime();
		if (now < state.blinkReadyAt) {
			return false;
		}
		if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)
				|| target.distanceTo(player.getEyePosition()) > cfg.range + slack) {
			return false;
		}
		ServerLevel level = (ServerLevel) player.level();
		AABB moved = player.getBoundingBox().move(target.subtract(player.position()));
		if (!level.noCollision(player, moved) || (!cfg.allowAirTarget && !hasGround(player, level, moved))) {
			say(player, "monk.blink.no_ground");
			return false;
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
		return true;
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
