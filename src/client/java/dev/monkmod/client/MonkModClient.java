package dev.monkmod.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import dev.monkmod.MonkMod;
import dev.monkmod.logic.MonkAccess;
import dev.monkmod.logic.MonkEquipment;
import dev.monkmod.network.ProgressPayload;
import dev.monkmod.network.StatePayload;
import dev.monkmod.requirement.BodyRequirements;

public final class MonkModClient implements ClientModInitializer {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MonkMod.id("monk"));
	public static KeyMapping blinkKey;

	@Override
	public void onInitializeClient() {
		blinkKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.monk.blink", InputConstants.Type.KEYSYM, InputConstants.KEY_G, CATEGORY));

		MonkAccess.installClient(
				player -> player == Minecraft.getInstance().player ? ClientMonkState.abilities() : dev.monkmod.level.AbilitySet.EMPTY,
				MonkEquipment::computeUnarmored);

		ClientPlayNetworking.registerGlobalReceiver(StatePayload.TYPE, (payload, context) -> ClientMonkState.apply(payload));
		ClientPlayNetworking.registerGlobalReceiver(ProgressPayload.TYPE, (payload, context) -> MonkHud.onProgress(payload.progress(), payload.max()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientMonkState.reset();
			MonkHud.reset();
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			MonkHud.tick(client);
			BlinkClient.tick(client);
			WaterWalkClient.tick(client);
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, MonkMod.id("progress"), MonkHud::render);

		// Mirror the server's decisions so the hand swings and no other interaction fires.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() || !player.getItemInHand(hand).isEmpty() || !ClientMonkState.connectedToMonkServer()) {
				return InteractionResult.PASS;
			}
			String next = ClientMonkState.nextRequirement();
			boolean creeper = entity.getType() == EntityType.CREEPER;
			if (("pet".equals(next) && entity instanceof Animal) || ("kiss_creeper".equals(next) && creeper)
					|| (creeper && ClientMonkState.abilities().has("kiss_creeper"))) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && hand == InteractionHand.MAIN_HAND && !player.isShiftKeyDown()
					&& BodyRequirements.Sleep.looksLikeBed(ClientMonkState.nextRequirement(), player, hit.getBlockPos())) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}
}
