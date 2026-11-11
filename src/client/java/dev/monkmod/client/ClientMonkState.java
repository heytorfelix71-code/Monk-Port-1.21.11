package dev.monkmod.client;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.world.item.ItemStack;

import dev.monkmod.MonkMod;
import dev.monkmod.ability.MonkAbility;
import dev.monkmod.advancement.MonkAdvancements;
import dev.monkmod.level.AbilitySet;
import dev.monkmod.level.LevelTable;
import dev.monkmod.network.StatePayload;

/** What the client knows about its own monk progress, as sent by the server. */
public final class ClientMonkState {
	private static int level = -1;
	private static int maxLevel = -1;
	private static String nextRequirement = "";
	private static ItemStack nextIcon = ItemStack.EMPTY;
	private static AbilitySet abilities = AbilitySet.EMPTY;
	private static float blinkRange = 16;
	private static int blinkCooldown = 40;
	private static boolean connectedToMonkServer;

	private ClientMonkState() {
	}

	public static void apply(StatePayload payload) {
		connectedToMonkServer = true;
		level = payload.level();
		maxLevel = payload.maxLevel();
		String[] next = payload.nextRequirement().split("\\|", 2);
		nextRequirement = next[0];
		nextIcon = next.length > 1 ? MonkAdvancements.icon(next[1]) : ItemStack.EMPTY;
		blinkRange = payload.blinkRange();
		blinkCooldown = payload.blinkCooldown();
		List<MonkAbility> list = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		for (String json : payload.abilities()) {
			try {
				JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
				MonkAbility ability = LevelTable.buildAbility(obj, "server", errors);
				if (ability != null) {
					list.add(ability);
				}
			} catch (RuntimeException e) {
				errors.add(e.getMessage());
			}
		}
		errors.forEach(e -> MonkMod.LOGGER.debug("Ignoring ability from server: {}", e));
		abilities = new AbilitySet(list);
	}

	public static void reset() {
		level = -1;
		maxLevel = -1;
		nextRequirement = "";
		nextIcon = ItemStack.EMPTY;
		abilities = AbilitySet.EMPTY;
		connectedToMonkServer = false;
	}

	public static int level() {
		return level;
	}

	public static int maxLevel() {
		return maxLevel;
	}

	public static String nextRequirement() {
		return nextRequirement;
	}

	public static ItemStack nextIcon() {
		return nextIcon;
	}

	public static AbilitySet abilities() {
		return abilities;
	}

	public static float blinkRange() {
		return blinkRange;
	}

	public static int blinkCooldown() {
		return blinkCooldown;
	}

	public static boolean connectedToMonkServer() {
		return connectedToMonkServer;
	}
}
