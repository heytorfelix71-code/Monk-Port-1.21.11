package dev.monkmod.level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.monkmod.ability.MonkAbility;
import dev.monkmod.ability.ProtectionAbility;

/**
 * Immutable set of abilities active at one level, with pre-sorted views so hot paths
 * (ticks, damage, mining) never filter or allocate.
 */
public final class AbilitySet {
	public static final AbilitySet EMPTY = new AbilitySet(List.of());

	private final Map<String, MonkAbility> byKey;
	private final List<MonkAbility> ticking;
	private final List<ProtectionAbility> protections;

	public AbilitySet(Collection<MonkAbility> abilities) {
		LinkedHashMap<String, MonkAbility> map = new LinkedHashMap<>();
		List<MonkAbility> tick = new ArrayList<>();
		List<ProtectionAbility> prot = new ArrayList<>();
		for (MonkAbility a : abilities) {
			map.put(a.key(), a);
		}
		for (MonkAbility a : map.values()) {
			if (a.isTicking()) {
				tick.add(a);
			}
			if (a instanceof ProtectionAbility p) {
				prot.add(p);
			}
		}
		this.byKey = Collections.unmodifiableMap(map);
		this.ticking = List.copyOf(tick);
		this.protections = List.copyOf(prot);
	}

	public boolean has(String key) {
		return byKey.containsKey(key);
	}

	public MonkAbility get(String key) {
		return byKey.get(key);
	}

	@SuppressWarnings("unchecked")
	public <T extends MonkAbility> T get(String key, Class<T> type) {
		MonkAbility a = byKey.get(key);
		return type.isInstance(a) ? (T) a : null;
	}

	public Collection<MonkAbility> all() {
		return byKey.values();
	}

	public List<MonkAbility> ticking() {
		return ticking;
	}

	public List<ProtectionAbility> protections() {
		return protections;
	}

	public boolean isEmpty() {
		return byKey.isEmpty();
	}
}
