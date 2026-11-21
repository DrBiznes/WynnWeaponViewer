package com.wynnweaponviewer.config;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the settings screen: the full editor when Cloth Config is installed, otherwise a small screen that points
 * at the config file. Safe to call without Cloth Config - {@link ClothConfigScreen} is only loaded when it's there.
 */
public final class ConfigScreens {

    private static final String CLOTH_CONFIG_ID = "cloth-config";

    private ConfigScreens() {}

    public static boolean hasClothConfig() {
        return FabricLoader.getInstance().isModLoaded(CLOTH_CONFIG_ID);
    }

    public static Screen create(@Nullable Screen parent) {
        return hasClothConfig() ? ClothConfigScreen.create(parent) : new NoClothConfigScreen(parent);
    }
}
