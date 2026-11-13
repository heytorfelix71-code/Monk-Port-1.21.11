package dev.monkmod.ability;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import dev.monkmod.type.Params;

/** Natural (plant) food satisfies more; meat and fish are left as they are. */
public final class DietAbility extends MonkAbility {
	public static final List<String> DEFAULT_FOODS = List.of(
			"minecraft:apple", "minecraft:golden_apple", "minecraft:enchanted_golden_apple",
			"minecraft:bread", "minecraft:cookie", "minecraft:pumpkin_pie",
			"minecraft:carrot", "minecraft:golden_carrot", "minecraft:potato", "minecraft:baked_potato",
			"minecraft:beetroot", "minecraft:beetroot_soup", "minecraft:mushroom_stew", "minecraft:suspicious_stew",
			"minecraft:melon_slice", "minecraft:glistering_melon_slice", "minecraft:sweet_berries", "minecraft:glow_berries",
			"minecraft:dried_kelp", "minecraft:chorus_fruit", "minecraft:honey_bottle");

	private final Set<Identifier> foods = new HashSet<>();
	private final float nutrition;
	private final float saturation;

	public DietAbility(Params params) {
		super("natural_diet", params);
		this.nutrition = params.getFloat("nutrition_multiplier");
		this.saturation = params.getFloat("saturation_multiplier");
		for (String s : params.getList("foods")) {
			Identifier id = Identifier.tryParse(s.trim());
			if (id != null) {
				foods.add(id);
			}
		}
	}

	public boolean isNatural(ItemStack stack) {
		return !stack.isEmpty() && foods.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	public int nutrition(int vanilla) {
		return Math.max(vanilla, Math.round(vanilla * nutrition));
	}

	public float saturation(float vanilla) {
		return vanilla * saturation;
	}

	@Override
	protected Object[] descriptionArgs() {
		return new Object[]{percent(nutrition - 1.0)};
	}
}
