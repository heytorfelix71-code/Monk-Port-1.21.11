package dev.monkmod.logic;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;

import dev.monkmod.data.MonkAttachments;

/** Small shared behaviours: chat reactions and calming creepers. */
public final class MonkInteractions {
	private MonkInteractions() {
	}

	/** "<Cow> <3" in the player's chat, like the original mod. */
	public static void say(ServerPlayer player, Entity speaker, String key) {
		player.sendSystemMessage(Component.translatable("chat.type.text", speaker.getDisplayName(), Component.translatable(key)));
	}

	/** The creeper stops fusing and will never target anyone again (persisted on the entity). */
	public static void calmCreeper(Entity entity) {
		entity.setAttached(MonkAttachments.CALMED, true);
		if (entity instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		if (entity instanceof Creeper creeper) {
			creeper.setSwellDir(-1);
		}
	}
}
