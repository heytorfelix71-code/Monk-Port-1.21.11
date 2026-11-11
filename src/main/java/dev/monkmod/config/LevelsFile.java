package dev.monkmod.config;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

/**
 * config/monk/levels.json. Index in {@link #levels} is the monk level the entry unlocks.
 * Each requirement / ability entry is {"type": "...", ...parameters}; abilities may also carry "enabled": false.
 */
public final class LevelsFile {
	public static final int CURRENT_VERSION = 1;

	public int config_version = CURRENT_VERSION;
	public List<LevelEntry> levels = new ArrayList<>();

	public static final class LevelEntry {
		/** Item model id (e.g. monk:icon/tree) or an item id (e.g. minecraft:diamond). */
		public String icon = "monk:icon/meditate";
		public JsonObject requirement = new JsonObject();
		public List<JsonObject> add = new ArrayList<>();
		public List<String> remove = new ArrayList<>();

		public LevelEntry copy() {
			LevelEntry e = new LevelEntry();
			e.icon = icon;
			e.requirement = requirement == null ? new JsonObject() : requirement.deepCopy();
			e.add = new ArrayList<>();
			if (add != null) {
				add.forEach(o -> e.add.add(o.deepCopy()));
			}
			e.remove = remove == null ? new ArrayList<>() : new ArrayList<>(remove);
			return e;
		}
	}

	public LevelsFile copy() {
		LevelsFile f = new LevelsFile();
		f.config_version = config_version;
		levels.forEach(l -> f.levels.add(l.copy()));
		return f;
	}
}
