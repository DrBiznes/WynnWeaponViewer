package com.wynnweaponviewer.share;

import com.mojang.blaze3d.platform.NativeImage;
import com.wynntils.core.components.Models;
import com.wynntils.models.items.WynnItem;
import com.wynntils.screens.base.TextboxScreen;
import com.wynnweaponviewer.WynnWeaponViewer;
import com.wynnweaponviewer.accessor.ContainerScreenAccessor;
import com.wynnweaponviewer.card.ItemCardCache;
import com.wynnweaponviewer.card.ItemCardData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Ctrl+C on an item in any inventory: renders its card (always the full card, whatever the on-screen mode), saves it
 * to {@code screenshots/} as {@code <Item_Name>_<roll>%.png} and copies it to the clipboard. Progress and the
 * result show in a toast.
 *
 * <p>Tooltip pages the player hasn't looked at (powders etc.) are read first, the same way Wynntils' chat share
 * does it - see {@link PageFlip}.
 */
public final class CardShare {

    private static final String LANG = "toast." + WynnWeaponViewer.MOD_ID + ".share.";
    private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(4000L);
    /** If a share somehow never reports back, stop blocking new ones after this long (page reading included). */
    private static final long STUCK_AFTER_MS = 40_000;

    private static volatile long busySince;
    @Nullable
    private static PageFlip pageFlip;

    private CardShare() {}

    /** Ctrl (Cmd on macOS) plus the share key, and no text box is being typed in. */
    public static boolean isShareShortcut(Screen screen, KeyEvent event, boolean keyMatches) {
        if (!keyMatches) return false;
        int modifier = Util.getPlatform() == Util.OS.OSX ? GLFW.GLFW_MOD_SUPER | GLFW.GLFW_MOD_CONTROL : GLFW.GLFW_MOD_CONTROL;
        if ((event.modifiers() & modifier) == 0) return false;
        return !isTyping(screen);
    }

    private static boolean isTyping(Screen screen) {
        if (screen.getFocused() instanceof EditBox) return true;
        // Wynntils' own text boxes (container search etc.) track focus separately.
        return screen instanceof TextboxScreen textbox && textbox.getFocusedTextInput() != null;
    }

    /**
     * While pages are being read, clicks and keys on that item's slot are held back (as Wynntils does), so the
     * player can't move it mid-read.
     */
    public static boolean blocksInputOn(AbstractContainerScreen<?> screen) {
        PageFlip flip = pageFlip;
        return flip != null && screen instanceof ContainerScreenAccessor accessor
                && accessor.wynnweapon$getHoveredSlot() == flip.slot();
    }

    /** Client tick: drives the page reading. */
    public static void tick() {
        PageFlip flip = pageFlip;
        if (flip != null) flip.tick();
    }

    /**
     * Shares the item under the cursor (or held on it), if it has a card.
     *
     * @return true if the key press was used, so nothing else should handle it
     */
    public static boolean tryShare(AbstractContainerScreen<?> screen) {
        if (!(screen instanceof ContainerScreenAccessor accessor)) return false;
        ItemStack carried = screen.getMenu().getCarried();
        Slot hovered = accessor.wynnweapon$getHoveredSlot();
        // The item on the cursor, else the hovered one - like the zoom, but regardless of its item filters.
        Slot slot = carried.isEmpty() && hovered != null && hovered.hasItem() ? hovered : null;
        ItemStack stack = slot != null ? slot.getItem() : carried;
        if (stack.isEmpty()) return false;
        Optional<ItemCardData> data = ItemCardCache.get(stack);
        if (data.isEmpty()) return false;

        long now = Util.getMillis();
        if (busySince != 0 && now - busySince < STUCK_AFTER_MS) return true; // one at a time
        busySince = now;

        WynnItem wynnItem = Models.Item.getWynnItem(stack).orElse(null);
        PageFlip flip = PageFlip.prepare(screen, slot, complete -> {
            pageFlip = null;
            if (Minecraft.getInstance().player == null) { // left the world mid-read
                busySince = 0;
                return;
            }
            // Screen closed or timed out: still share, with whatever pages did arrive.
            if (!complete) WynnWeaponViewer.LOGGER.warn("Couldn't read every tooltip page; sharing what's known");
            // Each page arrives as a new stack in the slot; use the live one if it's still this item.
            ItemStack live = slot.getItem();
            boolean same = wynnItem != null && Models.Item.getWynnItem(live).orElse(null) == wynnItem;
            ItemStack finalStack = same ? live : stack;
            capture(finalStack, ItemCardCache.get(finalStack).orElse(data.get()));
        });

        if (flip != null) {
            toast("reading", Component.literal(data.get().name()));
            pageFlip = flip;
            flip.start();
        } else {
            capture(stack, data.get());
        }
        return true;
    }

    private static void capture(ItemStack stack, ItemCardData data) {
        toast("working", Component.literal(data.name()));
        try {
            CardSnapshot.capture(stack, data,
                    image -> Util.ioPool().execute(() -> saveAndCopy(image, data)),
                    CardShare::fail);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    /** IO thread. */
    private static void saveAndCopy(NativeImage image, ItemCardData data) {
        Path file;
        try (image) {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(dir);
            file = uniqueFile(dir, fileName(data));
            image.writeToFile(file);
        } catch (IOException | RuntimeException e) {
            fail(e);
            return;
        }

        boolean copied = ClipboardImage.copyPng(file);
        WynnWeaponViewer.LOGGER.info("Shared item card {} (clipboard: {})", file, copied);
        String name = data.name();
        Minecraft.getInstance().execute(() -> {
            busySince = 0;
            if (copied) {
                toast("copied", Component.translatable(LANG + "copied.detail", name));
            } else {
                toast("saved", Component.translatable(LANG + "saved.detail"));
            }
        });
    }

    private static void fail(Throwable error) {
        WynnWeaponViewer.LOGGER.error("Couldn't share item card", error);
        Minecraft.getInstance().execute(() -> {
            busySince = 0;
            toast("failed", Component.translatable(LANG + "failed.detail"));
        });
    }

    private static void toast(String state, Component detail) {
        Minecraft mc = Minecraft.getInstance();
        SystemToast.addOrUpdate(mc.getToastManager(), TOAST, Component.translatable(LANG + state), detail);
    }

    /** {@code Divzer_95.3%}, or just the name when the item has no overall roll (unidentified, crafted...). */
    private static String fileName(ItemCardData data) {
        String safe = data.name().replaceAll("[^A-Za-z0-9 _'-]", "").trim().replace(' ', '_');
        String name = safe.isEmpty() ? "item" : safe;
        return data.overall().percentage()
                .map(pct -> name + String.format(Locale.ROOT, "_%.1f%%", pct))
                .orElse(name);
    }

    private static Path uniqueFile(Path dir, String base) {
        Path file = dir.resolve(base + ".png");
        for (int i = 2; Files.exists(file); i++) {
            file = dir.resolve(base + "_" + i + ".png");
        }
        return file;
    }
}
