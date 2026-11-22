package com.wynnweaponviewer.config;

import com.wynnweaponviewer.HorizontalAlignment;
import com.wynnweaponviewer.config.ModConfig.AppearAnimation;
import com.wynnweaponviewer.config.ModConfig.Config;
import com.wynnweaponviewer.config.ModConfig.DisplayMode;
import com.wynnweaponviewer.config.ModConfig.IdleAnimation;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import me.shedaniel.clothconfig2.impl.builders.BooleanToggleBuilder;
import me.shedaniel.clothconfig2.impl.builders.EnumSelectorBuilder;
import me.shedaniel.clothconfig2.impl.builders.IntFieldBuilder;
import me.shedaniel.clothconfig2.impl.builders.IntSliderBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * The in-game settings editor, built with Cloth Config. Only loaded when Cloth Config is installed
 * (see {@link ConfigScreens}).
 *
 * <p>Every option's label is {@code config.wynn_weapon_viewer.option.<field>}, its tooltip {@code ...<field>.tooltip}
 * and each enum value {@code ...<field>.<value>}. Edits apply to the live config when Save is pressed, then it's
 * written to disk once.
 */
final class ClothConfigScreen {

    private static final String KEY = "config.wynn_weapon_viewer.";

    private ClothConfigScreen() {}

    static Screen create(@Nullable Screen parent) {
        Config current = ModConfig.getConfig();
        Config defaults = ModConfig.defaults();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(text("title"))
                .setSavingRunnable(ModConfig::save);
        ConfigEntryBuilder entries = builder.entryBuilder();

        // --- General -------------------------------------------------------------------------------------------
        ConfigCategory general = builder.getOrCreateCategory(text("category.general"));
        general.addEntry(toggle(entries, "enabled", current.enabled, defaults.enabled, v -> live().enabled = v).build());
        general.addEntry(toggle(entries, "showToggleMessage", current.showToggleMessage, defaults.showToggleMessage,
                v -> live().showToggleMessage = v).build());
        general.addEntry(entries.startTextDescription(text("general.controls")).build());

        SubCategoryBuilder sharing = entries.startSubCategory(text("subcategory.sharing")).setExpanded(true);
        sharing.add(toggle(entries, "showShareHint", current.showShareHint, defaults.showShareHint,
                v -> live().showShareHint = v).build());
        general.addEntry(sharing.build());

        // --- Display -------------------------------------------------------------------------------------------
        ConfigCategory display = builder.getOrCreateCategory(text("category.display"));
        display.addEntry(enumSelector(entries, "displayMode", DisplayMode.class, current.displayMode,
                defaults.displayMode, v -> live().displayMode = v).build());
        display.addEntry(entries.startIntSlider(option("scale"), Math.round(current.scale * 100), 10, 100)
                .setDefaultValue(Math.round(defaults.scale * 100))
                .setTextGetter(v -> Component.literal(v + "%"))
                .setTooltip(tooltip("scale"))
                .setSaveConsumer(v -> live().scale = v / 100f)
                .build());
        display.addEntry(enumSelector(entries, "alignment", HorizontalAlignment.class, current.horizontalAlignment,
                defaults.horizontalAlignment, v -> live().horizontalAlignment = v).build());
        display.addEntry(offset(entries, "xOffset", current.xOffset, defaults.xOffset, v -> live().xOffset = v).build());
        display.addEntry(offset(entries, "yOffset", current.yOffset, defaults.yOffset, v -> live().yOffset = v).build());

        SubCategoryBuilder card = entries.startSubCategory(text("subcategory.card"))
                .setTooltip(text("subcategory.card.tooltip"))
                .setExpanded(true);
        card.add(entries.startIntSlider(option("cardMaxIdentifications"), current.cardMaxIdentifications,
                        0, ModConfig.ALL_IDENTIFICATIONS)
                .setDefaultValue(defaults.cardMaxIdentifications)
                .setTextGetter(ClothConfigScreen::identificationCount)
                .setTooltip(tooltip("cardMaxIdentifications"))
                .setSaveConsumer(v -> live().cardMaxIdentifications = v)
                .build());
        card.add(toggle(entries, "cardShowPowders", current.cardShowPowders, defaults.cardShowPowders,
                v -> live().cardShowPowders = v).build());
        card.add(toggle(entries, "cardShowShinyStat", current.cardShowShinyStat, defaults.cardShowShinyStat,
                v -> live().cardShowShinyStat = v).build());
        card.add(toggle(entries, "cardShowScales", current.cardShowScales, defaults.cardShowScales,
                v -> live().cardShowScales = v).build());
        card.add(toggle(entries, "cardFrameEffects", current.cardFrameEffects, defaults.cardFrameEffects,
                v -> live().cardFrameEffects = v).build());
        display.addEntry(card.build());

        SubCategoryBuilder floating = entries.startSubCategory(text("subcategory.floating"))
                .setTooltip(text("subcategory.floating.tooltip"))
                .setExpanded(current.displayMode == DisplayMode.FLOATING);
        BooleanListEntry showItemInfo = toggle(entries, "showItemInfo", current.showItemInfo, defaults.showItemInfo,
                v -> live().showItemInfo = v).build();
        floating.add(showItemInfo);
        floating.add(toggle(entries, "itemNameAbove", current.itemNameAbove, defaults.itemNameAbove,
                v -> live().itemNameAbove = v)
                .setRequirement(Requirement.isTrue(showItemInfo))
                .build());
        display.addEntry(floating.build());

        // --- Animations ----------------------------------------------------------------------------------------
        ConfigCategory animations = builder.getOrCreateCategory(text("category.animations"));
        animations.addEntry(entries.startIntSlider(option("appearDelayMs"), current.appearDelayMs, 0, 1000)
                .setDefaultValue(defaults.appearDelayMs)
                .setTextGetter(v -> Component.translatable(KEY + "unit.ms", v))
                .setTooltip(tooltip("appearDelayMs"))
                .setSaveConsumer(v -> live().appearDelayMs = v)
                .build());
        animations.addEntry(enumSelector(entries, "appearAnimation", AppearAnimation.class, current.appearAnimation,
                defaults.appearAnimation, v -> live().appearAnimation = v).build());
        animations.addEntry(entries.startIntSlider(option("infoDelaySeconds"), current.infoDelaySeconds, 0, 10)
                .setDefaultValue(defaults.infoDelaySeconds)
                .setTextGetter(v -> Component.translatable(KEY + "unit.seconds", v))
                .setTooltip(tooltip("infoDelaySeconds"))
                .setSaveConsumer(v -> live().infoDelaySeconds = v)
                .build());
        animations.addEntry(toggle(entries, "textCascade", current.textCascade, defaults.textCascade,
                v -> live().textCascade = v).build());
        EnumListEntry<IdleAnimation> idle = enumSelector(entries, "idleAnimation", IdleAnimation.class,
                current.idleAnimation, defaults.idleAnimation, v -> live().idleAnimation = v).build();
        animations.addEntry(idle);
        animations.addEntry(toggle(entries, "cardIdleAnimation", current.cardIdleAnimation, defaults.cardIdleAnimation,
                v -> live().cardIdleAnimation = v)
                .setRequirement(Requirement.not(Requirement.isValue(idle, IdleAnimation.NONE)))
                .build());

        // --- Items ---------------------------------------------------------------------------------------------
        ConfigCategory items = builder.getOrCreateCategory(text("category.filters"));
        items.addEntry(entries.startTextDescription(text("filters.description")).build());
        items.addEntry(toggle(entries, "enableWeapons", current.enableWeapons, defaults.enableWeapons,
                v -> live().enableWeapons = v).build());
        items.addEntry(toggle(entries, "enableArmor", current.enableArmor, defaults.enableArmor,
                v -> live().enableArmor = v).build());
        items.addEntry(toggle(entries, "enableAccessories", current.enableAccessories, defaults.enableAccessories,
                v -> live().enableAccessories = v).build());
        items.addEntry(toggle(entries, "enableUnidentified", current.enableUnidentified, defaults.enableUnidentified,
                v -> live().enableUnidentified = v).build());
        items.addEntry(toggle(entries, "enableCorkianAmplifiers", current.enableCorkianAmplifiers,
                defaults.enableCorkianAmplifiers, v -> live().enableCorkianAmplifiers = v).build());

        return builder.build();
    }

    /** Edits go to whatever config is loaded when Save is pressed (a /wwb reload may have replaced it). */
    private static Config live() {
        return ModConfig.getConfig();
    }

    private static BooleanToggleBuilder toggle(ConfigEntryBuilder entries, String field, boolean value,
                                               boolean defaultValue, Consumer<Boolean> save) {
        return entries.startBooleanToggle(option(field), value)
                .setDefaultValue(defaultValue)
                .setTooltip(tooltip(field))
                .setSaveConsumer(save);
    }

    private static <T extends Enum<T>> EnumSelectorBuilder<T> enumSelector(ConfigEntryBuilder entries, String field,
                                                                         Class<T> type, T value, T defaultValue,
                                                                         Consumer<T> save) {
        return entries.startEnumSelector(option(field), type, value)
                .setDefaultValue(defaultValue)
                .setEnumNameProvider(v -> Component.translatable(KEY + "option." + field + "."
                        + v.name().toLowerCase(Locale.ROOT)))
                .setTooltip(tooltip(field))
                .setSaveConsumer(save);
    }

    /** A typed number rather than a slider: offsets are usually nudged by a few pixels. */
    private static IntFieldBuilder offset(ConfigEntryBuilder entries, String field, int value, int defaultValue,
                                          Consumer<Integer> save) {
        return entries.startIntField(option(field), value)
                .setDefaultValue(defaultValue)
                .setMin(-500)
                .setMax(500)
                .setTooltip(tooltip(field))
                .setSaveConsumer(save);
    }

    private static Component identificationCount(int count) {
        if (count <= 0) return text("option.cardMaxIdentifications.none");
        if (count >= ModConfig.ALL_IDENTIFICATIONS) return text("option.cardMaxIdentifications.all");
        return Component.literal(Integer.toString(count));
    }

    private static Component option(String field) {
        return text("option." + field);
    }

    private static Component tooltip(String field) {
        return text("option." + field + ".tooltip");
    }

    private static Component text(String key) {
        return Component.translatable(KEY + key);
    }
}
