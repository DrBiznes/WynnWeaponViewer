package com.wynnweaponviewer.render.card;

import com.mojang.blaze3d.platform.NativeImage;
import com.wynnweaponviewer.WynnWeaponViewer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * White copies of the pack's tooltip frame sprites, so a frame can be tinted to any colour while keeping its exact
 * shape (corner notches, ornaments). Each mask keeps the sprite's alpha and turns its colours into brightness, so
 * the frame's own shading survives the tint.
 *
 * <p>Built lazily from the sprite's PNG + {@code .mcmeta} (first animation frame only) and thrown away on every
 * resource reload, since the server pack can change.
 */
public final class FrameMasks {

    /** Darkest a shaded pixel may get, so dark outlines still pick up some of the tint. */
    private static final float MIN_BRIGHTNESS = 0.3f;

    public record Mask(Identifier texture, int textureWidth, int textureHeight, GuiSpriteScaling.NineSlice slice) {
        public GuiSpriteScaling.NineSlice.Border border() {
            return slice.border();
        }
    }

    private static final Map<Identifier, Optional<Mask>> CACHE = new HashMap<>();
    private static int nextId;

    private FrameMasks() {}

    /** @param sprite a GUI sprite id, e.g. {@code minecraft:tooltip/mythic_frame} */
    public static Optional<Mask> get(Identifier sprite) {
        return CACHE.computeIfAbsent(sprite, FrameMasks::load);
    }

    /** Releases every mask texture; called on resource reload. */
    public static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (Optional<Mask> mask : CACHE.values()) mask.ifPresent(m -> textures.release(m.texture()));
        CACHE.clear();
    }

    /** Draws the mask 9-sliced over the given rectangle (stretching edges and centre, like the pack's sprites). */
    public static void blitNineSlice(GuiGraphics graphics, Mask mask, int x, int y, int width, int height, int color) {
        GuiSpriteScaling.NineSlice slice = mask.slice();
        GuiSpriteScaling.NineSlice.Border border = slice.border();
        float texPerUnitX = (float) mask.textureWidth() / slice.width();
        float texPerUnitY = (float) mask.textureHeight() / slice.height();

        int[] dstX = {0, Math.min(border.left(), width / 2), width - Math.min(border.right(), width / 2), width};
        int[] dstY = {0, Math.min(border.top(), height / 2), height - Math.min(border.bottom(), height / 2), height};
        int[] srcX = {0, border.left(), slice.width() - border.right(), slice.width()};
        int[] srcY = {0, border.top(), slice.height() - border.bottom(), slice.height()};

        for (int row = 0; row < 3; row++) {
            int dh = dstY[row + 1] - dstY[row];
            int sh = srcY[row + 1] - srcY[row];
            if (dh <= 0 || sh <= 0) continue;
            for (int col = 0; col < 3; col++) {
                int dw = dstX[col + 1] - dstX[col];
                int sw = srcX[col + 1] - srcX[col];
                if (dw <= 0 || sw <= 0) continue;
                graphics.blit(RenderPipelines.GUI_TEXTURED, mask.texture(),
                        x + dstX[col], y + dstY[row],
                        srcX[col] * texPerUnitX, srcY[row] * texPerUnitY,
                        dw, dh,
                        Math.round(sw * texPerUnitX), Math.round(sh * texPerUnitY),
                        mask.textureWidth(), mask.textureHeight(), color);
            }
        }
    }

    private static Optional<Mask> load(Identifier sprite) {
        Identifier file = sprite.withPath(path -> "textures/gui/sprites/" + path + ".png");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
        if (resource.isEmpty()) return Optional.empty();

        try {
            Optional<GuiMetadataSection> meta = resource.get().metadata().getSection(GuiMetadataSection.TYPE);
            if (meta.isEmpty() || !(meta.get().scaling() instanceof GuiSpriteScaling.NineSlice slice)) {
                return Optional.empty();
            }

            NativeImage mask;
            try (InputStream in = resource.get().open(); NativeImage source = NativeImage.read(in)) {
                int width = source.getWidth();
                // Animated (shiny) sprites stack their frames vertically; only the first one is needed.
                int height = Math.min(source.getHeight(), Math.round(width * (float) slice.height() / slice.width()));

                float maxLuminance = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = source.getPixel(x, y);
                        if (ARGB.alpha(argb) > 0) maxLuminance = Math.max(maxLuminance, luminance(argb));
                    }
                }
                if (maxLuminance <= 0) maxLuminance = 1;

                mask = new NativeImage(width, height, false);
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int argb = source.getPixel(x, y);
                        int alpha = ARGB.alpha(argb);
                        if (alpha == 0) {
                            mask.setPixel(x, y, 0);
                            continue;
                        }
                        float brightness = MIN_BRIGHTNESS + (1 - MIN_BRIGHTNESS) * luminance(argb) / maxLuminance;
                        int v = Math.round(255 * Math.min(1f, brightness));
                        mask.setPixel(x, y, ARGB.color(alpha, v, v, v));
                    }
                }
            }

            Identifier texture = Identifier.fromNamespaceAndPath(WynnWeaponViewer.MOD_ID, "card_frame_mask/" + nextId++);
            Minecraft.getInstance().getTextureManager()
                    .register(texture, new DynamicTexture(() -> "Card frame mask for " + sprite, mask));
            return Optional.of(new Mask(texture, mask.getWidth(), mask.getHeight(), slice));
        } catch (IOException | RuntimeException e) {
            WynnWeaponViewer.LOGGER.warn("Couldn't build a frame mask from {}", file, e);
            return Optional.empty();
        }
    }

    private static float luminance(int argb) {
        return 0.299f * ARGB.red(argb) + 0.587f * ARGB.green(argb) + 0.114f * ARGB.blue(argb);
    }
}
