package dev.monkmod.type;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonObject;

/**
 * A named, configurable factory: the config file says {"type": key, ...params} and this turns it into an object.
 */
public final class MonkType<T> {
	private final String key;
	private final Map<String, ParamSpec> params;
	private final Function<Params, T> factory;
	private final String summary;

	public MonkType(String key, String summary, List<ParamSpec> params, Function<Params, T> factory) {
		this.key = key;
		this.summary = summary;
		LinkedHashMap<String, ParamSpec> map = new LinkedHashMap<>();
		for (ParamSpec p : params) {
			map.put(p.name(), p);
		}
		this.params = Collections.unmodifiableMap(map);
		this.factory = factory;
	}

	public String key() {
		return key;
	}

	public String summary() {
		return summary;
	}

	public Map<String, ParamSpec> params() {
		return params;
	}

	public Params wrap(JsonObject json) {
		return new Params(json, params);
	}

	public T create(Params params) {
		return factory.apply(params);
	}

	/** A JSON entry with the type key and every parameter at its default value. */
	public JsonObject defaultEntry() {
		JsonObject o = new JsonObject();
		o.addProperty("type", key);
		params.values().forEach(p -> o.add(p.name(), p.defaultJson()));
		return o;
	}
}
