package com.wynnweaponviewer;

import com.wynnweaponviewer.config.ModConfig;
import com.wynnweaponviewer.render.ZoomedItemRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

public class WynnWeaponViewer implements ClientModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("WynnWeaponViewer");
	public static final String MOD_ID = "wynn_weapon_viewer";

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(MOD_ID, "general")
	);
	private static KeyMapping toggleZoomKey;
	private static boolean zoomEnabled = true;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Wynn Weapon Viewer");

		// Register keybinding
		toggleZoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.wynn_weapon_viewer.toggle_zoom",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_PERIOD,
				CATEGORY
		));

		// Register lifecycle events
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ZoomedItemRenderer.cleanup();
		});

		// Register screen events for rendering
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof AbstractContainerScreen<?> containerScreen) {
				// Key press handler
				ScreenKeyboardEvents.afterKeyPress(screen).register((screen1, keyEvent) -> {
					if (toggleZoomKey.matches(keyEvent)) {
						toggleZoom();
					}
				});

				// Render events
				ScreenEvents.beforeRender(screen).register((screen1, graphics, mouseX, mouseY, delta) -> {
					ZoomedItemRenderer.beginFrame();
				});

				ScreenEvents.afterRender(screen).register((screen1, graphics, mouseX, mouseY, delta) -> {
					ZoomedItemRenderer.render(graphics, containerScreen, mouseX, mouseY);
				});

				// Cleanup on screen close
				ScreenEvents.remove(screen).register(screen1 -> {
					ZoomedItemRenderer.onScreenClose();
				});
			}
		});

		// Register commands
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> ModCommands.register(dispatcher));
	}

	public static void toggleZoom() {
		setZoomEnabled(!zoomEnabled);
		LOGGER.info("Weapon zoom toggled: {}", zoomEnabled ? "ON" : "OFF");
		if (ModConfig.shouldShowToggleMessage()) {
			sendChatMessage(zoomEnabled
					? "\u00A76[Weapon Viewer] Your weapon grows gargantuan (but only in your inventory)"
					: "\u00A76[Weapon Viewer] Your weapon weeps, for it yearns to be big!");
		}
	}

	public static void setZoomEnabled(boolean enabled) {
		zoomEnabled = enabled;
		ModConfig.setEnabled(enabled);
	}

	public static boolean isZoomEnabled() {
		return zoomEnabled;
	}

	public static boolean shouldRenderZoom() {
		return ModConfig.isEnabled() && zoomEnabled;
	}

	private static void sendChatMessage(String message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(message), false);
		}
	}
}
