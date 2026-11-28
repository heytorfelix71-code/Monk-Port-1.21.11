package dev.monkmod.logic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.config.MonkConfig;
import dev.monkmod.config.MonkConfigManager;

/**
 * Rewind (with time stopped, hold Shift): the world around you runs backwards. Built on the idea of the Rewind mod by TheEpitex (CC0),
 * made light: only what is near a monk who can stop time is remembered, ten times a second, for a few seconds — where mobs, items,
 * projectiles and other players were and their health, the blocks that changed, and the mobs that died (brought back when rewound past).
 * The monk who rewinds keeps their own place.
 */
public final class TimeRewind {
	private record Snap(ServerLevel level, double x, double y, double z, float yRot, float xRot, float health) {
	}

	private record BlockChange(ServerLevel level, BlockPos pos, BlockState old) {
	}

	private record Dead(ServerLevel level, UUID id, CompoundTag tag) {
	}

	private static final class Frame {
		final Map<UUID, Snap> entities = new HashMap<>();
		final List<BlockChange> blocks = new ArrayList<>();
		final List<Dead> dead = new ArrayList<>();
	}

	private static final Deque<Frame> HISTORY = new ArrayDeque<>();
	private static Frame current = new Frame();
	/** Where the remembering monks are this tick (blocks and deaths are only remembered near them). */
	private static final List<Vec3> RECORDERS = new ArrayList<>();
	private static final List<ServerLevel> RECORDER_LEVELS = new ArrayList<>();
	private static boolean restoring;
	private static final double RADIUS = 48.0;

	private TimeRewind() {
	}

	private static MonkConfig.Gameplay cfg() {
		return MonkConfigManager.config().gameplay;
	}

	private static boolean near(Level level, double x, double y, double z) {
		for (int i = 0; i < RECORDERS.size(); i++) {
			if (RECORDER_LEVELS.get(i) == level && RECORDERS.get(i).distanceToSqr(x, y, z) < RADIUS * RADIUS) {
				return true;
			}
		}
		return false;
	}

	/** A block is about to change (from the Level.setBlock mixin): remember what it was. */
	public static void blockChanging(Level level, BlockPos pos) {
		if (restoring || RECORDERS.isEmpty() || !(level instanceof ServerLevel server) || !cfg().rewindBlocks
				|| server.getServer().tickRateManager().isFrozen() || !near(level, pos.getX(), pos.getY(), pos.getZ())) {
			return;
		}
		if (current.blocks.size() < 4096) {
			current.blocks.add(new BlockChange(server, pos.immutable(), level.getBlockState(pos)));
		}
	}

	/** A mob died near a remembering monk: keep it, to bring it back when rewound past. */
	public static void died(LivingEntity entity) {
		if (restoring || RECORDERS.isEmpty() || entity instanceof Player || !(entity.level() instanceof ServerLevel level)
				|| !near(level, entity.getX(), entity.getY(), entity.getZ())) {
			return;
		}
		try {
			TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
			if (entity.save(out)) {
				current.dead.add(new Dead(level, entity.getUUID(), out.buildResult()));
			}
		} catch (RuntimeException e) {
			// some modded entity that can't be saved: it just stays dead
		}
	}

	/** Every server tick: who remembers, and a frame every two ticks (not while time is stopped: nothing moves then). */
	public static void tick(MinecraftServer server) {
		RECORDERS.clear();
		RECORDER_LEVELS.clear();
		if (!cfg().rewindEnabled) {
			HISTORY.clear();
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (MonkPlayers.get(p).abilities().has("time_stop")) {
				RECORDERS.add(p.position());
				RECORDER_LEVELS.add(p.level());
			}
		}
		if (RECORDERS.isEmpty()) {
			HISTORY.clear();
			return;
		}
		if (server.tickRateManager().isFrozen() || server.getTickCount() % 2 != 0) {
			return;
		}
		Frame f = current;
		for (int i = 0; i < RECORDERS.size(); i++) {
			ServerLevel level = RECORDER_LEVELS.get(i);
			Vec3 c = RECORDERS.get(i);
			for (Entity e : level.getEntities((Entity) null, new AABB(c, c).inflate(RADIUS), TimeRewind::remembered)) {
				f.entities.putIfAbsent(e.getUUID(), new Snap(level, e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(),
						e instanceof LivingEntity l ? l.getHealth() : -1F));
			}
		}
		HISTORY.addFirst(f);
		current = new Frame();
		int max = Math.max(10, Math.min(600, cfg().rewindSeconds * 10));
		while (HISTORY.size() > max) {
			HISTORY.removeLast();
		}
	}

	private static boolean remembered(Entity e) {
		return e.isAlive() && (e instanceof Mob || e instanceof ItemEntity || e instanceof Projectile || e instanceof Player);
	}

	public static int secondsLeft() {
		return (HISTORY.size() + 9) / 10;
	}

	/** One step back in time (called each tick while the monk holds Shift with time stopped). False when there is nothing left. */
	public static boolean step(ServerPlayer monk) {
		Frame f = HISTORY.pollFirst();
		if (f == null) {
			return false;
		}
		restoring = true;
		try {
			// blocks first, newest change first
			for (int i = f.blocks.size() - 1; i >= 0; i--) {
				BlockChange b = f.blocks.get(i);
				b.level().setBlock(b.pos(), b.old(), 3);
			}
			for (Dead d : f.dead) {
				if (d.level().getEntity(d.id()) == null) {
					EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, d.level().registryAccess(), d.tag()), d.level(),
							EntitySpawnReason.LOAD).ifPresent(e -> {
								if (e instanceof LivingEntity l) {
									l.setHealth(Math.max(1F, l.getMaxHealth() * 0.5F));
									l.deathTime = 0;
								}
								d.level().addFreshEntity(e);
							});
				}
			}
			for (Map.Entry<UUID, Snap> en : f.entities.entrySet()) {
				if (en.getKey().equals(monk.getUUID())) {
					continue; // the one who rewinds keeps their place
				}
				Snap s = en.getValue();
				Entity e = s.level().getEntity(en.getKey());
				if (e == null) {
					continue;
				}
				if (e instanceof ServerPlayer other) {
					other.teleportTo(s.x(), s.y(), s.z());
				} else {
					e.snapTo(s.x(), s.y(), s.z(), s.yRot(), s.xRot());
				}
				e.setDeltaMovement(Vec3.ZERO);
				if (s.health() > 0 && e instanceof LivingEntity l && l.isAlive()) {
					l.setHealth(Math.min(l.getMaxHealth(), s.health()));
				}
			}
		} finally {
			restoring = false;
		}
		ServerLevel level = monk.level();
		if (level.getServer().getTickCount() % 4 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, monk.getX(), monk.getY() + 1, monk.getZ(), 12, 0.6, 0.8, 0.6, 0.05);
		}
		if (level.getServer().getTickCount() % 10 == 0) {
			level.playSound(null, monk.getX(), monk.getY(), monk.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.6F, 0.5F);
			monk.displayClientMessage(Component.translatable("monk.rewind.going", secondsLeft()), true);
		}
		return true;
	}

	public static void clear() {
		HISTORY.clear();
		current = new Frame();
		RECORDERS.clear();
		RECORDER_LEVELS.clear();
	}
}
