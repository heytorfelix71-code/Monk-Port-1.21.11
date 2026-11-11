package dev.monkmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/** Server → client: progress towards the next level (drives the HUD indicator). */
public record ProgressPayload(int progress, int max) implements CustomPacketPayload {
	public static final Type<ProgressPayload> TYPE = new Type<>(MonkMod.id("progress"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ProgressPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ProgressPayload::progress,
			ByteBufCodecs.VAR_INT, ProgressPayload::max,
			ProgressPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
