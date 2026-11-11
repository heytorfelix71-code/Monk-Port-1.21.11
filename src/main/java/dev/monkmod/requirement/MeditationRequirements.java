package dev.monkmod.requirement;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.data.MonkData;
import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.logic.Stare;
import dev.monkmod.type.Params;

/** Requirements checked every tick: meditation, breath holding, staring contests. */
public final class MeditationRequirements {
	private MeditationRequirements() {
	}

	/** Celestial angle (0..1) as vanilla computes it from the day time. */
	public static double celestialAngle(Level level) {
		double d = Mth.frac(level.getDayTime() / 24000.0 - 0.25);
		double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
		return (d * 2.0 + e) / 3.0;
	}

	/** Swim until your lungs nearly burst. */
	public static final class Drown extends MonkRequirement {
		public Drown(Params params) {
			super("drown", params, params.getInt("swim_time"));
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkData data, MonkPlayerState state) {
			if (!player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() > player.getMaxAirSupply() - 5) {
				data.resetProgress();
				return;
			}
			if (data.increase(1, limit) && player.getRandom().nextInt(50) == 0) {
				grant(player, data);
				player.setAirSupply(player.getMaxAirSupply());
			}
		}
	}

	/** Look at the sun while it touches the horizon. */
	public static final class Sunrise extends MonkRequirement {
		public Sunrise(Params params) {
			super("mediate_sunrise", params, params.getInt("time"));
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkData data, MonkPlayerState state) {
			if (player.level().dimension() != Level.OVERWORLD) {
				data.resetProgress();
				return;
			}
			double angle = celestialAngle(player.level()) * Math.PI * 2.0;
			double sunHeight = Math.cos(angle);
			if (Math.abs(sunHeight) < 0.1) {
				Vec3 sun = new Vec3(-Math.sin(angle), sunHeight, 0.0);
				if (sun.dot(player.getViewVector(1.0F)) > 0.99) {
					progress(player, data, 1);
					return;
				}
			}
			data.resetProgress();
		}
	}

	/** From a boat, contemplate the midnight moon. */
	public static final class WaterMoon extends MonkRequirement {
		public WaterMoon(Params params) {
			super("meditate_water_moon", params, params.getInt("stare_time"));
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkData data, MonkPlayerState state) {
			Entity vehicle = player.getVehicle();
			if (vehicle == null || !vehicle.getType().is(EntityTypeTags.BOAT) || player.level().dimension() != Level.OVERWORLD) {
				data.resetProgress();
				return;
			}
			double angle = celestialAngle(player.level()) * Math.PI * 2.0;
			double sunHeight = Math.cos(angle);
			if (sunHeight < -0.2) {
				Vec3 sun = new Vec3(-Math.sin(angle), sunHeight, 0.0);
				if (sun.dot(player.getViewVector(1.0F)) < -0.99) {
					progress(player, data, 1);
					return;
				}
			}
			data.resetProgress();
		}
	}

	/** Stay near endermen without angering them. */
	public static final class Endermen extends MonkRequirement {
		private final double radius;

		public Endermen(Params params) {
			super("meditate_endermen", params, params.getInt("time"));
			this.radius = params.getFloat("radius");
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkData data, MonkPlayerState state) {
			int interval = MonkConfigManager.config().performance.endermanScanInterval;
			if ((player.tickCount + state.tickOffset) % interval != 0) {
				return;
			}
			AABB box = player.getBoundingBox().inflate(radius);
			List<Mob> endermen = player.level().getEntitiesOfClass(Mob.class, box, m -> m.getType() == EntityType.ENDERMAN && m.isAlive());
			if (endermen.isEmpty()) {
				return;
			}
			for (Mob enderman : endermen) {
				if (enderman.getTarget() != null || (enderman instanceof NeutralMob neutral && neutral.isAngry())) {
					data.resetProgress();
					return;
				}
			}
			progress(player, data, endermen.size() * interval);
		}
	}

	/** Win a staring contest against a wither skeleton inside a nether fortress. */
	public static final class WitherStare extends MonkRequirement {
		public WitherStare(Params params) {
			super("wither_stare", params, params.getInt("stare_time"));
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkData data, MonkPlayerState state) {
			int interval = MonkConfigManager.config().performance.stareInterval;
			if ((player.tickCount + state.tickOffset) % interval != 0) {
				return;
			}
			ServerLevel level = (ServerLevel) player.level();
			if (level.structureManager().getStructureWithPieceAt(player.blockPosition(), holder -> holder.is(BuiltinStructures.FORTRESS)).isValid()) {
				LivingEntity skeleton = Stare.find(player, 20, e -> e.getType() == EntityType.WITHER_SKELETON
						&& e instanceof Mob mob && mob.getTarget() == player);
				if (skeleton != null) {
					if (data.increase(interval, limit)) {
						((Mob) skeleton).setTarget(null);
						((Mob) skeleton).getNavigation().stop();
						grant(player, data);
					}
					return;
				}
			}
			data.setProgress(Math.max(0, data.progress() - 4 * interval), limit);
		}
	}
}
