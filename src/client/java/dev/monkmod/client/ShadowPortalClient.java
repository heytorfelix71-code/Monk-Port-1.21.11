package dev.monkmod.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import dev.monkmod.config.MonkConfig;
import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.network.ShadowPortalPayloads;

/** Draws the shadow portals near you: a see-through copy of a player standing as the door, or a glowing rift. */
final class ShadowPortalClient {
	private static List<double[]> ends = new ArrayList<>();

	private ShadowPortalClient() {
	}

	static void receive(ShadowPortalPayloads.Ends payload) {
		ends = payload.ends();
	}

	static void reset() {
		ends = new ArrayList<>();
	}

	static void tick(Minecraft mc) {
		if (ends.isEmpty() || mc.level == null || mc.isPaused()) {
			return;
		}
		int color = MonkConfigManager.config().portal.color & 0xFFFFFF;
		for (double[] e : ends) { // a few motes around each door
			if (mc.level.getRandom().nextInt(3) == 0) {
				mc.level.addParticle(new DustParticleOptions(color, 0.9F), e[0] + (mc.level.getRandom().nextDouble() - 0.5) * 0.9,
						e[1] + mc.level.getRandom().nextDouble() * 2.0, e[2] + (mc.level.getRandom().nextDouble() - 0.5) * 0.9, 0, 0.02, 0);
			}
		}
	}

	static void render(WorldRenderContext context) {
		if (ends.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return;
		}
		MonkConfig.PortalLook look = MonkConfigManager.config().portal;
		int rgb = look.color & 0xFFFFFF;
		double time = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		if (look.style == MonkConfig.PortalStyle.SHADOW) {
			List<double[]> spots = new ArrayList<>();
			for (double[] e : ends) {
				spots.add(new double[] {e[0], e[1], e[2], e[3]});
			}
			float pulse = 0.55F + 0.12F * (float) Math.sin(time * 0.15);
			BlinkShadow.drawFigures(context, spots, rgb, pulse);
			return;
		}
		MultiBufferSource buffers = context.consumers();
		if (buffers == null) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderTypes.lightning());
		Matrix4f m = context.matrices().last().pose();
		Vec3 cam = mc.gameRenderer.getMainCamera().position();
		for (double[] e : ends) {
			double x = e[0] - cam.x;
			double y = e[1] - cam.y;
			double z = e[2] - cam.z;
			double yaw = Math.toRadians(e[3]);
			// a tall crack of light: two crossed glowing planes, brighter in the middle, breathing slowly
			float w = (float) (0.42 + 0.05 * Math.sin(time * 0.2));
			for (int k = 0; k < 2; k++) {
				double a = yaw + k * Math.PI / 2;
				double dx = Math.cos(a) * w;
				double dz = Math.sin(a) * w;
				int core = 0xC0 << 24 | rgb;
				int edge = rgb; // transparent at the sides
				quad(vc, m, x - dx, y, z - dz, x, y, z, x, y + 2.1, z, x - dx, y + 2.1, z - dz, edge, core);
				quad(vc, m, x, y, z, x + dx, y, z + dz, x + dx, y + 2.1, z + dz, x, y + 2.1, z, core, edge);
			}
		}
	}

	private static void quad(VertexConsumer vc, Matrix4f m, double x0, double y0, double z0, double x1, double y1, double z1,
			double x2, double y2, double z2, double x3, double y3, double z3, int c0, int c1) {
		vc.addVertex(m, (float) x0, (float) y0, (float) z0).setColor(c0);
		vc.addVertex(m, (float) x1, (float) y1, (float) z1).setColor(c1);
		vc.addVertex(m, (float) x2, (float) y2, (float) z2).setColor(c1);
		vc.addVertex(m, (float) x3, (float) y3, (float) z3).setColor(c0);
		vc.addVertex(m, (float) x3, (float) y3, (float) z3).setColor(c0);
		vc.addVertex(m, (float) x2, (float) y2, (float) z2).setColor(c1);
		vc.addVertex(m, (float) x1, (float) y1, (float) z1).setColor(c1);
		vc.addVertex(m, (float) x0, (float) y0, (float) z0).setColor(c0);
	}
}
