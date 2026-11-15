package dev.monkmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/** Sent now and then while the blink key is held on a far target: the server starts loading the arrival chunks early. */
public record BlinkPreloadPayload(double dx, double dy, double dz) implements CustomPacketPayload {
	public static final Type<BlinkPreloadPayload> TYPE = new Type<>(MonkMod.id("blink_preload"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BlinkPreloadPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, BlinkPreloadPayload::dx,
			ByteBufCodecs.DOUBLE, BlinkPreloadPayload::dy,
			ByteBufCodecs.DOUBLE, BlinkPreloadPayload::dz,
			BlinkPreloadPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
