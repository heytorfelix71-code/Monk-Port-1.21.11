package dev.monkmod.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.fabricmc.loader.api.FabricLoader;

/** Mod Menu "Config" button. Uses YACL when installed, otherwise a small screen explaining the files. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")
				? MonkConfigScreen.create(parent)
				: new NoYaclScreen(parent);
	}
}
