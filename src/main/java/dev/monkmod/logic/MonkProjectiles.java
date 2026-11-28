package dev.monkmod.logic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.ability.SimpleAbility;

/**
 * The "catch arrows" ability: projectiles coming from the front are caught and go to the inventory;
 * with the upgraded ability, projectiles from behind are dodged (with a chance to be caught as well).
 */
public final class MonkProjectiles {
	private MonkProjectiles() {
	}

	/** True while this player is dodging that projectile. */
	public static boolean isDodging(Projectile projectile, Entity target) {
		if (!(target instanceof ServerPlayer player)) {
			return false;
		}
		MonkPlayerState state = MonkPlayers.getIfPresent(player);
		return state != null && !state.dodgedProjectiles.isEmpty() && state.dodgedProjectiles.contains(projectile.getId());
	}

	/** True if the projectile must not hit this entity (it was caught or dodged). */
	public static boolean intercept(Projectile projectile, Entity target) {
		if (!(target instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive() || projectile.isRemoved()) {
			return false;
		}
		MonkPlayerState state = MonkPlayers.getIfPresent(player);
		if (state == null) {
			return false;
		}
		SimpleAbility ability = state.abilities().get("catch_arrows", SimpleAbility.class);
		if (ability == null || projectile.getOwner() == player) {
			return false;
		}
		boolean arrow = projectile.getType().is(EntityTypeTags.ARROWS);
		boolean catchable = arrow || projectile instanceof AbstractArrow || projectile instanceof ItemSupplier;
		if (!catchable || (!arrow && !ability.params().getBool("catch_all"))) {
			return false;
		}
		// A projectile passing through a dodging player is checked every tick: decide only once.
		if (state.dodgedProjectiles.contains(projectile.getId())) {
			return true;
		}
		boolean handsOk = !ability.params().getBool("require_empty_hand") || state.unarmed();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 motion = projectile.getDeltaMovement();
		Vec3 flight = new Vec3(motion.x, 0.0, motion.z);
		if (flight.lengthSqr() < 1.0E-6) {
			flight = new Vec3(player.getX() - projectile.getX(), 0.0, player.getZ() - projectile.getZ());
		}
		boolean front = flight.lengthSqr() < 1.0E-6 || flight.normalize().dot(look) <= ability.params().getFloat("max_angle_dot");
		if (front) {
			if (!handsOk || player.getRandom().nextFloat() >= ability.params().getFloat("catch_chance")) {
				return false;
			}
			grab(player, projectile, ability);
			return true;
		}
		dev.monkmod.config.MonkConfig.Gameplay g = dev.monkmod.config.MonkConfigManager.config().gameplay;
			// From behind: only a chance to catch it (the level's own, or the general one); if that fails the arrow hits you, unless
			// "dodge from behind" is on for this level, then it flies through instead.
			boolean dodge = g.backDodge && ability.params().getBool("back_dodge");
			float backChance = ability.params().getFloat("back_catch_chance");
			if (backChance <= 0F) {
				backChance = g.backCatchChance;
			}
			if (g.backCatch && handsOk && player.getRandom().nextFloat() < backChance) {
				grab(player, projectile, ability);
				return true;
			}
			if (!dodge) {
				return false;
			}
			if (state.dodgedProjectiles.size() > 64) {
			state.dodgedProjectiles.clear();
		}
		state.dodgedProjectiles.add(projectile.getId());
		return true;
	}

	private static void grab(ServerPlayer player, Projectile projectile, SimpleAbility ability) {
		if (ability.params().getBool("to_inventory")) {
			ItemStack stack = itemOf(projectile);
			if (!stack.isEmpty() && !player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.6F);
		projectile.discard();
	}

	/** The item a caught projectile turns into (empty when it must not be duplicated). */
	/** With time stopped: the monk who stopped it grabs a projectile hanging in the air (it goes to the inventory). */
	public static void catchFrozen(ServerPlayer player, Entity target) {
		if (!(target instanceof Projectile projectile) || projectile.isRemoved() || !dev.monkmod.ability.CombatAbilities.TimeStop.active()
				|| dev.monkmod.ability.CombatAbilities.TimeStop.isFrozen(player) || player.distanceToSqr(projectile) > 7.0 * 7.0) {
			return;
		}
		ItemStack stack = itemOf(projectile);
		if (!stack.isEmpty() && !player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.4F);
		projectile.discard();
	}

	private static ItemStack itemOf(Projectile projectile) {
		if (projectile instanceof AbstractArrow arrow) {
			// Player arrows that can't be picked up (Infinity, creative, multishot extras) would be duplicated.
			if (arrow.pickup != AbstractArrow.Pickup.ALLOWED && arrow.getOwner() instanceof Player) {
				return ItemStack.EMPTY;
			}
			return arrow.getPickupItemStackOrigin().copy();
		}
		if (projectile instanceof ItemSupplier supplier) {
			ItemStack stack = supplier.getItem().copy();
			if (!stack.isEmpty()) {
				stack.setCount(1);
			}
			return stack;
		}
		return ItemStack.EMPTY;
	}
}
