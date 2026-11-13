package dev.monkmod.logic;

/** Implemented on every Mob by MobMixin: the withering stare freezes its AI for a while (not saved). */
public interface Paralyzable {
	void monk$paralyze(long untilGameTime);

	boolean monk$isParalyzed();
}
