package dev.monkmod.config;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** The 21 levels of the original Monk Mod 1.4, faithfully reproduced. */
public final class DefaultLevels {
	private DefaultLevels() {
	}

	public static LevelsFile create() {
		LevelsFile f = new LevelsFile();
		// Becoming a monk: two full minutes of still meditation under the sky.
		level(f, "meditate", req("meditate").p("time", 2400));
		level(f, "tree", req("break_wood").p("number", 32),
				ab("mining").p("harvest_level", 0).p("speed_increase", 0.5).list("harvest_tools", "", "shovel", "axe"),
				ab("strength").p("damage", 1.0),
				ab("natural_diet"),
				ab("counter_attack").p("chance", 0.05));
		level(f, "sunrise", req("mediate_sunrise").p("time", 400),
				ab("hunger").p("hunger_chance", 0.4),
				ab("armor").p("armor", 1.0));
		level(f, "squid", req("drown").p("swim_time", 400),
				ab("water_breathing_partial").p("oxygen_bonus", 2.0).p("swim_boost", 0.02).p("swim_up", 0.01).p("column_resist", 0.25),
				ab("armor").p("armor", 2.0),
				ab("regeneration").p("delay", 3600).p("interval", 100).p("amount", 1.0));
		level(f, "silverfish", req("break_block").p("block", "minecraft:stone").p("number", 64),
				ab("mining").p("harvest_level", 1).p("speed_increase", 1.5));
		level(f, "love", req("pet").p("number", 30),
				ab("tame_animals"),
				ab("armor").p("armor", 3.0),
				ab("counter_attack").p("chance", 0.1));
		level(f, "feather", req("sprint").p("distance", 1000.0),
				ab("swift").p("increase", 0.2),
				ab("jump").p("jump", 1.1),
				ab("strength").p("damage", 2.0),
				ab("hunger").p("hunger_chance", 0.6));
		level(f, "wolf", req("kill_undead").p("kills", 25),
				ab("strength").p("damage", 3.0),
				ab("armor").p("armor", 5.0),
				ab("swift").p("increase", 0.3));
		level(f, "skeleton", req("arrow_dodge").p("dodges", 8),
				ab("catch_arrows").p("catch_chance", 0.25),
				ab("swift").p("increase", 0.45),
				ab("step_up"));
		level(f, "iron_golem", req("break_block").p("block", "minecraft:iron_block").p("number", 8),
				ab("mining").p("harvest_level", 2).p("speed_increase", 3.0),
				ab("counter_attack").p("chance", 0.15));
		level(f, "water", req("meditate_water_moon").p("stare_time", 400),
				ab("water_walking"),
				ab("water_breathing_partial").p("oxygen_bonus", 4.0).p("swim_boost", 0.04).p("swim_up", 0.02).p("column_resist", 0.5));
		level(f, "enderman", req("meditate_endermen").p("time", 1200),
				ab("blink"),
				ab("regeneration").p("delay", 2400).p("interval", 60).p("amount", 1.0),
				ab("catch_arrows").p("catch_chance", 0.4));
		level(f, "blaze", req("walk_fire").p("distance", 30.0),
				ab("fire_resistance").p("multiplier", 0.5),
				ab("lava_protection"),
				ab("swift").p("increase", 0.6));
		level(f, "ghast", req("break_block").p("block", "minecraft:obsidian").p("number", 4),
				ab("mining").p("harvest_level", 3).p("speed_increase", 5.0),
				ab("armor").p("armor", 9.0),
				ab("armor_toughness").p("armor_toughness", 3.0));
		level(f, "creeper", req("kiss_creeper"),
				ab("kiss_creeper"),
				ab("explosion_resistance").p("multiplier", 0.5),
				ab("hunger").p("hunger_chance", 0.75),
				ab("counter_attack").p("chance", 0.25));
		level(f, "evil_eye", req("wither_stare").p("stare_time", 400),
				ab("withering_stare"),
				ab("water_breathing_full").p("swim_boost", 0.06).p("swim_up", 0.035).p("column_resist", 0.75));
		level(f, "ocelot", req("fall").p("distance", 50.0),
				ab("feather_fall").p("multiplier", 0.2),
				ab("catch_arrows").p("catch_chance", 0.55),
				ab("armor").p("armor", 13.0),
				ab("armor_toughness").p("armor_toughness", 6.0),
				ab("swift").p("increase", 0.8));
		level(f, "bed", req("bedrock_sleep"),
				ab("mining").p("harvest_level", 5).p("speed_increase", 8.0),
				ab("strength").p("damage", 5.0),
				ab("blindness"),
				ab("night_vision"),
				ab("counter_attack").p("chance", 0.3));
		LevelsFile.LevelEntry blind = level(f, "blind_eye", req("kill_blind").p("kills", 10),
				ab("strength").p("damage", 8.0),
				ab("swift").p("increase", 1.0),
				ab("catch_arrows").p("catch_chance", 0.7));
		blind.remove.add("blindness");
		level(f, "wither", req("kill_entity_type").p("kills", 1).p("entity_type", "minecraft:wither"),
				ab("potion_immunity"),
				ab("armor").p("armor", 18.0),
				ab("armor_toughness").p("armor_toughness", 10.0),
				ab("iron_body").p("multiplier", 0.7),
				ab("water_breathing_full").p("swim_boost", 0.08).p("swim_up", 0.05).p("column_resist", 1.0));
		level(f, "ascend", req("void_fall"),
				ab("fly"),
				ab("time_stop"),
				ab("regeneration").p("delay", 1800).p("interval", 40).p("amount", 1.0),
				ab("catch_arrows").p("catch_chance", 0.85).p("back_dodge", true).p("back_catch_chance", 0.2),
				ab("iron_body").p("multiplier", 0.5),
				ab("hunger").p("hunger_chance", 0.99),
				ab("swift").p("increase", 1.2),
				ab("strength").p("damage", 20.0),
				ab("armor").p("armor", 20.0),
				ab("armor_toughness").p("armor_toughness", 14.0),
				ab("knockback_resistance").p("knockback_resistance", 5.0),
				ab("counter_attack").p("chance", 0.4));
		return f;
	}

	private static LevelsFile.LevelEntry level(LevelsFile f, String icon, J requirement, J... abilities) {
		LevelsFile.LevelEntry e = new LevelsFile.LevelEntry();
		e.icon = "monk:icon/" + icon;
		e.requirement = requirement.o;
		for (J a : abilities) {
			e.add.add(a.o);
		}
		f.levels.add(e);
		return e;
	}

	private static J req(String type) {
		return new J(type);
	}

	private static J ab(String type) {
		return new J(type);
	}

	/** Tiny JSON builder; parameters left out take their defaults when loaded. */
	private static final class J {
		final JsonObject o = new JsonObject();

		J(String type) {
			o.addProperty("type", type);
		}

		J p(String k, Number v) {
			o.addProperty(k, v);
			return this;
		}

		J p(String k, boolean v) {
			o.addProperty(k, v);
			return this;
		}

		J p(String k, String v) {
			o.addProperty(k, v);
			return this;
		}

		J list(String k, String... values) {
			JsonArray a = new JsonArray();
			List.of(values).forEach(a::add);
			o.add(k, a);
			return this;
		}
	}
}
