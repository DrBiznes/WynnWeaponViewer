package com.wynnweaponviewer.render.card;

import com.wynntils.core.text.fonts.CommonFonts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;

/**
 * One horizontal strip of the card body, laid out in tooltip pixels. Rows are built by {@link CardBody} from card
 * data; they only know how to measure and draw themselves.
 */
public sealed interface CardRow {

    int height();

    /** Draws the row with its top-left at (0, y) in content coordinates, spanning {@code width}. */
    void render(GuiGraphics graphics, Font font, int y, int width, float alpha);

    // --- shared drawing helpers --------------------------------------------------------------------------------

    static int withAlpha(int rgb, float alpha) {
        return ARGB.color(Math.round(255 * alpha), rgb & 0xFFFFFF);
    }

    static void text(GuiGraphics graphics, Font font, Component text, int x, int y, float alpha) {
        graphics.drawString(font, text, x, y, ARGB.white(alpha), true);
    }

    static void textRight(GuiGraphics graphics, Font font, Component text, int right, int y, float alpha) {
        text(graphics, font, text, right - font.width(text), y, alpha);
    }

    static MutableComponent wynn(String text, int rgb) {
        return Component.literal(text).withStyle(WynnGlyphs.wynnTextStyle().withColor(rgb & 0xFFFFFF));
    }

    // --- rows --------------------------------------------------------------------------------------------------

    /** Wynncraft's ◇ divider line, tinted with the tier's divider colour (plain 1px rule without the pack). */
    record Divider(int color, boolean pack) implements CardRow {
        @Override
        public int height() {
            return CardLayout.LINE_HEIGHT;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int y, int width, float alpha) {
            if (pack) {
                Component glyph = Component.literal("")
                        .withStyle(Style.EMPTY.withFont(CommonFonts.TOOLTIP_DIVIDER_FONT).withColor(color & 0xFFFFFF))
                        .withoutShadow();
                graphics.drawString(font, glyph, (width - font.width(glyph)) / 2, y, ARGB.white(alpha), false);
            } else {
                graphics.fill(8, y + 4, width - 8, y + 5, withAlpha(color, 0.6f * alpha));
            }
        }
    }

    /**
     * One identification: name on the left, value + roll % on the right, and a 1px roll bar along the bottom
     * filled to the roll and tinted with the user's Wynntils roll colour.
     */
    record Identification(Component name, Component value, Component percent, float rollFraction, int rollColor,
                          boolean showBar) implements CardRow {
        private static final int GAP = 4;
        private static final int BAR_TRACK = 0x40000000;
        /** Text is 9px tall with its shadow; the bar sits 2px below that and leaves 3px before the next row. */
        private static final int BAR_OFFSET = 11;

        @Override
        public int height() {
            return 15;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int y, int width, float alpha) {
            int right = width;
            if (percent != null) {
                textRight(graphics, font, percent, right, y, alpha);
                right -= font.width(percent) + GAP;
            }
            textRight(graphics, font, value, right, y, alpha);
            right -= font.width(value) + GAP + 2;

            // Long stat names are cut with an ellipsis rather than wrapped, so every row stays one line.
            int maxNameWidth = Math.max(0, right);
            if (font.width(name) <= maxNameWidth) {
                text(graphics, font, name, 0, y, alpha);
            } else {
                String ellipsis = "…";
                int room = Math.max(0, maxNameWidth - font.width(ellipsis));
                FormattedCharSequence cut = Language.cut(font, name, room);
                graphics.drawString(font, cut, 0, y, ARGB.white(alpha), true);
                graphics.drawString(font, wynn(ellipsis, 0xAAAAAA), font.width(cut), y, ARGB.white(alpha), true);
            }

            if (showBar) {
                int barY = y + BAR_OFFSET;
                graphics.fill(0, barY, width, barY + 1, ARGB.multiplyAlpha(BAR_TRACK, alpha));
                int filled = Math.round(width * Math.clamp(rollFraction, 0f, 1f));
                if (filled > 0) graphics.fill(0, barY, filled, barY + 1, withAlpha(rollColor, 0.85f * alpha));
            }
        }
    }

    /** "+N more" when identifications had to be capped to fit the screen. */
    record More(Component text) implements CardRow {
        @Override
        public int height() {
            return CardLayout.LINE_HEIGHT;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int y, int width, float alpha) {
            CardRow.text(graphics, font, text, (width - font.width(text)) / 2, y, alpha);
        }
    }

    /** Anything left/right aligned on one line: powders + rerolls, scales, shiny stat. */
    record Split(Component left, Component right) implements CardRow {
        @Override
        public int height() {
            return 11;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, int y, int width, float alpha) {
            if (left != null) text(graphics, font, left, 0, y, alpha);
            if (right != null) textRight(graphics, font, right, width, y, alpha);
        }
    }

    record Spacer(int height) implements CardRow {
        @Override
        public void render(GuiGraphics graphics, Font font, int y, int width, float alpha) {}
    }

    /** Helpers for cutting styled text to a width. */
    final class Language {
        private Language() {}

        /** Character-level cut (not word wrap), measured with each part's own font. */
        static FormattedCharSequence cut(Font font, Component text, int width) {
            return net.minecraft.locale.Language.getInstance()
                    .getVisualOrder(font.substrByWidth(text, Math.max(0, width)));
        }
    }
}
