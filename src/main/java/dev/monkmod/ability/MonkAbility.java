package dev.monkmod.ability;

import java.text.NumberFormat;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.type.Params;

/**
 * An ability a monk has while at or above the level that grants it (until a later level removes it).
 * Instances are immutable and shared by every player at that level.
 */
public abstract class MonkAbility {
	protected final String key;
	protected final Params params;

	protected MonkAbility(String key, Params params) {
		this.key = key;
		this.params = params;
	}

	public final String key() {
		return key;
	}

	public final Params params() {
		return params;
	}

	/** Whether {@link #tick} should be called every server tick for players with this ability. */
	public boolean isTicking() {
		return false;
	}

	public void tick(ServerPlayer player, MonkPlayerState state) {
	}

	/** Called when a player loses this ability (level change, reload, disabled in config). */
	public void onRemoved(ServerPlayer player, MonkPlayerState state) {
	}

	public String translationKey() {
		return "monk.advancements.ability." + key;
	}

	protected Object[] descriptionArgs() {
		return new Object[0];
	}

	/** Optional "name" / "description" text set in levels.json; null when not customised. */
	private String custom(String field) {
		com.google.gson.JsonElement e = params.json().get(field);
		if (e == null || !e.isJsonPrimitive()) {
			return null;
		}
		String s = e.getAsString().trim();
		return s.isEmpty() ? null : s;
	}

	public MutableComponent name() {
		String custom = custom("name");
		return custom != null ? Component.literal(custom) : Component.translatable(translationKey());
	}

	public MutableComponent description() {
		String custom = custom("description");
		return custom != null ? Component.literal(custom) : Component.translatable(translationKey() + ".desc", descriptionArgs());
	}

	/** "Name: description" used in the reward advancements and /monk info. */
	public Component describe() {
		MutableComponent name = name().withStyle(ChatFormatting.AQUA);
		MutableComponent desc = Component.literal(": ").append(description()).withStyle(ChatFormatting.RESET);
		return name.append(desc);
	}

	public static String percent(double value) {
		return NumberFormat.getPercentInstance(Locale.ROOT).format(value);
	}

	public static String number(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value);
	}
}
