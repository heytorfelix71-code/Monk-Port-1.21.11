package dev.monkmod.ability;

import java.util.function.Predicate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.type.Params;

/** Multiplies incoming damage of a given kind (fire, explosions...). */
public class ProtectionAbility extends MonkAbility {
	private final Predicate<DamageSource> handles;
	private final float multiplier;

	public ProtectionAbility(String key, Params params, Predicate<DamageSource> handles, float multiplier) {
		super(key, params);
		this.handles = handles;
		this.multiplier = multiplier;
	}

	public boolean handles(DamageSource source) {
		return handles.test(source);
	}

	public float factor(ServerPlayer player, MonkPlayerState state, DamageSource source) {
		return multiplier;
	}

	@Override
	protected Object[] descriptionArgs() {
		return new Object[]{percent(1.0 - multiplier)};
	}
}
