package com.wynnweaponviewer.render.card;

import com.wynntils.core.text.fonts.CommonFonts;
import com.wynntils.core.text.fonts.FontManager;
import com.wynntils.core.text.fonts.type.TooltipBannerIcon;
import com.wynntils.handlers.tooltip.type.ItemEmblemSprite;
import com.wynntils.models.elements.type.Element;
import com.wynntils.models.gear.type.GearRestrictions;
import com.wynntils.models.gear.type.GearType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Single place for the Wynncraft server-pack fonts / glyphs the card uses.
 *
 * <p>All fonts live in the Wynncraft resource pack (namespace {@code minecraft}). We never ship or copy those assets;
 * we only reference them, and fall back to a plain skin when the pack isn't loaded ({@link #isPackActive()}).
 */
public final class WynnGlyphs {

    /** A font file that only exists in Wynncraft's pack; its presence tells us the tooltip glyphs will render. */
    private static final Identifier PACK_PROBE = Identifier.withDefaultNamespace("font/tooltip/emblem/frame.json");
    private static final long PACK_CHECK_INTERVAL_MS = 2000;

    /** Colour Wynncraft's text shader expects for untinted emblem sprites (mirrors Wynntils' TooltipBuilder). */
    public static final int EMBLEM_SPRITE_COLOR = 0x00eb1c;
    /** Restriction (untradable / quest) badge background, mirrors Wynntils' TooltipBuilder. */
    public static final int RESTRICTION_BADGE_COLOR = 0xff4242;

    private static boolean packActive;
    private static long lastPackCheckMs;

    private WynnGlyphs() {}

    public static boolean isPackActive() {
        long now = System.currentTimeMillis();
        if (now - lastPackCheckMs > PACK_CHECK_INTERVAL_MS) {
            lastPackCheckMs = now;
            packActive = Minecraft.getInstance().getResourceManager().getResource(PACK_PROBE).isPresent();
        }
        return packActive;
    }

    // --- text ------------------------------------------------------------------------------------------------

    public static MutableComponent glyph(String text, FontDescription font) {
        return Component.literal(text).withStyle(Style.EMPTY.withFont(font)).withoutShadow();
    }

    /** Horizontal offset of {@code pixels} (negative moves left) using Wynncraft's space font. */
    public static MutableComponent space(int pixels) {
        // Wynncraft's space font maps codepoint U+D0000 + n to an advance of n pixels (n may be negative).
        // Small offsets use that directly (identical to what Wynntils emits); larger ones let Wynntils combine glyphs.
        String text = Math.abs(pixels) <= 64
                ? new String(Character.toChars(0xD0000 + pixels))
                : FontManager.calculateOffset(0, pixels);
        return Component.literal(text).withStyle(Style.EMPTY.withFont(CommonFonts.SPACE_FONT));
    }

    /** Text in Wynncraft's pixel font, falling back to the default font if the pack is missing. */
    public static Style wynnTextStyle() {
        return Style.EMPTY.withFont(CommonFonts.WYNNTILS_LANGUAGE_WYNNCRAFT_FONT);
    }

    // --- lookups ---------------------------------------------------------------------------------------------

    public static ItemEmblemSprite emblemSprite(GearType type) {
        return switch (type) {
            case HELMET -> ItemEmblemSprite.HELMET;
            case CHESTPLATE -> ItemEmblemSprite.CHESTPLATE;
            case LEGGINGS -> ItemEmblemSprite.LEGGINGS;
            case BOOTS -> ItemEmblemSprite.BOOTS;
            case BOW -> ItemEmblemSprite.BOW;
            case DAGGER -> ItemEmblemSprite.DAGGER;
            case WAND -> ItemEmblemSprite.WAND;
            case RELIK -> ItemEmblemSprite.RELIK;
            case SPEAR -> ItemEmblemSprite.SPEAR;
            case RING -> ItemEmblemSprite.RING;
            case BRACELET -> ItemEmblemSprite.BRACELET;
            case NECKLACE -> ItemEmblemSprite.NECKLACE;
            default -> ItemEmblemSprite.CHESTPLATE;
        };
    }

    public static TooltipBannerIcon elementIcon(Element element) {
        return switch (element) {
            case EARTH -> TooltipBannerIcon.EARTH;
            case THUNDER -> TooltipBannerIcon.THUNDER;
            case WATER -> TooltipBannerIcon.WATER;
            case FIRE -> TooltipBannerIcon.FIRE;
            case AIR -> TooltipBannerIcon.AIR;
        };
    }

    @Nullable
    public static TooltipBannerIcon restrictionIcon(GearRestrictions restriction) {
        return switch (restriction) {
            case NONE -> null;
            case UNTRADABLE -> TooltipBannerIcon.UNTRADABLE;
            case QUEST_ITEM -> TooltipBannerIcon.QUEST;
        };
    }
}
