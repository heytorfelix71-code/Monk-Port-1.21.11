package dev.monkmod.ability;

import java.util.List;
import java.util.function.DoubleSupplier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.type.Params;

/**
 * Passive abilities that work through game mechanics instead of status effects or speed attributes,
 * so they show no effect icon and do not stretch the field of view.
 */
public final class PassiveAbilities {
	/** Client-side "how much of the running bonus do I want" (Ctrl + mouse wheel). 1 on the server. */
	private static DoubleSupplier runScale = () -> 1.0;

	public static final float VANILLA_JUMP_HEIGHT = 1.25F;

	private PassiveAbilities() {
	}

	/** Jump velocity that reaches the given height (vanilla physics: gravity 0.08, drag 0.98). */
	public static float jumpPowerFor(float height) {
		float lo = 0.42F;
		float hi = 4.0F;
		for (int i = 0; i < 30; i++) {
			float mid = (lo + hi) / 2;
			if (simulateJump(mid) < height) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return hi;
	}

	private static double simulateJump(double v) {
		double y = 0;
		while (v > 0) {
			y += v;
			v = (v - 0.08) * 0.98;
		}
		return y;
	}

	public static void installRunScale(DoubleSupplier supplier) {
		runScale = supplier;
	}

	/**
	 * Faster running. Applied by multiplying the player's ground speed (Player#getSpeed), not with a
	 * movement-speed attribute, so the camera FOV does not change.
	 */
	public static final class Swift extends MonkAbility {
		private final float increase;
		private final float sprintMultiplier;
		private final boolean unarmoredOnly;

		private final float jumpHeight;
		private final float maxJumpHeight;
		private final float doubleJump;
		private final float maxDoubleJump;

		public Swift(Params params) {
			super("swift", params);
			this.increase = params.getFloat("increase");
			this.sprintMultiplier = params.getFloat("sprint_multiplier");
			this.unarmoredOnly = params.getBool("only_unarmored");
			this.jumpHeight = params.getFloat("jump_height");
			this.maxJumpHeight = params.getFloat("max_jump_height");
			this.doubleJump = params.getFloat("double_jump");
			this.maxDoubleJump = params.getFloat("max_double_jump");
		}

		/** Linear from {@code zero} (scale 0) to {@code atOne} (100%) and on to {@code atTop} (highest scale). */
		private static float curve(double scale, double topScale, float zero, float atOne, float atTop) {
			if (scale <= 0) {
				return zero;
			}
			if (scale <= 1.0 || topScale <= 1.0) {
				return (float) (zero + (atOne - zero) * Math.min(1.0, scale));
			}
			double t = Math.min(1.0, (scale - 1.0) / (topScale - 1.0));
			return (float) (atOne + (atTop - atOne) * t);
		}

		/** Height (blocks) of a normal jump at this running scale. */
		public float jumpHeight(double scale, double topScale) {
			return Math.max(VANILLA_JUMP_HEIGHT, curve(scale, topScale, VANILLA_JUMP_HEIGHT, jumpHeight, maxJumpHeight));
		}

		/** Extra height (blocks) of the double jump at this running scale (0 = no double jump). */
		public float doubleJumpHeight(double scale, double topScale) {
			return Math.max(0.0F, curve(scale, topScale, 0.0F, doubleJump, maxDoubleJump));
		}

		/** Client: the chosen running scale (Ctrl + wheel). */
		public static double clientScale() {
			return runScale.getAsDouble();
		}

		public boolean unarmoredOnly() {
			return unarmoredOnly;
		}

		public float multiplier(Player player) {
			double bonus = increase * (player.isSprinting() ? sprintMultiplier : 1.0F);
			if (player.level().isClientSide()) {
				bonus *= runScale.getAsDouble();
			}
			return (float) (1.0 + bonus);
		}

		@Override
		protected Object[] descriptionArgs() {
			return new Object[]{percent(increase)};
		}
	}

	/**
	 * Water breathing (partial or full) with extra swim speed. Infinite air is enforced by a mixin on
	 * the air supply, so there is no flicker; the air bubbles can be hidden on the client.
	 */
	public static final class WaterBreathing extends AttributeAbility {
		private final boolean infinite;
		private final boolean hideBubbles;

		public WaterBreathing(String key, Params params, boolean alwaysInfinite) {
			super(key, params, Condition.ALWAYS, mods(params, alwaysInfinite), a -> new Object[0]);
			this.infinite = alwaysInfinite || params.getBool("infinite");
			this.hideBubbles = true;
		}

		private static List<Mod> mods(Params params, boolean full) {
			Mod swim = new Mod(Attributes.WATER_MOVEMENT_EFFICIENCY, "swim", Operation.ADD_VALUE, (pl, s) -> params.getFloat("swim_speed"));
			if (full) {
				return List.of(swim);
			}
			return List.of(new Mod(Attributes.OXYGEN_BONUS, "oxygen", Operation.ADD_VALUE, (pl, s) -> params.getFloat("oxygen_bonus")), swim);
		}

		public boolean infinite() {
			return infinite;
		}

		public boolean hideBubbles() {
			return hideBubbles;
		}

		@Override
		public void tick(ServerPlayer player, MonkPlayerState state) {
			super.tick(player, state);
			if (infinite && player.getAirSupply() < player.getMaxAirSupply()) {
				player.setAirSupply(player.getMaxAirSupply());
			}
		}
	}

	/** Night vision toggled with its own key (V by default). */
	public static final class NightVision extends MonkAbility {
		public NightVision(Params params) {
			super("night_vision", params);
		}

		/** Our night vision: infinite, hidden particles and icon. Potions and beacons are left alone. */
		public static boolean isOurs(MobEffectInstance instance) {
			return instance != null && instance.isInfiniteDuration() && instance.isAmbient() && !instance.isVisible() && !instance.showIcon();
		}

		private static MobEffectInstance create() {
			return new MobEffectInstance(MobEffects.NIGHT_VISION, MobEffectInstance.INFINITE_DURATION, 0, true, false, false);
		}

		/** The on/off choice is saved on the player, so /effect clear, milk or death don't switch it off. */
		public static void toggle(ServerPlayer player) {
			boolean on = !player.hasAttached(dev.monkmod.data.MonkAttachments.NIGHT_VISION);
			if (on) {
				player.setAttached(dev.monkmod.data.MonkAttachments.NIGHT_VISION, true);
				MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
				if (current == null || !current.isInfiniteDuration()) {
					player.forceAddEffect(create(), null);
				}
			} else {
				player.removeAttached(dev.monkmod.data.MonkAttachments.NIGHT_VISION);
				if (isOurs(player.getEffect(MobEffects.NIGHT_VISION))) {
					player.removeEffect(MobEffects.NIGHT_VISION);
				}
			}
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkPlayerState state) {
			if (player.hasAttached(dev.monkmod.data.MonkAttachments.NIGHT_VISION) && player.isAlive()
					&& !player.hasEffect(MobEffects.NIGHT_VISION)) {
				player.forceAddEffect(create(), null);
			}
		}

		@Override
		public void onRemoved(ServerPlayer player, MonkPlayerState state) {
			player.removeAttached(dev.monkmod.data.MonkAttachments.NIGHT_VISION);
			if (isOurs(player.getEffect(MobEffects.NIGHT_VISION))) {
				player.removeEffect(MobEffects.NIGHT_VISION);
			}
		}
	}
}
