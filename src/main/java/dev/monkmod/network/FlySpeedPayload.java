package dev.monkmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/** Client → server: wanted flight speed (validated and clamped by the server). */
public record FlySpeedPayload(float speed) implements CustomPacketPayload {
	public static final Type<FlySpeedPayload> TYPE = new Type<>(MonkMod.id("fly_speed"));
	public static final StreamCodec<RegistryFriendlyByteBuf, FlySpeedPayload> CODEC =
			ByteBufCodecs.FLOAT.<RegistryFriendlyByteBuf>cast().map(FlySpeedPayload::new, FlySpeedPayload::speed);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
