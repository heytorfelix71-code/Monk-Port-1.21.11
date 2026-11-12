package dev.monkmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/** Client → server: start or end the time stop. */
public record TimeStopPayload() implements CustomPacketPayload {
	public static final TimeStopPayload INSTANCE = new TimeStopPayload();
	public static final Type<TimeStopPayload> TYPE = new Type<>(MonkMod.id("time_stop"));
	public static final StreamCodec<RegistryFriendlyByteBuf, TimeStopPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
