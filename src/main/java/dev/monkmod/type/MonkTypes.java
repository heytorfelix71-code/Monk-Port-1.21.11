package dev.monkmod.type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

import dev.monkmod.ability.AttributeAbility;
import dev.monkmod.ability.AttributeAbility.Condition;
import dev.monkmod.ability.AttributeAbility.Mod;
import dev.monkmod.ability.EffectAbilities;
import dev.monkmod.ability.MiningAbility;
import dev.monkmod.ability.MonkAbility;
import dev.monkmod.ability.CombatAbilities;
import dev.monkmod.ability.DietAbility;
import dev.monkmod.ability.MovementAbilities;
import dev.monkmod.ability.PassiveAbilities;
import dev.monkmod.ability.ProtectionAbility;
import dev.monkmod.ability.SimpleAbility;
import dev.monkmod.ability.WitheringStare;
import dev.monkmod.requirement.BlockRequirements;
import dev.monkmod.requirement.BodyRequirements;
import dev.monkmod.requirement.CombatRequirements;
import dev.monkmod.requirement.MeditationRequirements;
import dev.monkmod.requirement.MonkRequirement;
import dev.monkmod.requirement.SocialRequirements;

import static dev.monkmod.ability.MonkAbility.number;
import static dev.monkmod.ability.MonkAbility.percent;
import static dev.monkmod.type.ParamSpec.ofBool;
import static dev.monkmod.type.ParamSpec.ofFloat;
import static dev.monkmod.type.ParamSpec.ofInt;
import static dev.monkmod.type.ParamSpec.ofList;
import static dev.monkmod.type.ParamSpec.ofString;

/** Every ability and requirement type available in levels.json. */
public final class MonkTypes {
	public static final Map<String, MonkType<MonkAbility>> ABILITIES = new LinkedHashMap<>();
	public static final Map<String, MonkType<MonkRequirement>> REQUIREMENTS = new LinkedHashMap<>();
	private static final int BIG = Integer.MAX_VALUE;

	private MonkTypes() {
	}

	private static void ability(String key, String summary, Function<Params, MonkAbility> factory, ParamSpec... params) {
		ABILITIES.put(key, new MonkType<>(key, summary, List.of(params), factory));
	}

	private static void requirement(String key, String summary, Function<Params, MonkRequirement> factory, ParamSpec... params) {
		REQUIREMENTS.put(key, new MonkType<>(key, summary, List.of(params), factory));
	}

	public static void bootstrap() {
		if (!ABILITIES.isEmpty()) {
			return;
		}
		// ---------------------------------------------------------------- abilities
		ability("armor", "Natural armor points while wearing no armor.",
				p -> new AttributeAbility("armor", p, Condition.UNARMORED,
						List.of(new Mod(Attributes.ARMOR, "armor", Operation.ADD_VALUE, (pl, s) -> p.getFloat("armor"))),
						a -> new Object[]{number(p.getFloat("armor"))}),
				ofFloat("armor", 1, 0, 1024, "Armor points added."));
		ability("armor_toughness", "Natural armor toughness while wearing no armor.",
				p -> new AttributeAbility("armor_toughness", p, Condition.UNARMORED,
						List.of(new Mod(Attributes.ARMOR_TOUGHNESS, "toughness", Operation.ADD_VALUE, (pl, s) -> p.getFloat("armor_toughness"))),
						a -> new Object[]{number(p.getFloat("armor_toughness"))}),
				ofFloat("armor_toughness", 1, 0, 1024, "Armor toughness added."));
		ability("knockback_resistance", "Resist knockback while wearing no armor.",
				p -> new AttributeAbility("knockback_resistance", p, Condition.UNARMORED,
						List.of(new Mod(Attributes.KNOCKBACK_RESISTANCE, "knockback", Operation.ADD_VALUE, (pl, s) -> p.getFloat("knockback_resistance") / 10.0)),
						a -> new Object[]{number(p.getFloat("knockback_resistance"))}),
				ofFloat("knockback_resistance", 5, 0, 10, "Knockback resistance points (full netherite armor = 4, 10 = no knockback at all)."));
		ability("strength", "Extra bare-hand damage.",
				p -> new AttributeAbility("strength", p, Condition.UNARMED,
						List.of(new Mod(Attributes.ATTACK_DAMAGE, "damage", Operation.ADD_VALUE, (pl, s) -> p.getFloat("damage"))),
						a -> new Object[]{number(p.getFloat("damage"))}),
				ofFloat("damage", 1, 0, 2048, "Damage points added to unarmed attacks."));
		ability("health", "More max health.",
				p -> new AttributeAbility("health", p, Condition.ALWAYS,
						List.of(new Mod(Attributes.MAX_HEALTH, "health", Operation.ADD_MULTIPLIED_BASE, (pl, s) -> p.getFloat("health_increase"))),
						a -> new Object[]{percent(p.getFloat("health_increase"))}),
				ofFloat("health_increase", 0.5, 0, 100, "Multiplier added to base max health (0.5 = +50%)."));
		ability("swift", "Run faster while unarmored (bonus doubled while sprinting). Does not change your FOV; Ctrl + mouse wheel adjusts it.",
				PassiveAbilities.Swift::new,
				ofFloat("increase", 0.4, 0, 10, "Speed bonus (0.4 = +40%)."),
				ofFloat("sprint_multiplier", 2, 0, 10, "Bonus multiplier while sprinting."),
				ofBool("only_unarmored", true, "Only works while wearing no armor."),
				ofFloat("jump_height", 4, 1.25, 64, "Jump height in blocks with the running bonus at 100%."),
				ofFloat("max_jump_height", 7, 1.25, 64, "Jump height in blocks with the highest running bonus (200% at the last level)."),
				ofFloat("double_jump", 2, 0, 64, "Extra height of the double jump (press jump again in the air) at 100%. 0 = no double jump."),
				ofFloat("max_double_jump", 5, 0, 64, "Extra height of the double jump with the highest running bonus."));
		ability("natural_diet", "Plant food (bread, fruit, vegetables, soups...) satisfies much more. Meat and fish don't.",
				DietAbility::new,
				ofFloat("nutrition_multiplier", 1.5, 1, 10, "Hunger points restored by natural food (1.5 = +50%)."),
				ofFloat("saturation_multiplier", 2, 1, 20, "Saturation from natural food (2 = twice as long until you get hungry)."),
				ofList("foods", DietAbility.DEFAULT_FOODS, "Item ids that count as natural food."));
		ability("jump", "Jump higher and take fall damage later.",
				p -> new AttributeAbility("jump", p, Condition.ALWAYS,
						List.of(new Mod(Attributes.JUMP_STRENGTH, "jump", Operation.ADD_MULTIPLIED_BASE, (pl, s) -> p.getFloat("jump") - 1.0),
								new Mod(Attributes.SAFE_FALL_DISTANCE, "safe_fall", Operation.ADD_VALUE, (pl, s) -> p.getFloat("safe_fall"))),
						a -> new Object[]{percent(p.getFloat("jump") - 1.0)}),
				ofFloat("jump", 1.2, 1, 10, "Jump strength multiplier (1.2 = +20%)."),
				ofFloat("safe_fall", 3, 0, 256, "Extra blocks you can fall without damage."));
		ability("step_up", "Walk up full blocks without jumping (hold sneak to disable).",
				p -> new AttributeAbility("step_up", p, Condition.UNARMORED,
						List.of(new Mod(Attributes.STEP_HEIGHT, "step", Operation.ADD_VALUE,
								(pl, s) -> pl.isShiftKeyDown() ? 0.0 : Math.max(0.0, p.getFloat("height") - 0.6))),
						a -> new Object[0]),
				ofFloat("height", 1.5, 0.6, 10, "Total step height in blocks (vanilla is 0.6)."));
		ability("feather_fall", "Less fall damage.",
				p -> new AttributeAbility("feather_fall", p, Condition.ALWAYS,
						List.of(new Mod(Attributes.FALL_DAMAGE_MULTIPLIER, "fall", Operation.ADD_MULTIPLIED_TOTAL, (pl, s) -> p.getFloat("multiplier") - 1.0)),
						a -> new Object[]{percent(1.0 - p.getFloat("multiplier"))}),
				ofFloat("multiplier", 0.2, 0, 1, "Fraction of fall damage still taken (0.2 = 80% less)."));
		ability("water_breathing_partial", "Hold your breath much longer and swim faster.",
				p -> new PassiveAbilities.WaterBreathing("water_breathing_partial", p, false),
				ofFloat("oxygen_bonus", 3, 0, 1024, "Oxygen bonus (like Respiration levels)."),
				ofBool("infinite", false, "Never run out of air at all."),
				ofFloat("swim_speed", 0.33, 0, 1, "Extra swimming speed (0.33 = like Depth Strider I, 1 = Depth Strider III)."),
				ofBool("hide_bubbles", false, "Hide the air bubbles above the hotbar."),
				ofFloat("swim_boost", 0.06, 0, 0.1, "Extra swimming speed added every tick (0.06 = about twice as fast)."),
				ofBool("hold_depth", true, "You don't sink: press sneak to go down, press jump once to stay at that depth again."));
		ability("water_breathing_full", "Never drown; swim faster.",
				p -> new PassiveAbilities.WaterBreathing("water_breathing_full", p, true),
				ofFloat("swim_speed", 0.66, 0, 1, "Extra swimming speed (0.33 = like Depth Strider I, 1 = Depth Strider III)."),
				ofBool("hide_bubbles", true, "Hide the air bubbles above the hotbar."),
				ofFloat("swim_boost", 0.08, 0, 0.1, "Extra swimming speed added every tick (0.06 = about twice as fast)."),
				ofBool("hold_depth", true, "You don't sink: press sneak to go down, press jump once to stay at that depth again."));
		ability("fire_resistance", "Less fire damage.",
				p -> new ProtectionAbility("fire_resistance", p, s -> s.is(DamageTypeTags.IS_FIRE), p.getFloat("multiplier")),
				ofFloat("multiplier", 0.5, 0, 1, "Fraction of fire damage still taken."));
		ability("explosion_resistance", "Less explosion damage.",
				p -> new ProtectionAbility("explosion_resistance", p, s -> s.is(DamageTypeTags.IS_EXPLOSION), p.getFloat("multiplier")),
				ofFloat("multiplier", 0.5, 0, 1, "Fraction of explosion damage still taken."));
		ability("lava_protection", "Entering lava makes you immune to fire for a while (no potion effect).",
				MovementAbilities.LavaProtection::new,
				ofInt("duration", 80, 1, 72000, "Immunity duration in ticks (20 ticks = 1 second)."),
				ofInt("cooldown", 200, 0, 72000, "Ticks before it can trigger again."),
				ofBool("infinite", false, "Always immune to fire and lava."));
		ability("hunger", "Hunger drains slower.", MovementAbilities.Hunger::new,
				ofFloat("hunger_chance", 0.5, 0, 1, "How much slower (0.5 = 50% slower)."),
				ofFloat("movement_reduction", 0.6, 0, 1, "Extra reduction while sprinting or swimming (0.6 = 60% less on top). Running faster with Swiftness never costs more hunger than normal running."));
		ability("mining", "Mine with your hands (also while holding an item or the wrong tool).", MiningAbility::new,
				ofInt("harvest_level", 0, 0, 5, "0 wood, 1 stone, 2 iron, 3 diamond, 4 netherite, 5 anything."),
				ofFloat("speed_increase", 0, 0, 1000, "Bare-hand speed bonus (2 = 3x as fast)."),
				ofList("harvest_tools", MiningAbility.defaultTools(), "Tool kinds your hands replace: pickaxe, shovel, axe, hoe (\"\" = none needed)."));
		ability("blindness", "Permanent blindness.", EffectAbilities.Blindness::new);
		ability("potion_immunity", "Immune to the listed effects; can also block dragon breath and creeper explosions.",
				EffectAbilities.PotionImmunity::new,
				ofList("ignored_effects", EffectAbilities.PotionImmunity.DEFAULT_IMMUNITIES,
						"Effects you are immune to. Extra entries: monk:dragon_breath, monk:creeper_explosion, * (every harmful effect)."));
		ability("fly", "Creative-style flight while unarmored.", MovementAbilities.Fly::new);
		ability("withering_stare", "Looking at undead withers them.", WitheringStare::new,
				ofInt("range", 20, 1, 128, "Stare distance in blocks."),
				ofInt("duration", 200, 1, 72000, "Wither effect duration in ticks."),
				ofFloat("wither_boss_damage", 5, 0, 1000, "Damage dealt to the Wither boss per stare check."),
				ofBool("hostile_mobs", true, "Also withers every hostile mob (creepers, spiders...), not only the undead."));
		ability("blink", "Teleport where you look with the Blink key.", p -> new SimpleAbility("blink", p));
		ability("catch_arrows", "Catch projectiles flying at you from the front; they go to your inventory.", p -> new SimpleAbility("catch_arrows", p),
				ofFloat("max_angle_dot", 0.2, -1, 1, "How far to the side projectiles can be caught (higher = wider)."),
				ofFloat("catch_chance", 0.25, 0, 1, "Chance to catch a projectile coming from the front (0.25 = 25%). It grows with the levels."),
				ofBool("catch_all", true, "Catch everything thrown (tridents, snowballs, eggs, ender pearls, potions, fireworks, fire charges), not only arrows."),
				ofBool("to_inventory", true, "Caught projectiles go to your inventory."),
				ofBool("require_empty_hand", true, "You need an empty main hand to catch."),
				ofBool("back_dodge", false, "Projectiles coming from behind are dodged (they fly through without hitting you)."),
				ofFloat("back_catch_chance", 0, 0, 1, "With back dodge: chance to catch a projectile from behind instead of just dodging it (0.2 = 20%)."));
		ability("kiss_creeper", "Ask creepers kindly not to explode (right click with an empty hand); calmed creepers never explode.",
				p -> new SimpleAbility("kiss_creeper", p),
				ofBool("passive", false, "Creepers also never chase or explode at you, even without asking."));
		ability("iron_body", "Take less damage of every kind while wearing no armor.", CombatAbilities.IronBody::new,
				ofFloat("multiplier", 0.6, 0, 1, "Fraction of damage still taken (0.6 = 40% less)."));
		ability("regeneration", "Heal slowly after some time without fighting.", CombatAbilities.Regeneration::new,
				ofInt("delay", 1800, 0, 72000, "Ticks without dealing or taking damage before healing starts (1800 = 90 seconds)."),
				ofInt("interval", 40, 1, 72000, "Ticks between heals."),
				ofFloat("amount", 1, 0, 1024, "Health restored each time (1 = half a heart)."));
		ability("time_stop", "Stop time with its key (J): only players keep moving.", CombatAbilities.TimeStop::new,
				ofInt("duration", 600, 20, 72000, "How long time stays stopped, in ticks (600 = 30 seconds)."),
				ofInt("cooldown", 1200, 0, 720000, "Ticks before it can be used again (1200 = 60 seconds)."),
				ofBool("flight", true, "You can fly while time is stopped."),
				ofBool("freeze_players", true, "Other players are frozen too (they can't move, attack, use or break anything)."));
		ability("night_vision", "Toggle night vision with its key (V by default).", PassiveAbilities.NightVision::new);
		ability("tame_animals", "Pet animals to calm them or make them fall in love.", p -> new SimpleAbility("tame_animals", p));
		ability("water_walking", "Walk on water (sneak to sink).", p -> new SimpleAbility("water_walking", p),
				ofFloat("height", 0.89, 0.6, 1.0, "Height of your feet above the bottom of the water block (0.89 = right on the surface, 1 = one pixel above)."),
				ofBool("lift", true, "Walking in from the shore or dropping in from low height puts you back on the surface."),
				ofFloat("max_fall", 3, 0, 256, "Falls higher than this (in blocks) dive into the water instead of landing on it."));
		ability("glass_touch", "Break glass bare-handed without shattering it.", p -> new SimpleAbility("glass_touch", p));

		// ---------------------------------------------------------------- requirements
		requirement("meditate", "Meditate: sneak without moving, unarmored and empty-handed, under the open sky.", MeditationRequirements.Meditate::new,
				ofInt("time", 2400, 1, BIG, "Ticks of still meditation needed (2400 = 2 minutes)."),
				ofBool("open_sky", true, "Must be under the open sky."));
		requirement("break_block", "Break N blocks (or a #tag) with an empty hand.", BlockRequirements.BreakBlock::fromSpec,
				ofString("block", "minecraft:stone", "Block id, or #tag id."),
				ofInt("number", 5, 1, BIG, "How many."));
		requirement("break_wood", "Break N logs with an empty hand.", BlockRequirements.BreakBlock::wood,
				ofInt("number", 10, 1, BIG, "How many."));
		requirement("drown", "Stay underwater until almost out of air.", MeditationRequirements.Drown::new,
				ofInt("swim_time", 280, 1, BIG, "Ticks of holding breath needed."));
		requirement("mediate_sunrise", "Look at the sun at sunrise or sunset.", MeditationRequirements.Sunrise::new,
				ofInt("time", 200, 1, BIG, "Ticks of staring."));
		requirement("pet", "Pet N different animals with an empty hand.", SocialRequirements.Pet::new,
				ofInt("number", 20, 1, BIG, "How many animals."));
		requirement("sprint", "Sprint a distance unarmored (jumps don't count).",
				p -> new BodyRequirements.Walk("sprint", p, BodyRequirements.Walk.Mode.SPRINT),
				ofFloat("distance", 200, 1, 1_000_000, "Blocks."));
		requirement("walk_fire", "Walk through fire.",
				p -> new BodyRequirements.Walk("walk_fire", p, BodyRequirements.Walk.Mode.FIRE),
				ofFloat("distance", 10, 1, 1_000_000, "Blocks."));
		requirement("kill_undead", "Kill N undead unarmed.",
				p -> new CombatRequirements.Kill("kill_undead", p, e -> e.getType().is(EntityTypeTags.UNDEAD), false),
				ofInt("kills", 10, 1, BIG, "How many."));
		requirement("kill_hostile", "Kill N hostile mobs unarmed.",
				p -> new CombatRequirements.Kill("kill_hostile", p, e -> e instanceof Enemy, false),
				ofInt("kills", 10, 1, BIG, "How many."));
		requirement("kill_blind", "Kill N hostile mobs unarmed; cures blindness.",
				p -> new CombatRequirements.Kill("kill_blind", p, e -> e instanceof Enemy, true),
				ofInt("kills", 5, 1, BIG, "How many."));
		requirement("kill_entity_type", "Defeat N mobs of a type in fair melee (25%+ of its health, no armor/weapons/projectiles).",
				CombatRequirements.EnemyDefeat::new,
				ofString("entity_type", "minecraft:wither", "Entity id."),
				ofInt("kills", 1, 1, BIG, "How many."));
		requirement("fall", "Fall from high up onto a block (hay).", BodyRequirements.Fall::new,
				ofFloat("distance", 40, 1, 10_000, "Minimum fall height in blocks."),
				ofString("block", "minecraft:hay_block", "Block you must land on."));
		requirement("kiss_creeper", "Kiss a creeper (empty hand).", SocialRequirements.KissCreeper::new);
		requirement("arrow_dodge", "Dodge N skeleton arrows in a row.", CombatRequirements.ArrowDodge::new,
				ofInt("dodges", 5, 1, BIG, "How many."));
		requirement("meditate_water_moon", "From a boat, look at the midnight moon.", MeditationRequirements.WaterMoon::new,
				ofInt("stare_time", 200, 1, BIG, "Ticks."));
		requirement("wither_stare", "Stare down a wither skeleton in a nether fortress.", MeditationRequirements.WitherStare::new,
				ofInt("stare_time", 200, 1, BIG, "Ticks."));
		requirement("meditate_endermen", "Stay near endermen without angering them.", MeditationRequirements.Endermen::new,
				ofInt("time", 600, 1, BIG, "Enderman-ticks needed (more endermen = faster)."),
				ofFloat("radius", 20, 1, 128, "Search radius."));
		requirement("bedrock_sleep", "Sleep on a 2-block 'bed of rock' (bedrock).",
				p -> new BodyRequirements.Sleep("bedrock_sleep", p, BodyRequirements.Sleep.Kind.BEDROCK));
		requirement("sleep_cactus", "Sleep on a cactus.",
				p -> new BodyRequirements.Sleep("sleep_cactus", p, BodyRequirements.Sleep.Kind.CACTUS));
		requirement("advancement", "Complete an advancement.", SocialRequirements.Advancement::new,
				ofString("advancement_resource_location", "minecraft:story/mine_diamond", "Advancement id."),
				ofString("achievement_name", "Diamonds!", "Name shown in the description (plain text or JSON text)."));
		requirement("void_fall", "Fall into the void and come back.", BodyRequirements.VoidFall::new,
				ofInt("messages", 14, 0, 14, "Visions shown before ascending."));
		requirement("none", "Granted immediately.", SocialRequirements.Free::new);
	}

	/** Text listing every type and parameter, written to config/monk/README.txt. */
	public static String reference() {
		StringBuilder b = new StringBuilder();
		b.append("ABILITIES (use in \"add\")\n=========================\n");
		ABILITIES.values().forEach(t -> describe(b, t));
		b.append("\nREQUIREMENTS (use as \"requirement\")\n====================================\n");
		REQUIREMENTS.values().forEach(t -> describe(b, t));
		return b.toString();
	}

	private static void describe(StringBuilder b, MonkType<?> t) {
		b.append("\n").append(t.key()).append(" - ").append(t.summary()).append('\n');
		t.params().values().forEach(p -> b.append("    ").append(p.describe()).append('\n'));
	}
}
