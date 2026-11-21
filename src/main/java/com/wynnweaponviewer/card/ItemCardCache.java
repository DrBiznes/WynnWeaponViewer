package com.wynnweaponviewer.card;

import com.wynntils.core.components.Models;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.properties.PagedItemProperty;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Holds the card snapshot for the currently hovered item and rebuilds it only when something changed.
 *
 * <p>Wynntils mutates its {@link WynnItem} in place as the player flips tooltip pages (powders, sets) and as
 * async data (item weights) arrives, so besides the stack/item identity we also re-check a cheap fingerprint and
 * refresh periodically.
 */
public final class ItemCardCache {

    private static final long REFRESH_INTERVAL_MS = 1000;

    private static ItemStack cachedStack = ItemStack.EMPTY;
    private static WynnItem cachedItem;
    private static int cachedFingerprint;
    private static long builtAtMs;
    private static Optional<ItemCardData> cachedData = Optional.empty();

    /** Last successfully built card, kept after the screen closes so {@code /wwb debug card} can inspect it. */
    private static ItemCardData lastHovered;

    private ItemCardCache() {}

    /**
     * @param liveStack the actual slot / carried stack, never a copy (copies lose the Wynntils annotation)
     */
    public static Optional<ItemCardData> get(ItemStack liveStack) {
        if (liveStack == null || liveStack.isEmpty()) return Optional.empty();

        WynnItem item = Models.Item.getWynnItem(liveStack).orElse(null);
        int fingerprint = fingerprint(item);
        long now = System.currentTimeMillis();

        boolean sameItem = liveStack == cachedStack && item == cachedItem && fingerprint == cachedFingerprint;
        if (sameItem && now - builtAtMs < REFRESH_INTERVAL_MS) {
            return cachedData;
        }

        cachedStack = liveStack;
        cachedItem = item;
        cachedFingerprint = fingerprint;
        builtAtMs = now;
        cachedData = item == null ? Optional.empty() : ItemCardDataFactory.create(liveStack);
        cachedData.ifPresent(data -> lastHovered = data);
        return cachedData;
    }

    public static Optional<ItemCardData> getLastHovered() {
        return Optional.ofNullable(lastHovered);
    }

    /** Drops the per-hover cache (screen closed); keeps {@link #getLastHovered()} for debugging. */
    public static void clear() {
        cachedStack = ItemStack.EMPTY;
        cachedItem = null;
        cachedFingerprint = 0;
        builtAtMs = 0;
        cachedData = Optional.empty();
    }

    private static int fingerprint(WynnItem item) {
        if (item == null) return 0;
        int hash = System.identityHashCode(item);
        if (item instanceof PagedItemProperty paged) {
            hash = 31 * hash + paged.currentPage();
            hash = 31 * hash + (paged.hasParsedPage(1) ? 1 : 0);
            hash = 31 * hash + (paged.hasParsedPage(2) ? 1 : 0);
        }
        return hash;
    }
}
