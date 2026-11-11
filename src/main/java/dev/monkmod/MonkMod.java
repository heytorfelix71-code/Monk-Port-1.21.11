package dev.monkmod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import dev.monkmod.advancement.MonkCriteria;
import dev.monkmod.command.MonkCommand;
import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.data.MonkAttachments;
import dev.monkmod.logic.MonkEvents;
import dev.monkmod.network.MonkNetwork;
import dev.monkmod.type.MonkTypes;

public final class MonkMod implements ModInitializer {
	public static final String MOD_ID = "monk";
	public static final Logger LOGGER = LoggerFactory.getLogger("Monk Mod");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		MonkAttachments.init();
		MonkCriteria.init();
		MonkTypes.bootstrap();
		MonkConfigManager.loadAll();
		MonkNetwork.init();
		MonkEvents.register();
		MonkCommand.register();
		LOGGER.info("Monk Mod ready: {} levels, {} ability types, {} requirement types",
				MonkConfigManager.table().maxLevel() + 1, MonkTypes.ABILITIES.size(), MonkTypes.REQUIREMENTS.size());
	}
}
