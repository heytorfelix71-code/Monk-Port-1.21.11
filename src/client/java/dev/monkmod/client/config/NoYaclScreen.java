package dev.monkmod.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import dev.monkmod.config.MonkConfigManager;

/** Shown by Mod Menu when YetAnotherConfigLib is not installed. */
public final class NoYaclScreen extends Screen {
	private final Screen parent;

	public NoYaclScreen(Screen parent) {
		super(Component.translatable("monk.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("monk.config.open_folder"), b -> Util.getPlatform().openPath(MonkConfigManager.dir()))
				.bounds(width / 2 - 100, height / 2 + 10, 200, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
				.bounds(width / 2 - 100, height / 2 + 34, 200, 20).build());
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 34, 0xFFFFFFFF);
		graphics.drawCenteredString(font, Component.translatable("monk.config.no_yacl"), width / 2, height / 2 - 18, 0xFFAAAAAA);
		graphics.drawCenteredString(font, Component.translatable("monk.config.no_yacl.2"), width / 2, height / 2 - 6, 0xFFAAAAAA);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
