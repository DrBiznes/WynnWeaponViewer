package com.wynnweaponviewer;

import com.wynnweaponviewer.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

public class WynnWeaponViewer implements ClientModInitializer {

	public static Logger LOGGER = LogManager.getLogger();

	public static final String MOD_ID = "wynn_weapon_viewer";
	public static final String MOD_NAME = "Wynn Weapon Viewer";

	public static final String KEY_BINDING_CATEGORY = "key.categories." + MOD_ID;
	public static final KeyBinding TOGGLE_KEY_BINDING = new KeyBinding("key." + MOD_ID + ".toggle_zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, KEY_BINDING_CATEGORY);

	private static boolean zoomEnabled = false;

	@Override
	public void onInitializeClient() {
		log(Level.INFO, "Initializing Wynn Weapon Viewer");
		KeyBindingHelper.registerKeyBinding(TOGGLE_KEY_BINDING);
	}

	public static void log(Level level, String message){
		LOGGER.log(level, "["+MOD_NAME+"] " + message);
	}

	public static boolean shallRender() {
		while (TOGGLE_KEY_BINDING.wasPressed()) {
			zoomEnabled = !zoomEnabled;
		}
		return ModConfig.isEnabled() && zoomEnabled;
	}
}