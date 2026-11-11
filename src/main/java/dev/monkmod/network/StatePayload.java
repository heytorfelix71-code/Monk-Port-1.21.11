package dev.monkmod.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/**
 * Server → client: current level, the next requirement type and the active abilities (as their JSON
 * config entries, so the client builds identical ability objects for client-side mechanics).
 */
public record StatePayload(int level, int maxLevel, String nextRequirement, List<String> abilities, float blinkRange, int blinkCooldown)
		implements CustomPacketPayload {
	public static final Type<StatePayload> TYPE = new Type<>(MonkMod.id("state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StatePayload::level,
			ByteBufCodecs.VAR_INT, StatePayload::maxLevel,
			ByteBufCodecs.STRING_UTF8, StatePayload::nextRequirement,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), StatePayload::abilities,
			ByteBufCodecs.FLOAT, StatePayload::blinkRange,
			ByteBufCodecs.VAR_INT, StatePayload::blinkCooldown,
			StatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
