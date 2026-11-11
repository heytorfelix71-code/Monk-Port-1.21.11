package dev.monkmod.ability;

import java.util.List;
import java.util.function.Function;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import dev.monkmod.MonkMod;
import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.type.Params;

/**
 * Ability implemented with vanilla attribute modifiers (armor, damage, speed, step height, ...).
 * Modifiers are transient and only touched when the wanted value actually changes.
 */
public class AttributeAbility extends MonkAbility {
	public enum Condition { ALWAYS, UNARMORED, UNARMED }

	public interface Amount {
		double get(ServerPlayer player, MonkPlayerState state);
	}

	public record Mod(Holder<Attribute> attribute, String suffix, AttributeModifier.Operation operation, Amount amount) {
	}

	private final Condition condition;
	private final List<Mod> mods;
	private final Identifier[] ids;
	private final Function<AttributeAbility, Object[]> args;

	public AttributeAbility(String key, Params params, Condition condition, List<Mod> mods, Function<AttributeAbility, Object[]> args) {
		super(key, params);
		this.condition = condition;
		this.mods = mods;
		this.args = args;
		this.ids = new Identifier[mods.size()];
		for (int i = 0; i < mods.size(); i++) {
			ids[i] = MonkMod.id("ability." + key + "." + mods.get(i).suffix());
		}
	}

	@Override
	public boolean isTicking() {
		return true;
	}

	private boolean conditionMet(MonkPlayerState state) {
		return switch (condition) {
			case ALWAYS -> true;
			case UNARMORED -> state.unarmored();
			case UNARMED -> state.unarmed();
		};
	}

	@Override
	public void tick(ServerPlayer player, MonkPlayerState state) {
		boolean active = conditionMet(state);
		for (int i = 0; i < mods.size(); i++) {
			Mod mod = mods.get(i);
			AttributeInstance instance = player.getAttribute(mod.attribute());
			if (instance == null) {
				continue;
			}
			double amount = active ? mod.amount().get(player, state) : 0.0;
			if (amount == 0.0) {
				if (instance.getModifier(ids[i]) != null) {
					instance.removeModifier(ids[i]);
				}
				continue;
			}
			AttributeModifier current = instance.getModifier(ids[i]);
			if (current == null || current.amount() != amount) {
				instance.addOrUpdateTransientModifier(new AttributeModifier(ids[i], amount, mod.operation()));
			}
		}
	}

	@Override
	public void onRemoved(ServerPlayer player, MonkPlayerState state) {
		for (int i = 0; i < mods.size(); i++) {
			AttributeInstance instance = player.getAttribute(mods.get(i).attribute());
			if (instance != null && instance.getModifier(ids[i]) != null) {
				instance.removeModifier(ids[i]);
			}
		}
	}

	@Override
	protected Object[] descriptionArgs() {
		return args.apply(this);
	}
}
