package com.wynnweaponviewer.render.card;

import com.wynnweaponviewer.card.ItemCardData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Turns card data into drawable lines. {@link WynncraftSkin} uses the server pack's glyph fonts and per-rarity
 * frames; {@link PlainSkin} is the fallback when that pack isn't loaded.
 */
public interface CardSkin {

    /**
     * Header lines laid out like a vanilla tooltip: the first line is followed by a 2px gap, every line is 10px.
     * Lines may contain alignment glyphs, so they must never be re-wrapped, only uniformly scaled.
     */
    List<Component> header(ItemCardData data);

    /** Tooltip style for the frame, or null for the default tooltip frame. */
    @Nullable
    Identifier frameStyle(ItemCardData data);

    static CardSkin current() {
        return WynnGlyphs.isPackActive() ? WynncraftSkin.INSTANCE : PlainSkin.INSTANCE;
    }
}
