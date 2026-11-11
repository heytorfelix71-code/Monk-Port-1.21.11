package dev.monkmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.network.BlinkPayload;

/** Hold the Blink key to aim (particles show where you will land), release to teleport. */
final class BlinkClient {
	private static final DustParticleOptions AIM = new DustParticleOptions(0xCC00FA, 1.0F);
	private static final DustParticleOptions AIM_DIM = new DustParticleOptions(0x66007D, 0.8F);
	private static final DustParticleOptions BLOCKED = new DustParticleOptions(0x800000, 1.0F);

	private static boolean wasDown;
	private static int cooldown;

	private BlinkClient() {
	}

	static void tick(Minecraft client) {
		if (client.isPaused()) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
		}
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null || client.screen != null || !ClientMonkState.abilities().has("blink")) {
			wasDown = false;
			return;
		}
		boolean down = MonkModClient.blinkKey.isDown();
		if (down) {
			if (MonkConfigManager.config().blink.previewParticles) {
				preview(player, level);
			}
		} else if (wasDown && cooldown == 0) {
			Vec3 target = findTarget(player, level);
			if (target != null && ClientPlayNetworking.canSend(BlinkPayload.TYPE)) {
				ClientPlayNetworking.send(new BlinkPayload(target.x, target.y, target.z));
				cooldown = ClientMonkState.blinkCooldown();
			}
		}
		wasDown = down;
	}

	private static void preview(LocalPlayer player, ClientLevel level) {
		Vec3 target = findTarget(player, level);
		RandomSource random = level.getRandom();
		if (target == null) {
			Vec3 point = aimPoint(player, level);
			level.addParticle(BLOCKED, point.x, point.y, point.z, 0, 0, 0);
			return;
		}
		float chance = 1.0F - cooldown / 20.0F;
		for (int j = 0; j < 4; j++) {
			for (int i = 0; i <= 2; i++) {
				if (random.nextFloat() < chance) {
					level.addParticle(AIM, target.x + random.nextGaussian() * 0.2, target.y + i + random.nextGaussian() * 0.2,
							target.z + random.nextGaussian() * 0.2, 0, 0, 0);
				}
			}
		}
		for (int i = 0; i < 6; i++) {
			if (random.nextFloat() < chance) {
				level.addParticle(AIM_DIM, target.x, target.y + random.nextFloat() * 2.0, target.z, 0, 0, 0);
			}
		}
	}

	private static Vec3 aimPoint(LocalPlayer player, ClientLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(ClientMonkState.blinkRange()));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** Feet position to land on, or null if there is no free space near the aim point. */
	static Vec3 findTarget(LocalPlayer player, ClientLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(ClientMonkState.blinkRange()));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = hit.getBlockPos();
			Direction face = hit.getDirection();
			VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
			double top = shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y);
			// Aiming at the upper half of a wall: climb on top of it.
			if (face.getAxis() != Direction.Axis.Y && hit.getLocation().y > pos.getY() + top / 2.0) {
				Vec3 onTop = fits(player, level, new Vec3(pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5));
				if (onTop != null) {
					return onTop;
				}
			}
			if (face == Direction.UP) {
				Vec3 onFace = fits(player, level, new Vec3(hit.getLocation().x, pos.getY() + top, hit.getLocation().z));
				if (onFace != null) {
					return onFace;
				}
			}
			BlockPos side = pos.relative(face);
			Vec3 r = fits(player, level, Vec3.atBottomCenterOf(side));
			if (r == null) {
				r = fits(player, level, Vec3.atBottomCenterOf(side.below()));
			}
			return r;
		}
		Vec3 r = fits(player, level, end);
		if (r == null) {
			BlockPos p = BlockPos.containing(end);
			r = fits(player, level, Vec3.atBottomCenterOf(p));
			if (r == null) {
				r = fits(player, level, Vec3.atBottomCenterOf(p.below()));
			}
		}
		return r;
	}

	private static Vec3 fits(LocalPlayer player, ClientLevel level, Vec3 feet) {
		AABB box = player.getBoundingBox().move(feet.subtract(player.position()));
		return level.noCollision(player, box) ? feet : null;
	}
}
