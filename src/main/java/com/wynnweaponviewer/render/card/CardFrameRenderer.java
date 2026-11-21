package com.wynnweaponviewer.render.card;

import com.wynnweaponviewer.card.ItemCardData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Draws the item's tooltip frame (Wynncraft's per-rarity 9-slice {@code tooltip/<style>_background|_frame} sprites).
 *
 * <p>Same geometry as vanilla {@code TooltipRenderUtil.renderTooltipBackground} (3px padding + 9px margin around the
 * content box), but with an alpha so the frame can fade with the card.
 *
 * <p>Shiny items need nothing extra: their {@code _shiny} sprites are animated by the pack itself. Perfect and
 * defective rolls get a frame of their own ({@link Effect}): a white copy of the item's own frame
 * ({@link FrameMasks}), tinted strip by strip around the border.
 */
public final class CardFrameRenderer {

    /** Distance the frame extends past the content box on every side. */
    public static final int FRAME_INSET = 3 + 9;

    private static final Identifier DEFAULT_BACKGROUND = Identifier.withDefaultNamespace("tooltip/background");
    private static final Identifier DEFAULT_FRAME = Identifier.withDefaultNamespace("tooltip/frame");
    /** Wynncraft's common-tier frame: white with grey shading, so it takes any tint. */
    private static final Identifier NEUTRAL_FRAME = Identifier.withDefaultNamespace("tooltip/normal_frame");
    private static final Identifier NEUTRAL_BACKGROUND = Identifier.withDefaultNamespace("tooltip/normal_background");

    /** Extra depth past the sprite's 9-slice border that each tinted band covers. */
    private static final int BAND_SLACK = 2;
    /** Length of one tinted strip along the border, in tooltip pixels. */
    private static final int STRIP = 6;

    /** Seconds for the rainbow to travel once around the card. */
    private static final float RAINBOW_PERIOD = 4f;
    private static final float RAINBOW_SATURATION = 0.7f;
    /** The defective frame's static re-rolls this many times a second... */
    private static final float STATIC_RATE = 7f;
    /** ...and every this many seconds it dims briefly, like a failing light. */
    private static final float FLICKER_PERIOD = 2.7f;
    private static final float FLICKER_LENGTH = 0.12f;

    public enum Effect {
        NONE,
        /** 100% overall roll: a rainbow that flows around the border. */
        PERFECT,
        /** 0% overall roll: the opposite - colour drained, grey static that jitters and flickers. */
        DEFECTIVE;

        /** Debug override ({@code /wwb debug frame}); null = decide from the item's roll. */
        @Nullable
        public static Effect forced;

        static Effect of(ItemCardData data, boolean pack) {
            // The tinted frames are built from the Wynncraft pack's white frame sprite.
            if (!pack) return NONE;
            if (forced != null) return forced;
            if (data.overall().perfect()) return PERFECT;
            if (data.overall().defective()) return DEFECTIVE;
            return NONE;
        }
    }

    private CardFrameRenderer() {}

    /**
     * @param x,y,width,height the content box (text area), not the outer frame
     * @param style            the item's {@code minecraft:tooltip_style}, or null for the default tooltip
     * @param time             seconds, for the animated effects
     */
    public static void render(GuiGraphics graphics, int x, int y, int width, int height,
                              @Nullable Identifier style, Effect effect, float alpha, float time) {
        if (alpha <= 0) return;
        int color = ARGB.white(alpha);

        int fx = x - FRAME_INSET;
        int fy = y - FRAME_INSET;
        int fw = width + FRAME_INSET * 2;
        int fh = height + FRAME_INSET * 2;

        Identifier background = effect == Effect.DEFECTIVE ? NEUTRAL_BACKGROUND : backgroundSprite(style);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, background, fx, fy, fw, fh, color);

        Identifier frame = frameSprite(style);
        Optional<FrameMasks.Mask> mask = effect == Effect.NONE ? Optional.empty()
                : FrameMasks.get(frame).or(() -> FrameMasks.get(NEUTRAL_FRAME));
        if (mask.isPresent()) {
            renderTintedFrame(graphics, mask.get(), fx, fy, fw, fh, effect, alpha, time);
        } else {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, frame, fx, fy, fw, fh, color);
        }
    }

    /**
     * Draws a white copy of the item's own frame ({@link FrameMasks}) once per strip of the border, each clipped to
     * its strip and tinted by its position around the perimeter (clockwise from the top-left corner), so the effect
     * follows the frame's real shape. Strips never overlap, so the GUI renderer keeps them on one layer.
     */
    private static void renderTintedFrame(GuiGraphics graphics, FrameMasks.Mask mask, int fx, int fy, int fw, int fh,
                                          Effect effect, float alpha, float time) {
        // Each band is as deep as the sprite's 9-slice border on that side (plus a little slack for ornaments).
        var border = mask.border();
        int top = Math.min(border.top() + BAND_SLACK, fh / 2);
        int bottom = Math.min(border.bottom() + BAND_SLACK, fh / 2);
        int left = Math.min(border.left() + BAND_SLACK, fw / 2);
        int right = Math.min(border.right() + BAND_SLACK, fw / 2);

        float perimeter = 2f * (fw + fh);
        float dim = effect == Effect.DEFECTIVE && Mth.positiveModulo(time, FLICKER_PERIOD) < FLICKER_LENGTH ? 0.45f : 1f;
        int index = 0;

        // Top and bottom bands span the full width (and so own the corners); the sides fill the gap between them.
        for (int sx = 0; sx < fw; sx += STRIP, index++) {
            int ex = Math.min(fw, sx + STRIP);
            float mid = (sx + ex) * 0.5f;
            int topColor = tint(effect, mid / perimeter, index, time, alpha * dim);
            strip(graphics, mask, fx, fy, fw, fh, fx + sx, fy, fx + ex, fy + top, topColor);
            int bottomColor = tint(effect, (fw + fh + (fw - mid)) / perimeter, index + 4096, time, alpha * dim);
            strip(graphics, mask, fx, fy, fw, fh, fx + sx, fy + fh - bottom, fx + ex, fy + fh, bottomColor);
        }
        for (int sy = top; sy < fh - bottom; sy += STRIP, index++) {
            int ey = Math.min(fh - bottom, sy + STRIP);
            float mid = (sy + ey) * 0.5f;
            int rightColor = tint(effect, (fw + mid) / perimeter, index, time, alpha * dim);
            strip(graphics, mask, fx, fy, fw, fh, fx + fw - right, fy + sy, fx + fw, fy + ey, rightColor);
            int leftColor = tint(effect, (2f * fw + fh + (fh - mid)) / perimeter, index + 4096, time, alpha * dim);
            strip(graphics, mask, fx, fy, fw, fh, fx, fy + sy, fx + left, fy + ey, leftColor);
        }
    }

    private static void strip(GuiGraphics graphics, FrameMasks.Mask mask, int fx, int fy, int fw, int fh,
                              int x0, int y0, int x1, int y1, int color) {
        graphics.enableScissor(x0, y0, x1, y1);
        FrameMasks.blitNineSlice(graphics, mask, fx, fy, fw, fh, color);
        graphics.disableScissor();
    }

    /**
     * @param position 0-1 around the perimeter
     * @param strip    a stable id for the strip (for the defective static)
     */
    private static int tint(Effect effect, float position, int strip, float time, float alpha) {
        int rgb;
        if (effect == Effect.PERFECT) {
            float hue = Mth.frac(position - time / RAINBOW_PERIOD);
            rgb = Mth.hsvToRgb(hue, RAINBOW_SATURATION, 1f) & 0xFFFFFF;
        } else {
            float noise = noise(strip, (int) Math.floor(time * STATIC_RATE));
            int grey = noise < 0.08f ? 0x22 : 0x48 + Math.round(noise * 0x50);
            rgb = (grey << 16) | (grey << 8) | grey;
        }
        return ARGB.color(Math.round(255 * Math.clamp(alpha, 0f, 1f)), rgb);
    }

    /** Cheap deterministic hash → [0, 1). */
    private static float noise(int strip, int tick) {
        int h = strip * 0x27d4eb2d ^ tick * 0x165667b1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return (h & 0xFFFF) / 65536f;
    }

    private static Identifier backgroundSprite(@Nullable Identifier style) {
        return style == null ? DEFAULT_BACKGROUND : style.withPath(path -> "tooltip/" + path + "_background");
    }

    private static Identifier frameSprite(@Nullable Identifier style) {
        return style == null ? DEFAULT_FRAME : style.withPath(path -> "tooltip/" + path + "_frame");
    }
}
