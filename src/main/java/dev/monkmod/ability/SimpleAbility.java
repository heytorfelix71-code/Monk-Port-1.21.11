package dev.monkmod.ability;

import java.util.function.Function;

import dev.monkmod.type.Params;

/** Ability whose behaviour lives in an event hook or mixin; it only marks that the player has it. */
public class SimpleAbility extends MonkAbility {
	private final Function<Params, Object[]> args;

	public SimpleAbility(String key, Params params) {
		this(key, params, p -> new Object[0]);
	}

	public SimpleAbility(String key, Params params, Function<Params, Object[]> args) {
		super(key, params);
		this.args = args;
	}

	@Override
	protected Object[] descriptionArgs() {
		return args.apply(params);
	}
}
