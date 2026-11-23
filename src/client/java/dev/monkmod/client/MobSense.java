package dev.monkmod.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import dev.monkmod.config.MonkConfig;
import dev.monkmod.config.MonkConfigManager;

/**
 * Mob Sense (Monk level 20): mobs seen through walls, hostile ones in one color (red) and passive ones in another (blue). Either a
 * glowing silhouette (the game's own outline, which shows through walls) or a small colored square on the screen. Off at the start;
 * the key (. by default) switches it. All client side: nothing is sent to the server.
 */
public final class MobSense {
	private static boolean on;
	/** Mobs shown right now: entity id -> RGB color. Rebuilt every tick. */
	private static final Map<Integer, Integer> SHOWN = new HashMap<>();

	private MobSense() {
	}

	private static boolean owned() {
		return ClientMonkState.abilities().has("mob_sense");
	}

	static void tick(Minecraft mc) {
		while (MonkModClient.mobSenseKey.consumeClick()) {
			if (owned() && mc.player != null) {
				on = !on;
				mc.player.displayClientMessage(Component.translatable(on ? "monk.mob_sense.on" : "monk.mob_sense.off"), true);
			}
		}
		SHOWN.clear();
		LocalPlayer player = mc.player;
		if (!on || player == null || mc.level == null || !owned()) {
			if (!owned()) {
				on = false;
			}
			return;
		}
		MonkConfig.Vision cfg = MonkConfigManager.config().vision;
		double range = cfg.range;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity living) || e == player || e instanceof Player || e instanceof ArmorStand || !living.isAlive()
					|| e.distanceToSqr(player) > range * range) {
				continue;
			}
			boolean hostile = e instanceof Enemy;
			if (!hostile && !cfg.showPassive) {
				continue;
			}
			if (cfg.onlyHidden && player.hasLineOfSight(e)) {
				continue;
			}
			SHOWN.put(e.getId(), hostile ? cfg.hostileColor : cfg.passiveColor);
		}
	}

	/** The game asks if this entity gets the glowing outline (a mixin on Minecraft.shouldEntityAppearGlowing). */
	public static boolean glows(Entity entity) {
		return on && !SHOWN.isEmpty() && MonkConfigManager.config().vision.mode == MonkConfig.VisionMode.OUTLINE && SHOWN.containsKey(entity.getId());
	}

	/** The outline color of that entity (a mixin on Entity.getTeamColor), or null to leave it alone. */
	public static Integer color(Entity entity) {
		return glows(entity) ? SHOWN.get(entity.getId()) : null;
	}

	/** The DOT style: a small square on the screen where each mob is (also through walls). */
	static void renderDots(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		MonkConfig.Vision cfg = MonkConfigManager.config().vision;
		if (!on || SHOWN.isEmpty() || cfg.mode != MonkConfig.VisionMode.DOT || mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		float yaw = (float) Math.toRadians(net.minecraft.util.Mth.lerp(partial, mc.player.yRotO, mc.player.getYRot()));
		float pitch = (float) Math.toRadians(net.minecraft.util.Mth.lerp(partial, mc.player.xRotO, mc.player.getXRot()));
		Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 up = right.cross(forward);
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		double scale = (h / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
		int half = Math.max(1, cfg.dotSize / 2);
		for (Map.Entry<Integer, Integer> en : SHOWN.entrySet()) {
			Entity e = mc.level.getEntity(en.getKey());
			if (e == null) {
				continue;
			}
			Vec3 at = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0).subtract(cam);
			double f = at.dot(forward);
			if (f < 0.2) {
				continue;
			}
			double sx = w / 2.0 + at.dot(right) / f * scale;
			double sy = h / 2.0 - at.dot(up) / f * scale;
			if (sx < 0 || sy < 0 || sx > w || sy > h) {
				continue;
			}
			int x = (int) Math.round(sx);
			int y = (int) Math.round(sy);
			graphics.fill(x - half - 1, y - half - 1, x + half + 1, y + half + 1, ARGB.color(190, 0, 0, 0));
			graphics.fill(x - half, y - half, x + half, y + half, ARGB.opaque(en.getValue()));
		}
	}
}
