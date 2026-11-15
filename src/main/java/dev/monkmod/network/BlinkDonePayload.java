package dev.monkmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/** Server → client: the blink really happened (the client plays its sound only then). */
public record BlinkDonePayload(double fx, double fy, double fz, double tx, double ty, double tz) implements CustomPacketPayload {
	public static final Type<BlinkDonePayload> TYPE = new Type<>(MonkMod.id("blink_done"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BlinkDonePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, BlinkDonePayload::fx, ByteBufCodecs.DOUBLE, BlinkDonePayload::fy, ByteBufCodecs.DOUBLE, BlinkDonePayload::fz,
			ByteBufCodecs.DOUBLE, BlinkDonePayload::tx, ByteBufCodecs.DOUBLE, BlinkDonePayload::ty, ByteBufCodecs.DOUBLE, BlinkDonePayload::tz,
			BlinkDonePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
