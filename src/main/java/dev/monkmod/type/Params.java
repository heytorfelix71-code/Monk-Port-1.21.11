package dev.monkmod.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Typed, validated view over the JSON object describing one ability or requirement entry.
 * Missing or malformed values fall back to the spec default; numbers are clamped to range.
 */
public final class Params {
	private final JsonObject json;
	private final Map<String, ParamSpec> specs;
	private final List<String> warnings = new ArrayList<>();

	public Params(JsonObject json, Map<String, ParamSpec> specs) {
		this.json = json;
		this.specs = specs;
	}

	public JsonObject json() {
		return json;
	}

	public List<String> warnings() {
		return warnings;
	}

	private ParamSpec spec(String name) {
		ParamSpec spec = specs.get(name);
		if (spec == null) {
			throw new IllegalStateException("Undeclared parameter '" + name + "'");
		}
		return spec;
	}

	private JsonElement raw(String name) {
		JsonElement element = json.get(name);
		return element == null || element.isJsonNull() ? null : element;
	}

	public int getInt(String name) {
		ParamSpec spec = spec(name);
		JsonElement e = raw(name);
		int value = ((Number) spec.defaultValue()).intValue();
		if (e != null) {
			try {
				value = (int) Math.round(e.getAsDouble());
			} catch (RuntimeException ex) {
				warnings.add("'" + name + "' must be a whole number, using default " + value);
			}
		}
		return (int) Math.max(spec.min(), Math.min(spec.max(), value));
	}

	public float getFloat(String name) {
		ParamSpec spec = spec(name);
		JsonElement e = raw(name);
		double value = ((Number) spec.defaultValue()).doubleValue();
		if (e != null) {
			try {
				value = e.getAsDouble();
			} catch (RuntimeException ex) {
				warnings.add("'" + name + "' must be a number, using default " + value);
			}
		}
		return (float) Math.max(spec.min(), Math.min(spec.max(), value));
	}

	public String getString(String name) {
		ParamSpec spec = spec(name);
		JsonElement e = raw(name);
		if (e != null && e.isJsonPrimitive()) {
			return e.getAsString();
		}
		return (String) spec.defaultValue();
	}

	public boolean getBool(String name) {
		ParamSpec spec = spec(name);
		JsonElement e = raw(name);
		if (e != null && e.isJsonPrimitive()) {
			return e.getAsBoolean();
		}
		return (Boolean) spec.defaultValue();
	}

	public List<String> getList(String name) {
		ParamSpec spec = spec(name);
		JsonElement e = raw(name);
		if (e instanceof JsonArray array) {
			List<String> out = new ArrayList<>(array.size());
			for (JsonElement el : array) {
				out.add(el.getAsString().trim());
			}
			return out;
		}
		if (e != null && e.isJsonPrimitive()) {
			List<String> out = new ArrayList<>();
			for (String s : e.getAsString().split(",", -1)) {
				out.add(s.trim());
			}
			return out;
		}
		List<String> out = new ArrayList<>();
		for (Object o : (List<?>) spec.defaultValue()) {
			out.add(String.valueOf(o));
		}
		return out;
	}
}
