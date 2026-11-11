package dev.monkmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/** Client → server: toggle the monk night vision. */
public record NightVisionPayload() implements CustomPacketPayload {
	public static final NightVisionPayload INSTANCE = new NightVisionPayload();
	public static final Type<NightVisionPayload> TYPE = new Type<>(MonkMod.id("night_vision"));
	public static final StreamCodec<RegistryFriendlyByteBuf, NightVisionPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
