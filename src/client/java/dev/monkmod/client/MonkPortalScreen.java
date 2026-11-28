package dev.monkmod.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import dev.monkmod.network.ShadowPortalPayloads;

/** Shift + Blink (level 20): type where the shadow portal leads, pick the dimension, open it. */
final class MonkPortalScreen extends Screen {
	private EditBox x;
	private EditBox y;
	private EditBox z;
	private final List<String> dims = new ArrayList<>();
	private int dim; // 0 = where you are
	private Component error;

	MonkPortalScreen() {
		super(Component.translatable("monk.portal.title"));
	}

	@Override
	protected void init() {
		dims.clear();
		if (minecraft != null && minecraft.getConnection() != null) {
			for (ResourceKey<Level> key : minecraft.getConnection().levels()) {
				dims.add(key.identifier().toString());
			}
		}
		int cx = width / 2;
		int top = height / 2 - 50;
		x = addRenderableWidget(new EditBox(font, cx - 100, top + 20, 62, 20, Component.literal("X")));
		y = addRenderableWidget(new EditBox(font, cx - 31, top + 20, 62, 20, Component.literal("Y")));
		z = addRenderableWidget(new EditBox(font, cx + 38, top + 20, 62, 20, Component.literal("Z")));
		x.setHint(Component.literal("X"));
		y.setHint(Component.literal("Y"));
		z.setHint(Component.literal("Z"));
		if (minecraft != null && minecraft.player != null) {
			x.setValue(Long.toString((long) Math.floor(minecraft.player.getX())));
			y.setValue(Long.toString((long) Math.floor(minecraft.player.getY())));
			z.setValue(Long.toString((long) Math.floor(minecraft.player.getZ())));
		}
		addRenderableWidget(Button.builder(dimLabel(), b -> {
			dim = (dim + 1) % (dims.size() + 1);
			b.setMessage(dimLabel());
		}).bounds(cx - 100, top + 46, 200, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("monk.portal.open"), b -> open()).bounds(cx - 100, top + 72, 200, 20).build());
		setInitialFocus(x);
	}

	private Component dimLabel() {
		if (dim <= 0 || dim > dims.size()) {
			return Component.translatable("monk.portal.dim", Component.translatable("monk.portal.dim_here"));
		}
		String id = dims.get(dim - 1);
		int c = id.indexOf(':');
		return Component.translatable("monk.portal.dim", (c >= 0 ? id.substring(c + 1) : id).replace('_', ' '));
	}

	private static double parse(String text, double base) {
		String t = text.trim();
		if (t.startsWith("~")) {
			return base + (t.length() > 1 ? Double.parseDouble(t.substring(1)) : 0);
		}
		return Double.parseDouble(t);
	}

	private void open() {
		if (minecraft == null || minecraft.player == null) {
			return;
		}
		try {
			double tx = parse(x.getValue(), minecraft.player.getX());
			double ty = parse(y.getValue(), minecraft.player.getY());
			double tz = parse(z.getValue(), minecraft.player.getZ());
			String d = dim <= 0 || dim > dims.size() ? "" : dims.get(dim - 1);
			if (ClientPlayNetworking.canSend(ShadowPortalPayloads.Create.TYPE)) {
				ClientPlayNetworking.send(new ShadowPortalPayloads.Create(Math.floor(tx) + 0.5, ty, Math.floor(tz) + 0.5, d));
			}
			onClose();
		} catch (NumberFormatException e) {
			error = Component.translatable("monk.portal.bad_number");
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int top = height / 2 - 50;
		g.drawCenteredString(font, title, width / 2, top, 0xFFE0C8FF);
		g.drawCenteredString(font, Component.translatable("monk.portal.hint"), width / 2, top + 98, 0xFF9A8AB0);
		if (error != null) {
			g.drawCenteredString(font, error, width / 2, top + 110, 0xFFFF6060);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
