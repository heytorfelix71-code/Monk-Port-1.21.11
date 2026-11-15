package dev.monkmod.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Extra bare-hand break speed per block: a list of "block=factor" or "#tag=factor" (for example "minecraft:obsidian=1000",
 * "#minecraft:sand=1.5"); the first match wins and the factor multiplies the monk's speed on top of the global one.
 * A big factor (100+ on soft blocks, 1000+ on obsidian) makes the block break at once.
 */
public final class MiningSpeeds {
	private record Rule(TagKey<Block> tag, Block block, float factor) {
	}

	private static List<String> source;
	private static List<Rule> rules = List.of();

	private MiningSpeeds() {
	}

	private static void parse(List<String> lines) {
		List<Rule> out = new ArrayList<>();
		for (String raw : lines) {
			if (raw == null) {
				continue;
			}
			String line = raw.trim().toLowerCase(Locale.ROOT);
			int eq = line.lastIndexOf('=');
			if (eq <= 0) {
				continue;
			}
			float factor;
			try {
				factor = Float.parseFloat(line.substring(eq + 1).trim().replace(',', '.'));
			} catch (NumberFormatException e) {
				continue;
			}
			String id = line.substring(0, eq).trim();
			boolean tag = id.startsWith("#");
			Identifier key = Identifier.tryParse(tag ? id.substring(1) : id);
			if (key == null) {
				continue;
			}
			if (tag) {
				out.add(new Rule(TagKey.create(Registries.BLOCK, key), null, factor));
			} else if (BuiltInRegistries.BLOCK.containsKey(key)) {
				out.add(new Rule(null, BuiltInRegistries.BLOCK.getValue(key), factor));
			}
		}
		rules = out;
	}

	/** Multiplier for the hands' break speed on this block (the global multiplier included). */
	public static float factor(BlockState state) {
		MonkConfig.Mining m = MonkConfigManager.config().mining;
		if (m == null) {
			return 1.0F;
		}
		List<String> lines = m.blockSpeeds;
		if (lines != source) {
			source = lines;
			parse(lines == null ? List.of() : lines);
		}
		float f = Math.max(0F, m.speedMultiplier);
		for (Rule r : rules) {
			if (r.block() != null ? state.is(r.block()) : state.is(r.tag())) {
				return f * Math.max(0F, r.factor());
			}
		}
		return f;
	}
}
