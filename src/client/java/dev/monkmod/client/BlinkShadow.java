package dev.monkmod.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import dev.monkmod.config.MonkConfig;
import dev.monkmod.config.MonkConfigManager;

/**
 * The Blink "shadow": a see-through copy of your own player model (your skin) standing where the blink takes
 * you, or several along the way. Drawn only by your own client, so nobody else sees it.
 */
final class BlinkShadow {
	/** Feet positions to draw this frame (set every tick while aiming, cleared otherwise). */
	private static final List<Vec3> SPOTS = new ArrayList<>();
	private static float fade = 1.0F;

	private BlinkShadow() {
	}

	/** Called from the blink tick: where the shadows stand, or nothing. */
	static void update(LocalPlayer player, Vec3 target, float strength) {
		SPOTS.clear();
		MonkConfig.Blink cfg = MonkConfigManager.config().blink;
		if (target == null || cfg.shadowMode == MonkConfig.ShadowMode.OFF) {
			return;
		}
		fade = strength;
		Vec3 from = player.position();
		double distance = from.distanceTo(target);
		int count = 1;
		if (cfg.shadowMode == MonkConfig.ShadowMode.PATH) {
			count = (int) Math.max(1, Math.min(cfg.shadowCount, Math.floor(distance / Math.max(1.0F, cfg.shadowSpacing))));
		}
		for (int i = 1; i <= count; i++) {
			Vec3 spot = from.lerp(target, i / (double) count);
			// A shadow on top of yourself would fill the screen in first person.
			if (spot.distanceTo(from) >= 1.5) {
				SPOTS.add(spot);
			}
		}
	}

	static void clear() {
		SPOTS.clear();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	static void render(WorldRenderContext context) {
		if (SPOTS.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			SPOTS.clear();
			return;
		}
		EntityRenderer<?, ?> renderer = client.getEntityRenderDispatcher().getRenderer(player);
		if (!(renderer instanceof AvatarRenderer avatar)) {
			return;
		}
		MonkConfig.Blink cfg = MonkConfigManager.config().blink;
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		AvatarRenderState state = (AvatarRenderState) avatar.createRenderState();
		avatar.extractRenderState(player, state, partialTick);
		// Standing still, facing where you look.
		state.pose = Pose.STANDING;
		state.isCrouching = false;
		state.isVisuallySwimming = false;
		state.isFallFlying = false;
		state.swimAmount = 0.0F;
		state.walkAnimationSpeed = 0.0F;
		state.walkAnimationPos = 0.0F;
		state.bodyRot = player.getYRot();
		state.yRot = 0.0F;
		state.xRot = 0.0F;
		state.isUpsideDown = false;
		state.isInvisible = false;
		state.isInvisibleToPlayer = false;

		PlayerModel model = (PlayerModel) avatar.getModel();
		Identifier texture = avatar.getTextureLocation(state);
		MultiBufferSource buffers = context.consumers();
		if (buffers == null) {
			return;
		}
		VertexConsumer consumer = buffers.getBuffer(RenderTypes.entityTranslucent(texture));
		model.setupAnim(state);
		PoseStack pose = context.matrices();
		Vec3 camera = client.gameRenderer.getMainCamera().position();
		int rgb = cfg.shadowColor & 0xFFFFFF;
		float opacity = Math.max(0.05F, Math.min(1.0F, cfg.shadowOpacity / 100.0F)) * Math.max(0.3F, fade);
		float scale = state.scale * 0.9375F;
		int n = SPOTS.size();
		for (int i = 0; i < n; i++) {
			Vec3 spot = SPOTS.get(i);
			// Along the way: the shadows fade in towards the destination.
			float a = n == 1 ? opacity : opacity * (0.35F + 0.65F * (i + 1) / n);
			int alpha = Math.max(8, Math.min(255, Math.round(a * 255)));
			pose.pushPose();
			pose.translate(spot.x - camera.x, spot.y - camera.y, spot.z - camera.z);
			pose.mulPose(Axis.YP.rotationDegrees(180.0F - state.bodyRot));
			pose.scale(-1.0F, -1.0F, 1.0F);
			pose.scale(scale, scale, scale);
			pose.translate(0.0F, -1.501F, 0.0F);
			model.renderToBuffer(pose, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, alpha << 24 | rgb);
			pose.popPose();
		}
	}
}
