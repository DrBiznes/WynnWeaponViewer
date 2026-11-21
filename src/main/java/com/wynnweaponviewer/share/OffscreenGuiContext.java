package com.wynnweaponviewer.share;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.jetbrains.annotations.Nullable;

/**
 * While active, Minecraft's GUI renderer draws into {@link #target()} instead of the main window, as if the window
 * were {@code width x height} pixels at {@code guiScale}.
 *
 * <p>{@link net.minecraft.client.gui.render.GuiRenderer} reads the window size, GUI scale and main render target
 * straight from {@code Minecraft}, so {@code WindowMixin} and {@code MinecraftMixin} answer from here instead.
 * Only ever active synchronously on the render thread, for the length of one offscreen GUI pass.
 */
public final class OffscreenGuiContext {

    private static boolean active;
    private static int width;
    private static int height;
    private static int guiScale;
    @Nullable
    private static RenderTarget target;

    private OffscreenGuiContext() {}

    public static void begin(RenderTarget renderTarget, int guiScale) {
        if (active) throw new IllegalStateException("Offscreen GUI pass already running");
        OffscreenGuiContext.target = renderTarget;
        OffscreenGuiContext.width = renderTarget.width;
        OffscreenGuiContext.height = renderTarget.height;
        OffscreenGuiContext.guiScale = guiScale;
        OffscreenGuiContext.active = true;
    }

    public static void end() {
        active = false;
        target = null;
    }

    public static boolean isActive() {
        return active;
    }

    public static int width() {
        return width;
    }

    public static int height() {
        return height;
    }

    public static int guiScale() {
        return guiScale;
    }

    @Nullable
    public static RenderTarget target() {
        return target;
    }
}
