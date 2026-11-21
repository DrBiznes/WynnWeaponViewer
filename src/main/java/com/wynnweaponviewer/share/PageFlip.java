package com.wynnweaponviewer.share;

import com.wynntils.core.components.Models;
import com.wynntils.models.items.FakeItemStack;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.properties.PagedItemProperty;
import com.wynntils.utils.wynn.ContainerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Flips an item's Wynncraft tooltip through all its pages, so Wynntils parses the ones the player hasn't looked at
 * (powders live on a later page). Mirrors Wynntils' own chat item share ({@code ChatItemFeature.PageParser}):
 * a "swap hands" click on the slot asks Wynncraft for the next page; three of them come back round to the first.
 *
 * <p>Only used when sharing - hovering never sends anything to the server. Ticked from the client tick.
 */
final class PageFlip {

    /** Wynncraft gear has three tooltip pages; three flips also lands back on the first one. */
    private static final int PAGES = 3;
    /** Same pacing as Wynntils: a pause after each page arrives, a retry if the server answers unchanged. */
    private static final int PAGE_DELAY_TICKS = 6;
    private static final int RETRY_DELAY_TICKS = 10;
    private static final int MAX_ATTEMPTS = 3;
    /** Give up on a page after 5 seconds without it arriving. */
    private static final int TIMEOUT_TICKS = 100;

    private final AbstractContainerScreen<?> screen;
    private final AbstractContainerMenu menu;
    private final Slot slot;
    private final PagedItemProperty item;
    /** Called once, with whether every page arrived. */
    private final Consumer<Boolean> onFinished;

    private int remainingFlips = PAGES;
    private int expectedPage;
    private boolean awaitingPage;
    private int waitTicks;
    private int attempts;
    private int delayTicks;
    @Nullable
    private ItemStack stackWhenSent;
    private boolean finished;

    private PageFlip(AbstractContainerScreen<?> screen, Slot slot, PagedItemProperty item,
                     Consumer<Boolean> onFinished) {
        this.screen = screen;
        this.menu = screen.getMenu();
        this.slot = slot;
        this.item = item;
        this.onFinished = onFinished;
    }

    /**
     * The pages still to read for the item in {@code slot}, or null when there's nothing to flip: all pages already
     * parsed, not a paged item, not in a real slot of the open container, or not on a Wynncraft world (where the
     * click would really swap the item into the offhand).
     */
    @Nullable
    static PageFlip prepare(AbstractContainerScreen<?> screen, @Nullable Slot slot, Consumer<Boolean> onFinished) {
        Minecraft mc = Minecraft.getInstance();
        if (slot == null || !slot.hasItem() || slot.getItem() instanceof FakeItemStack) return null;
        if (mc.player == null || mc.player.containerMenu != screen.getMenu()) return null;
        if (!Models.WorldState.onWorld()) return null;
        if (!(Models.Item.getWynnItem(slot.getItem()).orElse(null) instanceof PagedItemProperty paged)) return null;
        if (allPagesParsed(paged)) return null;
        return new PageFlip(screen, slot, paged, onFinished);
    }

    private static boolean allPagesParsed(PagedItemProperty item) {
        for (int page = 0; page < PAGES; page++) {
            if (page != item.currentPage() && !item.hasParsedPage(page)) return false;
        }
        return true;
    }

    Slot slot() {
        return slot;
    }

    void start() {
        requestNextPage();
    }

    void tick() {
        if (finished) return;
        if (!isStillValid()) {
            finish(false);
            return;
        }

        if (delayTicks > 0) {
            if (--delayTicks == 0) requestNextPage();
            return;
        }
        if (!awaitingPage) return;

        if (item.currentPage() == expectedPage) {
            awaitingPage = false;
            attempts = 0;
            if (--remainingFlips <= 0) {
                finish(true);
            } else {
                delayTicks = PAGE_DELAY_TICKS;
            }
            return;
        }

        // The slot was resent without the page changing: Wynncraft ignored that click, ask again shortly.
        if (slot.getItem() != stackWhenSent && attempts < MAX_ATTEMPTS) {
            awaitingPage = false;
            delayTicks = RETRY_DELAY_TICKS;
            return;
        }

        if (++waitTicks >= TIMEOUT_TICKS) finish(false);
    }

    /** Same screen, same container, and the slot still holds this item (Wynntils keeps one WynnItem per item). */
    private boolean isStillValid() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != screen || mc.player == null || mc.player.containerMenu != menu) return false;
        if (!Models.WorldState.onWorld()) return false;
        WynnItem current = Models.Item.getWynnItem(slot.getItem()).orElse(null);
        return current == item;
    }

    private void requestNextPage() {
        expectedPage = (item.currentPage() + 1) % PAGES;
        awaitingPage = true;
        waitTicks = 0;
        attempts++;
        stackWhenSent = slot.getItem();
        // Through Wynntils' own helper, so its page tracking (PagedItemUpdateTracker) sees the request.
        ContainerUtils.pressSwapHandsOnSlot(menu.containerId, menu.getStateId(), slot.index, menu.getCarried());
    }

    private void finish(boolean complete) {
        if (finished) return;
        finished = true;
        onFinished.accept(complete);
    }
}
