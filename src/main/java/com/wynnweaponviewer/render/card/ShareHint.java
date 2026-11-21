package com.wynnweaponviewer.render.card;

import com.wynntils.core.components.Managers;
import com.wynntils.features.chat.ChatItemFeature;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.core.components.Models;
import com.wynnweaponviewer.WynnWeaponViewer;
import com.wynnweaponviewer.config.ModConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The small print under the preview that says how to share the item:
 * <pre>
 *   F5 TO SHARE IN CHAT
 *   CTRL+C TO SHARE AS IMAGE
 * </pre>
 * The first line names whatever key Wynntils' "Share Item" is bound to, and is left out when that key is unbound,
 * Wynntils' chat item feature is off, or the item isn't one Wynntils can share (unidentified gear). The second names
 * this mod's share key.
 *
 * <p>Drawn in Wynntils' copy of Wynncraft's 5-pixel small-caps font, which ships with Wynntils, so it works without
 * the Wynncraft resource pack. That font has letters, digits and a few symbols only; anything else (spaces, "+",
 * key names in other scripts) falls back to the default font. Measured in hint pixels, drawn under one scale.
 */
public final class ShareHint {

    private static final FontDescription SMALL_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("wynntils", "five"));
    /** The small font's glyphs, in codepoint order from U+E040 (upper and lower case share a glyph). */
    private static final String SMALL_GLYPHS = "abcdefghijklmnopqrstuvwxyz?[]\\%&0123456789!()<>=";
    private static final int SMALL_GLYPH_BASE = 0xE040;

    /** The name of Wynntils' chat item share keybind (its translation key, also its options.txt name). */
    private static final String WYNNTILS_SHARE_KEY = "wynntils.keybind.shareItem";
    private static final String LANG = "hint." + WynnWeaponViewer.MOD_ID + ".share.";

    public static final int LINE_HEIGHT = 8;
    /** Space between the preview and the hint, in hint pixels. */
    public static final int GAP = 5;
    private static final int TEXT_COLOR = 0x8C8C8C;
    private static final int KEY_COLOR = 0xE6E6E6;

    /** Rebuilt only when something it shows changes (keys, share setting, which lines apply). */
    @Nullable
    private static String cachedSignature;
    @Nullable
    private static ShareHint cached;

    private final List<Component> lines;
    private final List<Integer> lineWidths;
    private final int width;

    private ShareHint(List<Component> lines) {
        Font font = Minecraft.getInstance().font;
        this.lines = lines;
        this.lineWidths = lines.stream().map(font::width).toList();
        this.width = lineWidths.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    public int width() {
        return width;
    }

    /** Height including the gap above it. */
    public int height() {
        return GAP + lines.size() * LINE_HEIGHT;
    }

    /**
     * The hint for {@code stack}, or null if it's turned off or there's no way to share this item.
     *
     * @param hasCard whether the item has a card (Ctrl+C shares cards only)
     */
    @Nullable
    public static ShareHint forItem(ItemStack stack, boolean hasCard) {
        if (!ModConfig.getConfig().showShareHint) return null;

        String chatKey = canShareInChat(stack) ? wynntilsShareKeyName() : null;
        String imageKey = hasCard ? cardShareKeyName() : null;
        if (chatKey == null && imageKey == null) return null;

        ModConfig.ShareAction action = ModConfig.getConfig().shareAction;
        String signature = chatKey + "|" + imageKey + "|" + action;
        if (!signature.equals(cachedSignature)) {
            List<Component> lines = new ArrayList<>(2);
            if (chatKey != null) lines.add(line(LANG + "chat", chatKey));
            if (imageKey != null) {
                // Save Only can't be pasted anywhere, so it says "save" rather than "share".
                lines.add(line(LANG + (action.copies() ? "image" : "image.save"), imageKey));
            }
            cached = new ShareHint(lines);
            cachedSignature = signature;
        }
        return cached;
    }

    /** Forgets the built lines, e.g. after a resource reload changed the language or fonts. */
    public static void clearCache() {
        cachedSignature = null;
        cached = null;
    }

    /**
     * Draws the lines centred on {@code centerX}, from {@code top} down.
     *
     * @param scale GUI pixels per hint pixel
     */
    public void render(GuiGraphics graphics, float centerX, float top, float scale, float alpha) {
        if (alpha <= 0.02f) return;
        Font font = Minecraft.getInstance().font;
        int color = ARGB.white(alpha);
        float y = top + GAP * scale;
        for (int i = 0; i < lines.size(); i++) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(ItemCardRenderer.snapToPixel(centerX - lineWidths.get(i) * scale / 2f),
                    ItemCardRenderer.snapToPixel(y));
            graphics.pose().scale(scale, scale);
            graphics.drawString(font, lines.get(i), 0, 0, color, true);
            graphics.pose().popMatrix();
            y += LINE_HEIGHT * scale;
        }
    }

    // --- text ------------------------------------------------------------------------------------------------

    /** A translated line with its one {@code %s} filled by a highlighted key name. */
    private static Component line(String key, String keyName) {
        String template = Language.getInstance().getOrDefault(key);
        int at = template.indexOf("%s");
        if (at < 0) return small(template, TEXT_COLOR);
        return Component.empty()
                .append(small(template.substring(0, at), TEXT_COLOR))
                .append(small(keyName, KEY_COLOR))
                .append(small(template.substring(at + 2), TEXT_COLOR));
    }

    /** {@code text} in the small font, with characters it lacks in the default font. */
    private static MutableComponent small(String text, int color) {
        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        boolean runIsSmall = true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int glyph = SMALL_GLYPHS.indexOf(Character.toLowerCase(c));
            boolean isSmall = glyph >= 0;
            if (isSmall != runIsSmall && !run.isEmpty()) {
                out.append(segment(run.toString(), runIsSmall, color));
                run.setLength(0);
            }
            runIsSmall = isSmall;
            run.append(isSmall ? (char) (SMALL_GLYPH_BASE + glyph) : c);
        }
        if (!run.isEmpty()) out.append(segment(run.toString(), runIsSmall, color));
        return out;
    }

    private static Component segment(String text, boolean smallFont, int color) {
        Style style = Style.EMPTY.withColor(color);
        return Component.literal(text).withStyle(smallFont ? style.withFont(SMALL_FONT) : style);
    }

    // --- keys ------------------------------------------------------------------------------------------------

    /** Wynntils' chat share works on identified gear (it refuses unidentified boxes). */
    private static boolean canShareInChat(ItemStack stack) {
        return Models.Item.asWynnItem(stack, GearItem.class).isPresent();
    }

    /** The key Wynntils' "Share Item" is bound to, or null if it's unbound or the feature is off. */
    @Nullable
    private static String wynntilsShareKeyName() {
        try {
            if (!Managers.Feature.getFeatureInstance(ChatItemFeature.class).isEnabled()) return null;
        } catch (RuntimeException | LinkageError e) {
            // A Wynntils version that moved the feature: assume it's on, the key lookup below still decides.
        }
        // Found by name among the game's key mappings, so it doesn't depend on Wynntils' keybind API.
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (WYNNTILS_SHARE_KEY.equals(mapping.getName())) return keyName(mapping);
        }
        return null;
    }

    /** "Ctrl+C" (Cmd on macOS), or null if the share key is unbound. */
    @Nullable
    private static String cardShareKeyName() {
        String key = keyName(WynnWeaponViewer.shareCardKey());
        if (key == null) return null;
        return (Util.getPlatform() == Util.OS.OSX ? "Cmd+" : "Ctrl+") + key;
    }

    @Nullable
    private static String keyName(@Nullable KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return null;
        return mapping.getTranslatedKeyMessage().getString();
    }
}
