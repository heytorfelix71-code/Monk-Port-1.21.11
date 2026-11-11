package dev.monkmod.type;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * Describes one parameter of an ability or requirement type: its name, kind, default value,
 * a human readable comment and (for numbers) the allowed range.
 */
public record ParamSpec(String name, ParamType type, Object defaultValue, String comment, double min, double max) {
	public static ParamSpec ofInt(String name, int def, int min, int max, String comment) {
		return new ParamSpec(name, ParamType.INT, def, comment, min, max);
	}

	public static ParamSpec ofFloat(String name, double def, double min, double max, String comment) {
		return new ParamSpec(name, ParamType.FLOAT, def, comment, min, max);
	}

	public static ParamSpec ofString(String name, String def, String comment) {
		return new ParamSpec(name, ParamType.STRING, def, comment, 0, 0);
	}

	public static ParamSpec ofList(String name, List<String> def, String comment) {
		return new ParamSpec(name, ParamType.STRING_LIST, List.copyOf(def), comment, 0, 0);
	}

	public static ParamSpec ofBool(String name, boolean def, String comment) {
		return new ParamSpec(name, ParamType.BOOL, def, comment, 0, 0);
	}

	public JsonElement defaultJson() {
		return switch (type) {
			case INT -> new JsonPrimitive(((Number) defaultValue).intValue());
			case FLOAT -> new JsonPrimitive(((Number) defaultValue).doubleValue());
			case STRING -> new JsonPrimitive((String) defaultValue);
			case BOOL -> new JsonPrimitive((Boolean) defaultValue);
			case STRING_LIST -> {
				JsonArray array = new JsonArray();
				for (Object o : (List<?>) defaultValue) {
					array.add(String.valueOf(o));
				}
				yield array;
			}
		};
	}

	public String describe() {
		String range = (type == ParamType.INT || type == ParamType.FLOAT) ? " [" + fmt(min) + " .. " + fmt(max) + "]" : "";
		return name + " (" + type.name().toLowerCase() + ", default " + defaultJson() + range + ") - " + comment;
	}

	private static String fmt(double d) {
		if (d >= Integer.MAX_VALUE) return "inf";
		return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
	}
}
