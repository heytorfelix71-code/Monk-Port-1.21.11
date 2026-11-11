package dev.monkmod.advancement;

import net.minecraft.advancements.CriteriaTriggers;

public final class MonkCriteria {
	public static final MonkLevelTrigger LEVEL_UP = CriteriaTriggers.register("monk:levelup", new MonkLevelTrigger());

	private MonkCriteria() {
	}

	public static void init() {
	}
}
