package dev.monkmod.ability;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import dev.monkmod.type.Params;

/**
 * Bare-handed mining. harvest_level: 0 wood, 1 stone, 2 iron, 3 diamond, 4 netherite, 5+ anything.
 * harvest_tools lists which tool kinds the hands replace ("" = blocks that need no tool).
 */
public final class MiningAbility extends MonkAbility {
	@SuppressWarnings("unchecked")
	private static final TagKey<Block>[] INCORRECT = new TagKey[]{
			BlockTags.INCORRECT_FOR_WOODEN_TOOL,
			BlockTags.INCORRECT_FOR_STONE_TOOL,
			BlockTags.INCORRECT_FOR_IRON_TOOL,
			BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
			BlockTags.INCORRECT_FOR_NETHERITE_TOOL
	};

	private final int harvestLevel;
	private final float speedIncrease;
	private final boolean pickaxe;
	private final boolean shovel;
	private final boolean axe;
	private final boolean hoe;

	public MiningAbility(Params params) {
		super("mining", params);
		this.harvestLevel = params.getInt("harvest_level");
		this.speedIncrease = params.getFloat("speed_increase");
		Set<String> tools = new HashSet<>();
		for (String s : params.getList("harvest_tools")) {
			tools.add(s.trim().toLowerCase());
		}
		// The original mod never let level-0 hands act as a pickaxe.
		this.pickaxe = tools.contains("pickaxe") && harvestLevel > 0;
		this.shovel = tools.contains("shovel");
		this.axe = tools.contains("axe");
		this.hoe = tools.contains("hoe");
	}

	public static List<String> defaultTools() {
		return List.of("", "pickaxe", "shovel", "axe");
	}

	/** True if bare hands with this ability may harvest (get drops from) the block. */
	public boolean canHarvest(BlockState state) {
		if (!state.requiresCorrectToolForDrops()) {
			return true;
		}
		if (harvestLevel >= 5) {
			return true;
		}
		boolean toolOk = (pickaxe && state.is(BlockTags.MINEABLE_WITH_PICKAXE))
				|| (shovel && state.is(BlockTags.MINEABLE_WITH_SHOVEL))
				|| (axe && state.is(BlockTags.MINEABLE_WITH_AXE))
				|| (hoe && state.is(BlockTags.MINEABLE_WITH_HOE));
		if (!toolOk) {
			return false;
		}
		return harvestLevel >= 0 && !state.is(INCORRECT[Math.min(harvestLevel, INCORRECT.length - 1)]);
	}

	/** Bare-hand break speed: vanilla speed boosted up to (1 + speed_increase), never slower than vanilla. */
	public float modifySpeed(float vanilla) {
		if (speedIncrease <= 0) {
			return vanilla;
		}
		return Math.max(vanilla, (1.0F + speedIncrease) * Math.min(vanilla, 1.0F));
	}

	@Override
	public String translationKey() {
		return super.translationKey() + "." + Math.max(0, Math.min(5, harvestLevel));
	}
}
