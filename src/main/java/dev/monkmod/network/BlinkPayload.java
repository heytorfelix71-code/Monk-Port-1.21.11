package dev.monkmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/** Client → server: request to blink to a position (validated by the server). */
public record BlinkPayload(double x, double y, double z) implements CustomPacketPayload {
	public static final Type<BlinkPayload> TYPE = new Type<>(MonkMod.id("blink"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BlinkPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, BlinkPayload::x,
			ByteBufCodecs.DOUBLE, BlinkPayload::y,
			ByteBufCodecs.DOUBLE, BlinkPayload::z,
			BlinkPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
