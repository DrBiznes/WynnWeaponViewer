package com.wynnweaponviewer.render.card;

import com.wynnweaponviewer.card.ItemCardData;
import com.wynntils.models.elements.type.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** Fallback when Wynncraft's resource pack isn't loaded: vanilla font, chat colours, default tooltip frame. */
public final class PlainSkin implements CardSkin {

    public static final PlainSkin INSTANCE = new PlainSkin();

    private PlainSkin() {}

    @Override
    public List<Component> header(ItemCardData data) {
        ChatFormatting rarity = data.tier().getChatFormatting();

        MutableComponent name = Component.literal(data.shiny() ? "Shiny " + data.name() : data.name())
                .withStyle(rarity);
        data.overall().percentage().ifPresent(pct ->
                name.append(Component.literal(String.format(Locale.ROOT, " [%.1f%%]", pct))
                        .withStyle(ChatFormatting.GRAY)));

        MutableComponent tags = Component.empty()
                .append(Component.literal(data.tier().getName()).withStyle(rarity, ChatFormatting.BOLD))
                .append(Component.literal(" " + titleCase(data.type().name())).withStyle(ChatFormatting.GRAY));
        for (Element element : data.elements()) {
            // No element symbol: those glyphs live in the Wynncraft pack this skin exists to avoid.
            tags.append(Component.literal(" " + element.getDisplayName())
                    .withStyle(element.getColorCode()));
        }
        data.majorId().map(ItemCardData.MajorId::name).filter(n -> !n.isBlank())
                .ifPresent(n -> tags.append(Component.literal(" ✦ " + n).withStyle(ChatFormatting.AQUA)));
        if (data.unidentified()) {
            tags.append(Component.literal(" Unidentified").withStyle(ChatFormatting.DARK_GRAY));
        }
        return List.of(name, tags);
    }

    @Nullable
    @Override
    public Identifier frameStyle(ItemCardData data) {
        return null;
    }

    private static String titleCase(String s) {
        String lower = s.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
