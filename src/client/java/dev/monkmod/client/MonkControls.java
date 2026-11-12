package dev.monkmod.client;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import dev.monkmod.MonkMod;
import dev.monkmod.ability.PassiveAbilities;
import dev.monkmod.config.MonkConfig;
import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.network.FlySpeedPayload;
import dev.monkmod.network.NightVisionPayload;
import dev.monkmod.network.StareTogglePayload;
import dev.monkmod.network.TimeStopPayload;

/**
 * Mouse wheel and key controls of the monk abilities:
 * Blink key + wheel = blink distance, Sprint key (Ctrl) + wheel = running bonus, Jump key (Space) + wheel
 * = flight speed (while flying). Only one of them reacts at a time, in that order of priority, so the
 * wheel never changes two things at once. Each one also has optional plain keys (unbound by default).
 */
public final class MonkControls {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final float VANILLA_FLY = 0.05F;

	/** Saved in config/monk/client.json. */
	private static final class Saved {
		float runScale = 1.0F;
		float blinkDistance = -1.0F;
	}

	private static Saved saved = new Saved();
	private static boolean dirty;
	private static int saveDelay;

	private MonkControls() {
	}

	private static MonkConfig.Controls cfg() {
		return MonkConfigManager.config().controls;
	}

	// ------------------------------------------------------------------ values

	public static float runScale() {
		return saved.runScale;
	}

	/** Chosen blink distance, inside [min, range] of the server. */
	public static float blinkDistance() {
		float max = ClientMonkState.blinkRange();
		float min = Math.min(ClientMonkState.blinkMinRange(), max);
		float d = saved.blinkDistance <= 0 ? max : saved.blinkDistance;
		return Math.max(min, Math.min(max, d));
	}

	// ------------------------------------------------------------------ init / persistence

	static void init() {
		Path path = MonkConfigManager.dir().resolve("client.json");
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				Saved s = GSON.fromJson(r, Saved.class);
				if (s != null) {
					saved = s;
				}
			} catch (Exception e) {
				MonkMod.LOGGER.warn("Could not read config/monk/client.json: {}", e.getMessage());
			}
		}
		if (!Float.isFinite(saved.runScale)) {
			saved.runScale = 1.0F;
		}
		PassiveAbilities.installRunScale(() -> saved.runScale);
	}

	private static void markDirty() {
		dirty = true;
		saveDelay = 40;
	}

	private static void save() {
		dirty = false;
		try {
			Files.createDirectories(MonkConfigManager.dir());
			try (Writer w = Files.newBufferedWriter(MonkConfigManager.dir().resolve("client.json"), StandardCharsets.UTF_8)) {
				GSON.toJson(saved, w);
			}
		} catch (Exception e) {
			MonkMod.LOGGER.warn("Could not write config/monk/client.json: {}", e.getMessage());
		}
	}

	// ------------------------------------------------------------------ input

	static void tick(Minecraft client) {
		if (dirty && --saveDelay <= 0) {
			save();
		}
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		while (MonkModClient.nightVisionKey.consumeClick()) {
			if (ClientMonkState.abilities().has("night_vision") && ClientPlayNetworking.canSend(NightVisionPayload.TYPE)) {
				ClientPlayNetworking.send(NightVisionPayload.INSTANCE);
			} else if (ClientMonkState.connectedToMonkServer()) {
				show(player, Component.translatable("monk.controls.night_vision.locked").withStyle(ChatFormatting.GRAY));
			}
		}
		while (MonkModClient.timeStopKey.consumeClick()) {
			if (ClientMonkState.abilities().has("time_stop") && ClientPlayNetworking.canSend(TimeStopPayload.TYPE)) {
				ClientPlayNetworking.send(TimeStopPayload.INSTANCE);
			} else if (ClientMonkState.connectedToMonkServer()) {
				show(player, Component.translatable("monk.time_stop.locked").withStyle(ChatFormatting.GRAY));
			}
		}
		while (MonkModClient.stareKey.consumeClick()) {
			if (ClientMonkState.abilities().has("withering_stare") && ClientPlayNetworking.canSend(StareTogglePayload.TYPE)) {
				ClientPlayNetworking.send(StareTogglePayload.INSTANCE);
			} else if (ClientMonkState.connectedToMonkServer()) {
				show(player, Component.translatable("monk.controls.stare.locked").withStyle(ChatFormatting.GRAY));
			}
		}
		while (MonkModClient.blinkFarther.consumeClick()) {
			adjustBlink(player, 1);
		}
		while (MonkModClient.blinkCloser.consumeClick()) {
			adjustBlink(player, -1);
		}
		while (MonkModClient.runFaster.consumeClick()) {
			adjustRun(player, 1);
		}
		while (MonkModClient.runSlower.consumeClick()) {
			adjustRun(player, -1);
		}
		while (MonkModClient.flyFaster.consumeClick()) {
			adjustFly(player, 1);
		}
		while (MonkModClient.flySlower.consumeClick()) {
			adjustFly(player, -1);
		}
	}

	/** Called from the mouse handler mixin; true = the wheel was used by the mod (hotbar stays). */
	public static boolean onScroll(double amount) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.screen != null || amount == 0 || player.isSpectator()) {
			return false;
		}
		int dir = amount > 0 ? 1 : -1;
		MonkConfig.Controls c = cfg();
		// Priority: blink key, then sprint key, then jump key. Only one acts.
		if (isDown(MonkModClient.blinkKey) && ClientMonkState.abilities().has("blink")) {
			if (!c.scrollBlinkDistance) {
				return false;
			}
			adjustBlink(player, dir);
			return true;
		}
		if (isDown(client.options.keySprint) && ClientMonkState.abilities().has("swift")) {
			if (!c.scrollRunSpeed) {
				return false;
			}
			adjustRun(player, dir);
			return true;
		}
		if (isDown(client.options.keyJump) && player.getAbilities().flying && ClientMonkState.abilities().has("fly")) {
			if (!c.scrollFlySpeed) {
				return false;
			}
			adjustFly(player, dir);
			return true;
		}
		return false;
	}

	private static boolean isDown(KeyMapping key) {
		return key != null && key.isDown();
	}

	// ------------------------------------------------------------------ adjustments

	private static void adjustBlink(LocalPlayer player, int dir) {
		if (!ClientMonkState.abilities().has("blink")) {
			return;
		}
		float max = ClientMonkState.blinkRange();
		float min = Math.min(ClientMonkState.blinkMinRange(), max);
		float value = Math.max(min, Math.min(max, blinkDistance() + dir * cfg().blinkStep));
		saved.blinkDistance = value;
		markDirty();
		show(player, Component.translatable("monk.controls.blink_distance", fmt(value), fmt(max)));
	}

	private static void adjustRun(LocalPlayer player, int dir) {
		if (!ClientMonkState.abilities().has("swift")) {
			return;
		}
		MonkConfig.Controls c = cfg();
		float value = Math.round((saved.runScale + dir * c.runStep) * 100.0F) / 100.0F;
		value = Math.max(0.0F, Math.min(c.maxRunScale, value));
		saved.runScale = value;
		markDirty();
		show(player, Component.translatable("monk.controls.run_speed", Math.round(value * 100.0F) + "%"));
	}

	private static void adjustFly(LocalPlayer player, int dir) {
		if (!ClientMonkState.abilities().has("fly") || !ClientPlayNetworking.canSend(FlySpeedPayload.TYPE)) {
			return;
		}
		float current = player.getAbilities().getFlyingSpeed();
		float value = Math.round((current + dir * cfg().flyStep) * 1000.0F) / 1000.0F;
		value = Math.max(ClientMonkState.flyMin(), Math.min(ClientMonkState.flyMax(), value));
		player.getAbilities().setFlyingSpeed(value); // instant feedback; the server confirms
		ClientPlayNetworking.send(new FlySpeedPayload(value));
		show(player, Component.translatable("monk.controls.fly_speed", Math.round(value / VANILLA_FLY * 100.0F) + "%"));
	}

	private static void show(LocalPlayer player, Component message) {
		if (cfg().showChanges) {
			player.displayClientMessage(message, true);
		}
	}

	private static String fmt(float v) {
		return v == Math.rint(v) ? Integer.toString((int) v) : String.format(Locale.ROOT, "%.1f", v);
	}
}
