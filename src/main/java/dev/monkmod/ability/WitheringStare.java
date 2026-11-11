package dev.monkmod.ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.logic.Stare;
import dev.monkmod.type.Params;

/** Looking at an undead creature withers it. The Wither itself also takes damage. */
public final class WitheringStare extends MonkAbility {
	private final int range;
	private final int duration;
	private final float witherDamage;

	public WitheringStare(Params params) {
		super("withering_stare", params);
		this.range = params.getInt("range");
		this.duration = params.getInt("duration");
		this.witherDamage = params.getFloat("wither_boss_damage");
	}

	@Override
	public boolean isTicking() {
		return true;
	}

	@Override
	public void tick(ServerPlayer player, MonkPlayerState state) {
		int interval = MonkConfigManager.config().performance.stareInterval;
		if ((player.tickCount + state.tickOffset) % interval != 0) {
			return;
		}
		LivingEntity target = Stare.find(player, range, e -> e.getType().is(EntityTypeTags.UNDEAD));
		if (target == null) {
			return;
		}
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, duration), player);
		if (target.getType() == EntityType.WITHER && witherDamage > 0) {
			target.hurtServer((net.minecraft.server.level.ServerLevel) player.level(), player.damageSources().wither(), witherDamage * interval / 4.0F);
		}
	}
}
