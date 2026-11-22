package com.wynnweaponviewer.render;

import com.mojang.blaze3d.Blaze3D;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class AnimationState {

    private ItemStack currentStack = ItemStack.EMPTY;
    private @Nullable Item currentItem;
    private String currentName = "";
    /** The slot the animation belongs to; null while the item is on the cursor, so it adopts the slot it lands in. */
    private @Nullable Slot currentSlot;
    private double animationStartTime = 0;
    private boolean isActive = false;

    private static final float APPEAR_DURATION = 0.1f;

    public void beginFrame() {
    }

    /**
     * Restarts the animation only when a different item is hovered. The comparison is by slot and item name, not the
     * full stack: cycling the tooltip page swaps in a stack with new lore, which should not count as a new item.
     *
     * @param slot the hovered slot, or null when the item is on the cursor
     */
    public void update(ItemStack stack, @Nullable Slot slot) {
        if (stack == null || stack.isEmpty()) {
            reset();
            return;
        }

        String name = stack.getHoverName().getString();
        boolean sameItem = isActive
                && stack.getItem() == currentItem
                && name.equals(currentName)
                && (slot == null || currentSlot == null || slot == currentSlot);

        currentStack = stack;
        currentSlot = slot;
        if (!sameItem) {
            currentItem = stack.getItem();
            currentName = name;
            animationStartTime = Blaze3D.getTime();
            isActive = true;
        }
    }

    public void reset() {
        currentStack = ItemStack.EMPTY;
        currentItem = null;
        currentName = "";
        currentSlot = null;
        animationStartTime = 0;
        isActive = false;
    }

    private float getElapsed() {
        return (float) (Blaze3D.getTime() - animationStartTime);
    }

    public float getAppearProgress(boolean hasAnimation, int delayMs) {
        if (!isActive) return 0.0f;

        float elapsed = getElapsed();
        float delaySec = delayMs / 1000.0f;

        if (hasAnimation && delaySec > 0) {
            float delayedElapsed = elapsed - delaySec;
            if (delayedElapsed <= 0) return 0.0f;
            return Math.min(1.0f, delayedElapsed / APPEAR_DURATION);
        } else {
            return Math.min(1.0f, elapsed / APPEAR_DURATION);
        }
    }

    public float getHoverDuration() {
        if (!isActive) return 0.0f;
        return getElapsed();
    }

    public float getIdleAnimationTime(boolean hasAppearAnimation, int delayMs) {
        if (!isActive) return 0.0f;
        float elapsed = getElapsed();
        float delaySec = delayMs / 1000.0f;
        if (hasAppearAnimation && delaySec > 0) {
            float delayedElapsed = elapsed - delaySec;
            if (delayedElapsed <= 0) return 0.0f;
            return delayedElapsed;
        }
        return elapsed;
    }

    public boolean isActive() {
        return isActive;
    }

    public ItemStack getCurrentStack() {
        return currentStack;
    }

    public boolean shouldShowInfo(int delaySeconds) {
        return isActive && getHoverDuration() >= delaySeconds;
    }

    public float getTextAppearProgress(int delaySeconds) {
        if (!isActive) return 0.0f;
        float hoverDuration = getHoverDuration();
        if (hoverDuration < delaySeconds) return 0.0f;

        float textAnimDuration = 0.15f;
        float timeSinceDelay = hoverDuration - delaySeconds;
        return Math.min(1.0f, timeSinceDelay / textAnimDuration);
    }
}
