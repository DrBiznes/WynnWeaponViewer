package com.wynnweaponviewer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.wynnweaponviewer.HorizontalAlignment;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig implements ModMenuApi {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "wynn_weapon_viewer.json");

    private static Config config;

    static {
        loadConfig();
    }

    private static class Config {
        boolean enabled = true;
        float scale = 0.4F;
        HorizontalAlignment horizontalAlignment = HorizontalAlignment.RIGHT;  // Changed default to RIGHT
        int xOffset = 0;
        int yOffset = 0;
        boolean showToggleMessage = true;  // New option
    }

    public static boolean isEnabled() {
        return config.enabled;
    }

    public static void setEnabled(boolean enabled) {
        config.enabled = enabled;
        saveConfig();
    }

    public static float getScale() {
        return config.scale;
    }

    public static HorizontalAlignment getHorizontalAlignment() {
        return config.horizontalAlignment;
    }

    public static int getXOffset() {
        return config.xOffset;
    }

    public static int getYOffset() {
        return config.yOffset;
    }

    public static boolean shouldShowToggleMessage() {
        return config.showToggleMessage;
    }

    private static void loadConfig() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                config = GSON.fromJson(reader, Config.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (config == null) {
            config = new Config();
            saveConfig();
        }
    }

    private static void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.translatable("config.wynn_weapon_viewer.title"));

            ConfigCategory general = builder.getOrCreateCategory(Text.translatable("config.wynn_weapon_viewer.category.general"));

            ConfigEntryBuilder entryBuilder = builder.entryBuilder();
            general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.wynn_weapon_viewer.option.enabled"), config.enabled)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.wynn_weapon_viewer.option.enabled.tooltip"))
                    .setSaveConsumer(newValue -> {
                        config.enabled = newValue;
                        saveConfig();
                    })
                    .build());

            general.addEntry(entryBuilder.startFloatField(Text.translatable("config.wynn_weapon_viewer.option.scale"), config.scale)
                    .setDefaultValue(0.4F)
                    .setMin(0.1F)
                    .setMax(1.0F)
                    .setTooltip(Text.translatable("config.wynn_weapon_viewer.option.scale.tooltip"))
                    .setSaveConsumer(newValue -> {
                        config.scale = newValue;
                        saveConfig();
                    })
                    .build());

            general.addEntry(entryBuilder.startEnumSelector(Text.translatable("config.wynn_weapon_viewer.option.alignment"), HorizontalAlignment.class, config.horizontalAlignment)
                    .setDefaultValue(HorizontalAlignment.RIGHT)
                    .setTooltip(Text.translatable("config.wynn_weapon_viewer.option.alignment.tooltip"))
                    .setSaveConsumer(newValue -> {
                        config.horizontalAlignment = newValue;
                        saveConfig();
                    })
                    .build());

            general.addEntry(entryBuilder.startIntField(Text.translatable("config.wynn_weapon_viewer.option.xOffset"), config.xOffset)
                    .setDefaultValue(0)
                    .setMin(-500)
                    .setMax(500)
                    .setTooltip(Text.translatable("config.wynn_weapon_viewer.option.xOffset.tooltip"))
                    .setSaveConsumer(newValue -> {
                        config.xOffset = newValue;
                        saveConfig();
                    })
                    .build());

            general.addEntry(entryBuilder.startIntField(Text.translatable("config.wynn_weapon_viewer.option.yOffset"), config.yOffset)
                    .setDefaultValue(0)
                    .setMin(-500)
                    .setMax(500)
                    .setTooltip(Text.translatable("config.wynn_weapon_viewer.option.yOffset.tooltip"))
                    .setSaveConsumer(newValue -> {
                        config.yOffset = newValue;
                        saveConfig();
                    })
                    .build());

            general.addEntry(entryBuilder.startBooleanToggle(Text.translatable("config.wynn_weapon_viewer.option.showToggleMessage"), config.showToggleMessage)
                    .setDefaultValue(true)
                    .setTooltip(Text.translatable("config.wynn_weapon_viewer.option.showToggleMessage.tooltip"))
                    .setSaveConsumer(newValue -> {
                        config.showToggleMessage = newValue;
                        saveConfig();
                    })
                    .build());

            return builder.build();
        };
    }
}