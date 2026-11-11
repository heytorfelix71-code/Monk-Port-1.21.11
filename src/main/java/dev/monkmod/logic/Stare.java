package dev.monkmod.logic;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Finds the living entity a player is looking straight at (blocks stop the gaze). */
public final class Stare {
	private Stare() {
	}

	public static LivingEntity find(ServerPlayer player, double distance, Predicate<LivingEntity> filter) {
		Level level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(look.scale(distance));
		HitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (blockHit.getType() != HitResult.Type.MISS) {
			end = blockHit.getLocation();
		}
		AABB search = new AABB(start, end).inflate(1.0);
		List<Entity> candidates = level.getEntities(player, search,
				e -> e instanceof LivingEntity living && living.isAlive() && e.isPickable() && filter.test(living));
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (Entity e : candidates) {
			Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(start, end);
			if (hit.isEmpty()) {
				continue;
			}
			double d = start.distanceToSqr(hit.get());
			if (d < bestDist && player.hasLineOfSight(e)) {
				best = (LivingEntity) e;
				bestDist = d;
			}
		}
		return best;
	}
}
