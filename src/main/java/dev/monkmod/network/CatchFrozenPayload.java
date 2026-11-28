package dev.monkmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/** With time stopped: grab this projectile hanging in the air (right click on it). */
public record CatchFrozenPayload(int entity) implements CustomPacketPayload {
	public static final Type<CatchFrozenPayload> TYPE = new Type<>(MonkMod.id("catch_frozen"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CatchFrozenPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CatchFrozenPayload::entity, CatchFrozenPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
