package dev.monkmod.requirement;

import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import dev.monkmod.data.MonkData;
import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.type.Params;

/** Break blocks with an empty main hand. */
public final class BlockRequirements {
	private BlockRequirements() {
	}

	public static final class BreakBlock extends MonkRequirement {
		private final Predicate<BlockState> matcher;
		private final Component blockName;

		public BreakBlock(String key, Params params, int number, Predicate<BlockState> matcher, Component blockName) {
			super(key, params, number);
			this.matcher = matcher;
			this.blockName = blockName;
		}

		/** "minecraft:stone" for one block or "#minecraft:logs" for a block tag. */
		public static BreakBlock fromSpec(Params params) {
			String spec = params.getString("block").trim();
			int number = params.getInt("number");
			if (spec.startsWith("#")) {
				Identifier id = Identifier.tryParse(spec.substring(1));
				if (id == null) {
					throw new IllegalArgumentException("Invalid block tag '" + spec + "'");
				}
				TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
				return new BreakBlock("break_block", params, number, s -> s.is(tag), Component.literal(spec));
			}
			Identifier id = Identifier.tryParse(spec);
			Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
			if (block == null) {
				throw new IllegalArgumentException("Unknown block '" + spec + "'");
			}
			return new BreakBlock("break_block", params, number, s -> s.is(block), block.getName());
		}

		public static BreakBlock wood(Params params) {
			return new BreakBlock("break_wood", params, params.getInt("number"), s -> s.is(BlockTags.LOGS), Component.empty());
		}

		@Override
		public void onBlockBreak(ServerPlayer player, MonkData data, MonkPlayerState state, BlockPos pos, BlockState blockState) {
			if (player.getMainHandItem().isEmpty() && matcher.test(blockState)) {
				progress(player, data, 1);
			}
		}

		@Override
		protected Object[] descriptionArgs() {
			return key.equals("break_wood") ? new Object[]{limit} : new Object[]{limit, blockName};
		}
	}
}
