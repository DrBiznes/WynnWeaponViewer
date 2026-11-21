package com.wynnweaponviewer;

import com.wynnweaponviewer.config.ModConfig;
import com.wynnweaponviewer.render.ZoomedItemPIPRenderer;
import com.wynnweaponviewer.render.ZoomedItemRenderer;
import com.wynnweaponviewer.render.card.FrameMasks;
import com.wynnweaponviewer.share.CardShare;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public class WynnWeaponViewer implements ClientModInitializer {
	public static final Logger LOGGER = LogManager.getLogger("WynnWeaponViewer");
	public static final String MOD_ID = "wynn_weapon_viewer";

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(MOD_ID, "general")
	);
	private static KeyMapping toggleZoomKey;
	private static KeyMapping shareCardKey;
	@Nullable
	private static Screen pendingScreen;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Initializing Wynn Weapon Viewer");
		// Load (and if needed, migrate or repair) the config file now rather than on the first inventory opened.
		ModConfig.getConfig();

		// Tinted card frames are built from the pack's frame sprites; rebuild them whenever the pack changes.
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(
				Identifier.fromNamespaceAndPath(MOD_ID, "card_frame_masks"),
				(ResourceManagerReloadListener) manager -> FrameMasks.clear());

		// The big model is drawn as a picture-in-picture element. Fabric adds this renderer to the game's
		// GuiRenderer (and makes more of it when several are drawn in one frame); the GuiRenderer closes it.
		SpecialGuiElementRegistry.register(context -> new ZoomedItemPIPRenderer(context.vertexConsumers()));

		// Register keybinding
		toggleZoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.wynn_weapon_viewer.toggle_zoom",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_PERIOD,
				CATEGORY
		));

		// Used with Ctrl (Cmd on macOS) inside inventories only, so it doesn't clash with in-game C bindings.
		shareCardKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.wynn_weapon_viewer.share_card",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_C,
				CATEGORY
		));

		// Drives the tooltip page reading that sharing does first.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			CardShare.tick();
			if (pendingScreen != null) {
				Screen screen = pendingScreen;
				pendingScreen = null;
				client.setScreen(screen);
			}
		});

		// Register lifecycle events
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ZoomedItemRenderer.cleanup();
		});

		// Register screen events for rendering
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof AbstractContainerScreen<?> containerScreen) {
				// Ctrl+C: share the hovered item's card. Swallowed when it shares, so nothing else sees it.
				// While its tooltip pages are being read, keys on that slot (drop, hotbar swap...) are held back.
				ScreenKeyboardEvents.allowKeyPress(screen).register((screen1, keyEvent) -> {
					if (CardShare.blocksInputOn(containerScreen) && !keyEvent.isEscape()) return false;
					return !(CardShare.isShareShortcut(screen1, keyEvent, shareCardKey.matches(keyEvent))
							&& CardShare.tryShare(containerScreen));
				});
				ScreenMouseEvents.allowMouseClick(screen).register((screen1, mouseEvent) ->
						!CardShare.blocksInputOn(containerScreen));

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
		setZoomEnabled(!isZoomEnabled());
		LOGGER.info("Weapon zoom toggled: {}", isZoomEnabled() ? "ON" : "OFF");
		if (ModConfig.shouldShowToggleMessage()) {
			sendChatMessage(isZoomEnabled()
					? "\u00A76[Weapon Viewer] Your weapon grows gargantuan (but only in your inventory)"
					: "\u00A76[Weapon Viewer] Your weapon weeps, for it yearns to be big!");
		}
	}

	/** The saved "Enabled" option is the one on/off switch; the keybind, commands and config screen all set it. */
	public static void setZoomEnabled(boolean enabled) {
		ModConfig.setEnabled(enabled);
	}

	public static boolean isZoomEnabled() {
		return ModConfig.isEnabled();
	}

	public static boolean shouldRenderZoom() {
		return ModConfig.isEnabled();
	}

	/**
	 * Opens a screen on the next client tick. Commands run while the chat screen is still open, and it closes
	 * itself right after, so setting a screen straight away would be undone.
	 */
	public static void openScreenNextTick(Screen screen) {
		pendingScreen = screen;
	}

	private static void sendChatMessage(String message) {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.displayClientMessage(Component.literal(message), false);
		}
	}
}
