package dev.monkmod.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.monkmod.network.ShadowPortalPayloads;

/**
 * Shadow portals (Monk level 20): a pair of doors shaped like your own shadow (or a rift), the entrance in front of you and the exit
 * at the coordinates you typed, in any dimension. Players, mobs, items and projectiles go through, both ways, keeping their speed.
 * Light on the game: a portal nobody is near does nothing (standby), and only the one chunk of the far end is kept loaded, only
 * while someone is close to one of its ends.
 */
public final class ShadowPortals {
	private static final class End {
		final ServerLevel level;
		Vec3 pos;
		final float yaw;

		End(ServerLevel level, Vec3 pos, float yaw) {
			this.level = level;
			this.pos = pos;
			this.yaw = yaw;
		}
	}

	private static final class Pair {
		final UUID owner;
		final End a;
		final End b;
		final int until;
		boolean ready;

		Pair(UUID owner, End a, End b, int until) {
			this.owner = owner;
			this.a = a;
			this.b = b;
			this.until = until;
		}
	}

	private static final List<Pair> PAIRS = new ArrayList<>();
	/** Entities that just went through (no ping-pong): uuid -> until which tick. */
	private static final Map<UUID, Integer> RECENT = new HashMap<>();

	private ShadowPortals() {
	}

	private static void say(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable(key, args), true);
	}

	/** Opens a pair for this player (an older pair of theirs closes). */
	public static void create(ServerPlayer p, ServerLevel dest, Vec3 target, int seconds) {
		MinecraftServer server = p.level().getServer();
		PAIRS.removeIf(x -> x.owner.equals(p.getUUID()));
		Vec3 front = Vec3.directionFromRotation(0, p.getYRot());
		End a = new End((ServerLevel) p.level(), p.position().add(front.x * 2.0, 0, front.z * 2.0), p.getYRot());
		End b = new End(dest, target, p.getYRot());
		Pair pair = new Pair(p.getUUID(), a, b, server.getTickCount() + 20 * Math.max(5, seconds));
		dest.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, new ChunkPos(BlockPos.containing(target)), 0);
		PAIRS.add(pair);
		p.level().playSound(null, a.pos.x, a.pos.y, a.pos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.7F);
		say(p, "monk.portal.opened", (long) Math.floor(target.x), (long) Math.floor(target.y), (long) Math.floor(target.z), Math.max(5, seconds));
	}

	/** A spot to stand at, near the typed one: out of blocks and onto the ground (only reads the loaded chunk). */
	private static Vec3 snap(ServerLevel level, Vec3 at) {
		AABB body = new AABB(at.x - 0.3, at.y, at.z - 0.3, at.x + 0.3, at.y + 1.8, at.z + 0.3);
		if (level.noCollision(body)) {
			for (int i = 0; i < 6; i++) { // a little above the ground: drop onto it
				if (!level.noCollision(body.move(0, -0.25, 0))) {
					return new Vec3(at.x, body.minY, at.z);
				}
				body = body.move(0, -0.5, 0);
			}
		}
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
		return new Vec3(Math.floor(at.x) + 0.5, top, Math.floor(at.z) + 0.5);
	}

	/** Arriving a step in front of the other door (never inside it, so nobody bounces back). */
	private static Vec3 stepOut(ServerLevel level, Entity e, End o) {
		Vec3 front = Vec3.directionFromRotation(0, o.yaw);
		double[] turns = {0, 90, -90, 180};
		for (double turn : turns) {
			Vec3 d = front.yRot((float) Math.toRadians(turn));
			for (double dist : new double[] {1.5, 2.2}) {
				Vec3 spot = o.pos.add(d.x * dist, 0, d.z * dist);
				AABB box = e.getBoundingBox().move(spot.subtract(e.position()));
				if (level.noCollision(e, box)) {
					return spot;
				}
			}
		}
		return o.pos;
	}

	private static boolean someoneNear(End e, double r) {
		return e.level.getNearestPlayer(e.pos.x, e.pos.y, e.pos.z, r, false) != null;
	}

	public static void tick(MinecraftServer server) {
		if (PAIRS.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		RECENT.values().removeIf(t -> t <= now);
		for (Iterator<Pair> it = PAIRS.iterator(); it.hasNext();) {
			Pair pair = it.next();
			if (pair.until <= now) {
				it.remove();
				continue;
			}
			if (!pair.ready) {
				BlockPos bp = BlockPos.containing(pair.b.pos);
				if (pair.b.level.getChunkSource().getChunkNow(bp.getX() >> 4, bp.getZ() >> 4) == null) {
					continue; // the far end is still loading
				}
				pair.b.pos = snap(pair.b.level, pair.b.pos);
				pair.a.pos = snap(pair.a.level, pair.a.pos);
				pair.ready = true;
			}
			boolean nearA = someoneNear(pair.a, 48);
			boolean nearB = someoneNear(pair.b, 48);
			if (!nearA && !nearB) {
				continue; // standby
			}
			if (now % 100 == 0) { // keep the other end's chunk loaded while someone is close
				End far = nearA ? pair.b : pair.a;
				far.level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, new ChunkPos(BlockPos.containing(far.pos)), 0);
			}
			if (nearA) {
				cross(pair.a, pair.b, now);
			}
			if (nearB) {
				cross(pair.b, pair.a, now);
			}
		}
	}

	private static void cross(End from, End to, int now) {
		AABB door = new AABB(from.pos.x - 0.45, from.pos.y, from.pos.z - 0.45, from.pos.x + 0.45, from.pos.y + 1.9, from.pos.z + 0.45);
		List<Entity> going = from.level.getEntities((Entity) null, door,
				e -> e.isAlive() && !e.isSpectator() && !e.isPassenger() && !RECENT.containsKey(e.getUUID()));
		for (Entity e : going) {
			Vec3 out = stepOut(to.level, e, to);
			RECENT.put(e.getUUID(), now + 20);
			Entity moved = e.teleport(new TeleportTransition(to.level, out, e.getDeltaMovement(), e.getYRot(), e.getXRot(), TeleportTransition.DO_NOTHING));
			if (moved != null) {
				RECENT.put(moved.getUUID(), now + 20);
				moved.resetFallDistance();
			}
			if (e instanceof Player) {
				to.level.playSound(null, out.x, out.y, out.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.2F);
			}
		}
	}

	/** Every half second: the doors near each player, for their client to draw. */
	public static void sync(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!ServerPlayNetworking.canSend(p, ShadowPortalPayloads.Ends.TYPE)) {
				continue;
			}
			List<double[]> ends = new ArrayList<>();
			for (Pair pair : PAIRS) {
				if (!pair.ready) {
					continue;
				}
				Entity owner = server.getPlayerList().getPlayer(pair.owner);
				for (End e : new End[] {pair.a, pair.b}) {
					if (e.level == p.level() && e.pos.distanceToSqr(p.position()) < 96 * 96) {
						ends.add(new double[] {e.pos.x, e.pos.y, e.pos.z, e.yaw, owner != null && owner.level() == p.level() ? owner.getId() : -1});
					}
				}
			}
			if (!ends.isEmpty() || p.tickCount % 40 == 0) {
				ServerPlayNetworking.send(p, new ShadowPortalPayloads.Ends(ends));
			}
		}
	}

	public static void clear() {
		PAIRS.clear();
		RECENT.clear();
	}
}
