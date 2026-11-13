package dev.monkmod.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/**
 * Server → client: current level, the next requirement type and the active abilities (as their JSON
 * config entries, so the client builds identical ability objects for client-side mechanics), plus the
 * server settings the client needs to aim blinks and choose flight speeds.
 */
public record StatePayload(int level, int maxLevel, String nextRequirement, List<String> abilities,
		float blinkRange, int blinkCooldown, boolean blinkAllowAir, float blinkMinRange, int groundSearchDepth,
		float flyMin, float flyMax, float runScaleMax) implements CustomPacketPayload {
	public static final Type<StatePayload> TYPE = new Type<>(MonkMod.id("state"));
	private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> STRINGS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
	public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> CODEC = StreamCodec.ofMember(StatePayload::write, StatePayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(level);
		buf.writeVarInt(maxLevel);
		buf.writeUtf(nextRequirement);
		STRINGS.encode(buf, abilities);
		buf.writeFloat(blinkRange);
		buf.writeVarInt(blinkCooldown);
		buf.writeBoolean(blinkAllowAir);
		buf.writeFloat(blinkMinRange);
		buf.writeVarInt(groundSearchDepth);
		buf.writeFloat(flyMin);
		buf.writeFloat(flyMax);
		buf.writeFloat(runScaleMax);
	}

	private static StatePayload read(RegistryFriendlyByteBuf buf) {
		return new StatePayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), STRINGS.decode(buf),
				buf.readFloat(), buf.readVarInt(), buf.readBoolean(), buf.readFloat(), buf.readVarInt(),
				buf.readFloat(), buf.readFloat(), buf.readFloat());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
