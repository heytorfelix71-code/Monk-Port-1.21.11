package dev.monkmod.config;

import java.util.ArrayList;
import java.util.List;

/**
 * General settings, saved as config/monk/monk.json. The level layout lives in levels.json.
 * Field names are the JSON keys, so keep them stable.
 */
public final class MonkConfig {
	/** Settings format; older files are updated on load (see sanitize). */
	public int configVersion = 0;
	public Hud hud = new Hud();
	public Blink blink = new Blink();
	public Equipment equipment = new Equipment();
	public Gameplay gameplay = new Gameplay();
	public Performance performance = new Performance();
	public Flight flight = new Flight();
	public Water water = new Water();
	public Controls controls = new Controls();
	public Mining mining = new Mining();
	public Vision vision = new Vision();

	public enum VisionMode { OUTLINE, DOT }

	/** Mob Sense (see mobs through walls): how it looks. It is switched on and off with its key (. by default), off at the start. */
	public static final class Vision {
		/** OUTLINE = a glowing silhouette through the walls, DOT = a small colored square on the screen where the mob is. */
		public VisionMode mode = VisionMode.OUTLINE;
		public int hostileColor = 0xFF3030;
		public int passiveColor = 0x3080FF;
		public boolean showPassive = true;
		/** Only mobs you cannot see directly (behind walls). Off = every mob in range. */
		public boolean onlyHidden = true;
		public int range = 48;
		public int dotSize = 4;
	}

	/** Bare-hand break speed on top of the Mining ability. */
	public static final class Mining {
		/** Multiplies the break speed of the hands on every block (1 = as the ability gives). */
		public float speedMultiplier = 1.0F;
		/** "block=factor" or "#tag=factor": extra speed for those blocks (first match wins). Huge factors break at once. */
		public List<String> blockSpeeds = new ArrayList<>(List.of(
				"minecraft:obsidian=1000", "minecraft:crying_obsidian=1000",
				"#minecraft:dirt=12", "#minecraft:sand=1.5", "minecraft:snow_block=1.5", "minecraft:snow=1.5"));
	}

	public enum HudStyle { RING, BAR, TEXT }

	public enum HudCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

	/**
	 * Which keys change the blink distance, the running speed and the flight speed:
	 * ARROWS = the two speed keys (arrows by default), SCROLL = the mouse wheel with a modifier, CUSTOM = only the
	 * separate keys (unbound by default, pick any key you like).
	 */
	public enum ControlScheme { ARROWS, SCROLL, CUSTOM }

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
		/** Aiming at a wall (or hill) that is too high to land on: land on its top if it is within range. */
		public boolean climbWalls = true;
		/** Aiming just above a block (a tree, a pillar...) lands on its top. */
		public boolean landOnTops = true;
		/** Aiming past the loaded chunks (far islands seen with Voxy/Distant Horizons): the server loads the way and finds the ground. */
		public boolean farBlink = true;
		/** Blinks up to this far use the direct way (a ray, like the Ender Skills teleport); farther ones load the arrival in the background. */
		public float syncRange = 2000F;
		/** How close above a block top the aim must pass to land on it (blocks). */
		public float topTolerance = 1.5F;
		/** Highest wall the blink climbs, in blocks above the point you aimed at. */
		public int climbHeight = 32;
		/** What the aim preview shows while the blink key is held. */
		public PreviewMode previewMode = PreviewMode.FULL;
		public int previewColor = 0xCC00FA;
		/** Particles per tick of the preview (1 = very light, 10 = dense). */
		public int previewDensity = 4;
		/** Play the teleport sound. */
		public boolean sound = true;
		/** Teleport sound volume in percent. */
		public int soundVolume = 100;
		/** How much the preview line bends upward like a thrown arc (0 = straight, 100 = high arc). */
		public int previewCurve = 35;
		/** Translucent copy of your own player model where you will land. */
		public ShadowMode shadowMode = ShadowMode.TARGET;
		/** "Along the path": how many shadows at most (the last one is at the destination). */
		public int shadowCount = 4;
		/** "Along the path": smallest distance between two shadows, in blocks. */
		public float shadowSpacing = 3.0F;
		public int shadowColor = 0x9966FF;
		/** Shadow opacity in percent (100 = solid). */
		public int shadowOpacity = 40;
	}

	public enum ShadowMode {
		/** No shadow. */
		OFF,
		/** One shadow at the destination. */
		TARGET,
		/** Several shadows along the way, the last one at the destination. */
		PATH
	}

	public enum PreviewMode {
		/** No particles at all. */
		OFF,
		/** A small dot where you will land. */
		DOT,
		/** A dotted line from you to the landing spot. */
		LINE,
		/** A column of particles at the landing spot (the original look). */
		FULL
	}

	/** Water related settings of the water breathing abilities. */
	public enum SneakMode { TAP, HOLD }

	public static final class Water {
		/** Hide the air bubbles above the hotbar while you have infinite air. */
		public boolean hideAirBubbles = false;
		/** Monks with water breathing mine at full speed underwater, even floating. */
		public boolean underwaterMining = true;
		/** River currents (vanilla flowing water or mods like Streams) don't push monks who hold their depth. */
		public boolean ignoreCurrents = true;
		/** How sneak lets go of your depth: TAP = tap once to sink and be carried (tap again or jump to hold), HOLD = only while held. */
		public SneakMode sneakMode = SneakMode.TAP;
		/** While you let go, currents (also Streams) carry you. */
		public boolean letGoCurrents = true;
		/** While you let go, magma bubble columns pull you down normally. */
		public boolean letGoColumns = true;
		/** Extra downward speed while you let go (0 = only the normal water gravity). */
		public float sinkSpeed = 0.03F;
	}

	public static final class Flight {
		/** Vanilla flying speed is 0.05. */
		public float minSpeed = 0.01F;
		public float maxSpeed = 0.10F;
		/** Speed set when monk flight is unlocked or comes back (after armor, death...). */
		public float defaultSpeed = 0.035F;
	}

	/** Client-side controls (scroll wheel modifiers and step sizes). */
	public static final class Controls {
		/** Which keys adjust the blink distance, the running speed and the flight speed. */
		public ControlScheme scheme = ControlScheme.ARROWS;
		/** Each change of the blink distance is at least this % of the current distance (0 = always the fixed step). */
		public float blinkStepPercent = 10.0F;
		public float blinkStep = 1.0F;
		/** Step of the running bonus scale (1.0 = the full bonus of your level). */
		public float runStep = 0.1F;
		/** Share of the running bonus used while walking (not sprinting): 0.25 = a quarter, still following the chosen %. */
		public float walkShare = 0.25F;
		public float maxRunScale = 2.0F;
		public float flyStep = 0.005F;
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
		/** The "Blindness" level gives no blindness at all. */
		public boolean noBlindness = false;
		/** Catch Arrows: dodge projectiles coming from behind. Off = for every level, whatever levels.json says. */
		public boolean backDodge = true;
		/** Catch Arrows: chance to catch projectiles from behind. Off = for every level. */
		public boolean backCatch = true;
		/** Chance to catch an arrow that comes from behind when the level has no back catch of its own; if it fails the arrow hits you. */
		public float backCatchChance = 0.15F;
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
		if (water == null) water = new Water();
		if (water.sneakMode == null) water.sneakMode = SneakMode.TAP;
		water.sinkSpeed = Math.max(0F, Math.min(1F, water.sinkSpeed));
		if (blink.previewMode == null) blink.previewMode = PreviewMode.FULL;
		if (!blink.previewParticles) {
			blink.previewMode = PreviewMode.OFF;
			blink.previewParticles = true;
		}
		blink.climbHeight = clamp(blink.climbHeight, 0, 255);
		blink.syncRange = Math.max(64F, Math.min(29_999_984F, blink.syncRange));
		blink.previewDensity = clamp(blink.previewDensity, 1, 10);
		blink.previewColor &= 0xFFFFFF;
		blink.previewCurve = clamp(blink.previewCurve, 0, 100);
		if (blink.shadowMode == null) blink.shadowMode = ShadowMode.TARGET;
		blink.shadowCount = clamp(blink.shadowCount, 1, 16);
		blink.shadowSpacing = Math.max(1F, Math.min(32F, blink.shadowSpacing));
		blink.shadowColor &= 0xFFFFFF;
		blink.shadowOpacity = clamp(blink.shadowOpacity, 10, 100);
		blink.soundVolume = clamp(blink.soundVolume, 0, 100);
		if (configVersion >= 2 && configVersion < 3) {
			// 2.9.0: the blink goes up to 5000 blocks; whoever had the old maximum gets the new one
			if (blink.range >= 255F) {
				blink.range = 5000F;
			}
			configVersion = 3;
		}
		if (configVersion < 2) {
			// 2.5.0: monk flight was far too fast.
			flight.minSpeed = Math.min(flight.minSpeed, 0.01F);
			flight.maxSpeed = Math.min(flight.maxSpeed, 0.10F);
			if (controls != null) {
				controls.flyStep = Math.min(controls.flyStep, 0.005F);
			}
			configVersion = 3;
		}
		if (configVersion < 4) {
			// 2.9.3: running / flight speed moved from Ctrl/Space + mouse wheel to the arrow keys
			configVersion = 4;
		}
		if (configVersion < 5) {
			// 2.13.3: far blink off made every blink at a distance do nothing, silently; it is turned on once
			blink.farBlink = true;
			configVersion = 5;
		}
		flight.defaultSpeed = Math.max(flight.minSpeed, Math.min(flight.maxSpeed, flight.defaultSpeed));
		if (controls == null) controls = new Controls();
		if (mining == null) mining = new Mining();
		if (vision == null) vision = new Vision();
		if (vision.mode == null) vision.mode = VisionMode.OUTLINE;
		vision.hostileColor &= 0xFFFFFF;
		vision.passiveColor &= 0xFFFFFF;
		vision.range = clamp(vision.range, 8, 128);
		vision.dotSize = clamp(vision.dotSize, 2, 16);
		gameplay.backCatchChance = Math.max(0F, Math.min(1F, gameplay.backCatchChance));
		if (mining.blockSpeeds == null) mining.blockSpeeds = new ArrayList<>();
		mining.speedMultiplier = Math.max(0F, Math.min(100_000F, mining.speedMultiplier));
		if (controls.scheme == null) controls.scheme = ControlScheme.ARROWS;
		if (gameplay.heavyArmor == null) gameplay.heavyArmor = new ArrayList<>();
		gameplay.heavyArmorSlowdown = Math.max(0F, Math.min(0.25F, gameplay.heavyArmorSlowdown));
		blink.minRange = Math.max(0.5F, Math.min(blink.range, blink.minRange));
		blink.groundSearchDepth = clamp(blink.groundSearchDepth, 0, 64);
		flight.minSpeed = Math.max(0.001F, Math.min(1F, flight.minSpeed));
		flight.maxSpeed = Math.max(flight.minSpeed, Math.min(2F, flight.maxSpeed));
		controls.blinkStep = Math.max(0.1F, Math.min(100_000F, controls.blinkStep));
		controls.blinkStepPercent = Math.max(0F, Math.min(100F, controls.blinkStepPercent));
		controls.runStep = Math.max(0.01F, Math.min(5F, controls.runStep));
		controls.walkShare = Math.max(0F, Math.min(1F, controls.walkShare));
		controls.maxRunScale = Math.max(0F, Math.min(10F, controls.maxRunScale));
		controls.flyStep = Math.max(0.001F, Math.min(0.5F, controls.flyStep));
		if (hud.style == null) hud.style = HudStyle.RING;
		if (hud.corner == null) hud.corner = HudCorner.BOTTOM_RIGHT;
		hud.size = clamp(hud.size, 8, 128);
		hud.opacity = Math.max(0.05F, Math.min(1F, hud.opacity));
		hud.visibleTicks = clamp(hud.visibleTicks, 0, 1200);
		blink.range = Math.max(1F, Math.min(29_999_984F, blink.range));
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
