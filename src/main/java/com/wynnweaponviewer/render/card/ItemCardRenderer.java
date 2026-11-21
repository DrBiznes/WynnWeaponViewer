package com.wynnweaponviewer.render.card;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Draws an item card from a {@link CardLayout}.
 *
 * <p>Drawing is split in two so the 3D model (a picture-in-picture texture) can sit between them:
 * {@link #renderBackground} (frame + model window) → caller submits the model into {@link #windowRect} →
 * {@link #renderText}. Minecraft's GUI renderer stacks anything that overlaps earlier content on a higher layer,
 * so submitting in that order is enough to get frame → model → text.
 *
 * <p>All card content is laid out in tooltip pixels and drawn under one uniform scale that is snapped so each
 * tooltip pixel covers a whole number of screen pixels, keeping Wynncraft's pixel fonts crisp.
 */
public final class ItemCardRenderer {

    private static final int WINDOW_FILL = 0x38000000;
    private static final int WINDOW_BORDER_ALPHA = 0x60;

    private ItemCardRenderer() {}

    /** Where the card's outer frame sits on screen: top-left corner in GUI coordinates and the uniform scale. */
    public record Placement(float left, float top, float scale) {
        public float width(CardLayout layout) {
            return layout.outerWidth() * scale;
        }

        public float height(CardLayout layout) {
            return layout.outerHeight() * scale;
        }
    }

    /**
     * Largest scale <= {@code desired} that maps one tooltip pixel to a whole number of screen pixels
     * (never below 1 screen pixel per tooltip pixel).
     */
    public static float snapScale(float desired) {
        int guiScale = guiScale();
        int screenPixels = Math.max(1, (int) Math.floor(desired * guiScale));
        return (float) screenPixels / guiScale;
    }

    /** Rounds a GUI coordinate to the physical pixel grid so glyph edges don't land between pixels. */
    public static float snapToPixel(float guiCoordinate) {
        int guiScale = guiScale();
        return Math.round(guiCoordinate * guiScale) / (float) guiScale;
    }

    public static void renderBackground(GuiGraphics graphics, CardLayout layout, Placement placement, float alpha) {
        if (alpha <= 0.02f) return;
        beginContent(graphics, placement);

        float time = (Util.getMillis() % 3_600_000L) / 1000f;
        CardFrameRenderer.render(graphics, 0, 0, layout.contentWidth(), layout.contentHeight(),
                layout.frameStyle(), layout.frameEffect(), alpha, time);

        // Model window: a soft dark inset with a faint border in the rarity colour (or matching the special frames).
        int x0 = layout.windowX();
        int y0 = layout.windowY();
        int x1 = x0 + layout.windowWidth();
        int y1 = y0 + layout.windowHeight();
        graphics.fill(x0, y0, x1, y1, ARGB.multiplyAlpha(WINDOW_FILL, alpha));
        int accent = switch (layout.frameEffect()) {
            case PERFECT -> Mth.hsvToRgb(Mth.frac(-time / 4f), 0.7f, 1f);
            case DEFECTIVE -> 0x777777;
            case NONE -> layout.accentColor();
        };
        int border = ARGB.color(Math.round(WINDOW_BORDER_ALPHA * alpha), accent & 0xFFFFFF);
        graphics.fill(x0, y0, x1, y0 + 1, border);
        graphics.fill(x0, y1 - 1, x1, y1, border);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, border);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, border);

        graphics.pose().popMatrix();
    }

    /** The model window in GUI coordinates: {x0, y0, x1, y1}. */
    public static float[] windowRect(CardLayout layout, Placement placement) {
        // Same origin as beginContent(), so the model lines up with the window drawn by renderBackground().
        float originX = snapToPixel(placement.left()) + CardFrameRenderer.FRAME_INSET * placement.scale();
        float originY = snapToPixel(placement.top()) + CardFrameRenderer.FRAME_INSET * placement.scale();
        float s = placement.scale();
        return new float[] {
                originX + layout.windowX() * s,
                originY + layout.windowY() * s,
                originX + (layout.windowX() + layout.windowWidth()) * s,
                originY + (layout.windowY() + layout.windowHeight()) * s
        };
    }

    /**
     * Draws the header and body. Lines cascade in top to bottom: each fades in over {@link #LINE_FADE} while rising
     * a few pixels into place, starting a little after the line above. The whole card is settled about a quarter of
     * a second after {@code revealTime} reaches 0 - quick, since players flick through inventories fast.
     *
     * @param revealTime seconds since the text started appearing (negative: not yet)
     * @param stagger    false: no animation, every line is simply there once {@code revealTime} passes 0
     */
    public static void renderText(GuiGraphics graphics, CardLayout layout, Placement placement, float alpha,
                                  float revealTime, boolean stagger) {
        if (alpha <= 0.02f || revealTime <= 0) return;
        Font font = Minecraft.getInstance().font;
        beginContent(graphics, placement);

        renderHeaderLines(graphics, font, layout.headerLines(), alpha, revealTime, stagger);

        int rowY = layout.bodyY();
        for (int i = 0; i < layout.body().size(); i++) {
            CardRow row = layout.body().get(i);
            float reveal = stagger ? reveal(revealTime, Math.min(BODY_START + i * ROW_STEP, LAST_START)) : 1f;
            if (reveal > 0) {
                row.render(graphics, font, rowY + (stagger ? rise(reveal) : 0), layout.contentWidth(), alpha * reveal);
            }
            rowY += row.height();
        }

        graphics.pose().popMatrix();
    }

    /**
     * Card-less mode: only the header lines, with their origin at (left, top) in GUI coordinates and no frame.
     * Same cascade as {@link #renderText}.
     */
    public static void renderFloatingHeader(GuiGraphics graphics, FloatingHeader header, float left, float top,
                                            float scale, float alpha, float revealTime, boolean stagger) {
        if (alpha <= 0.02f || revealTime <= 0) return;
        graphics.pose().pushMatrix();
        graphics.pose().translate(snapToPixel(left), snapToPixel(top));
        graphics.pose().scale(scale, scale);
        renderHeaderLines(graphics, Minecraft.getInstance().font, header.lines(), alpha, revealTime, stagger);
        graphics.pose().popMatrix();
    }

    /** Header lines laid out like a vanilla tooltip, from y = 0 of the current transform. */
    private static void renderHeaderLines(GuiGraphics graphics, Font font, List<Component> lines, float alpha,
                                          float revealTime, boolean stagger) {
        int y = 0;
        for (int i = 0; i < lines.size(); i++) {
            float reveal = stagger ? reveal(revealTime, i * HEADER_STEP) : 1f;
            if (reveal > 0) {
                graphics.drawString(font, lines.get(i), 0, y + (stagger ? rise(reveal) : 0),
                        ARGB.white(alpha * reveal), true);
            }
            y += CardLayout.LINE_HEIGHT + (i == 0 ? CardLayout.TITLE_GAP : 0);
        }
    }

    // Cascade timing, in seconds.
    private static final float LINE_FADE = 0.09f;
    private static final float HEADER_STEP = 0.02f;
    private static final float BODY_START = 0.05f;
    private static final float ROW_STEP = 0.012f;
    /** No line waits longer than this, so cards with many IDs don't drag. */
    private static final float LAST_START = 0.16f;
    /** How far (tooltip pixels) a line rises while it fades in. */
    private static final int RISE = 3;

    /** 0-1 fade for things drawn after the card's last line (the share hint): it comes in as the text settles. */
    public static float trailingReveal(float revealTime, boolean stagger) {
        if (revealTime <= 0) return 0f;
        return stagger ? reveal(revealTime, LAST_START + LINE_FADE) : 1f;
    }

    /** 0-1 eased progress of a line that starts at {@code start}. */
    private static float reveal(float time, float start) {
        float t = Math.clamp((time - start) / LINE_FADE, 0f, 1f);
        float inv = 1 - t;
        return 1 - inv * inv * inv;
    }

    /** Whole-pixel offset so text stays crisp while it moves. */
    private static int rise(float reveal) {
        return Math.round((1 - reveal) * RISE);
    }

    /** Pushes a transform whose origin is the content box's top-left, in tooltip pixels. Caller pops. */
    private static void beginContent(GuiGraphics graphics, Placement placement) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(snapToPixel(placement.left()), snapToPixel(placement.top()));
        graphics.pose().scale(placement.scale(), placement.scale());
        graphics.pose().translate(CardFrameRenderer.FRAME_INSET, CardFrameRenderer.FRAME_INSET);
    }

    private static int guiScale() {
        return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }
}
