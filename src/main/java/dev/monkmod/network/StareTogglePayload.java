package dev.monkmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/** Client → server: toggle the withering stare. */
public record StareTogglePayload() implements CustomPacketPayload {
	public static final StareTogglePayload INSTANCE = new StareTogglePayload();
	public static final Type<StareTogglePayload> TYPE = new Type<>(MonkMod.id("stare_toggle"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StareTogglePayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
