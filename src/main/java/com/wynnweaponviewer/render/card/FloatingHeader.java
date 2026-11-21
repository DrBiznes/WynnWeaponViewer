package com.wynnweaponviewer.render.card;

import com.wynnweaponviewer.card.ItemCardData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Card-less mode's text: just the card's header (emblem, name + overall roll, rarity/type/element/major ID badges),
 * floating under the model with no frame, model window or body. Measured in tooltip pixels like {@link CardLayout}.
 *
 * @param width    visual width, including {@code overhang}
 * @param overhang how far the lines draw left of their origin (Wynncraft's emblem starts with a negative advance)
 */
public record FloatingHeader(List<Component> lines, int width, int height, int overhang) {

    /** {@link WynncraftSkin}'s emblem line opens with a -16px space, so the emblem reaches 16px left of x = 0. */
    private static final int EMBLEM_OVERHANG = 16;

    public static FloatingHeader build(ItemCardData data) {
        CardSkin skin = CardSkin.current();
        List<Component> lines = skin.header(data);
        Font font = Minecraft.getInstance().font;

        int lineWidth = 0;
        for (Component line : lines) lineWidth = Math.max(lineWidth, font.width(line));
        int overhang = skin == WynncraftSkin.INSTANCE ? EMBLEM_OVERHANG : 0;
        int height = lines.size() * CardLayout.LINE_HEIGHT + (lines.size() > 1 ? CardLayout.TITLE_GAP : 0);
        return new FloatingHeader(lines, lineWidth + overhang, height, overhang);
    }
}
