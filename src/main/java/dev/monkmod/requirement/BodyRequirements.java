package dev.monkmod.requirement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.data.MonkData;
import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.type.Params;

/** Requirements about moving, falling and sleeping. */
public final class BodyRequirements {
	private BodyRequirements() {
	}

	/** Cover a distance on foot while a condition holds (sprint, walk_fire). Jumps do not count. */
	public static final class Walk extends MonkRequirement {
		public enum Mode { SPRINT, FIRE }

		private final Mode mode;

		public Walk(String key, Params params, Mode mode) {
			super(key, params, Math.round(params.getFloat("distance") * 100));
			this.mode = mode;
		}

		private boolean conditionHolds(ServerPlayer player, MonkPlayerState state) {
			if (!state.unarmored()) {
				return false;
			}
			return switch (mode) {
				case SPRINT -> player.isSprinting();
				case FIRE -> player.isOnFire() && (player.isInLava()
						|| player.level().getBlockStates(player.getBoundingBox()).anyMatch(s -> s.is(BlockTags.FIRE)));
			};
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkData data, MonkPlayerState state) {
			if (!conditionHolds(player, state)) {
				state.tracking = false;
				data.resetProgress();
				return;
			}
			double x = player.getX();
			double z = player.getZ();
			if (state.tracking && player.onGround()) {
				double dx = x - state.trackX;
				double dz = z - state.trackZ;
				int cm = (int) Math.round(Math.sqrt(dx * dx + dz * dz) * 100);
				if (cm > 0 && cm < 200) {
					progress(player, data, cm);
				}
			}
			state.tracking = true;
			state.trackX = x;
			state.trackZ = z;
		}

		@Override
		protected void onGrant(ServerPlayer player) {
			if (mode == Mode.FIRE) {
				player.clearFire();
				player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 2));
			}
		}

		@Override
		protected Object[] descriptionArgs() {
			return new Object[]{limit / 100.0F};
		}
	}

	/** A leap of faith from a great height onto a soft block (hay by default). */
	public static final class Fall extends MonkRequirement {
		private final double distance;
		private final Block landing;

		public Fall(Params params) {
			super("fall", params, -1);
			this.distance = params.getFloat("distance");
			Identifier id = Identifier.tryParse(params.getString("block"));
			this.landing = id == null ? Blocks.HAY_BLOCK : BuiltInRegistries.BLOCK.getOptional(id).orElse(Blocks.HAY_BLOCK);
		}

		@Override
		public boolean allowDamage(ServerPlayer player, MonkData data, MonkPlayerState state, DamageSource source, float amount) {
			if (!source.is(DamageTypeTags.IS_FALL) || player.fallDistance < distance) {
				return true;
			}
			BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
			if (player.level().getBlockState(below).is(landing)) {
				grant(player, data);
				return false;
			}
			return true;
		}
	}

	/** Fall into the void, contemplate eternity, and come back. */
	public static final class VoidFall extends MonkRequirement {
		private final int messages;

		public VoidFall(Params params) {
			super("void_fall", params, -1);
			this.messages = params.getInt("messages");
		}

		@Override
		public boolean allowDeath(ServerPlayer player, MonkData data, MonkPlayerState state, DamageSource source, float amount) {
			if (!source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
				return true;
			}
			player.setHealth(player.getMaxHealth());
			int step = data.progress();
			if (step >= messages) {
				grant(player, data);
				MinecraftServer server = player.level().getServer();
				if (server != null) {
					ServerLevel overworld = server.overworld();
					Vec3 spawn = player.adjustSpawnLocation(overworld, overworld.getRespawnData().pos()).getBottomCenter();
					player.teleport(new TeleportTransition(overworld, spawn, Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
					player.resetFallDistance();
				}
			} else {
				player.sendSystemMessage(Component.translatable("monk.final." + step));
				data.setProgress(step + 1, messages);
			}
			return false;
		}
	}

	/** Sleep on a strange "bed" (bedrock, cactus...) and wake up refreshed. */
	public static final class Sleep extends MonkRequirement {
		public enum Kind { BEDROCK, CACTUS }

		private final Kind kind;

		public Sleep(String key, Params params, Kind kind) {
			super(key, params, -1);
			this.kind = kind;
		}

		/** Checks the block the player lies on ("the bed"). */
		public boolean isBed(Level level, BlockPos bed) {
			BlockState state = level.getBlockState(bed);
			if (!level.getBlockState(bed.above()).isAir()) {
				return false;
			}
			return switch (kind) {
				case CACTUS -> state.is(Blocks.CACTUS);
				case BEDROCK -> {
					if (!state.is(Blocks.BEDROCK)) {
						yield false;
					}
					for (Direction d : Direction.Plane.HORIZONTAL) {
						BlockPos other = bed.relative(d);
						if (level.getBlockState(other).is(Blocks.BEDROCK) && level.getBlockState(other.above()).isAir()) {
							yield true;
						}
					}
					yield false;
				}
			};
		}

		private static boolean isNight(Level level) {
			long t = Math.floorMod(level.getDayTime(), 24000L);
			return t >= 12542L && t <= 23459L;
		}

		@Override
		public InteractionResult onUseBlock(ServerPlayer player, MonkData data, MonkPlayerState state, BlockHitResult hit, InteractionHand hand) {
			Level level = player.level();
			BlockPos bed = hit.getBlockPos();
			if (level.dimension() != Level.OVERWORLD || !isBed(level, bed)) {
				return null;
			}
			if (player.isSleeping() || player.isShiftKeyDown()) {
				return null;
			}
			if (!isNight(level)) {
				player.displayClientMessage(Component.translatable("block.minecraft.bed.no_sleep"), true);
				return InteractionResult.SUCCESS;
			}
			player.startSleeping(bed.above());
			return InteractionResult.SUCCESS;
		}

		@Override
		public boolean isValidSleepSpot(ServerPlayer player, BlockPos sleepingPos) {
			return isBed(player.level(), sleepingPos.below());
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkData data, MonkPlayerState state) {
			if (player.isSleeping() && player.getSleepTimer() >= 100) {
				player.getSleepingPos().ifPresent(pos -> {
					if (isBed(player.level(), pos.below())) {
						grant(player, data);
					}
				});
			}
		}

		@Override
		public boolean allowDamage(ServerPlayer player, MonkData data, MonkPlayerState state, DamageSource source, float amount) {
			if (kind == Kind.CACTUS && source.is(DamageTypes.CACTUS) && player.isSleeping()) {
				return false;
			}
			return true;
		}

		/** Client-side helper so the hand swings when using a valid "bed". */
		public static boolean looksLikeBed(String key, Player player, BlockPos pos) {
			BlockState state = player.level().getBlockState(pos);
			return ("bedrock_sleep".equals(key) && state.is(Blocks.BEDROCK)) || ("sleep_cactus".equals(key) && state.is(Blocks.CACTUS));
		}
	}
}
