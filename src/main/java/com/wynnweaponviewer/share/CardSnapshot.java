package com.wynnweaponviewer.share;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.wynnweaponviewer.card.ItemCardData;
import com.wynnweaponviewer.mixin.GameRendererAccessor;
import com.wynnweaponviewer.render.ZoomedItemRenderer;
import com.wynnweaponviewer.render.card.CardBody;
import com.wynnweaponviewer.render.card.CardLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

/**
 * Renders an item card into its own offscreen canvas - not a crop of the screen - so the image is the same
 * size and resolution on every setup, has a transparent background, and never shows the inventory, cursor or
 * tooltips. Works whether or not the card is showing on screen.
 *
 * <p>Minecraft's {@link GuiRenderer} always draws to the main window, so we run a private one inside an
 * {@link OffscreenGuiContext}, which makes the window size, GUI scale and main render target report our canvas.
 */
public final class CardSnapshot {

    /** The image is about this tall: enough to stay sharp on Discord without becoming huge. */
    private static final int TARGET_HEIGHT_PX = 1600;
    /** Pixels per tooltip pixel. Whole numbers only, so Wynncraft's pixel fonts stay crisp. */
    private static final int MIN_SCALE = 2;
    private static final int MAX_SCALE = 4;
    /** Model window height as a fraction of its width. */
    private static final float WINDOW_ASPECT = 0.85f;

    private CardSnapshot() {}

    /**
     * Renders the card for {@code stack} and hands back the image (owned by the receiver) once the GPU has
     * finished, a frame or so later. Must be called on the render thread, outside of a frame being drawn.
     */
    public static void capture(ItemStack stack, ItemCardData data, Consumer<NativeImage> onImage,
                               Consumer<Throwable> onError) {
        // Full card with a slightly wide model window - the shared image doesn't have to squeeze beside an inventory.
        CardLayout full = CardLayout.build(data, CardBody.Density.FULL);
        CardLayout layout = full.withWindowHeight(Math.round(full.windowWidth() * WINDOW_ASPECT));

        int scale = Math.clamp(TARGET_HEIGHT_PX / layout.outerHeight(), MIN_SCALE, MAX_SCALE);
        int width = layout.outerWidth() * scale;
        int height = layout.outerHeight() * scale;

        TextureTarget target = new TextureTarget("Wynn Weapon Bigger card share", width, height, true);
        try {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    target.getColorTexture(), 0, target.getDepthTexture(), 1.0);
            ItemStackRenderState renderState = new ItemStackRenderState();
            renderGui(target, scale, graphics -> ZoomedItemRenderer.drawStillCard(graphics, renderState, stack, layout));
        } catch (RuntimeException e) {
            target.destroyBuffers();
            throw e;
        }
        readBack(target, onImage, onError);
    }

    private static void renderGui(TextureTarget target, int guiScale, Consumer<GuiGraphics> draw) {
        Minecraft mc = Minecraft.getInstance();
        GameRenderer gameRenderer = mc.gameRenderer;
        GuiRenderState state = new GuiRenderState();

        OffscreenGuiContext.begin(target, guiScale);
        // Our own renderer (and so its own item atlas and picture-in-picture textures), so the screen's is untouched.
        // GuiRendererMixin gives it our model renderer like any other GuiRenderer.
        try (GuiRenderer renderer = new GuiRenderer(state, mc.renderBuffers().bufferSource(),
                gameRenderer.getSubmitNodeStorage(), gameRenderer.getFeatureRenderDispatcher(), List.of())) {
            GuiGraphics graphics = new GuiGraphics(mc, state, -1, -1);
            draw.accept(graphics);
            renderer.render(((GameRendererAccessor) gameRenderer).wynnweapon$getFogRenderer()
                    .getBuffer(FogRenderer.FogMode.NONE));
        } finally {
            OffscreenGuiContext.end();
        }
    }

    /**
     * Copies the canvas back to the CPU (like vanilla's screenshot, but keeping the alpha channel) and frees it.
     */
    private static void readBack(TextureTarget target, Consumer<NativeImage> onImage, Consumer<Throwable> onError) {
        GpuTexture texture = target.getColorTexture();
        int width = target.width;
        int height = target.height;
        int pixelSize = texture.getFormat().pixelSize();

        GpuDevice device = RenderSystem.getDevice();
        GpuBuffer buffer = device.createBuffer(() -> "Wynn Weapon Bigger card share readback",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * pixelSize);
        CommandEncoder encoder = device.createCommandEncoder();
        try {
            encoder.copyTextureToBuffer(texture, buffer, 0L, () -> {
                NativeImage image = null;
                try (GpuBuffer.MappedView view = encoder.mapBuffer(buffer, true, false)) {
                    ByteBuffer pixels = view.data();
                    image = new NativeImage(width, height, false);
                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int abgr = pixels.getInt((x + y * width) * pixelSize);
                            // GPU rows start at the bottom.
                            image.setPixelABGR(x, height - 1 - y, unpremultiply(abgr));
                        }
                    }
                } catch (RuntimeException e) {
                    if (image != null) image.close();
                    image = null;
                    onError.accept(e);
                } finally {
                    buffer.close();
                    target.destroyBuffers();
                }
                if (image != null) onImage.accept(image);
            }, 0);
        } catch (RuntimeException e) {
            buffer.close();
            target.destroyBuffers();
            throw e;
        }
    }

    /**
     * GUI blending onto a transparent canvas leaves colours multiplied by their alpha; PNGs want them straight,
     * or the card's translucent background would come out darker than it looks in game.
     */
    private static int unpremultiply(int abgr) {
        int a = abgr >>> 24;
        if (a == 0) return 0;
        if (a == 255) return abgr;
        int r = Math.min(255, (abgr & 0xFF) * 255 / a);
        int g = Math.min(255, ((abgr >> 8) & 0xFF) * 255 / a);
        int b = Math.min(255, ((abgr >> 16) & 0xFF) * 255 / a);
        return a << 24 | b << 16 | g << 8 | r;
    }
}
