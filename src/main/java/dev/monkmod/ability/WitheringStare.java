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
	private final boolean hostile;
	private final int paralyze;

	public WitheringStare(Params params) {
		super("withering_stare", params);
		this.range = params.getInt("range");
		this.duration = params.getInt("duration");
		this.witherDamage = params.getFloat("wither_boss_damage");
		this.hostile = params.getBool("hostile_mobs");
		this.paralyze = params.getInt("paralyze_ticks");
	}

	@Override
	public boolean isTicking() {
		return true;
	}

	@Override
	public void tick(ServerPlayer player, MonkPlayerState state) {
		if (player.hasAttached(dev.monkmod.data.MonkAttachments.STARE_OFF)) {
			return; // switched off with the toggle key
		}
		int interval = MonkConfigManager.config().performance.stareInterval;
		if ((player.tickCount + state.tickOffset) % interval != 0) {
			return;
		}
		LivingEntity target = Stare.find(player, range, e -> e.getType().is(EntityTypeTags.UNDEAD) || (hostile && e instanceof net.minecraft.world.entity.monster.Enemy));
		if (target == null) {
			return;
		}
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, duration), player);
		if (paralyze > 0 && target instanceof dev.monkmod.logic.Paralyzable p && target.getType() != EntityType.WITHER
				&& target.getType() != EntityType.ENDER_DRAGON && !p.monk$isParalyzed()) {
			p.monk$paralyze(player.level().getGameTime() + paralyze);
			((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
					target.getX(), target.getEyeY(), target.getZ(), 12, 0.3, 0.3, 0.3, 0.01);
		}
		if (target.getType() == EntityType.WITHER && witherDamage > 0) {
			target.hurtServer((net.minecraft.server.level.ServerLevel) player.level(), player.damageSources().wither(), witherDamage * interval / 4.0F);
		}
	}

	/** Toggle key: switches the stare on/off and tells the player. */
	public static void toggle(ServerPlayer player) {
		boolean nowOff = !player.hasAttached(dev.monkmod.data.MonkAttachments.STARE_OFF);
		if (nowOff) {
			player.setAttached(dev.monkmod.data.MonkAttachments.STARE_OFF, true);
		} else {
			player.removeAttached(dev.monkmod.data.MonkAttachments.STARE_OFF);
		}
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
				nowOff ? "monk.controls.stare.off" : "monk.controls.stare.on")
				.withStyle(nowOff ? net.minecraft.ChatFormatting.GRAY : net.minecraft.ChatFormatting.DARK_PURPLE), true);
	}
}
