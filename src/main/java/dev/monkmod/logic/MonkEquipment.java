package dev.monkmod.logic;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import dev.monkmod.MonkMod;
import dev.monkmod.config.MonkConfig;

/**
 * "Do not use weapons, armor or projectiles." Decides whether a player counts as unarmed / unarmored.
 * Items are judged by the attribute modifiers they grant, plus config lists and two item tags
 * (#monk:monk_safe, #monk:monk_forbidden) so modpacks can adjust it without code.
 */
public final class MonkEquipment {
	public static final TagKey<Item> MONK_SAFE = TagKey.create(Registries.ITEM, MonkMod.id("monk_safe"));
	public static final TagKey<Item> MONK_FORBIDDEN = TagKey.create(Registries.ITEM, MonkMod.id("monk_forbidden"));

	private static final EquipmentSlot[] HANDS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private static Set<Identifier> whitelist = Set.of();
	private static Set<Identifier> blacklist = Set.of();
	private static boolean strictArmor;

	private MonkEquipment() {
	}

	public static void reload(MonkConfig config) {
		whitelist = parse(config.equipment.whitelist);
		blacklist = parse(config.equipment.blacklist);
		strictArmor = config.equipment.strictArmor;
	}

	private static Set<Identifier> parse(Iterable<String> ids) {
		Set<Identifier> out = new HashSet<>();
		for (String s : ids) {
			Identifier id = Identifier.tryParse(s.trim());
			if (id != null) {
				out.add(id);
			}
		}
		return Set.copyOf(out);
	}

	public static boolean computeUnarmed(Player player) {
		for (EquipmentSlot slot : HANDS) {
			if (isContraband(player.getItemBySlot(slot), slot, Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED, false)) {
				return false;
			}
		}
		return true;
	}

	public static boolean computeUnarmored(Player player) {
		for (EquipmentSlot slot : ARMOR) {
			if (isContraband(player.getItemBySlot(slot), slot, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, strictArmor)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isContraband(ItemStack stack, EquipmentSlot slot, Holder<Attribute> a, Holder<Attribute> b, boolean strict) {
		if (stack.isEmpty()) {
			return false;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (whitelist.contains(id) || stack.is(MONK_SAFE)) {
			return false;
		}
		if (blacklist.contains(id) || stack.is(MONK_FORBIDDEN)) {
			return true;
		}
		if (strict) {
			return true;
		}
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if ((entry.attribute().equals(a) || entry.attribute().equals(b)) && entry.slot().test(slot)) {
				return true;
			}
		}
		return false;
	}
}
