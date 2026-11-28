package dev.monkmod.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.monkmod.MonkMod;

/** The shadow portals (Monk level 20): open one (client to server) and the ones near you (server to client). */
public final class ShadowPortalPayloads {
	private ShadowPortalPayloads() {
	}

	/** Open a portal: entrance in front of you, exit at these coordinates in this dimension ("" = yours). */
	public record Create(double x, double y, double z, String dim) implements CustomPacketPayload {
		public static final Type<Create> TYPE = new Type<>(MonkMod.id("shadow_portal_create"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Create> CODEC = StreamCodec.composite(
				ByteBufCodecs.DOUBLE, Create::x, ByteBufCodecs.DOUBLE, Create::y, ByteBufCodecs.DOUBLE, Create::z,
				ByteBufCodecs.STRING_UTF8, Create::dim, Create::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Portal ends near the player, each {x, y, z, yaw, owner entity id (-1 when not here)}. */
	public record Ends(List<double[]> ends) implements CustomPacketPayload {
		public static final Type<Ends> TYPE = new Type<>(MonkMod.id("shadow_portal_ends"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ends> CODEC = StreamCodec.of((buf, v) -> {
			buf.writeVarInt(v.ends().size());
			for (double[] e : v.ends()) {
				for (int i = 0; i < 5; i++) {
					buf.writeDouble(e[i]);
				}
			}
		}, buf -> {
			int n = Math.min(256, buf.readVarInt());
			List<double[]> list = new ArrayList<>(n);
			for (int k = 0; k < n; k++) {
				double[] e = new double[5];
				for (int i = 0; i < 5; i++) {
					e[i] = buf.readDouble();
				}
				list.add(e);
			}
			return new Ends(list);
		});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
