package dev.monkmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/** Blink aimed past the chunks loaded on the client (far horizons, Voxy/Distant Horizons): the server finds the spot. */
public record BlinkFarPayload(double dx, double dy, double dz) implements CustomPacketPayload {
	public static final Type<BlinkFarPayload> TYPE = new Type<>(MonkMod.id("blink_far"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BlinkFarPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, BlinkFarPayload::dx,
			ByteBufCodecs.DOUBLE, BlinkFarPayload::dy,
			ByteBufCodecs.DOUBLE, BlinkFarPayload::dz,
			BlinkFarPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
