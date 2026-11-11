package dev.monkmod.data;

import com.mojang.serialization.Codec;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import dev.monkmod.MonkMod;

public final class MonkAttachments {
	/** Monk level and progress of a player. */
	public static final AttachmentType<MonkData> DATA = AttachmentRegistry.create(
			MonkMod.id("monk_data"),
			builder -> builder
					.initializer(MonkData::new)
					.persistent(MonkData.CODEC)
					.copyOnDeath()
	);

	/** Marks a creeper that was kissed by a monk: it never targets anyone again. */
	public static final AttachmentType<Boolean> CALMED = AttachmentRegistry.create(
			MonkMod.id("calmed"),
			builder -> builder.persistent(Codec.BOOL)
	);

	/** Set while a player has switched their withering stare off (toggle key, K by default). */
	public static final AttachmentType<Boolean> STARE_OFF = AttachmentRegistry.create(
			MonkMod.id("stare_off"),
			builder -> builder.persistent(Codec.BOOL).copyOnDeath()
	);

	private MonkAttachments() {
	}

	public static void init() {
	}

	public static MonkData data(Player player) {
		return player.getAttachedOrCreate(DATA);
	}

	public static boolean isCalmed(Entity entity) {
		return entity.hasAttached(CALMED);
	}
}
