package com.wynnweaponviewer.render;

import com.wynnweaponviewer.WynnWeaponViewer;
import com.wynnweaponviewer.accessor.ContainerScreenAccessor;
import com.wynnweaponviewer.accessor.RecipeBookScreenAccessor;
import com.wynnweaponviewer.card.ItemCardCache;
import com.wynnweaponviewer.card.ItemCardData;
import com.wynnweaponviewer.render.card.CardBody;
import com.wynnweaponviewer.render.card.CardLayout;
import com.wynnweaponviewer.render.card.FloatingHeader;
import com.wynnweaponviewer.render.card.ItemCardRenderer;
import com.wynnweaponviewer.render.card.ShareHint;
import com.wynnweaponviewer.render.card.WynnGlyphs;
import com.wynnweaponviewer.mixin.GuiGraphicsAccessor;
import com.wynnweaponviewer.mixin.GuiRenderStateAccessor;
import com.wynnweaponviewer.config.ModConfig;
import com.wynnweaponviewer.HorizontalAlignment;
import com.wynntils.utils.mc.LoreUtils;
import com.wynntils.core.text.StyledText;
import com.wynntils.core.components.Models;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.gear.type.GearType;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynntils.models.items.items.game.AmplifierItem;
import com.wynntils.models.items.items.game.InsulatorItem;
import com.wynntils.models.items.items.game.SimulatorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ZoomedItemRenderer {

    private static final int PADDING = 5;
    private static final AnimationState animationState = new AnimationState();

    /** How long an emptied slot keeps its item on screen; covers the offhand round trip of a tooltip page cycle. */
    private static final long SWAP_GRACE_MS = 150;
    private static @Nullable Slot swapSlot;
    private static ItemStack swapStack = ItemStack.EMPTY;
    private static long swapSeenAtMs;
    private static final ItemStackRenderState itemRenderState = new ItemStackRenderState();

    private static final Pattern EXPIRED_PATTERN = Pattern.compile(".*Expired -.*");
    private static final Pattern FULFILLED_PATTERN = Pattern.compile(".*Fulfilled -.*");

    // Floating mode (the default) gives the model just the card's header under it; card mode puts it in a card.
    private static FloatingHeader floatingHeader;
    private static ItemCardData floatingHeaderData;
    private static boolean floatingHeaderWithPack;
    private static ItemCardData layoutData;
    private static boolean layoutWithPack;
    /** The config revision the cached layouts were built with (card contents are configurable). */
    private static int layoutConfigRevision;
    private static final java.util.EnumMap<CardBody.Density, CardLayout> cachedLayouts =
            new java.util.EnumMap<>(CardBody.Density.class);
    /** Never shrink the card below the size of a normal tooltip; trim content instead (see CardBody.Density). */
    private static final float MIN_CARD_SCALE = 1.0f;
    /** How much of the card's model window the model's square takes up. */
    private static final float MODEL_WINDOW_FILL = 0.98f;
    /**
     * The model is drawn this much larger inside the card's window than the floating model's default fit, which
     * leaves room for Wynncraft's oversized weapon models. Big models may touch the window's edges.
     */
    private static final float CARD_MODEL_ZOOM = 1.3f;

    public static void beginFrame() {
        animationState.beginFrame();
    }

    public static void render(GuiGraphics graphics, AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
        if (!WynnWeaponViewer.shouldRenderZoom()) {
            resetHover();
            return;
        }

        ModConfig.Config config = ModConfig.getConfig();

        if (!(screen instanceof ContainerScreenAccessor accessor)) {
            resetHover();
            return;
        }

        if (isLeftSideBlocked(screen, accessor) && config.horizontalAlignment == HorizontalAlignment.LEFT) {
            resetHover();
            return;
        }

        // Determine the item to render (cursor item takes priority, then hovered slot)
        Slot hoveredSlot = accessor.wynnweapon$getHoveredSlot();
        ItemStack renderStack = determineRenderStack(screen, hoveredSlot);
        boolean carried = !renderStack.isEmpty() && renderStack == screen.getMenu().getCarried();
        long now = System.currentTimeMillis();

        if (renderStack.isEmpty()) {
            renderStack = heldThroughSwap(hoveredSlot, now);
            if (renderStack.isEmpty()) {
                resetHover();
                return;
            }
        } else if (!carried) {
            swapSlot = hoveredSlot;
            swapStack = renderStack;
            swapSeenAtMs = now;
        }

        animationState.update(renderStack, carried ? null : hoveredSlot);

        // Build/refresh the card snapshot from the live stack (copies lose the Wynntils annotation).
        Optional<ItemCardData> cardData = ItemCardCache.get(renderStack);

        renderZoomedItem(graphics, screen, accessor, renderStack, cardData, config);
    }

    /**
     * Wynncraft cycles an item's tooltip page when the player presses the swap-hands key over it: the item hops
     * into the offhand and the server sends it straight back with the next page's lore. For that moment the slot is
     * empty, so while the mouse stays on the same slot we keep showing the last item instead of starting over.
     */
    private static ItemStack heldThroughSwap(@Nullable Slot hoveredSlot, long now) {
        if (hoveredSlot != null && hoveredSlot == swapSlot && now - swapSeenAtMs < SWAP_GRACE_MS) {
            return swapStack;
        }
        return ItemStack.EMPTY;
    }

    private static void resetHover() {
        animationState.reset();
        swapSlot = null;
        swapStack = ItemStack.EMPTY;
        swapSeenAtMs = 0;
    }

    private static ItemStack determineRenderStack(AbstractContainerScreen<?> screen, @Nullable Slot hoveredSlot) {
        // Check cursor stack first
        ItemStack cursorStack = screen.getMenu().getCarried();
        if (!cursorStack.isEmpty() && isValidWynncraftItem(cursorStack) && !isTradeMarketSoldItem(cursorStack)) {
            return cursorStack;
        }

        // Then check hovered slot
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            ItemStack slotStack = hoveredSlot.getItem();
            if (!slotStack.isEmpty() && isValidWynncraftItem(slotStack) && !isTradeMarketSoldItem(slotStack)) {
                return slotStack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static boolean isTradeMarketSoldItem(ItemStack itemStack) {
        List<StyledText> lore = LoreUtils.getLore(itemStack);
        if (!lore.isEmpty()) {
            StyledText firstLine = lore.get(0);
            String plainText = firstLine.getStringWithoutFormatting();
            return EXPIRED_PATTERN.matcher(plainText).matches() ||
                    FULFILLED_PATTERN.matcher(plainText).matches();
        }
        return false;
    }

    private static boolean isValidWynncraftItem(ItemStack itemStack) {
        ModConfig.Config config = ModConfig.getConfig();

        return Models.Item.asWynnItem(itemStack, GearItem.class)
                .map(gearItem -> {
                    GearType gearType = gearItem.getGearType();
                    if (gearType.isWeapon()) {
                        return config.enableWeapons;
                    } else if (gearType.isArmor()) {
                        return config.enableArmor;
                    } else if (gearType == GearType.RING || gearType == GearType.BRACELET || gearType == GearType.NECKLACE) {
                        return config.enableAccessories;
                    }
                    return false;
                })
                .orElse(false)
                || (config.enableUnidentified && isUnidentifiedItem(itemStack))
                || (config.enableCorkianAmplifiers && isCorkianItem(itemStack));
    }

    /** Corkian Amplifiers, Insulators and Simulators. */
    private static boolean isCorkianItem(ItemStack itemStack) {
        return Models.Item.asWynnItem(itemStack, AmplifierItem.class).isPresent()
                || Models.Item.asWynnItem(itemStack, InsulatorItem.class).isPresent()
                || Models.Item.asWynnItem(itemStack, SimulatorItem.class).isPresent();
    }

    private static boolean isUnidentifiedItem(ItemStack itemStack) {
        return Models.Item.asWynnItem(itemStack, GearBoxItem.class).isPresent();
    }

    private static void renderZoomedItem(GuiGraphics graphics, AbstractContainerScreen<?> screen, ContainerScreenAccessor accessor, ItemStack stack, Optional<ItemCardData> cardData, ModConfig.Config config) {
        Minecraft mc = Minecraft.getInstance();

        int guiLeft = accessor.wynnweapon$getLeftPos();
        int guiTop = accessor.wynnweapon$getTopPos();
        int guiHeight = accessor.wynnweapon$getImageHeight();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        boolean hasAppearAnimation = config.appearAnimation != ModConfig.AppearAnimation.NONE;
        int delayMs = config.appearDelayMs;
        float progress = animationState.getAppearProgress(hasAppearAnimation, delayMs);
        if (progress <= 0) return;

        if (config.displayMode == ModConfig.DisplayMode.CARD && cardData.isPresent()) {
            renderCardComposition(graphics, accessor, stack, cardData.get(), config, progress, hasAppearAnimation, delayMs);
            return;
        }

        // Calculate item size based on scale setting
        float scale = config.scale;
        int availableWidth = config.horizontalAlignment == HorizontalAlignment.LEFT
            ? guiLeft - PADDING * 2
            : screenWidth - (guiLeft + accessor.wynnweapon$getImageWidth()) - PADDING * 2;
        float size = Math.min(availableWidth * scale, screenHeight * scale);
        int itemSize = Math.max((int) size, 32);
        float itemScale = itemSize / 16.0f;

        // Calculate position based on alignment
        float itemX, itemY;
        if (config.horizontalAlignment == HorizontalAlignment.LEFT) {
            itemX = (guiLeft - itemSize) / 2.0f;
        } else {
            int rightEdge = guiLeft + accessor.wynnweapon$getImageWidth();
            itemX = rightEdge + (screenWidth - rightEdge - itemSize) / 2.0f;
        }
        itemY = (screenHeight - itemSize) / 2.0f;

        // Apply offsets
        itemX += config.xOffset;
        itemY += config.yOffset;

        float textX = itemX;
        int textGap = 8;
        float textY = itemY + itemSize - (itemSize * 0.07f) - textGap;

        float eased = easeOutCubic(progress);
        float alpha = 1.0f;
        float offsetX = 0, offsetY = 0;
        float animScale = 1.0f;
        ScreenRectangle scissor = null;

        switch (config.appearAnimation) {
            case FADE -> alpha = eased;
            case SCALE -> animScale = eased;
            case SLIDE_LEFT -> offsetX = (1 - eased) * -itemSize;
            case SLIDE_RIGHT -> {
                offsetX = (1 - eased) * itemSize;
                if (config.horizontalAlignment == HorizontalAlignment.LEFT) {
                    float distanceToGui = guiLeft - itemX + PADDING;
                    offsetX = (1 - eased) * distanceToGui;
                    scissor = new ScreenRectangle(0, 0, guiLeft, screenHeight);
                }
            }
            case SLIDE_TOP -> offsetY = (1 - eased) * -itemSize;
            case SLIDE_BOTTOM -> offsetY = (1 - eased) * itemSize;
            default -> {}
        }

        float idleTime = animationState.getIdleAnimationTime(hasAppearAnimation, delayMs);
        float idleAngle = 0;
        float idleScale = 1.0f;

        switch (config.idleAnimation) {
            case SWING -> idleAngle = (float) Math.sin(idleTime * 0.8) * 2.0f;
            case PULSE -> idleScale = 1.0f + (float) Math.sin(idleTime * 1.2) * 0.015f;
            default -> {}
        }

        float finalScale = animScale * idleScale;
        float scaledSize = itemSize * finalScale;
        float finalItemX = itemX + offsetX + (itemSize - scaledSize) / 2.0f;
        float finalItemY = itemY + offsetY + (itemSize - scaledSize) / 2.0f;
        float finalTextX = textX + offsetX;
        float finalTextY = textY + offsetY;
        float baseTextScale = config.scale * 2.5f;
        float nameLineHeight = 10 * baseTextScale;
        float finalNameAboveY = finalItemY - textGap - nameLineHeight;

        boolean showInfo = config.showItemInfo && animationState.shouldShowInfo(config.infoDelaySeconds);
        float textAlpha = showInfo ? easeOutCubic(animationState.getTextAppearProgress(config.infoDelaySeconds)) : 0;

        submitModel(graphics, itemRenderState, stack, finalItemX, finalItemY, scaledSize, 1f, idleAngle, alpha, scissor);

        if (cardData.isPresent()) {
            // Wynntils-parsed header (name + overall roll + badges) instead of the raw tooltip lines.
            float centerX = finalTextX + itemSize / 2f;
            float hintTop = finalTextY;
            if (config.showItemInfo) {
                hintTop = renderFloatingHeader(graphics, cardData.get(), config, centerX, finalTextY,
                        finalItemY - textGap, availableWidth, alpha, idleTime, scissor);
            }
            ShareHint hint = ShareHint.forItem(stack);
            if (hint != null) {
                float hintScale = ItemCardRenderer.snapScale(Math.min(config.scale * 2.5f,
                        (float) availableWidth / hint.width()));
                // Follows on from the header's cascade, or leads it if the header is hidden.
                float start = config.showItemInfo
                        ? ItemCardRenderer.trailingStart(floatingHeaderFor(cardData.get())) : 0f;
                if (scissor != null) {
                    graphics.enableScissor(scissor.left(), scissor.top(), scissor.right(), scissor.bottom());
                }
                hint.render(graphics, centerX, hintTop, hintScale, alpha, revealTime(idleTime, config), start,
                        config.textCascade);
                if (scissor != null) {
                    graphics.disableScissor();
                }
            }
        } else if (showInfo && textAlpha > 0) {
            renderItemInfo(graphics, mc, stack, finalTextX, finalTextY, finalNameAboveY, itemSize, textAlpha * alpha, config.scale, config.itemNameAbove);
        }
    }

    /**
     * Queues the 3D model as a picture-in-picture texture covering the given GUI-space square.
     *
     * @param zoom how much larger than the default fit the model is drawn inside that square
     */
    private static void submitModel(GuiGraphics graphics, ItemStackRenderState renderState, ItemStack stack,
                                    float x, float y, float size, float zoom, float angle, float alpha,
                                    ScreenRectangle scissor) {
        Minecraft mc = Minecraft.getInstance();
        mc.getItemModelResolver().updateForTopItem(renderState, stack, ItemDisplayContext.GUI, mc.level, mc.player, 0);
        GuiRenderState guiRenderState = ((GuiGraphicsAccessor) graphics).wynnweapon$getGuiRenderState();
        ZoomedItemRenderState state = new ZoomedItemRenderState(
                renderState, x, y, x + size, y + size, zoom, angle, alpha, scissor);
        ((GuiRenderStateAccessor) guiRenderState).wynnweapon$submitPictureInPicture(state);
    }

    /**
     * Card mode: the card is the layout root and the model is drawn inside its window.
     * Submission order matters - frame, then model, then text - see {@link ItemCardRenderer}.
     */
    private static void renderCardComposition(GuiGraphics graphics, ContainerScreenAccessor accessor, ItemStack stack,
                                              ItemCardData data, ModConfig.Config config, float progress,
                                              boolean hasAppearAnimation, int delayMs) {
        Minecraft mc = Minecraft.getInstance();
        int guiLeft = accessor.wynnweapon$getLeftPos();
        int guiRight = guiLeft + accessor.wynnweapon$getImageWidth();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        boolean leftAligned = config.horizontalAlignment == HorizontalAlignment.LEFT;

        // The free space beside the inventory.
        float regionX = leftAligned ? PADDING : guiRight + PADDING;
        float regionW = leftAligned ? guiLeft - PADDING * 2 : screenWidth - guiRight - PADDING * 2;
        float regionY = PADDING;
        float regionH = screenHeight - PADDING * 2;
        if (regionW <= 0 || regionH <= 0) return;

        // One scale per screen, chosen from a typical card rather than this item, so every card is the same size.
        // config.scale (0.1-1) sizes it within the free space: 1.0 fills it, the default 0.4 gives 70%. Never
        // smaller than a normal tooltip unless nothing else fits.
        float sizeFactor = 0.5f + 0.5f * config.scale;
        float baseScale = Math.max(MIN_CARD_SCALE, ItemCardRenderer.snapScale(sizeFactor * Math.min(
                regionW / CardLayout.REFERENCE_OUTER_WIDTH, regionH / CardLayout.REFERENCE_OUTER_HEIGHT)));

        // The share hint goes under the card, so leave room for it (in card pixels: it's drawn at the card's scale).
        ShareHint hint = ShareHint.forItem(stack);
        int hintHeight = hint == null ? 0 : hint.height();

        // Per item, the model window takes up the slack: as tall as allowed if there's room, shorter if the item
        // has many rows. Only when even the smallest window can't make room are rows trimmed (CardBody.Density).
        CardLayout layout = null;
        float scale = baseScale;
        for (CardBody.Density density : CardBody.Density.values()) {
            CardLayout candidate = layoutFor(data, density);
            float s = Math.min(baseScale, ItemCardRenderer.snapScale(regionW / candidate.outerWidth())); // long names
            int room = (int) Math.floor(regionH / s) - candidate.outerHeightWithoutWindow() - hintHeight;
            if (room >= candidate.minWindowHeight()) {
                layout = candidate.withWindowHeight(room);
                scale = s;
                break;
            }
        }
        if (layout == null) {
            // Even the leanest card doesn't fit at this size (tiny window or huge GUI scale): shrink it.
            CardLayout lean = layoutFor(data, CardBody.Density.MINIMAL);
            layout = lean.withWindowHeight(lean.minWindowHeight());
            scale = ItemCardRenderer.snapScale(Math.min(regionW / layout.outerWidth(),
                    regionH / (layout.outerHeight() + hintHeight)));
        }
        float cardW = layout.outerWidth() * scale;
        float cardH = layout.outerHeight() * scale;
        float left = regionX + (regionW - cardW) / 2f + config.xOffset;
        float top = regionY + (regionH - cardH - hintHeight * scale) / 2f + config.yOffset;

        float eased = easeOutCubic(progress);
        float alpha = 1.0f;
        ScreenRectangle scissor = null;
        switch (config.appearAnimation) {
            case FADE -> alpha = eased;
            case SCALE -> {
                // Unsnapped while animating; it settles on the pixel-exact scale when eased reaches 1.
                float animated = scale * eased;
                if (animated < 0.01f) return;
                left += (cardW - layout.outerWidth() * animated) / 2f;
                top += (cardH - layout.outerHeight() * animated) / 2f;
                scale = animated;
            }
            case SLIDE_LEFT -> left += (1 - eased) * -cardW;
            case SLIDE_RIGHT -> {
                if (leftAligned) {
                    // Slide out from behind the inventory, clipped at its edge.
                    left += (1 - eased) * (guiLeft - left + PADDING);
                    scissor = new ScreenRectangle(0, 0, guiLeft, screenHeight);
                } else {
                    left += (1 - eased) * cardW;
                }
            }
            case SLIDE_TOP -> top += (1 - eased) * -cardH;
            case SLIDE_BOTTOM -> top += (1 - eased) * cardH;
            default -> {}
        }

        // The card holds the model still unless the user opts in to the idle animation for it too.
        float idleTime = animationState.getIdleAnimationTime(hasAppearAnimation, delayMs);
        float idleAngle = 0;
        float idleScale = 1.0f;
        ModConfig.IdleAnimation idle = config.cardIdleAnimation ? config.idleAnimation : ModConfig.IdleAnimation.NONE;
        switch (idle) {
            case SWING -> idleAngle = (float) Math.sin(idleTime * 0.8) * 2.0f;
            case PULSE -> idleScale = 1.0f + (float) Math.sin(idleTime * 1.2) * 0.015f;
            default -> {}
        }

        ItemCardRenderer.Placement placement = new ItemCardRenderer.Placement(left, top, scale);
        if (scissor != null) {
            graphics.enableScissor(scissor.left(), scissor.top(), scissor.right(), scissor.bottom());
        }

        // Text cascades in from when the card appears (or from the info delay, if that ends later).
        float revealTime = revealTime(idleTime, config);
        drawCard(graphics, itemRenderState, stack, layout, placement, alpha, idleAngle, idleScale,
                revealTime, config.textCascade, scissor);

        if (hint != null) {
            float hintScale = Math.min(scale, ItemCardRenderer.snapScale(regionW / hint.width()));
            hint.render(graphics, left + placement.width(layout) / 2f, top + placement.height(layout), hintScale,
                    alpha, revealTime, ItemCardRenderer.trailingStart(layout), config.textCascade);
        }

        if (scissor != null) {
            graphics.disableScissor();
        }
    }

    /** Frame, then model, then text - see {@link ItemCardRenderer} for why the order matters. */
    private static void drawCard(GuiGraphics graphics, ItemStackRenderState renderState, ItemStack stack,
                                 CardLayout layout, ItemCardRenderer.Placement placement, float alpha,
                                 float idleAngle, float idleScale, float revealTime, boolean stagger,
                                 ScreenRectangle scissor) {
        // 1. frame + model window
        ItemCardRenderer.renderBackground(graphics, layout, placement, alpha);

        // 2. model, centred in the window
        float[] window = ItemCardRenderer.windowRect(layout, placement);
        float windowW = window[2] - window[0];
        float windowH = window[3] - window[1];
        float modelSize = Math.min(windowW, windowH) * MODEL_WINDOW_FILL * idleScale;
        float modelX = window[0] + (windowW - modelSize) / 2f;
        float modelY = window[1] + (windowH - modelSize) / 2f;
        submitModel(graphics, renderState, stack, modelX, modelY, modelSize, CARD_MODEL_ZOOM, idleAngle, alpha,
                scissor);

        // 3. header and body text
        ItemCardRenderer.renderText(graphics, layout, placement, alpha, revealTime, stagger);
    }

    /**
     * Card-less mode's text: the card header floating under the model (or above it with itemNameAbove), centred on
     * it and sized like the old item info text, shrunk if it would be wider than the free space.
     *
     * @return where whatever goes under the model should start: below the header, or {@code belowTop} if it's above
     */
    private static float renderFloatingHeader(GuiGraphics graphics, ItemCardData data, ModConfig.Config config,
                                             float centerX, float belowTop, float aboveBottom, int maxWidth,
                                             float alpha, float idleTime, ScreenRectangle scissor) {
        FloatingHeader header = floatingHeaderFor(data);
        float scale = ItemCardRenderer.snapScale(Math.min(config.scale * 2.5f, (float) maxWidth / header.width()));
        float width = header.width() * scale;
        float height = header.height() * scale;
        float left = centerX - width / 2f + header.overhang() * scale;
        float top = config.itemNameAbove ? aboveBottom - height : belowTop;

        // Same reveal as the card: from when the model appears, or from the info delay if that ends later.
        float revealTime = revealTime(idleTime, config);
        if (scissor != null) {
            graphics.enableScissor(scissor.left(), scissor.top(), scissor.right(), scissor.bottom());
        }
        ItemCardRenderer.renderFloatingHeader(graphics, header, left, top, scale, alpha, revealTime,
                config.textCascade);
        if (scissor != null) {
            graphics.disableScissor();
        }
        return config.itemNameAbove ? belowTop : top + height;
    }

    /** Seconds since the item's text started appearing: when the model did, or after the info delay if later. */
    private static float revealTime(float idleTime, ModConfig.Config config) {
        return Math.min(idleTime, animationState.getHoverDuration() - config.infoDelaySeconds);
    }

    private static FloatingHeader floatingHeaderFor(ItemCardData data) {
        boolean packActive = WynnGlyphs.isPackActive();
        if (floatingHeader == null || data != floatingHeaderData || packActive != floatingHeaderWithPack) {
            floatingHeader = FloatingHeader.build(data);
            floatingHeaderData = data;
            floatingHeaderWithPack = packActive;
        }
        return floatingHeader;
    }

    /** Layouts are rebuilt only when the card data, the resource pack state or the settings change. */
    private static CardLayout layoutFor(ItemCardData data, CardBody.Density density) {
        boolean packActive = WynnGlyphs.isPackActive();
        int revision = ModConfig.revision();
        if (data != layoutData || packActive != layoutWithPack || revision != layoutConfigRevision) {
            cachedLayouts.clear();
            layoutData = data;
            layoutWithPack = packActive;
            layoutConfigRevision = revision;
        }
        return cachedLayouts.computeIfAbsent(density, d -> CardLayout.build(data, d));
    }

    private static void invalidateLayouts() {
        cachedLayouts.clear();
        layoutData = null;
        floatingHeader = null;
    }

    private static boolean isLeftSideBlocked(AbstractContainerScreen<?> screen, ContainerScreenAccessor accessor) {
        if (screen instanceof RecipeBookScreenAccessor recipeAccessor) {
            if (recipeAccessor.wynnweapon$isRecipeBookVisible()) {
                return true;
            }
        }

        int guiLeft = accessor.wynnweapon$getLeftPos();
        int minSpace = 50;
        return guiLeft < minSpace;
    }

    private static void renderItemInfo(GuiGraphics graphics, Minecraft mc, ItemStack stack,
                                       float x, float y, float nameAboveY, int itemSize, float alpha, float zoomScale, boolean nameAbove) {
        if (alpha <= 0) return;

        Font font = mc.font;
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(mc.level);
        List<Component> tooltip = stack.getTooltipLines(tooltipContext, mc.player, TooltipFlag.Default.NORMAL);
        if (tooltip.isEmpty()) return;

        int textAlphaInt = (int) (alpha * 255);

        // Base text scale influenced by zoom scale setting
        float baseTextScale = zoomScale * 2.5f; // Multiplier to make text reasonably sized

        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int availableHeight = (int) (screenHeight - y - 10);

        ItemEnchantments enchantments = stack.getEnchantments();
        int enchantmentCount = enchantments.size();
        int essentialLines = 1 + enchantmentCount;

        int maxWidth = Math.max(itemSize, 100);

        int totalHeight = calculateTextHeight(font, tooltip, tooltip.size(), maxWidth, baseTextScale);
        boolean showExtras = totalHeight <= availableHeight;
        int linesToRender = showExtras ? tooltip.size() : essentialLines;

        // If name above is enabled, render line 0 (item name) above the item separately
        int startI = 0;
        int sourceLineIndex = 0;
        if (nameAbove && !tooltip.isEmpty()) {
            Component nameLine = tooltip.get(0);
            String plainText = nameLine.getString();
            if (!plainText.trim().isEmpty()) {
                float lineScale = baseTextScale;
                int color = ARGB.color(textAlphaInt, 0xFF, 0xFF, 0xFF);
                int scaledMaxWidth = (int) (maxWidth / lineScale);
                List<FormattedCharSequence> wrappedLines = font.split(nameLine, scaledMaxWidth);
                float ny = nameAboveY;
                for (FormattedCharSequence wrappedLine : wrappedLines) {
                    int lineWidth = font.width(wrappedLine);
                    float drawX = x + (itemSize - lineWidth * lineScale) / 2;
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(drawX, ny);
                    graphics.pose().scale(lineScale, lineScale);
                    graphics.drawString(font, wrappedLine, 0, 0, color, false);
                    graphics.pose().popMatrix();
                    ny += (int) (10 * lineScale);
                }
                startI = 1;
                sourceLineIndex = 1;
            }
        }

        float currentY = y;

        for (int i = startI; i < tooltip.size() && sourceLineIndex < linesToRender; i++) {
            Component line = tooltip.get(i);
            String plainText = line.getString();

            if (plainText.trim().isEmpty()) {
                if (i < linesToRender) {
                    currentY += 6 * baseTextScale;
                }
                continue;
            }

            if (i >= essentialLines && !showExtras) {
                break;
            }

            int baseColor;
            float lineScaleMultiplier;

            if (i == 0) {
                baseColor = 0xFFFFFF;
                lineScaleMultiplier = 1.0f;
            } else if (i < essentialLines) {
                baseColor = 0xAAAAAA;
                lineScaleMultiplier = 1.0f;
            } else {
                baseColor = 0x888888;
                lineScaleMultiplier = 0.75f;
            }

            float lineScale = baseTextScale * lineScaleMultiplier;
            int color = ARGB.color(textAlphaInt, ARGB.red(baseColor), ARGB.green(baseColor), ARGB.blue(baseColor));
            int scaledMaxWidth = (int) (maxWidth / lineScale);

            List<FormattedCharSequence> wrappedLines = font.split(line, scaledMaxWidth);

            for (FormattedCharSequence wrappedLine : wrappedLines) {
                int lineWidth = font.width(wrappedLine);
                float drawX = x + (itemSize - lineWidth * lineScale) / 2;

                graphics.pose().pushMatrix();
                graphics.pose().translate(drawX, currentY);
                graphics.pose().scale(lineScale, lineScale);
                graphics.drawString(font, wrappedLine, 0, 0, color, false);
                graphics.pose().popMatrix();
                currentY += (int) (10 * lineScale);
            }

            sourceLineIndex++;
        }
    }

    private static int calculateTextHeight(Font font, List<Component> tooltip, int maxSourceLines, int maxWidth, float baseTextScale) {
        int height = 0;

        for (int i = 0; i < Math.min(tooltip.size(), maxSourceLines); i++) {
            Component line = tooltip.get(i);
            String plainText = line.getString();

            if (plainText.trim().isEmpty()) {
                height += (int) (6 * baseTextScale);
                continue;
            }

            float lineScaleMultiplier = (i == 0 || i < maxSourceLines) ? 1.0f : 0.75f;
            float lineScale = baseTextScale * lineScaleMultiplier;
            int scaledMaxWidth = (int) (maxWidth / lineScale);

            List<FormattedCharSequence> wrappedLines = font.split(line, scaledMaxWidth);
            height += (int) (10 * lineScale) * wrappedLines.size();
        }

        return height;
    }

    private static float easeOutCubic(float t) {
        return 1 - (float) Math.pow(1 - t, 3);
    }

    public static void cleanup() {
        resetHover();
        ItemCardCache.clear();
        invalidateLayouts();
    }

    public static void onScreenClose() {
        resetHover();
        ItemCardCache.clear();
        invalidateLayouts();
    }
}
