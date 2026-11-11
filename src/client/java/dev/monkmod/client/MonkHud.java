package dev.monkmod.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import dev.monkmod.config.MonkConfig;
import dev.monkmod.config.MonkConfigManager;

/**
 * Progress indicator for the current challenge. Fades in when progress changes and out after a while,
 * like the 1.12 ring, but with configurable style, corner, size, colors and opacity.
 */
public final class MonkHud {
	private static int progress;
	private static int max = -1;
	private static float shown;
	private static float previousShown;
	private static boolean losing;
	private static int ticksSinceUpdate = 10_000;

	private MonkHud() {
	}

	public static void onProgress(int newProgress, int newMax) {
		losing = newProgress < progress && newMax == max;
		progress = newProgress;
		max = newMax;
		ticksSinceUpdate = 0;
	}

	public static void reset() {
		progress = 0;
		max = -1;
		shown = 0;
		previousShown = 0;
		ticksSinceUpdate = 10_000;
	}

	private static float target() {
		return max <= 0 ? 0F : Mth.clamp(progress / (float) max, 0F, 1F);
	}

	static void tick(Minecraft client) {
		if (client.isPaused()) {
			return;
		}
		ticksSinceUpdate++;
		previousShown = shown;
		float t = target();
		float step = 0.02F;
		shown += Mth.clamp(t - shown, -step, step);
	}

	private static float alpha(MonkConfig.Hud cfg, float partial) {
		if (cfg.alwaysVisible) {
			return cfg.opacity;
		}
		float t = ticksSinceUpdate + partial;
		float fadeIn = Math.min(1F, t / 5F);
		float fadeOut = t <= cfg.visibleTicks ? 1F : 1F - (t - cfg.visibleTicks) / 20F;
		return Mth.clamp(Math.min(fadeIn, fadeOut), 0F, 1F) * cfg.opacity;
	}

	public static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		MonkConfig.Hud cfg = MonkConfigManager.config().hud;
		if (!cfg.enabled || max <= 0 || mc.player == null || mc.options.hideGui) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float a = alpha(cfg, partial);
		if (a <= 0.01F) {
			return;
		}
		float value = Mth.lerp(partial, previousShown, shown);
		int color = argb(a, losing ? cfg.colorLoss : cfg.colorGain);
		int dim = argb(a * 0.35F, 0x000000);
		Font font = mc.font;
		String percent = Math.round(target() * 100) + "%";
		Component label = Component.translatable("monk.hud.level", ClientMonkState.level() + 1);

		int size = cfg.size;
		int w = switch (cfg.style) {
			case RING -> size;
			case BAR -> size * 3;
			case TEXT -> font.width(label) + 4 + font.width(percent);
		};
		int h = switch (cfg.style) {
			case RING -> size + (cfg.showPercent ? font.lineHeight + 1 : 0);
			case BAR -> 6 + font.lineHeight + 2;
			case TEXT -> font.lineHeight;
		};
		int sw = graphics.guiWidth();
		int sh = graphics.guiHeight();
		int x = switch (cfg.corner) {
			case TOP_LEFT, BOTTOM_LEFT -> cfg.offsetX;
			case TOP_RIGHT, BOTTOM_RIGHT -> sw - cfg.offsetX - w;
		};
		int y = switch (cfg.corner) {
			case TOP_LEFT, TOP_RIGHT -> cfg.offsetY;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> sh - cfg.offsetY - h;
		};
		int textColor = argb(a, 0xFFFFFF);

		switch (cfg.style) {
			case RING -> {
				drawRing(graphics, x + size / 2, y + size / 2, size / 2, value, color, dim);
				ItemStack icon = ClientMonkState.nextIcon();
				if (cfg.showIcon && !icon.isEmpty() && size >= 20 && a > 0.5F) {
					graphics.renderItem(icon, x + size / 2 - 8, y + size / 2 - 8);
				}
				if (cfg.showPercent) {
					graphics.drawString(font, percent, x + (size - font.width(percent)) / 2, y + size + 1, textColor, true);
				}
			}
			case BAR -> {
				graphics.fill(x, y, x + w, y + 6, dim);
				graphics.fill(x + 1, y + 1, x + 1 + Math.round((w - 2) * value), y + 5, color);
				String text = label.getString() + (cfg.showPercent ? "  " + percent : "");
				graphics.drawString(font, text, x, y + 8, textColor, true);
			}
			case TEXT -> graphics.drawString(font, label.getString() + "  " + percent, x, y, textColor, true);
		}
	}

	/** A ring of small squares; filled up to {@code value} (0..1) starting at 12 o'clock, clockwise. */
	private static void drawRing(GuiGraphics g, int cx, int cy, int radius, float value, int color, int dim) {
		int dots = Mth.clamp(radius * 4, 24, 160);
		int dot = Math.max(1, radius / 7);
		float r = radius - dot;
		for (int i = 0; i < dots; i++) {
			float f = i / (float) dots;
			double angle = f * Math.PI * 2.0 - Math.PI / 2.0;
			int px = cx + Math.round((float) Math.cos(angle) * r);
			int py = cy + Math.round((float) Math.sin(angle) * r);
			g.fill(px - dot / 2, py - dot / 2, px - dot / 2 + dot, py - dot / 2 + dot, f < value ? color : dim);
		}
	}

	private static int argb(float alpha, int rgb) {
		return (Mth.clamp(Math.round(alpha * 255F), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}
}
