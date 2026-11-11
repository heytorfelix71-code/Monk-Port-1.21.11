package dev.monkmod.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import dev.monkmod.MonkMod;
import dev.monkmod.level.LevelTable;
import dev.monkmod.logic.MonkEquipment;
import dev.monkmod.type.MonkTypes;

/**
 * Loads, validates and saves config/monk/monk.json and config/monk/levels.json, and keeps the compiled
 * {@link LevelTable}. A broken file never crashes the game: it is backed up and defaults are used.
 */
public final class MonkConfigManager {
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static MonkConfig config = new MonkConfig();
	private static LevelsFile levels = DefaultLevels.create();
	private static volatile LevelTable table;
	private static int version;
	private static List<String> lastErrors = List.of();

	private MonkConfigManager() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("monk");
	}

	public static MonkConfig config() {
		return config;
	}

	/** A deep copy of the current level layout, for editing. */
	public static LevelsFile levelsCopy() {
		return levels.copy();
	}

	public static LevelTable table() {
		LevelTable t = table;
		if (t == null) {
			rebuild();
			t = table;
		}
		return t;
	}

	public static List<String> lastErrors() {
		return lastErrors;
	}

	public static synchronized List<String> loadAll() {
		try {
			Files.createDirectories(dir());
		} catch (IOException e) {
			MonkMod.LOGGER.error("Could not create config directory", e);
		}
		config = read("monk.json", MonkConfig.class, MonkConfig::new);
		config.sanitize();
		write("monk.json", config);

		LevelsFile loaded = read("levels.json", LevelsFile.class, DefaultLevels::create);
		if (loaded.levels == null || loaded.levels.isEmpty()) {
			MonkMod.LOGGER.warn("levels.json has no levels, regenerating the default 21 levels");
			loaded = DefaultLevels.create();
		}
		if (loaded.migrate()) {
			MonkMod.LOGGER.info("Updated config/monk/levels.json to version {}", LevelsFile.CURRENT_VERSION);
		}
		levels = loaded;
		write("levels.json", levels);
		writeReference();
		return rebuild();
	}

	private static synchronized List<String> rebuild() {
		List<String> errors = new ArrayList<>();
		table = LevelTable.compile(levels, config.gameplay.disabledAbilities, ++version, errors);
		MonkEquipment.reload(config);
		errors.forEach(e -> MonkMod.LOGGER.warn("[config] {}", e));
		lastErrors = List.copyOf(errors);
		return lastErrors;
	}

	public static synchronized List<String> save(MonkConfig newConfig, LevelsFile newLevels) {
		newConfig.sanitize();
		config = newConfig;
		if (newLevels != null && newLevels.levels != null && !newLevels.levels.isEmpty()) {
			levels = newLevels;
		}
		write("monk.json", config);
		write("levels.json", levels);
		return rebuild();
	}

	public static synchronized List<String> resetLevelsToDefault() {
		levels = DefaultLevels.create();
		write("levels.json", levels);
		return rebuild();
	}

	private static <T> T read(String name, Class<T> type, java.util.function.Supplier<T> fallback) {
		Path path = dir().resolve(name);
		if (!Files.exists(path)) {
			return fallback.get();
		}
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			T value = GSON.fromJson(reader, type);
			return value == null ? fallback.get() : value;
		} catch (Exception e) {
			Path backup = dir().resolve(name + ".broken");
			MonkMod.LOGGER.error("Could not read config/monk/{} ({}). It was copied to {} and defaults are used.", name, e.getMessage(), backup.getFileName());
			try {
				Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// best effort
			}
			return fallback.get();
		}
	}

	private static void write(String name, Object value) {
		Path path = dir().resolve(name);
		Path tmp = dir().resolve(name + ".tmp");
		try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
			GSON.toJson(value, writer);
		} catch (IOException e) {
			MonkMod.LOGGER.error("Could not write config/monk/{}", name, e);
			return;
		}
		try {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			try {
				Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e2) {
				MonkMod.LOGGER.error("Could not write config/monk/{}", name, e2);
			}
		}
	}

	private static void writeReference() {
		String text = """
				Monk Mod - configuration reference
				==================================

				monk.json   general options (HUD, blink, equipment rules, performance).
				levels.json the level path. Entry N unlocks monk level N (players start at -1).
				            Each level: "icon", "requirement" {type + params}, "add" [abilities], "remove" [ability types].
				            Abilities accumulate from level to level; adding the same type again replaces the older one.
				            Any ability may have "enabled": false. Missing parameters use their defaults.

				Edit in game with Mod Menu + YetAnotherConfigLib, or edit the files and run /monk reload.
				Icons: an item model id (monk:icon/tree, ...) or any item id (minecraft:diamond).
				Item tags: #monk:monk_safe (always allowed) and #monk:monk_forbidden (never allowed).

				""" + MonkTypes.reference();
		try {
			Files.writeString(dir().resolve("README.txt"), text, StandardCharsets.UTF_8);
		} catch (IOException e) {
			MonkMod.LOGGER.warn("Could not write config/monk/README.txt", e);
		}
	}
}
