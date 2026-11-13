package dev.monkmod.client.config;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

import dev.monkmod.command.MonkCommand;
import dev.monkmod.config.LevelsFile;
import dev.monkmod.config.MonkConfig;
import dev.monkmod.config.MonkConfigManager;
import dev.monkmod.type.MonkType;
import dev.monkmod.type.MonkTypes;
import dev.monkmod.type.ParamSpec;
import dev.monkmod.type.Params;

/**
 * The full in-game editor. Works on copies of the config and only writes them on "Save".
 * In singleplayer the running world picks the changes up immediately (abilities, advancements, HUD).
 */
public final class MonkConfigScreen {
	private MonkConfigScreen() {
	}

	private static MutableComponent t(String key, Object... args) {
		return Component.translatable("monk.config." + key, args);
	}

	private static OptionDescription d(String key) {
		return OptionDescription.of(Component.translatable("monk.config." + key + ".desc"));
	}

	public static Screen create(Screen parent) {
		MonkConfig defaults = new MonkConfig();
		MonkConfig cfg = MonkConfigManager.GSON.fromJson(MonkConfigManager.GSON.toJson(MonkConfigManager.config()), MonkConfig.class);
		cfg.sanitize();
		LevelsFile levels = MonkConfigManager.levelsCopy();

		return YetAnotherConfigLib.createBuilder()
				.title(t("title"))
				.category(general(cfg, defaults))
				.category(equipment(cfg, defaults))
				.category(abilities(cfg))
				.category(levels(levels, parent))
				.save(() -> save(cfg, levels))
				.build()
				.generateScreen(parent);
	}

	private static void save(MonkConfig cfg, LevelsFile levels) {
		List<String> errors = MonkConfigManager.save(cfg, levels);
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			server.execute(() -> MonkCommand.applyReload(server));
		}
		if (mc.player != null) {
			if (errors.isEmpty()) {
				mc.player.displayClientMessage(t("saved").withStyle(ChatFormatting.GREEN), false);
			} else {
				mc.player.displayClientMessage(t("saved_with_errors", errors.size()).withStyle(ChatFormatting.RED), false);
				errors.stream().limit(8).forEach(e -> mc.player.displayClientMessage(Component.literal(" - " + e).withStyle(ChatFormatting.GRAY), false));
			}
		}
	}

	// ------------------------------------------------------------------ general

	private static ConfigCategory general(MonkConfig cfg, MonkConfig def) {
		MonkConfig.Hud h = cfg.hud;
		MonkConfig.Hud hd = def.hud;
		OptionGroup hud = OptionGroup.createBuilder().name(t("hud")).description(d("hud"))
				.option(bool("hud.enabled", hd.enabled, () -> h.enabled, v -> h.enabled = v))
				.option(Option.<MonkConfig.HudStyle>createBuilder().name(t("hud.style")).description(d("hud.style"))
						.binding(hd.style, () -> h.style, v -> h.style = v)
						.controller(o -> EnumControllerBuilder.create(o).enumClass(MonkConfig.HudStyle.class)
								.formatValue(v -> t("hud.style." + v.name().toLowerCase())))
						.build())
				.option(Option.<MonkConfig.HudCorner>createBuilder().name(t("hud.corner")).description(d("hud.corner"))
						.binding(hd.corner, () -> h.corner, v -> h.corner = v)
						.controller(o -> EnumControllerBuilder.create(o).enumClass(MonkConfig.HudCorner.class)
								.formatValue(v -> t("hud.corner." + v.name().toLowerCase())))
						.build())
				.option(intSlider("hud.offset_x", hd.offsetX, 0, 300, 1, () -> h.offsetX, v -> h.offsetX = v))
				.option(intSlider("hud.offset_y", hd.offsetY, 0, 300, 1, () -> h.offsetY, v -> h.offsetY = v))
				.option(intSlider("hud.size", hd.size, 8, 128, 1, () -> h.size, v -> h.size = v))
				.option(floatSlider("hud.opacity", hd.opacity, 0.05F, 1F, 0.05F, () -> h.opacity, v -> h.opacity = v))
				.option(bool("hud.show_percent", hd.showPercent, () -> h.showPercent, v -> h.showPercent = v))
				.option(bool("hud.show_icon", hd.showIcon, () -> h.showIcon, v -> h.showIcon = v))
				.option(bool("hud.always_visible", hd.alwaysVisible, () -> h.alwaysVisible, v -> h.alwaysVisible = v))
				.option(intSlider("hud.visible_ticks", hd.visibleTicks, 0, 400, 5, () -> h.visibleTicks, v -> h.visibleTicks = v))
				.option(color("hud.color_gain", hd.colorGain, () -> h.colorGain, v -> h.colorGain = v))
				.option(color("hud.color_loss", hd.colorLoss, () -> h.colorLoss, v -> h.colorLoss = v))
				.build();

		MonkConfig.Blink b = cfg.blink;
		MonkConfig.Blink bd = def.blink;
		OptionGroup blink = OptionGroup.createBuilder().name(t("blink")).description(d("blink"))
				.option(floatSlider("blink.range", bd.range, 1F, 64F, 0.5F, () -> b.range, v -> b.range = v))
				.option(intSlider("blink.cooldown", bd.cooldownTicks, 0, 400, 5, () -> b.cooldownTicks, v -> b.cooldownTicks = v))
				.option(floatSlider("blink.fall_damage", bd.fallDamage, 0F, 20F, 0.5F, () -> b.fallDamage, v -> b.fallDamage = v))
				.option(bool("blink.preview", bd.previewParticles, () -> b.previewParticles, v -> b.previewParticles = v))
				.option(bool("blink.require_unarmored", bd.requireUnarmored, () -> b.requireUnarmored, v -> b.requireUnarmored = v))
				.option(bool("blink.allow_air", bd.allowAirTarget, () -> b.allowAirTarget, v -> b.allowAirTarget = v))
				.option(floatSlider("blink.min_range", bd.minRange, 0.5F, 16F, 0.5F, () -> b.minRange, v -> b.minRange = v))
				.option(intSlider("blink.ground_depth", bd.groundSearchDepth, 0, 64, 1, () -> b.groundSearchDepth, v -> b.groundSearchDepth = v))
				.build();

		MonkConfig.Flight f = cfg.flight;
		MonkConfig.Flight fd = def.flight;
		OptionGroup flight = OptionGroup.createBuilder().name(t("flight")).description(d("flight"))
				.option(floatSlider("flight.min_speed", fd.minSpeed, 0.01F, 0.1F, 0.005F, () -> f.minSpeed, v -> f.minSpeed = v))
				.option(floatSlider("flight.max_speed", fd.maxSpeed, 0.05F, 1F, 0.01F, () -> f.maxSpeed, v -> f.maxSpeed = v))
				.build();

		MonkConfig.Controls c = cfg.controls;
		MonkConfig.Controls cd = def.controls;
		OptionGroup controls = OptionGroup.createBuilder().name(t("controls")).description(d("controls"))
				.option(LabelOption.create(t("controls.info")))
				.option(bool("controls.scroll_blink", cd.scrollBlinkDistance, () -> c.scrollBlinkDistance, v -> c.scrollBlinkDistance = v))
				.option(bool("controls.scroll_run", cd.scrollRunSpeed, () -> c.scrollRunSpeed, v -> c.scrollRunSpeed = v))
				.option(bool("controls.scroll_fly", cd.scrollFlySpeed, () -> c.scrollFlySpeed, v -> c.scrollFlySpeed = v))
				.option(floatSlider("controls.blink_step", cd.blinkStep, 0.5F, 8F, 0.5F, () -> c.blinkStep, v -> c.blinkStep = v))
				.option(floatSlider("controls.run_step", cd.runStep, 0.05F, 1F, 0.05F, () -> c.runStep, v -> c.runStep = v))
				.option(floatSlider("controls.max_run_scale", cd.maxRunScale, 0.5F, 5F, 0.25F, () -> c.maxRunScale, v -> c.maxRunScale = v))
				.option(floatSlider("controls.fly_step", cd.flyStep, 0.005F, 0.05F, 0.005F, () -> c.flyStep, v -> c.flyStep = v))
				.option(bool("controls.show_changes", cd.showChanges, () -> c.showChanges, v -> c.showChanges = v))
				.build();

		MonkConfig.Gameplay g = cfg.gameplay;
		MonkConfig.Gameplay gd = def.gameplay;
		OptionGroup gameplay = OptionGroup.createBuilder().name(t("gameplay")).description(d("gameplay"))
				.option(bool("gameplay.announce", gd.announceLevelUps, () -> g.announceLevelUps, v -> g.announceLevelUps = v))
				.option(bool("gameplay.action_bar", gd.actionBarProgress, () -> g.actionBarProgress, v -> g.actionBarProgress = v))
				.option(bool("gameplay.info_everyone", gd.infoCommandForEveryone, () -> g.infoCommandForEveryone, v -> g.infoCommandForEveryone = v))
				.option(floatSlider("gameplay.heavy_slowdown", gd.heavyArmorSlowdown, 0F, 0.25F, 0.05F, () -> g.heavyArmorSlowdown, v -> g.heavyArmorSlowdown = v))
				.option(bool("gameplay.heavy_no_tools", gd.heavyArmorNoTools, () -> g.heavyArmorNoTools, v -> g.heavyArmorNoTools = v))
				.option(Option.<String>createBuilder().name(t("gameplay.heavy_list")).description(d("gameplay.heavy_list"))
						.binding(String.join(", ", gd.heavyArmor), () -> String.join(", ", g.heavyArmor), v -> {
							g.heavyArmor.clear();
							for (String part : v.split(",")) {
								if (!part.isBlank()) {
									g.heavyArmor.add(part.trim());
								}
							}
						})
						.controller(StringControllerBuilder::create).build())
				.build();

		MonkConfig.Performance p = cfg.performance;
		MonkConfig.Performance pd = def.performance;
		OptionGroup performance = OptionGroup.createBuilder().name(t("performance")).description(d("performance")).collapsed(true)
				.option(intSlider("performance.stare", pd.stareInterval, 1, 40, 1, () -> p.stareInterval, v -> p.stareInterval = v))
				.option(intSlider("performance.enderman", pd.endermanScanInterval, 1, 40, 1, () -> p.endermanScanInterval, v -> p.endermanScanInterval = v))
				.option(intSlider("performance.equipment", pd.equipmentCheckInterval, 1, 40, 1, () -> p.equipmentCheckInterval, v -> p.equipmentCheckInterval = v))
				.build();

		return ConfigCategory.createBuilder().name(t("category.general"))
				.group(hud).group(controls).group(blink).group(flight).group(gameplay).group(performance)
				.option(ButtonOption.createBuilder().name(t("open_folder")).description(d("open_folder"))
						.action((screen, opt) -> Util.getPlatform().openPath(MonkConfigManager.dir())).build())
				.build();
	}

	// ------------------------------------------------------------------ equipment

	private static ConfigCategory equipment(MonkConfig cfg, MonkConfig def) {
		MonkConfig.Equipment e = cfg.equipment;
		return ConfigCategory.createBuilder().name(t("category.equipment"))
				.option(LabelOption.create(t("equipment.info")))
				.option(bool("equipment.strict", def.equipment.strictArmor, () -> e.strictArmor, v -> e.strictArmor = v))
				.group(ListOption.<String>createBuilder().name(t("equipment.whitelist")).description(d("equipment.whitelist"))
						.binding(def.equipment.whitelist, () -> e.whitelist, v -> e.whitelist = new ArrayList<>(v))
						.controller(StringControllerBuilder::create).initial("minecraft:").build())
				.group(ListOption.<String>createBuilder().name(t("equipment.blacklist")).description(d("equipment.blacklist"))
						.binding(def.equipment.blacklist, () -> e.blacklist, v -> e.blacklist = new ArrayList<>(v))
						.controller(StringControllerBuilder::create).initial("minecraft:").build())
				.build();
	}

	// ------------------------------------------------------------------ ability switches

	private static ConfigCategory abilities(MonkConfig cfg) {
		ConfigCategory.Builder category = ConfigCategory.createBuilder().name(t("category.abilities"))
				.option(LabelOption.create(t("abilities.info")));
		for (MonkType<?> type : MonkTypes.ABILITIES.values()) {
			String key = type.key();
			category.option(Option.<Boolean>createBuilder()
					.name(Component.translatable("monk.advancements.ability." + (key.equals("mining") ? "mining.0" : key)).append(" (" + key + ")"))
					.description(OptionDescription.of(summary("ability", type)))
					.binding(true, () -> !cfg.gameplay.disabledAbilities.contains(key), v -> {
						cfg.gameplay.disabledAbilities.remove(key);
						if (!v) {
							cfg.gameplay.disabledAbilities.add(key);
						}
					})
					.controller(TickBoxControllerBuilder::create)
					.build());
		}
		return category.build();
	}

	// ------------------------------------------------------------------ levels

	private static ConfigCategory levels(LevelsFile levels, Screen parent) {
		ConfigCategory.Builder category = ConfigCategory.createBuilder().name(t("category.levels"))
				.option(LabelOption.create(t("levels.info", levels.levels.size())))
				.option(ButtonOption.createBuilder().name(t("levels.reset")).description(d("levels.reset"))
						.action((screen, opt) -> {
							MonkConfigManager.resetLevelsToDefault();
							Minecraft.getInstance().setScreen(create(parent));
						}).build());

		for (int i = 0; i < levels.levels.size(); i++) {
			LevelsFile.LevelEntry entry = levels.levels.get(i);
			String reqType = entry.requirement != null && entry.requirement.has("type") ? entry.requirement.get("type").getAsString() : "?";
			OptionGroup.Builder group = OptionGroup.createBuilder()
					.name(t("levels.level", i, Component.translatable("monk.config.req." + reqType)))
					.description(OptionDescription.of(t("levels.level.desc")))
					.collapsed(true);
			group.option(Option.<String>createBuilder().name(t("levels.name")).description(d("levels.name"))
					.binding("", () -> entry.name == null ? "" : entry.name, v -> entry.name = v)
					.controller(StringControllerBuilder::create).build());
			group.option(Option.<String>createBuilder().name(t("levels.icon")).description(d("levels.icon"))
					.binding(entry.icon, () -> entry.icon, v -> entry.icon = v)
					.controller(StringControllerBuilder::create).build());

			MonkType<?> req = MonkTypes.REQUIREMENTS.get(reqType);
			group.option(LabelOption.create(t("levels.requirement", Component.translatable("monk.config.req." + reqType))));
			if (req != null) {
				for (ParamSpec spec : req.params().values()) {
					group.option(param(entry.requirement, reqType, spec, "  "));
				}
			}

			if (entry.add != null) {
				for (JsonObject ability : entry.add) {
					String abType = ability.has("type") ? ability.get("type").getAsString() : "?";
					MonkType<?> type = MonkTypes.ABILITIES.get(abType);
					group.option(Option.<Boolean>createBuilder()
							.name(t("levels.ability", Component.translatable("monk.advancements.ability." + (abType.equals("mining") ? "mining.0" : abType)), abType))
							.description(OptionDescription.of(type == null ? t("levels.unknown_ability") : summary("ability", type)))
							.binding(true, () -> !ability.has("enabled") || ability.get("enabled").getAsBoolean(), v -> {
								if (v) {
									ability.remove("enabled");
								} else {
									ability.addProperty("enabled", false);
								}
							})
							.controller(TickBoxControllerBuilder::create).build());
					group.option(text(ability, "name", "levels.custom_name"));
					group.option(text(ability, "description", "levels.custom_desc"));
					if (type != null) {
						for (ParamSpec spec : type.params().values()) {
							group.option(param(ability, abType, spec, "    "));
						}
					}
				}
			}
			if (entry.remove != null && !entry.remove.isEmpty()) {
				group.option(LabelOption.create(t("levels.removes", String.join(", ", entry.remove))));
			}
			category.group(group.build());
		}
		return category.build();
	}

	/** Translated summary of an ability / requirement type (English text as fallback). */
	private static Component summary(String kind, MonkType<?> type) {
		return Component.translatableWithFallback("monk.type." + kind + "." + type.key(), type.summary());
	}

	/** Optional free text ("name" / "description") stored in an ability entry; empty = default text. */
	private static Option<String> text(JsonObject json, String field, String key) {
		return Option.<String>createBuilder().name(Component.literal("    ").append(t(key))).description(d(key))
				.binding("", () -> json.has(field) && json.get(field).isJsonPrimitive() ? json.get(field).getAsString() : "", v -> {
					if (v == null || v.isBlank()) {
						json.remove(field);
					} else {
						json.addProperty(field, v);
					}
				})
				.controller(StringControllerBuilder::create).build();
	}

	/** Builds an editable option for one parameter stored inside a JSON entry. */
	private static Option<?> param(JsonObject json, String owner, ParamSpec spec, String indent) {
		Params view = new Params(json, Map.of(spec.name(), spec));
		String base = "monk.param." + owner + "." + spec.name();
		Component name = Component.literal(indent).append(Component.translatableWithFallback(base, spec.name()))
				.append(Component.literal(" (" + spec.name() + ")").withStyle(ChatFormatting.DARK_GRAY));
		OptionDescription desc = OptionDescription.of(Component.translatableWithFallback(base + ".desc", spec.comment()));
		String key = spec.name();
		return switch (spec.type()) {
			case INT -> Option.<Integer>createBuilder().name(name).description(desc)
					.binding(((Number) spec.defaultValue()).intValue(), () -> view.getInt(key), v -> json.addProperty(key, v))
					.controller(o -> IntegerFieldControllerBuilder.create(o).range((int) spec.min(), (int) Math.min(Integer.MAX_VALUE, spec.max())))
					.build();
			case FLOAT -> Option.<Float>createBuilder().name(name).description(desc)
					.binding(((Number) spec.defaultValue()).floatValue(), () -> view.getFloat(key), v -> json.addProperty(key, v))
					.controller(o -> FloatFieldControllerBuilder.create(o).range((float) spec.min(), (float) spec.max()))
					.build();
			case BOOL -> Option.<Boolean>createBuilder().name(name).description(desc)
					.binding((Boolean) spec.defaultValue(), () -> view.getBool(key), v -> json.addProperty(key, v))
					.controller(TickBoxControllerBuilder::create)
					.build();
			case STRING -> Option.<String>createBuilder().name(name).description(desc)
					.binding((String) spec.defaultValue(), () -> view.getString(key), v -> json.addProperty(key, v))
					.controller(StringControllerBuilder::create)
					.build();
			case STRING_LIST -> Option.<String>createBuilder().name(name)
					.description(OptionDescription.of(Component.translatableWithFallback(base + ".desc", spec.comment())
							.append("\n").append(t("levels.comma_separated"))))
					.binding(String.join(",", view.getList(key)), () -> String.join(",", view.getList(key)), v -> {
						JsonArray array = new JsonArray();
						for (String s : v.split(",", -1)) {
							array.add(s.trim());
						}
						json.add(key, array);
					})
					.controller(StringControllerBuilder::create)
					.build();
		};
	}

	// ------------------------------------------------------------------ helpers

	private static Option<Boolean> bool(String key, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
		return Option.<Boolean>createBuilder().name(t(key)).description(d(key))
				.binding(def, get, set).controller(TickBoxControllerBuilder::create).build();
	}

	private static Option<Integer> intSlider(String key, int def, int min, int max, int step, Supplier<Integer> get, Consumer<Integer> set) {
		return Option.<Integer>createBuilder().name(t(key)).description(d(key))
				.binding(def, get, set).controller(o -> IntegerSliderControllerBuilder.create(o).range(min, max).step(step)).build();
	}

	private static Option<Float> floatSlider(String key, float def, float min, float max, float step, Supplier<Float> get, Consumer<Float> set) {
		return Option.<Float>createBuilder().name(t(key)).description(d(key))
				.binding(def, get, set).controller(o -> FloatSliderControllerBuilder.create(o).range(min, max).step(step)).build();
	}

	private static Option<Color> color(String key, int def, Supplier<Integer> get, Consumer<Integer> set) {
		return Option.<Color>createBuilder().name(t(key)).description(d(key))
				.binding(new Color(def), () -> new Color(get.get()), v -> set.accept(v.getRGB() & 0xFFFFFF))
				.controller(ColorControllerBuilder::create).build();
	}
}
