package dev.monkmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
	private static final DustParticleOptions BLOCKED = new DustParticleOptions(0x800000, 1.0F);

	private static boolean wasDown;
	/** Sneak was pressed while aiming: this blink is cancelled until the key is released. */
	private static boolean cancelled;
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
		if (!down) {
			if (cancelled) {
				cancelled = false;
				wasDown = false;
				return;
			}
		} else if (client.options.keyShift.isDown() || cancelled) {
			cancelled = true; // sneak cancels the blink, even with the key still held
			wasDown = true;
			return;
		}
		if (down) {
			if (MonkConfigManager.config().blink.previewMode != dev.monkmod.config.MonkConfig.PreviewMode.OFF) {
				preview(player, level);
			}
		} else if (wasDown && cooldown == 0) {
			Vec3 target = findTarget(player, level);
			if (target != null && ClientPlayNetworking.canSend(BlinkPayload.TYPE)) {
				Vec3 from = player.position();
				ClientPlayNetworking.send(new BlinkPayload(target.x, target.y, target.z));
				cooldown = ClientMonkState.blinkCooldown();
				dev.monkmod.config.MonkConfig.Blink cfg = MonkConfigManager.config().blink;
				float volume = cfg.sound ? Math.max(0, Math.min(100, cfg.soundVolume)) / 100.0F : 0.0F;
				if (volume > 0) {
					level.playLocalSound(from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, volume, 1.0F, false);
					level.playLocalSound(target.x, target.y, target.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, volume, 1.0F, false);
				}
			}
		}
		wasDown = down;
	}

	private static void preview(LocalPlayer player, ClientLevel level) {
		dev.monkmod.config.MonkConfig.Blink cfg = MonkConfigManager.config().blink;
		Vec3 target = findTarget(player, level);
		RandomSource random = level.getRandom();
		if (target == null) {
			Vec3 point = aimPoint(player, level);
			level.addParticle(BLOCKED, point.x, point.y, point.z, 0, 0, 0);
			return;
		}
		int color = cfg.previewColor & 0xFFFFFF;
		int density = Math.max(1, cfg.previewDensity);
		float chance = 1.0F - cooldown / 20.0F;
		DustParticleOptions main = new DustParticleOptions(color, 1.0F);
		switch (cfg.previewMode) {
			case DOT -> {
				if (random.nextFloat() < chance) {
					level.addParticle(new DustParticleOptions(color, 0.6F), target.x, target.y + 0.1, target.z, 0, 0, 0);
				}
			}
			case LINE -> {
				Vec3 from = player.getEyePosition().add(0, -0.4, 0);
				Vec3 to = target.add(0, 0.1, 0);
				double length = from.distanceTo(to);
				int steps = Math.max(2, (int) (length * density / 2.0));
				DustParticleOptions small = new DustParticleOptions(color, 0.5F);
				for (int i = 1; i <= steps; i++) {
					if (random.nextFloat() < chance) {
						Vec3 p = from.lerp(to, i / (double) steps);
						level.addParticle(small, p.x, p.y, p.z, 0, 0, 0);
					}
				}
			}
			default -> {
				DustParticleOptions dim = new DustParticleOptions(darker(color), 0.8F);
				for (int j = 0; j < density; j++) {
					for (int i = 0; i <= 2; i++) {
						if (random.nextFloat() < chance) {
							level.addParticle(main, target.x + random.nextGaussian() * 0.2, target.y + i + random.nextGaussian() * 0.2,
									target.z + random.nextGaussian() * 0.2, 0, 0, 0);
						}
					}
				}
				for (int i = 0; i < density + 2; i++) {
					if (random.nextFloat() < chance) {
						level.addParticle(dim, target.x, target.y + random.nextFloat() * 2.0, target.z, 0, 0, 0);
					}
				}
			}
		}
	}

	private static int darker(int rgb) {
		return ((rgb >> 17 & 0x7F) << 16) | ((rgb >> 9 & 0x7F) << 8) | (rgb >> 1 & 0x7F);
	}

	/**
	 * Aiming at a wall or hill from below: land on its top when that spot is inside the blink range
	 * (Blink > "climb walls"). Only without mid-air targets.
	 */
	private static Vec3 climb(LocalPlayer player, ClientLevel level) {
		dev.monkmod.config.MonkConfig.Blink cfg = MonkConfigManager.config().blink;
		if (!cfg.climbWalls || cfg.climbHeight <= 0) {
			return null;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(MonkControls.blinkDistance()));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection().getAxis() == Direction.Axis.Y) {
			return null;
		}
		BlockPos column = hit.getBlockPos();
		for (int dy = 1; dy <= cfg.climbHeight; dy++) {
			BlockPos above = column.above(dy);
			if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
				continue;
			}
			BlockPos below = above.below();
			VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
			double y = below.getY() + (floor.isEmpty() ? 0.0 : floor.max(Direction.Axis.Y));
			Vec3 spot = fits(player, level, new Vec3(column.getX() + 0.5, y, column.getZ() + 0.5));
			if (spot == null) {
				continue; // a gap too small to stand in (overhang): keep looking higher
			}
			if (eye.distanceTo(spot) > ClientMonkState.blinkRange() + 0.5) {
				return null; // the top is too far: no free ride
			}
			return spot;
		}
		return null;
	}

	private static Vec3 aimPoint(LocalPlayer player, ClientLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(MonkControls.blinkDistance()));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/**
	 * Feet position to land on, or null. Unless the server allows mid-air targets, the spot must have a
	 * block (or water) under it; when aiming at nothing, the ground below the aim point is searched.
	 */
	static Vec3 findTarget(LocalPlayer player, ClientLevel level) {
		if (!ClientMonkState.blinkAllowAir()) {
			Vec3 top = climb(player, level);
			if (top != null) {
				return top;
			}
		}
		Vec3 r = rawTarget(player, level);
		if (r == null || ClientMonkState.blinkAllowAir()) {
			return r;
		}
		AABB box = player.getBoundingBox().move(r.subtract(player.position()));
		if (dev.monkmod.network.MonkNetwork.hasGround(player, level, box)) {
			return r;
		}
		int depth = ClientMonkState.groundSearchDepth();
		if (depth <= 0) {
			return null;
		}
		BlockHitResult down = level.clip(new ClipContext(r.add(0, 0.01, 0), r.subtract(0, depth, 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		if (down.getType() != HitResult.Type.BLOCK || down.getDirection() != Direction.UP) {
			return null;
		}
		Vec3 ground = fits(player, level, down.getLocation());
		if (ground == null || player.getEyePosition().distanceTo(ground) > ClientMonkState.blinkRange() + 2.5) {
			return null;
		}
		return ground;
	}

	private static Vec3 rawTarget(LocalPlayer player, ClientLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(MonkControls.blinkDistance()));
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
