package com.wynnweaponviewer.render.card;

import com.wynnweaponviewer.card.ItemCardData;
import com.wynntils.core.components.Handlers;
import com.wynntils.core.text.fonts.CommonFonts;
import com.wynntils.handlers.tooltip.TooltipBuilder;
import com.wynntils.handlers.tooltip.type.TooltipOptions;
import com.wynntils.models.gear.type.ItemWeightSource;
import com.wynntils.utils.StringUtils;
import com.wynntils.utils.colors.CommonColors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Builds the card body below the model window: identifications with roll bars, then a footer with powders,
 * rerolls, shiny stat and Nori / Wynnpool scales. Deliberately leaves out what Wynncraft's own tooltip already
 * shows next to the card (DPS, requirements, major ID text).
 */
public final class CardBody {

    /**
     * How much body content to keep. The card's size is fixed per screen and the model window absorbs most height
     * differences; these are only tried, in order, when an item has too many rows even with the smallest window
     * (see ZoomedItemRenderer).
     */
    public enum Density {
        FULL(Integer.MAX_VALUE, true),
        CAPPED(10, true),
        MINIMAL(6, false);

        public final int maxIdentifications;
        public final boolean footer;

        Density(int maxIdentifications, boolean footer) {
            this.maxIdentifications = maxIdentifications;
            this.footer = footer;
        }
    }

    private static final int NAME_COLOR = 0xFFFFFF;
    private static final int FIXED_NAME_COLOR = 0xAAAAAA;
    private static final int MUTED_COLOR = 0x888888;
    private static final int SHINY_COLOR = 0xFFFFFF;

    private CardBody() {}

    public static List<CardRow> build(ItemCardData data, Density density, boolean pack) {
        TooltipOptions options = Handlers.Tooltip.getTooltipOptions();
        int dividerColor = TooltipBuilder.getDividerColor(data.tier()).asInt();
        List<CardRow> rows = new ArrayList<>();

        // --- identifications ---
        List<ItemCardData.Identification> ids = data.identifications();
        if (!ids.isEmpty()) {
            rows.add(new CardRow.Divider(dividerColor, pack));
            int shown = Math.min(ids.size(), density.maxIdentifications);
            for (int i = 0; i < shown; i++) {
                rows.add(identification(ids.get(i), options));
            }
            if (shown < ids.size()) {
                rows.add(new CardRow.More(CardRow.wynn("+" + (ids.size() - shown) + " more", MUTED_COLOR)));
            }
        }

        // --- footer ---
        if (density.footer) {
            List<CardRow> footer = footer(data, options, pack);
            if (!footer.isEmpty()) {
                rows.add(new CardRow.Divider(dividerColor, pack));
                rows.addAll(footer);
            }
        }
        return rows;
    }

    private static CardRow identification(ItemCardData.Identification id, TooltipOptions options) {
        int valueColor = (id.positive() ? CommonColors.WYNNCRAFT_GREEN : CommonColors.WYNNCRAFT_RED).asInt();
        Component name = CardRow.wynn(id.displayName(), id.fixed() ? FIXED_NAME_COLOR : NAME_COLOR);

        // Unidentified: show the possible range instead of a roll.
        if (id.displayRange() != null) {
            String range = StringUtils.toSignedCommaString(id.displayRange().low()) + id.unit()
                    + " to " + StringUtils.toSignedCommaString(id.displayRange().high()) + id.unit();
            return new CardRow.Identification(name, CardRow.wynn(range, valueColor), null, 0, 0, false);
        }

        Component value = CardRow.wynn(StringUtils.toSignedCommaString(id.value()) + id.unit(), valueColor);
        if (id.percentage().isEmpty()) {
            return new CardRow.Identification(name, value, null, 0, 0, false);
        }

        float pct = id.percentage().get();
        int rollColor = rollColor(options.colorMap(), options.colorLerp(), pct);
        String pctText = (id.estimated() ? "~" : "") + formatPercent(pct, options.decimalPlaces()) + "%";
        return new CardRow.Identification(name, value, CardRow.wynn(pctText, rollColor), pct / 100f, rollColor, true);
    }

    private static List<CardRow> footer(ItemCardData data, TooltipOptions options, boolean pack) {
        List<CardRow> rows = new ArrayList<>();

        // Powder sockets (left) + rerolls (right)
        ItemCardData.Powders powders = data.powders();
        Component sockets = powders.slots() > 0 ? sockets(powders, pack) : null;
        Component rerolls = data.rerolls() > 0
                ? CardRow.wynn(data.rerolls() + (data.rerolls() == 1 ? " reroll" : " rerolls"), MUTED_COLOR)
                : null;
        if (sockets != null || rerolls != null) rows.add(new CardRow.Split(sockets, rerolls));

        // Shiny stat
        data.shinyStat().ifPresent(shiny -> rows.add(new CardRow.Split(
                CardRow.wynn("✧ " + shiny.displayName(), SHINY_COLOR),
                CardRow.wynn(String.format(Locale.ROOT, "%,d", shiny.value()) + shiny.unit(), SHINY_COLOR))));

        // Scales: the first (main) weighting of each source, side by side.
        Map<ItemWeightSource, ItemCardData.Scale> topScales = new LinkedHashMap<>();
        for (ItemCardData.Scale scale : data.scales()) topScales.putIfAbsent(scale.source(), scale);
        if (!topScales.isEmpty()) {
            List<Component> entries = new ArrayList<>();
            for (ItemCardData.Scale scale : topScales.values()) entries.add(scale(scale, options));
            rows.add(new CardRow.Split(entries.get(0), entries.size() > 1 ? entries.get(1) : null));
        }

        if (!rows.isEmpty()) rows.add(new CardRow.Spacer(1));
        return rows;
    }

    private static Component sockets(ItemCardData.Powders powders, boolean pack) {
        if (!pack) {
            MutableComponent text = CardRow.wynn("Powders ", MUTED_COLOR);
            for (ItemCardData.PowderEntry entry : powders.applied()) {
                text.append(CardRow.wynn("●", entry.powder().getLightColor().getColor() == null
                        ? 0xFFFFFF : entry.powder().getLightColor().getColor()));
            }
            for (int i = powders.applied().size(); i < powders.slots(); i++) text.append(CardRow.wynn("○", MUTED_COLOR));
            return text;
        }
        MutableComponent text = Component.empty();
        for (int slot = 0; slot < powders.slots(); slot++) {
            String sprite = slot < powders.applied().size()
                    ? switch (powders.applied().get(slot).powder()) {
                        case EARTH -> "";
                        case THUNDER -> "";
                        case WATER -> "";
                        case FIRE -> "";
                        case AIR -> "";
                    }
                    : "";
            if (slot > 0) text.append(WynnGlyphs.space(1));
            text.append(Component.literal(sprite)
                    .withStyle(Style.EMPTY.withFont(CommonFonts.TOOLTIP_SOCKET_FONT).withColor(0xFFFFFF))
                    .withoutShadow());
        }
        return text;
    }

    /** Wynntils' weight icon in the source colour, then e.g. "Main 69.7%". */
    private static Component scale(ItemCardData.Scale scale, TooltipOptions options) {
        int pctColor = rollColor(options.colorMap(), options.colorLerp(), scale.percentage());
        return Component.empty()
                .append(Component.literal("").withStyle(Style.EMPTY
                        .withFont(CommonFonts.WYNNTILS_TOOLTIP_ICONS)
                        .withColor(scale.source().getColor().asInt() & 0xFFFFFF)))
                .append(CardRow.wynn(" " + scale.name() + " ", NAME_COLOR))
                .append(CardRow.wynn(formatPercent(scale.percentage(), options.decimalPlaces()) + "%", pctColor));
    }

    /**
     * Same colour scale Wynntils uses for roll percentages (the user's map, lerped or flat). Wynntils keeps its
     * own colour function private, so this mirrors {@code ColorScaleUtils}.
     */
    static int rollColor(NavigableMap<Float, TextColor> map, boolean lerp, float pct) {
        if (map == null || map.isEmpty()) return 0xFFFFFF;
        if (!lerp) {
            Map.Entry<Float, TextColor> higher = map.higherEntry(pct);
            return (higher != null ? higher : map.lastEntry()).getValue().getValue();
        }
        Map.Entry<Float, TextColor> lower = map.floorEntry(pct);
        Map.Entry<Float, TextColor> higher = map.ceilingEntry(pct);
        if (lower == null) return higher.getValue().getValue();
        if (higher == null || lower.getKey().equals(higher.getKey())) return lower.getValue().getValue();

        float t = (pct - lower.getKey()) / (higher.getKey() - lower.getKey());
        int a = lower.getValue().getValue();
        int b = higher.getValue().getValue();
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Truncates (like Wynntils) rather than rounds, so 99.96% never shows as 100%. */
    static String formatPercent(float pct, int decimals) {
        int d = Math.max(0, Math.min(2, decimals));
        double factor = Math.pow(10, d);
        double truncated = Math.floor(pct * factor) / factor;
        return String.format(Locale.ROOT, "%." + d + "f", truncated);
    }
}
