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
		level(f, "meditate", req("break_wood").p("number", 1));
		level(f, "tree", req("break_wood").p("number", 10),
				ab("mining").p("harvest_level", 0).p("speed_increase", 0.0).list("harvest_tools", "", "shovel", "axe"),
				ab("strength").p("damage", 1.0),
				ab("natural_diet"));
		level(f, "sunrise", req("mediate_sunrise"),
				ab("hunger").p("hunger_chance", 0.5),
				ab("armor").p("armor", 1.0));
		level(f, "squid", req("drown"),
				ab("water_breathing_partial"),
				ab("armor").p("armor", 2.0));
		level(f, "silverfish", req("break_block").p("block", "minecraft:stone").p("number", 5),
				ab("mining").p("harvest_level", 1).p("speed_increase", 2.0));
		level(f, "love", req("pet").p("number", 20),
				ab("tame_animals"),
				ab("armor").p("armor", 4.0));
		level(f, "feather", req("sprint").p("distance", 200.0),
				ab("swift").p("increase", 0.4),
				ab("jump").p("jump", 1.2),
				ab("hunger").p("hunger_chance", 0.75),
				ab("strength").p("damage", 2.0));
		level(f, "wolf", req("kill_undead").p("kills", 10),
				ab("strength").p("damage", 4.0),
				ab("armor").p("armor", 8.0));
		level(f, "skeleton", req("arrow_dodge").p("dodges", 5),
				ab("catch_arrows"),
				ab("swift").p("increase", 0.8),
				ab("step_up"));
		level(f, "iron_golem", req("break_block").p("block", "minecraft:iron_block").p("number", 4),
				ab("mining").p("harvest_level", 2).p("speed_increase", 5.0));
		level(f, "water", req("meditate_water_moon").p("stare_time", 200),
				ab("water_walking"));
		level(f, "enderman", req("meditate_endermen"),
				ab("blink"));
		level(f, "blaze", req("walk_fire").p("distance", 10.0),
				ab("fire_resistance").p("multiplier", 0.5),
				ab("lava_protection"));
		level(f, "ghast", req("break_block").p("block", "minecraft:obsidian").p("number", 1),
				ab("mining").p("harvest_level", 3).p("speed_increase", 9.0),
				ab("armor").p("armor", 14.0),
				ab("armor_toughness").p("armor_toughness", 4.0));
		level(f, "creeper", req("kiss_creeper"),
				ab("kiss_creeper"),
				ab("explosion_resistance").p("multiplier", 0.5));
		level(f, "evil_eye", req("wither_stare").p("stare_time", 200),
				ab("withering_stare"));
		level(f, "ocelot", req("fall").p("distance", 40.0),
				ab("feather_fall").p("multiplier", 0.2),
				ab("armor").p("armor", 18.0),
				ab("armor_toughness").p("armor_toughness", 8.0));
		level(f, "bed", req("bedrock_sleep"),
				ab("mining").p("harvest_level", 5).p("speed_increase", 12.0),
				ab("strength").p("damage", 8.0),
				ab("blindness"),
				ab("night_vision"));
		LevelsFile.LevelEntry blind = level(f, "blind_eye", req("kill_blind").p("kills", 5),
				ab("strength").p("damage", 16.0));
		blind.remove.add("blindness");
		level(f, "wither", req("kill_entity_type").p("kills", 1).p("entity_type", "minecraft:wither"),
				ab("potion_immunity"),
				ab("armor").p("armor", 20.0),
				ab("armor_toughness").p("armor_toughness", 12.0),
				ab("iron_body").p("multiplier", 0.6));
		level(f, "ascend", req("void_fall"),
				ab("fly"),
				ab("time_stop"),
				ab("hunger").p("hunger_chance", 0.9),
				ab("regeneration"),
				ab("catch_arrows").p("back_dodge", true).p("back_catch_chance", 0.2));
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
