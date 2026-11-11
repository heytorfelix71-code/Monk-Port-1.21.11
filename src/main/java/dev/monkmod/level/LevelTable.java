package dev.monkmod.level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import dev.monkmod.ability.MonkAbility;
import dev.monkmod.config.LevelsFile;
import dev.monkmod.requirement.CombatRequirements;
import dev.monkmod.requirement.MonkRequirement;
import dev.monkmod.type.MonkType;
import dev.monkmod.type.MonkTypes;
import dev.monkmod.type.Params;

/**
 * levels.json compiled into ready-to-use objects. Abilities are resolved per level once, so a lookup
 * during gameplay is a single array access plus a hash lookup.
 */
public final class LevelTable {
	private final MonkRequirement[] requirements;
	private final AbilitySet[] abilities;
	private final String[] icons;
	private final String[] names;
	private final List<List<MonkAbility>> added;
	private final List<MonkRequirement> deathListeners;
	private final int version;

	private LevelTable(MonkRequirement[] requirements, AbilitySet[] abilities, String[] icons, String[] names, List<List<MonkAbility>> added, int version) {
		this.requirements = requirements;
		this.abilities = abilities;
		this.icons = icons;
		this.names = names;
		this.added = added;
		this.version = version;
		List<MonkRequirement> listeners = new ArrayList<>();
		for (MonkRequirement r : requirements) {
			if (r instanceof CombatRequirements.EnemyDefeat) {
				listeners.add(r);
			}
		}
		this.deathListeners = List.copyOf(listeners);
	}

	public int version() {
		return version;
	}

	/** Highest level index (levels go from 0 to maxLevel; a fresh player is level -1). */
	public int maxLevel() {
		return requirements.length - 1;
	}

	/** The requirement a player at {@code currentLevel} is working on, or null at max level. */
	public MonkRequirement nextRequirement(int currentLevel) {
		int next = currentLevel + 1;
		return next >= 0 && next < requirements.length ? requirements[next] : null;
	}

	public MonkRequirement requirement(int level) {
		return requirements[level];
	}

	public AbilitySet abilitiesAt(int level) {
		if (level < 0 || abilities.length == 0) {
			return AbilitySet.EMPTY;
		}
		return abilities[Math.min(level, abilities.length - 1)];
	}

	public String icon(int level) {
		return icons[level];
	}

	/** Custom level title from levels.json, or null for the default "Level N". */
	public String name(int level) {
		String n = names[level];
		return n == null || n.isBlank() ? null : n.trim();
	}

	public List<MonkAbility> addedAt(int level) {
		return added.get(level);
	}

	public List<MonkRequirement> deathListeners() {
		return deathListeners;
	}

	// ------------------------------------------------------------------ compilation

	public static LevelTable compile(LevelsFile file, Collection<String> disabledAbilities, int version, List<String> errors) {
		int n = file.levels.size();
		MonkRequirement[] reqs = new MonkRequirement[n];
		AbilitySet[] sets = new AbilitySet[n];
		String[] icons = new String[n];
		String[] names = new String[n];
		List<List<MonkAbility>> added = new ArrayList<>(n);
		Set<String> disabled = Set.copyOf(disabledAbilities);
		Map<String, MonkAbility> current = new LinkedHashMap<>();

		for (int level = 0; level < n; level++) {
			LevelsFile.LevelEntry entry = file.levels.get(level);
			names[level] = entry.name;
			icons[level] = entry.icon == null || entry.icon.isBlank() ? "monk:icon/meditate" : entry.icon.trim();

			MonkRequirement req = buildRequirement(entry.requirement, level, errors);
			req.setLevelToGrant(level);
			reqs[level] = req;

			if (entry.remove != null) {
				for (String key : entry.remove) {
					current.remove(key.trim());
				}
			}
			List<MonkAbility> levelAdded = new ArrayList<>();
			if (entry.add != null) {
				for (JsonObject json : entry.add) {
					MonkAbility ability = buildAbility(json, "level " + level, errors);
					if (ability == null) {
						continue;
					}
					levelAdded.add(ability);
					if (!disabled.contains(ability.key())) {
						current.put(ability.key(), ability);
					}
				}
			}
			added.add(List.copyOf(levelAdded));
			sets[level] = new AbilitySet(new ArrayList<>(current.values()));
		}
		return new LevelTable(reqs, sets, icons, names, added, version);
	}

	private static boolean enabled(JsonObject json) {
		JsonElement e = json.get("enabled");
		return e == null || !e.isJsonPrimitive() || e.getAsBoolean();
	}

	/** Builds an ability from its JSON entry; returns null (and records why) if it is invalid or disabled. */
	public static MonkAbility buildAbility(JsonObject json, String where, List<String> errors) {
		if (json == null || !json.has("type")) {
			errors.add(where + ": ability entry without \"type\"");
			return null;
		}
		if (!enabled(json)) {
			return null;
		}
		String type = json.get("type").getAsString();
		MonkType<MonkAbility> t = MonkTypes.ABILITIES.get(type);
		if (t == null) {
			errors.add(where + ": unknown ability \"" + type + "\" (see config/monk/README.txt)");
			return null;
		}
		try {
			Params params = t.wrap(json);
			MonkAbility ability = t.create(params);
			params.warnings().forEach(w -> errors.add(where + " / " + type + ": " + w));
			return ability;
		} catch (RuntimeException ex) {
			errors.add(where + " / " + type + ": " + ex.getMessage());
			return null;
		}
	}

	private static MonkRequirement buildRequirement(JsonObject json, int level, List<String> errors) {
		String where = "level " + level + " requirement";
		if (json == null || !json.has("type")) {
			errors.add(where + ": missing \"type\"");
			return new Invalid("missing type");
		}
		String type = json.get("type").getAsString();
		MonkType<MonkRequirement> t = MonkTypes.REQUIREMENTS.get(type);
		if (t == null) {
			errors.add(where + ": unknown requirement \"" + type + "\" (see config/monk/README.txt)");
			return new Invalid("unknown requirement " + type);
		}
		try {
			Params params = t.wrap(json);
			MonkRequirement req = t.create(params);
			params.warnings().forEach(w -> errors.add(where + " / " + type + ": " + w));
			return req;
		} catch (RuntimeException ex) {
			errors.add(where + " / " + type + ": " + ex.getMessage());
			return new Invalid(ex.getMessage());
		}
	}

	/** Placeholder for a broken config entry: never completes, explains itself. */
	private static final class Invalid extends MonkRequirement {
		private final String reason;

		Invalid(String reason) {
			super("invalid", new Params(new JsonObject(), Map.of()), -1);
			this.reason = reason;
		}

		@Override
		public Component description() {
			return Component.literal("Invalid requirement in config/monk/levels.json: " + reason).withStyle(ChatFormatting.RED);
		}
	}
}
