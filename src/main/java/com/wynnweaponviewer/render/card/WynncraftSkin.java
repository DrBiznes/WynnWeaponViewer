package com.wynnweaponviewer.render.card;

import com.wynnweaponviewer.card.ItemCardData;
import com.wynntils.core.components.Handlers;
import com.wynntils.core.text.fonts.CommonFonts;
import com.wynntils.core.text.fonts.type.TooltipBannerIcon;
import com.wynntils.core.text.fonts.wynnfonts.BannerBoxFont;
import com.wynntils.core.text.fonts.wynnfonts.TooltipBannerFont;
import com.wynntils.handlers.tooltip.type.TooltipOptions;
import com.wynntils.models.elements.type.Element;
import com.wynntils.utils.colors.CommonColors;
import com.wynntils.utils.colors.CustomColor;
import com.wynntils.utils.mc.ComponentUtils;
import com.wynntils.utils.wynn.ColorScaleUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Wynncraft-native look: emblem + name, rarity/type/element badges, all in the server pack's glyph fonts.
 *
 * <p>The header composition (offsets, emblem placement, badge spacing) mirrors Wynntils'
 * {@code TooltipBuilder.buildHeader}, which in turn reproduces Wynncraft's own tooltip header.
 */
public final class WynncraftSkin implements CardSkin {

    public static final WynncraftSkin INSTANCE = new WynncraftSkin();

    private WynncraftSkin() {}

    @Override
    public List<Component> header(ItemCardData data) {
        CustomColor rarityColor = CustomColor.fromChatFormatting(data.tier().getChatFormatting());
        CustomColor secondaryColor = data.tier().getSecondaryColor();

        Optional<Component> elementTag = data.elements().isEmpty()
                ? Optional.empty()
                : Optional.of(elementTag(data.elements(), secondaryColor));
        Optional<Component> tertiary;
        Optional<Component> quaternary = Optional.empty();
        if (data.setName().isPresent()) {
            tertiary = Optional.of(BannerBoxFont.buildMessage(
                    bannerText(data.setName().get() + " set"), secondaryColor, CommonColors.BLACK, "󐀂"));
            quaternary = elementTag;
        } else if (data.kind() == ItemCardData.Kind.GEAR_BOX || data.kind() == ItemCardData.Kind.GEAR_UNIDENTIFIED) {
            tertiary = Optional.of(BannerBoxFont.buildMessage(
                    "unidentified", secondaryColor, CommonColors.BLACK, "󐀂"));
            quaternary = elementTag;
        } else {
            tertiary = elementTag;
        }

        // Major ID: just its name as a badge; the full description stays in Wynncraft's own tooltip.
        Optional<Component> majorBadge = data.majorId()
                .map(ItemCardData.MajorId::name)
                .filter(name -> !name.isBlank())
                .map(name -> BannerBoxFont.buildMessage(bannerText(name), CommonColors.WYNNCRAFT_AQUA, CommonColors.BLACK, ""));
        if (majorBadge.isPresent()) {
            if (tertiary.isEmpty()) {
                tertiary = majorBadge;
            } else if (quaternary.isEmpty()) {
                quaternary = majorBadge;
            } else {
                Component existing = quaternary.get();
                quaternary = Optional.of(Component.empty().append(existing)
                        .append(WynnGlyphs.space(1)).append(majorBadge.get()));
            }
        }
        boolean extraOffset = tertiary.isPresent();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());

        MutableComponent emblemLine = Component.empty()
                .append(WynnGlyphs.space(-16))
                .append(WynnGlyphs.glyph(data.emblem().getFrameCode(extraOffset), CommonFonts.TOOLTIP_EMBLEM_FRAME_FONT))
                .append(WynnGlyphs.space(-49))
                .append(WynnGlyphs.glyph(WynnGlyphs.emblemSprite(data.type()).getSpriteCode(extraOffset),
                                CommonFonts.TOOLTIP_EMBLEM_SPRITE_FONT)
                        .withColor(WynnGlyphs.EMBLEM_SPRITE_COLOR))
                .append(WynnGlyphs.space(5))
                .append(name(data, rarityColor));
        lines.add(emblemLine);

        MutableComponent tagLine = Component.empty()
                .append(WynnGlyphs.space(38))
                .append(BannerBoxFont.buildMessage(data.tier().getApiName(), rarityColor, CommonColors.BLACK, ""))
                .append(WynnGlyphs.space(3))
                .append(BannerBoxFont.buildMessage(data.type().getModelKey(), secondaryColor, CommonColors.BLACK, ""))
                .append(WynnGlyphs.space(1));
        TooltipBannerIcon restriction = WynnGlyphs.restrictionIcon(data.restriction());
        if (restriction != null) {
            tagLine.append(WynnGlyphs.space(2))
                    .append(TooltipBannerFont.buildMessage(restriction,
                            CustomColor.fromInt(WynnGlyphs.RESTRICTION_BADGE_COLOR), ""));
        }
        lines.add(tagLine);

        if (tertiary.isPresent()) {
            MutableComponent secondTagLine = Component.empty()
                    .append(WynnGlyphs.space(38))
                    .append(tertiary.get());
            quaternary.ifPresent(tag -> secondTagLine.append(WynnGlyphs.space(1)).append(tag));
            lines.add(secondTagLine);
        }
        return lines;
    }

    @Nullable
    @Override
    public Identifier frameStyle(ItemCardData data) {
        return data.tooltipStyle();
    }

    /** Item name in Wynncraft's font and rarity colour, with the overall roll appended (Wynntils colour scale). */
    private static Component name(ItemCardData data, CustomColor rarityColor) {
        TooltipOptions options = Handlers.Tooltip.getTooltipOptions();
        ItemCardData.Overall overall = data.overall();
        String itemName = data.shiny() ? "Shiny " + data.name() : data.name();
        Style style = WynnGlyphs.wynnTextStyle();

        MutableComponent name;
        if (options.perfectTitle() && overall.perfect()) {
            name = ComponentUtils.makeRainbowStyle("Perfect " + itemName, true).withStyle(style);
        } else if (options.defectiveTitle() && overall.defective()) {
            name = ComponentUtils.makeCrimsonStyle("Defective " + itemName, true).withStyle(style);
        } else {
            name = Component.literal(itemName).withStyle(style.withColor(rarityColor.asInt()));
        }

        overall.percentage().ifPresent(pct -> name.append(ColorScaleUtils.getPercentageTextComponent(
                options.colorMap(),
                pct,
                options.colorLerp(),
                overall.perfectInternalRolls(),
                Math.max(1, options.decimalPlaces()),
                overall.estimated())));
        return name;
    }

    /**
     * Wynncraft's banner-box font only has glyphs for letters and digits; anything else (an apostrophe in
     * "Forest's Blessing", a comma, a hyphen) turns into a broken run of missing glyphs. Drop apostrophes
     * and turn other punctuation into spaces.
     */
    private static String bannerText(String text) {
        return text.replaceAll("['’`]", "")
                .replaceAll("[^A-Za-z0-9 ]+", " ")
                .replaceAll(" {2,}", " ")
                .strip();
    }

    /** One element: icon + name badge. Several: icons only (same rule as Wynntils / Wynncraft). */
    private static Component elementTag(List<Element> elements, CustomColor background) {
        MutableComponent tag = Component.empty();
        if (elements.size() == 1) {
            Element element = elements.getFirst();
            tag.append(TooltipBannerFont.buildMessage(WynnGlyphs.elementIcon(element), background, "󏿿"));
            tag.append(BannerBoxFont.buildMessage(element.getDisplayName(), background, CommonColors.BLACK, "󐀂"));
        } else {
            for (Element element : elements) {
                tag.append(TooltipBannerFont.buildMessage(WynnGlyphs.elementIcon(element), background,
                        element == elements.getLast() ? "" : "󏿿"));
            }
        }
        return tag;
    }
}
