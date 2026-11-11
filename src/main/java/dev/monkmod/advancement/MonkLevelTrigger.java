package dev.monkmod.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/** "monk:levelup" — met when the player's monk level is at least {@code level}. */
public final class MonkLevelTrigger extends SimpleCriterionTrigger<MonkLevelTrigger.Conditions> {
	public void trigger(ServerPlayer player, int level) {
		trigger(player, conditions -> conditions.level() <= level);
	}

	@Override
	public Codec<Conditions> codec() {
		return Conditions.CODEC;
	}

	public record Conditions(Optional<ContextAwarePredicate> player, int level) implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ContextAwarePredicate.CODEC.optionalFieldOf("player").forGetter(Conditions::player),
				Codec.INT.fieldOf("level").forGetter(Conditions::level)
		).apply(instance, Conditions::new));
	}
}
