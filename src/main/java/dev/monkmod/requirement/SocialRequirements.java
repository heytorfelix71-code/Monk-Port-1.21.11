package dev.monkmod.requirement;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;

import dev.monkmod.data.MonkData;
import dev.monkmod.logic.MonkInteractions;
import dev.monkmod.logic.MonkPlayerState;
import dev.monkmod.type.Params;

/** Gentle requirements: petting, kissing creepers, plus generic advancement / free levels. */
public final class SocialRequirements {
	private SocialRequirements() {
	}

	/** Pet N different animals with an empty hand. */
	public static final class Pet extends MonkRequirement {
		public Pet(Params params) {
			super("pet", params, params.getInt("number"));
		}

		@Override
		public InteractionResult onUseEntity(ServerPlayer player, MonkData data, MonkPlayerState state, Entity target, InteractionHand hand) {
			if (!(target instanceof Animal) || !player.getItemInHand(hand).isEmpty()) {
				return null;
			}
			if (!data.petted().add(target.getUUID())) {
				return null;
			}
			MonkInteractions.say(player, target, "monk.heart");
			progress(player, data, 1);
			return InteractionResult.SUCCESS;
		}
	}

	/** Convince a creeper not to explode with a friendly kiss. */
	public static final class KissCreeper extends MonkRequirement {
		public KissCreeper(Params params) {
			super("kiss_creeper", params, -1);
		}

		@Override
		public InteractionResult onUseEntity(ServerPlayer player, MonkData data, MonkPlayerState state, Entity target, InteractionHand hand) {
			if (target.getType() != EntityType.CREEPER || !player.getItemInHand(hand).isEmpty()) {
				return null;
			}
			MonkInteractions.calmCreeper(target);
			MonkInteractions.say(player, target, "monk.blush");
			grant(player, data);
			return InteractionResult.SUCCESS;
		}
	}

	/** Complete any advancement (vanilla or from another mod). */
	public static final class Advancement extends MonkRequirement {
		private final Identifier advancement;
		private final Component name;

		public Advancement(Params params) {
			super("advancement", params, -1);
			this.advancement = Identifier.tryParse(params.getString("advancement_resource_location"));
			this.name = parseText(params.getString("achievement_name"));
			if (advancement == null) {
				throw new IllegalArgumentException("Invalid advancement id '" + params.getString("advancement_resource_location") + "'");
			}
		}

		private static Component parseText(String raw) {
			String s = raw.trim();
			if (s.startsWith("{") || s.startsWith("[") || s.startsWith("\"")) {
				try {
					return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(s)).result().orElse(Component.literal(s));
				} catch (RuntimeException ignored) {
					// fall through to plain text
				}
			}
			return Component.literal(s);
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkData data, MonkPlayerState state) {
			if ((player.tickCount + state.tickOffset) % 20 != 0) {
				return;
			}
			MinecraftServer server = player.level().getServer();
			if (server == null) {
				return;
			}
			AdvancementHolder holder = server.getAdvancements().get(advancement);
			if (holder == null || player.getAdvancements().getOrStartProgress(holder).isDone()) {
				grant(player, data);
			}
		}

		@Override
		protected Object[] descriptionArgs() {
			return new Object[]{name};
		}
	}

	/** Granted immediately: handy for levels that only exist to give or remove abilities. */
	public static final class Free extends MonkRequirement {
		public Free(Params params) {
			super("none", params, -1);
		}

		@Override
		public boolean isTicking() {
			return true;
		}

		@Override
		public void tick(ServerPlayer player, MonkData data, MonkPlayerState state) {
			grant(player, data);
		}
	}
}
