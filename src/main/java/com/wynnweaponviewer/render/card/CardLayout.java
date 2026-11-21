package com.wynnweaponviewer.render.card;

import com.wynnweaponviewer.card.ItemCardData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The card's geometry in "tooltip pixels" (1 unit = 1 pixel of a vanilla tooltip at scale 1), independent of where
 * or how large it ends up on screen. Width is fixed so every card - on screen or, later, shared - has the same shape.
 *
 * <pre>
 *   content (0,0) ┌──────────────────────────┐
 *                 │ header lines             │  headerHeight
 *                 │ ┌──────────────────────┐ │  ← window (the 3D model): its height flexes between
 *                 │ │                      │ │    MIN_WINDOW_ASPECT and MAX_WINDOW_ASPECT × width to fit the screen
 *                 │ └──────────────────────┘ │
 *                 │ ◇ IDs with roll bars     │  ← body rows (CardBody), starting at bodyY
 *                 │ ◇ powders / shiny / scale│
 *                 └──────────────────────────┘  contentHeight
 * </pre>
 * The frame extends {@link CardFrameRenderer#FRAME_INSET} past the content box on every side.
 * {@link #build} lays the card out with the tallest window; {@link #withWindowHeight} shrinks it.
 */
public record CardLayout(
        CardBody.Density density,
        List<Component> headerLines,
        int contentWidth,
        int headerHeight,
        int windowX,
        int windowY,
        int windowWidth,
        int windowHeight,
        List<CardRow> body,
        int bodyY,
        int contentHeight,
        @Nullable Identifier frameStyle,
        CardFrameRenderer.Effect frameEffect,
        int accentColor) {

    /** Fixed card width (content box). Grows only if a header line is wider, e.g. a very long item name. */
    public static final int CONTENT_WIDTH = 168;
    private static final int HEADER_GAP = 5;
    private static final int BODY_GAP = 3;

    /** Model window height as a multiple of its width: the tallest it gets, and the least it may shrink to. */
    public static final float MAX_WINDOW_ASPECT = 1.25f;
    public static final float MIN_WINDOW_ASPECT = 0.55f;

    /** Vanilla tooltip line metrics: every line is 10px, the first line gets an extra 2px gap below it. */
    public static final int LINE_HEIGHT = 10;
    public static final int TITLE_GAP = 2;

    /**
     * Outer height of a typical card (3 header lines, square window, 8 IDs, divider + 2 footer rows). The on-screen
     * scale is chosen from this rather than from each item, so every card comes out the same size.
     */
    public static final int REFERENCE_OUTER_HEIGHT = 3 * LINE_HEIGHT + TITLE_GAP + HEADER_GAP + CONTENT_WIDTH
            + BODY_GAP + LINE_HEIGHT + 8 * 15 + LINE_HEIGHT + 2 * 11 + 1 + CardFrameRenderer.FRAME_INSET * 2;
    public static final int REFERENCE_OUTER_WIDTH = CONTENT_WIDTH + CardFrameRenderer.FRAME_INSET * 2;

    public static CardLayout build(ItemCardData data, CardBody.Density density) {
        CardSkin skin = CardSkin.current();
        boolean pack = WynnGlyphs.isPackActive();
        List<Component> lines = skin.header(data);
        Font font = Minecraft.getInstance().font;

        int headerWidth = 0;
        for (Component line : lines) headerWidth = Math.max(headerWidth, font.width(line));
        int width = Math.max(CONTENT_WIDTH, headerWidth);

        int headerHeight = lines.size() * LINE_HEIGHT + (lines.size() > 1 ? TITLE_GAP : 0);
        int windowY = headerHeight + HEADER_GAP;
        int windowHeight = Math.round(width * MAX_WINDOW_ASPECT);

        List<CardRow> body = CardBody.build(data, density, pack);
        int bodyY = windowY + windowHeight + BODY_GAP;
        int bodyHeight = 0;
        for (CardRow row : body) bodyHeight += row.height();
        int contentHeight = body.isEmpty() ? windowY + windowHeight : bodyY + bodyHeight;

        return new CardLayout(density, lines, width, headerHeight, 0, windowY, width, windowHeight,
                body, bodyY, contentHeight, skin.frameStyle(data), CardFrameRenderer.Effect.of(data, pack),
                data.tier().getSecondaryColor().asInt());
    }

    /** Same card with the model window resized (clamped to the allowed aspect range); body moves with it. */
    public CardLayout withWindowHeight(int height) {
        int clamped = Math.clamp(height, minWindowHeight(), maxWindowHeight());
        if (clamped == windowHeight) return this;
        int delta = clamped - windowHeight;
        return new CardLayout(density, headerLines, contentWidth, headerHeight, windowX, windowY, windowWidth, clamped,
                body, bodyY + delta, contentHeight + delta, frameStyle, frameEffect, accentColor);
    }

    public int minWindowHeight() {
        return Math.round(windowWidth * MIN_WINDOW_ASPECT);
    }

    public int maxWindowHeight() {
        return Math.round(windowWidth * MAX_WINDOW_ASPECT);
    }

    /** Outer height with the window excluded - what the rest of the card needs no matter how tall the model is. */
    public int outerHeightWithoutWindow() {
        return outerHeight() - windowHeight;
    }

    public int outerWidth() {
        return contentWidth + CardFrameRenderer.FRAME_INSET * 2;
    }

    public int outerHeight() {
        return contentHeight + CardFrameRenderer.FRAME_INSET * 2;
    }
}
