package dev.monkmod.advancement;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.monkmod.MonkMod;
import dev.monkmod.ability.MonkAbility;
import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.level.LevelTable;

/**
 * Builds the "Monk" advancement tab from the level table every time datapacks (re)load.
 * Replaces the 1.12 approach of writing JSON files into the world folder.
 */
public final class MonkAdvancements {
	private static final Identifier BACKGROUND = MonkMod.id("gui/advancements/backgrounds/monk");

	private MonkAdvancements() {
	}

	public static void inject(Map<Identifier, Advancement> map) {
		LevelTable table = MonkConfigManager.table();
		boolean announce = MonkConfigManager.config().gameplay.announceLevelUps;
		if (table.maxLevel() < 0) {
			return;
		}
		try {
			MutableComponent rootDesc = Component.translatable("monk.advancements.level.start.desc")
					.append("\n").append(table.requirement(0).description());
			put(map, MonkMod.id("root"), Advancement.Builder.advancement()
					.display(icon(table.icon(0)), Component.translatable("monk.advancements.level.start"), rootDesc,
							BACKGROUND, AdvancementType.TASK, true, false, false)
					.addCriterion("level", criterion(0)));

			for (int level = 1; level <= table.maxLevel(); level++) {
				Identifier parent = level == 1 ? MonkMod.id("root") : MonkMod.id("level_" + (level - 1));
				put(map, MonkMod.id("level_" + level), Advancement.Builder.advancement()
						.parent(parent)
						.display(icon(table.icon(level)), Component.translatable("monk.advancements.level", level),
								table.requirement(level).description(), null, AdvancementType.TASK, true, announce, false)
						.addCriterion("level", criterion(level)));

				List<MonkAbility> added = table.addedAt(level);
				if (!added.isEmpty()) {
					MutableComponent rewards = Component.empty();
					for (int i = 0; i < added.size(); i++) {
						if (i > 0) {
							rewards.append("\n");
						}
						rewards.append(added.get(i).describe());
					}
					put(map, MonkMod.id("level_reward_" + level), Advancement.Builder.advancement()
							.parent(parent)
							.display(icon("monk:icon/rewards"), Component.translatable("monk.advancements.reward", level),
									rewards, null, AdvancementType.GOAL, false, false, true)
							.addCriterion("level", criterion(level)));
				}
			}
		} catch (RuntimeException e) {
			MonkMod.LOGGER.error("Could not build monk advancements", e);
		}
	}

	private static void put(Map<Identifier, Advancement> map, Identifier id, Advancement.Builder builder) {
		AdvancementHolder holder = builder.build(id);
		map.put(id, holder.value());
	}

	private static net.minecraft.advancements.Criterion<MonkLevelTrigger.Conditions> criterion(int level) {
		return MonkCriteria.LEVEL_UP.createCriterion(new MonkLevelTrigger.Conditions(Optional.empty(), level));
	}

	/** "monk:icon/x" style ids use our item models; any registered item id shows that item. */
	public static ItemStack icon(String spec) {
		Identifier id = Identifier.tryParse(spec);
		if (id == null) {
			id = MonkMod.id("icon/meditate");
		}
		Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
		if (item.isPresent() && item.get() != Items.AIR) {
			return new ItemStack(item.get());
		}
		ItemStack stack = new ItemStack(Items.PAPER);
		stack.set(DataComponents.ITEM_MODEL, id);
		return stack;
	}
}
