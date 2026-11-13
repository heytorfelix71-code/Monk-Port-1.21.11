package dev.monkmod.config;

import java.util.ArrayList;
import java.util.List;

/**
 * General settings, saved as config/monk/monk.json. The level layout lives in levels.json.
 * Field names are the JSON keys, so keep them stable.
 */
public final class MonkConfig {
	public Hud hud = new Hud();
	public Blink blink = new Blink();
	public Equipment equipment = new Equipment();
	public Gameplay gameplay = new Gameplay();
	public Performance performance = new Performance();
	public Flight flight = new Flight();
	public Controls controls = new Controls();

	public enum HudStyle { RING, BAR, TEXT }

	public enum HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

	public static final class Hud {
		public boolean enabled = true;
		public HudStyle style = HudStyle.RING;
		public HudCorner corner = HudCorner.BOTTOM_RIGHT;
		public int offsetX = 6;
		public int offsetY = 6;
		public int size = 28;
		public float opacity = 1.0F;
		public boolean showPercent = true;
		public boolean showIcon = true;
		/** Keep the indicator on screen instead of fading out after progress stops changing. */
		public boolean alwaysVisible = false;
		/** Ticks the indicator stays fully visible after the last change. */
		public int visibleTicks = 40;
		public int colorGain = 0x55FF55;
		public int colorLoss = 0xFF5555;
	}

	public static final class Blink {
		public float range = 16.0F;
		public int cooldownTicks = 40;
		public float fallDamage = 2.0F;
		public boolean previewParticles = true;
		public boolean requireUnarmored = false;
		/** Allow landing in mid-air. Off = you only land on a block (or water). */
		public boolean allowAirTarget = false;
		/** Shortest distance the scroll wheel can choose. */
		public float minRange = 2.0F;
		/** When aiming at nothing, how far below the aim point the ground is searched (blocks). */
		public int groundSearchDepth = 6;
	}

	public static final class Flight {
		/** Vanilla flying speed is 0.05. */
		public float minSpeed = 0.02F;
		public float maxSpeed = 0.30F;
	}

	/** Client-side controls (scroll wheel modifiers and step sizes). */
	public static final class Controls {
		/** Blink key + mouse wheel changes the blink distance. */
		public boolean scrollBlinkDistance = true;
		/** Sprint key (Ctrl) + mouse wheel changes the running speed bonus. */
		public boolean scrollRunSpeed = true;
		/** Jump key (Space) + mouse wheel changes the flight speed while flying. */
		public boolean scrollFlySpeed = true;
		public float blinkStep = 1.0F;
		/** Step of the running bonus scale (1.0 = the full bonus of your level). */
		public float runStep = 0.1F;
		public float maxRunScale = 2.0F;
		public float flyStep = 0.01F;
		/** Show the new value in the action bar when it changes. */
		public boolean showChanges = true;
	}

	public static final class Equipment {
		/** In strict mode anything worn in an armor slot counts as armor (except whitelisted items). */
		public boolean strictArmor = false;
		public List<String> whitelist = new ArrayList<>(List.of(
				"minecraft:carved_pumpkin", "minecraft:skeleton_skull", "minecraft:wither_skeleton_skull",
				"minecraft:zombie_head", "minecraft:creeper_head", "minecraft:player_head",
				"minecraft:piglin_head", "minecraft:dragon_head"));
		public List<String> blacklist = new ArrayList<>();
	}

	public static final class Gameplay {
		public boolean announceLevelUps = true;
		/** Ability types switched off everywhere, whatever levels.json says. */
		public List<String> disabledAbilities = new ArrayList<>();
		/** Progress messages in the action bar when progress changes. */
		public boolean actionBarProgress = false;
		/** Allow /monk info for non-operators. */
		public boolean infoCommandForEveryone = true;
		/** Monks wearing heavy armor are slowed down: lost speed per heavy piece (0.15 = 15%). */
		public float heavyArmorSlowdown = 0.15F;
		/** Monks wearing heavy armor can't break blocks with pickaxes or axes. */
		public boolean heavyArmorNoTools = true;
		/** Armor pieces that count as heavy. */
		public List<String> heavyArmor = new ArrayList<>(List.of(
				"minecraft:iron_helmet", "minecraft:iron_chestplate", "minecraft:iron_leggings", "minecraft:iron_boots",
				"minecraft:diamond_helmet", "minecraft:diamond_chestplate", "minecraft:diamond_leggings", "minecraft:diamond_boots",
				"minecraft:netherite_helmet", "minecraft:netherite_chestplate", "minecraft:netherite_leggings", "minecraft:netherite_boots"));
	}

	public static final class Performance {
		/** Ticks between "stare" ray casts (withering stare, wither skeleton staring contest). */
		public int stareInterval = 4;
		/** Ticks between enderman scans for the meditation requirement. */
		public int endermanScanInterval = 5;
		/** Ticks between equipment checks (armor / weapons) per player. */
		public int equipmentCheckInterval = 5;
	}

	public void sanitize() {
		if (hud == null) hud = new Hud();
		if (blink == null) blink = new Blink();
		if (equipment == null) equipment = new Equipment();
		if (gameplay == null) gameplay = new Gameplay();
		if (performance == null) performance = new Performance();
		if (flight == null) flight = new Flight();
		if (controls == null) controls = new Controls();
		if (gameplay.heavyArmor == null) gameplay.heavyArmor = new ArrayList<>();
		gameplay.heavyArmorSlowdown = Math.max(0F, Math.min(0.25F, gameplay.heavyArmorSlowdown));
		blink.minRange = Math.max(0.5F, Math.min(blink.range, blink.minRange));
		blink.groundSearchDepth = clamp(blink.groundSearchDepth, 0, 64);
		flight.minSpeed = Math.max(0.001F, Math.min(1F, flight.minSpeed));
		flight.maxSpeed = Math.max(flight.minSpeed, Math.min(2F, flight.maxSpeed));
		controls.blinkStep = Math.max(0.1F, Math.min(16F, controls.blinkStep));
		controls.runStep = Math.max(0.01F, Math.min(5F, controls.runStep));
		controls.maxRunScale = Math.max(0F, Math.min(10F, controls.maxRunScale));
		controls.flyStep = Math.max(0.001F, Math.min(0.5F, controls.flyStep));
		if (hud.style == null) hud.style = HudStyle.RING;
		if (hud.corner == null) hud.corner = HudCorner.BOTTOM_RIGHT;
		hud.size = clamp(hud.size, 8, 128);
		hud.opacity = Math.max(0.05F, Math.min(1F, hud.opacity));
		hud.visibleTicks = clamp(hud.visibleTicks, 0, 1200);
		blink.range = Math.max(1F, Math.min(128F, blink.range));
		blink.cooldownTicks = clamp(blink.cooldownTicks, 0, 1200);
		blink.fallDamage = Math.max(0F, blink.fallDamage);
		if (equipment.whitelist == null) equipment.whitelist = new ArrayList<>();
		if (equipment.blacklist == null) equipment.blacklist = new ArrayList<>();
		if (gameplay.disabledAbilities == null) gameplay.disabledAbilities = new ArrayList<>();
		performance.stareInterval = clamp(performance.stareInterval, 1, 40);
		performance.endermanScanInterval = clamp(performance.endermanScanInterval, 1, 40);
		performance.equipmentCheckInterval = clamp(performance.equipmentCheckInterval, 1, 40);
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}
}
