package com.wynnweaponviewer.share;

import com.mojang.blaze3d.platform.NativeImage;
import com.wynntils.core.components.Models;
import com.wynntils.models.items.WynnItem;
import com.wynntils.screens.base.TextboxScreen;
import com.wynnweaponviewer.WynnWeaponViewer;
import com.wynnweaponviewer.accessor.ContainerScreenAccessor;
import com.wynnweaponviewer.card.ItemCardCache;
import com.wynnweaponviewer.card.ItemCardData;
import com.wynnweaponviewer.config.ModConfig;
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
 * Ctrl+C on an item in any inventory: renders its card (always the card, whatever the on-screen mode), then saves
 * it to {@code screenshots/} as {@code <Item_Name>_<roll>%.png} and/or copies it to the clipboard, per the Share
 * setting. Progress and the result show in a toast.
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
        PageFlip flip = !ModConfig.getConfig().shareReadAllPages ? null : PageFlip.prepare(screen, slot, complete -> {
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

    /** IO thread. Saves to {@code screenshots/} and/or copies, depending on the Share setting. */
    private static void saveAndCopy(NativeImage image, ItemCardData data) {
        ModConfig.ShareAction action = ModConfig.getConfig().shareAction;
        Path screenshots = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
        Path file;
        try (image) {
            // Copy only: the clipboard tools read from a file, so write a temporary one.
            if (action.keepsFile()) {
                Files.createDirectories(screenshots);
                file = uniqueFile(screenshots, fileName(data));
            } else {
                file = Files.createTempFile("wynn_weapon_bigger_card", ".png");
            }
            image.writeToFile(file);
        } catch (IOException | RuntimeException e) {
            fail(e);
            return;
        }

        boolean copied = action.copies() && ClipboardImage.copyPng(file);
        boolean saved = action.keepsFile();
        if (!action.keepsFile()) {
            try {
                if (copied) {
                    Files.deleteIfExists(file);
                } else {
                    // Copying failed: keep the image in screenshots/ rather than lose it.
                    Files.createDirectories(screenshots);
                    Path kept = uniqueFile(screenshots, fileName(data));
                    Files.move(file, kept);
                    file = kept;
                    saved = true;
                }
            } catch (IOException e) {
                WynnWeaponViewer.LOGGER.warn("Couldn't tidy up the shared card file {}", file, e);
            }
        }
        WynnWeaponViewer.LOGGER.info("Shared item card (clipboard: {}, saved: {})", copied, saved ? file : "no");

        String name = displayName(data);
        String savedName = file.getFileName().toString();
        boolean wantedCopy = action.copies();
        Minecraft.getInstance().execute(() -> {
            busySince = 0;
            if (copied) {
                toast("copied", Component.translatable(LANG + "copied.detail", name));
            } else if (wantedCopy) {
                toast("saved", Component.translatable(LANG + "saved.detail"));
            } else {
                toast("saved", Component.translatable(LANG + "saved.file", savedName));
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
        return roll(data).map(roll -> name + "_" + roll).orElse(name);
    }

    /** {@code Divzer 95.3%} for the toast, matching the file name's roll; just the name when there's no roll. */
    private static String displayName(ItemCardData data) {
        return roll(data).map(roll -> data.name() + " " + roll).orElse(data.name());
    }

    /** The overall roll as {@code 95.3%}, empty when the item has none. */
    private static Optional<String> roll(ItemCardData data) {
        return data.overall().percentage().map(pct -> String.format(Locale.ROOT, "%.1f%%", pct));
    }

    private static Path uniqueFile(Path dir, String base) {
        Path file = dir.resolve(base + ".png");
        for (int i = 2; Files.exists(file); i++) {
            file = dir.resolve(base + "_" + i + ".png");
        }
        return file;
    }
}
