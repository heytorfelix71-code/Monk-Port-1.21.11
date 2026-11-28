package dev.monkmod.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where a long teleport lands, with as little chunk work as possible (same design in Ender Skills and Monk, no code shared):
 * 1. the part of the aim line that is already loaded is read with a normal ray (nothing is generated for it);
 * 2. beyond it the world generator is asked only for the HEIGHT of the ground along the line (no chunks), to find where the
 *    line meets the terrain;
 * 3. only the ONE chunk where you land is loaded (generated if needed, in parallel with C2ME when it is installed), and the
 *    landing is the ground of that column (heightmap), so it never depends on how far above the ground the aim ended.
 */
public final class FarTeleport {
	private FarTeleport() {
	}

	/**
	 * What can be decided right away, without loading anything: a spot (done), a failure, or "the rest is beyond the loaded
	 * area" from this distance on (then the terrain height is asked off the server thread and only the arrival chunk is loaded).
	 */
	public record Plan(Vec3 spot, boolean failed, double frontier) {
	}

	public static Plan plan(ServerLevel level, ServerPlayer p, Vec3 eye, Vec3 dir, double dist, boolean groundOnly) {
		double frontier = loadedReach(level, eye, dir, dist);
		boolean all = frontier >= dist - 0.01;
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(frontier)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		boolean hitSomething = hit.getType() != HitResult.Type.MISS;
		if (hitSomething || all) {
			double reach = hitSomething ? eye.distanceTo(hit.getLocation()) - 0.4 : dist;
			Vec3 spot = scanBack(level, p, eye, dir, reach, groundOnly, !hitSomething);
			return spot != null ? new Plan(spot, false, frontier) : new Plan(null, true, frontier);
		}
		return new Plan(null, false, frontier);
	}

	/** Spot to stand on, or null. groundOnly = it must have ground (or water) under it. */
	public static Vec3 find(ServerLevel level, ServerPlayer p, Vec3 eye, Vec3 dir, double dist, boolean groundOnly) {
		double frontier = loadedReach(level, eye, dir, dist);
		boolean all = frontier >= dist - 0.01;
		Vec3 end = eye.add(dir.scale(frontier));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		boolean hitSomething = hit.getType() != HitResult.Type.MISS;
		if (hitSomething || all) {
			double reach = hitSomething ? eye.distanceTo(hit.getLocation()) - 0.4 : dist;
			Vec3 spot = scanBack(level, p, eye, dir, reach, groundOnly, !hitSomething);
			if (spot != null) {
				return spot;
			}
			if (hitSomething) {
				return null;
			}
		}
		// beyond the loaded area: where does the line meet the ground?
		double meet = marchTerrain(level, eye, dir, frontier, dist);
		Vec3 point = eye.add(dir.scale(meet < 0 ? dist : meet));
		return landIn(level, p, point.x, point.y, point.z, groundOnly);
	}

	/** How far along the line the chunks are loaded (a block read there never generates anything). */
	private static double loadedReach(ServerLevel level, Vec3 eye, Vec3 dir, double dist) {
		double last = 0;
		for (double d = 16; d <= dist + 16; d += 16) {
			double use = Math.min(d, dist);
			Vec3 at = eye.add(dir.scale(use));
			if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(at.x, 0, at.z))
					|| !level.hasChunk((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4)) {
				return last;
			}
			last = use;
			if (use >= dist) {
				break;
			}
		}
		return last;
	}

	/** Walks back from the end of the line to the first spot where you fit; at the very end of a free line it uses the ground of that column. */
	private static Vec3 scanBack(ServerLevel level, ServerPlayer p, Vec3 eye, Vec3 dir, double reach, boolean groundOnly, boolean freeEnd) {
		Vec3 base = p.position();
		double lift = eye.y - base.y > 1 ? 0.2 : eye.y - base.y;
		int guard = 0;
		for (double d = reach; d > 0.5 && guard++ < 4000; d -= 0.5) {
			Vec3 feet = eye.add(dir.scale(d)).subtract(0, lift, 0);
			if (feet.y < level.getMinY() || feet.y > level.getMaxY()) {
				continue;
			}
			for (double up = 0; up <= 1.5; up += 0.5) {
				Vec3 spot = feet.add(0, up, 0);
				if (!level.noCollision(p, p.getBoundingBox().move(spot.subtract(base)))) {
					continue;
				}
				if (!groundOnly) {
					return spot;
				}
				if (freeEnd && d >= reach - 0.5) {
					Vec3 ground = landIn(level, p, spot.x, spot.y, spot.z, true);
					if (ground != null) {
						return ground;
					}
				}
				for (int i = 0; i < 128 && spot.y > level.getMinY(); i++) { // settle down onto the ground close below
					AABB box = p.getBoundingBox().move(spot.subtract(base));
					if (hasGround(p, level, box)) {
						return spot;
					}
					spot = spot.add(0, -0.5, 0);
				}
			}
		}
		return null;
	}

	/** The line first goes under the terrain here (asked to the world generator: no chunk is generated), or -1. */
	public static double marchTerrain(ServerLevel level, Vec3 eye, Vec3 dir, double from, double dist) {
		try {
			ChunkGenerator gen = level.getChunkSource().getGenerator();
			RandomState random = level.getChunkSource().randomState();
			double step = Math.max(16.0, dist / 400.0);
			double prev = from;
			for (double d = from + step; d <= dist + step; d += step) {
				double use = Math.min(d, dist);
				Vec3 at = eye.add(dir.scale(use));
				if (!level.getWorldBorder().isWithinBounds(BlockPos.containing(at.x, 0, at.z))) {
					return -1;
				}
				if (at.y <= gen.getBaseHeight((int) Math.floor(at.x), (int) Math.floor(at.z), Heightmap.Types.WORLD_SURFACE_WG, level, random)) {
					double lo = prev;
					double hi = use;
					for (int i = 0; i < 7; i++) {
						double mid = (lo + hi) / 2;
						Vec3 m = eye.add(dir.scale(mid));
						if (m.y <= gen.getBaseHeight((int) Math.floor(m.x), (int) Math.floor(m.z), Heightmap.Types.WORLD_SURFACE_WG, level, random)) {
							hi = mid;
						} else {
							lo = mid;
						}
					}
					return hi;
				}
				prev = use;
				if (use >= dist) {
					break;
				}
			}
		} catch (RuntimeException e) {
			// a generator that cannot answer: the end of the line is used
		}
		return -1;
	}

	/** Loads the chunk of this column (one chunk) and finds ground to stand on inside it. */
	public static Vec3 landIn(ServerLevel level, ServerPlayer p, double x, double y, double z, boolean groundOnly) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		if (!level.getWorldBorder().isWithinBounds(new BlockPos(bx, 0, bz))) {
			return null;
		}
		level.getChunk(bx >> 4, bz >> 4); // generated here if needed
		// stay away from the chunk edge so nothing of the neighbors is read
		int cx = Math.max((bx & ~15) + 2, Math.min((bx & ~15) + 13, bx));
		int cz = Math.max((bz & ~15) + 2, Math.min((bz & ~15) + 13, bz));
		if (level.dimensionType().hasCeiling()) { // Nether-like: first room found going down from the height asked, then up
			int from = (int) Math.max(level.getMinY() + 1, Math.min(level.getMaxY() - 2, y));
			for (int yy = from; yy >= level.getMinY() + 1; yy--) {
				Vec3 at = new Vec3(cx + 0.5, yy, cz + 0.5);
				if (fits(level, p, at, groundOnly)) {
					return at;
				}
			}
			for (int yy = from + 1; yy <= level.getMaxY() - 2; yy++) {
				Vec3 at = new Vec3(cx + 0.5, yy, cz + 0.5);
				if (fits(level, p, at, groundOnly)) {
					return at;
				}
			}
			return null;
		}
		for (int r = 0; r <= 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int px = cx + dx;
					int pz = cz + dz;
					int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
					for (int yy = top; yy <= Math.min(level.getMaxY() - 2, top + 6); yy++) {
						Vec3 at = new Vec3(px + 0.5, yy, pz + 0.5);
						if (fits(level, p, at, groundOnly)) {
							return at;
						}
					}
				}
			}
		}
		return null;
	}

	private static boolean fits(ServerLevel level, ServerPlayer p, Vec3 at, boolean groundOnly) {
		AABB box = p.getBoundingBox().move(at.subtract(p.position()));
		return level.noCollision(p, box) && (!groundOnly || hasGround(p, level, box));
	}

	private static boolean hasGround(ServerPlayer p, ServerLevel level, AABB box) {
		AABB below = new AABB(box.minX, box.minY - 0.6, box.minZ, box.maxX, box.minY, box.maxZ);
		return !level.noCollision(p, below) || level.containsAnyLiquid(below.inflate(0, 0.01, 0));
	}
}
