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
			if (!handsOk) {
				return false;
			}
			grab(player, projectile, ability);
			return true;
		}
		if (!ability.params().getBool("back_dodge")) {
			return false;
		}
		if (handsOk && player.getRandom().nextFloat() < ability.params().getFloat("back_catch_chance")) {
			grab(player, projectile, ability);
			return true;
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
