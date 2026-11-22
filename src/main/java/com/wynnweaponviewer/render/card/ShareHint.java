package com.wynnweaponviewer.render.card;

import com.wynntils.core.components.Managers;
import com.wynntils.core.consumers.features.Feature;
import com.wynntils.features.chat.ChatItemFeature;
import com.wynntils.features.inventory.ItemScreenshotFeature;
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
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The small print under the preview that says how to share the item:
 * <pre>
 *   F5 TO SHARE IN CHAT
 *   F4 TO SCREENSHOT
 * </pre>
 * The lines name whatever keys Wynntils' "Share Item" and "Screenshot Item" are bound to. Each is left out when its
 * key is unbound or its Wynntils feature is off; the chat line also when the item isn't one Wynntils can share in
 * chat (unidentified gear).
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

    /** Wynntils' keybind names (their translation keys, also their options.txt names). */
    private static final String WYNNTILS_SHARE_KEY = "wynntils.keybind.shareItem";
    private static final String WYNNTILS_SCREENSHOT_KEY = "wynntils.keybind.screenshotItem";
    private static final String LANG = "hint." + WynnWeaponViewer.MOD_ID + ".share.";

    public static final int LINE_HEIGHT = 8;
    /** Space between the preview and the hint, in hint pixels. */
    public static final int GAP = 5;
    private static final int TEXT_COLOR = 0x8C8C8C;
    private static final int KEY_COLOR = 0xE6E6E6;

    /** Rebuilt only when something it shows changes (keys, which lines apply). */
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

    /** The hint for {@code stack}, or null if it's turned off or Wynntils offers no way to share this item. */
    @Nullable
    public static ShareHint forItem(ItemStack stack) {
        if (!ModConfig.getConfig().showShareHint) return null;

        String chatKey = canShareInChat(stack) ? wynntilsKeyName(() -> ChatItemFeature.class, WYNNTILS_SHARE_KEY) : null;
        String screenshotKey = wynntilsKeyName(() -> ItemScreenshotFeature.class, WYNNTILS_SCREENSHOT_KEY);
        if (chatKey == null && screenshotKey == null) return null;

        String signature = chatKey + "|" + screenshotKey;
        if (!signature.equals(cachedSignature)) {
            List<Component> lines = new ArrayList<>(2);
            if (chatKey != null) lines.add(line(LANG + "chat", chatKey));
            if (screenshotKey != null) lines.add(line(LANG + "screenshot", screenshotKey));
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
     * Draws the lines centred on {@code centerX}, from {@code top} down, cascading in after the text above them.
     *
     * @param scale      GUI pixels per hint pixel
     * @param revealTime seconds since the text started appearing (as for the card)
     * @param start      when the first line starts, from {@link ItemCardRenderer#trailingStart}
     */
    public void render(GuiGraphics graphics, float centerX, float top, float scale, float alpha,
                       float revealTime, float start, boolean stagger) {
        if (alpha <= 0.02f) return;
        Font font = Minecraft.getInstance().font;
        float y = top + GAP * scale;
        for (int i = 0; i < lines.size(); i++) {
            float reveal = ItemCardRenderer.trailingReveal(revealTime, start, i, stagger);
            if (alpha * reveal > 0.02f) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(ItemCardRenderer.snapToPixel(centerX - lineWidths.get(i) * scale / 2f),
                        ItemCardRenderer.snapToPixel(y));
                graphics.pose().scale(scale, scale);
                graphics.drawString(font, lines.get(i), 0, ItemCardRenderer.trailingRise(reveal, stagger),
                        ARGB.white(alpha * reveal), true);
                graphics.pose().popMatrix();
            }
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

    /**
     * The key a Wynntils keybind is bound to, or null if it's unbound or its feature is off.
     *
     * @param feature supplies the feature's class, looked up inside the try so a moved class can't break the hint
     */
    @Nullable
    private static String wynntilsKeyName(Supplier<Class<? extends Feature>> feature, String keybindName) {
        try {
            if (!Managers.Feature.getFeatureInstance(feature.get()).isEnabled()) return null;
        } catch (RuntimeException | LinkageError e) {
            // A Wynntils version that moved the feature: assume it's on, the key lookup below still decides.
        }
        // Found by name among the game's key mappings, so it doesn't depend on Wynntils' keybind API.
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (keybindName.equals(mapping.getName())) return keyName(mapping);
        }
        return null;
    }

    @Nullable
    private static String keyName(@Nullable KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return null;
        return mapping.getTranslatedKeyMessage().getString();
    }
}
