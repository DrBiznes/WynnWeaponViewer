package com.wynnweaponviewer;

import com.wynnweaponviewer.config.ModConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WynnWeaponViewer implements ClientModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("WynnWeaponViewer");

	@SuppressWarnings("unused")
	public static final String MOD_ID = "wynn_weapon_viewer";

	private static KeyBinding toggleZoomKey;
	private static boolean zoomEnabled = true;  // Default to true

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Wynn Weapon Viewer");

		// Register keybinding
		toggleZoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.wynn_weapon_viewer.toggle_zoom",
				InputUtil.Type.KEYSYM,
				InputUtil.GLFW_KEY_PERIOD,  // Period key
				"category.wynn_weapon_viewer.general"
		));

		// Register tick event for handling keybinds
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (toggleZoomKey.wasPressed()) {
				zoomEnabled = !zoomEnabled;
				LOGGER.info("Weapon zoom toggled: {}", zoomEnabled ? "ON" : "OFF");
				if (ModConfig.shouldShowToggleMessage()) {
					sendChatMessage(zoomEnabled
							? "§6[Weapon Viewer] Your weapon grows gargantuan (but only in your inventory)"
							: "§6[Weapon Viewer] Your weapon weeps, for it yearns to be big!");
				}
			}
		});
	}

	public static boolean shouldRenderZoom() {
		return ModConfig.isEnabled() && zoomEnabled;
	}

	private void sendChatMessage(String message) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null) {
			client.player.sendMessage(Text.of(message), false);
		}
	}
}