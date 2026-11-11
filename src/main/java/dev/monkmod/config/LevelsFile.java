package dev.monkmod.config;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

/**
 * config/monk/levels.json. Index in {@link #levels} is the monk level the entry unlocks.
 * Each requirement / ability entry is {"type": "...", ...parameters}; abilities may also carry "enabled": false.
 */
public final class LevelsFile {
	public static final int CURRENT_VERSION = 2;

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
		config_version = CURRENT_VERSION;
		return true;
	}

	public LevelsFile copy() {
		LevelsFile f = new LevelsFile();
		f.config_version = config_version;
		levels.forEach(l -> f.levels.add(l.copy()));
		return f;
	}
}
