package dev.monkmod.config;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

/**
 * config/monk/levels.json. Index in {@link #levels} is the monk level the entry unlocks.
 * Each requirement / ability entry is {"type": "...", ...parameters}; abilities may also carry "enabled": false.
 */
public final class LevelsFile {
	public static final int CURRENT_VERSION = 10;

	public int config_version = CURRENT_VERSION;
	public List<LevelEntry> levels = new ArrayList<>();

	public static final class LevelEntry {
		/** Item model id (e.g. monk:icon/tree) or an item id (e.g. minecraft:diamond). */
		public String icon = "monk:icon/meditate";
		/** Optional custom title for this level's advancement ("" = default "Level N"). */
		public String name = "";
		public JsonObject requirement = new JsonObject();
		public List<JsonObject> add = new ArrayList<>();
		public List<String> remove = new ArrayList<>();

		public LevelEntry copy() {
			LevelEntry e = new LevelEntry();
			e.icon = icon;
			e.name = name;
			e.requirement = requirement == null ? new JsonObject() : requirement.deepCopy();
			e.add = new ArrayList<>();
			if (add != null) {
				add.forEach(o -> e.add.add(o.deepCopy()));
			}
			e.remove = remove == null ? new ArrayList<>() : new ArrayList<>(remove);
			return e;
		}
	}

	/**
	 * Updates a levels.json written by an older version: adds the abilities introduced since then to the
	 * matching default levels, without touching anything the user customised.
	 */
	public boolean migrate() {
		if (config_version >= CURRENT_VERSION) {
			return false;
		}
		if (config_version < 2) {
			for (LevelEntry e : levels) {
				if (e.add == null) {
					e.add = new ArrayList<>();
				}
				// Toxin immunity used to keep (not block) the listed effects: an empty old list means "use the new defaults".
				for (JsonObject a : e.add) {
					if (a.has("type") && "potion_immunity".equals(a.get("type").getAsString())
							&& a.has("ignored_effects") && a.get("ignored_effects").isJsonArray()
							&& a.getAsJsonArray("ignored_effects").isEmpty()) {
						a.remove("ignored_effects");
					}
				}
				if ("monk:icon/bed".equals(e.icon) && e.add.stream().noneMatch(a -> a.has("type") && "night_vision".equals(a.get("type").getAsString()))) {
					JsonObject nv = new JsonObject();
					nv.addProperty("type", "night_vision");
					e.add.add(nv);
				}
			}
		}
		if (config_version < 3 && !levels.isEmpty()) {
			if (!hasType("armor_toughness")) {
				addTo("monk:icon/ghast", "armor_toughness", "armor_toughness", 4.0);
				addTo("monk:icon/ocelot", "armor_toughness", "armor_toughness", 8.0);
				addTo("monk:icon/wither", "armor_toughness", "armor_toughness", 12.0);
			}
			if (!hasType("iron_body")) {
				addTo("monk:icon/wither", "iron_body", "multiplier", 0.6);
			}
			if (!hasType("time_stop")) {
				addTo("monk:icon/ascend", "time_stop", null, 0);
			}
			if (!hasType("regeneration")) {
				addTo("monk:icon/ascend", "regeneration", null, 0);
			}
			LevelEntry top = find("monk:icon/ascend");
			if (top.add.stream().noneMatch(a -> a.has("type") && "catch_arrows".equals(a.get("type").getAsString()))) {
				JsonObject up = new JsonObject();
				up.addProperty("type", "catch_arrows");
				up.addProperty("back_dodge", true);
				up.addProperty("back_catch_chance", 0.2);
				top.add.add(up);
			}
		}
		if (config_version < 4 && !levels.isEmpty()) {
			long hungerEntries = levels.stream().filter(e -> e.add != null)
					.flatMap(e -> e.add.stream()).filter(a -> a.has("type") && "hunger".equals(a.get("type").getAsString())).count();
			if (hungerEntries <= 1) {
				addTo("monk:icon/feather", "hunger", "hunger_chance", 0.75);
				addTo("monk:icon/ascend", "hunger", "hunger_chance", 0.9);
			}
		}
		if (config_version < 5 && levels.size() > 1 && !hasType("natural_diet")) {
			LevelEntry first = levels.get(1); // reward of the first mission
			if (first.add == null) {
				first.add = new ArrayList<>();
			}
			JsonObject diet = new JsonObject();
			diet.addProperty("type", "natural_diet");
			first.add.add(diet);
		}
		if (config_version < 10 && looksLikeDefaults()) {
			// 2.5.0 / 2.6.0 rebalances: same 21 levels, so saved progress still fits.
			rebalanceFromDefaults();
		}
		if (config_version < 7 && !rebalanced && looksLikeDefaults()) {
			JsonObject first = levels.get(0).requirement;
			if (first != null && first.has("type") && "meditate".equals(first.get("type").getAsString())
					&& first.has("time") && first.get("time").getAsInt() == 1200) {
				first.addProperty("time", 2400);
			}
			if (!hasType("knockback_resistance")) {
				addTo("monk:icon/ascend", "armor", "armor", 20.0);
				addTo("monk:icon/ascend", "armor_toughness", "armor_toughness", 14.0);
				addTo("monk:icon/ascend", "knockback_resistance", "knockback_resistance", 5.0);
			}
		}
		config_version = CURRENT_VERSION;
		return true;
	}

	private void rebalanceFromDefaults() {
		LevelsFile fresh = DefaultLevels.create();
		for (int i = 0; i < levels.size() && i < fresh.levels.size(); i++) {
			LevelEntry old = levels.get(i);
			LevelEntry neu = fresh.levels.get(i);
			neu.name = old.name;
			if (old.add == null) {
				continue;
			}
			for (JsonObject a : old.add) {
				if (!a.has("name") && !a.has("description")) {
					continue;
				}
				for (JsonObject b : neu.add) {
					if (b.get("type").equals(a.get("type")) && !b.has("name") && !b.has("description")) {
						if (a.has("name")) {
							b.add("name", a.get("name"));
						}
						if (a.has("description")) {
							b.add("description", a.get("description"));
						}
						break;
					}
				}
			}
		}
		levels = fresh.levels;
		rebalanced = true;
	}

	/** Set when migrate() replaced the levels with the new balance (the old file is backed up). */
	public transient boolean rebalanced;

	/** Same level path (icons) as the defaults: the player never reshaped the levels themselves. */
	private boolean looksLikeDefaults() {
		LevelsFile fresh = DefaultLevels.create();
		if (fresh.levels.size() != levels.size()) {
			return false;
		}
		for (int i = 0; i < levels.size(); i++) {
			if (!java.util.Objects.equals(fresh.levels.get(i).icon, levels.get(i).icon)) {
				return false;
			}
		}
		return true;
	}

	private boolean hasType(String type) {
		for (LevelEntry e : levels) {
			if (e.add != null && e.add.stream().anyMatch(a -> a.has("type") && type.equals(a.get("type").getAsString()))) {
				return true;
			}
		}
		return false;
	}

	/** Level with this icon, or the last level when the icons were customised. */
	private LevelEntry find(String icon) {
		LevelEntry found = levels.get(levels.size() - 1);
		for (LevelEntry e : levels) {
			if (icon.equals(e.icon)) {
				found = e;
				break;
			}
		}
		if (found.add == null) {
			found.add = new ArrayList<>();
		}
		return found;
	}

	private void addTo(String icon, String type, String param, double value) {
		JsonObject o = new JsonObject();
		o.addProperty("type", type);
		if (param != null) {
			o.addProperty(param, value);
		}
		find(icon).add.add(o);
	}

	public LevelsFile copy() {
		LevelsFile f = new LevelsFile();
		f.config_version = config_version;
		levels.forEach(l -> f.levels.add(l.copy()));
		return f;
	}
}
