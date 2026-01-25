package com.wynnweaponviewer.render;

import com.wynnweaponviewer.WynnWeaponViewer;
import com.wynnweaponviewer.accessor.ContainerScreenAccessor;
import com.wynnweaponviewer.accessor.RecipeBookScreenAccessor;
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

import java.util.List;
import java.util.regex.Pattern;

public class ZoomedItemRenderer {

    private static final int PADDING = 5;
    private static final AnimationState animationState = new AnimationState();
    private static final ItemStackRenderState itemRenderState = new ItemStackRenderState();

    private static final Pattern EXPIRED_PATTERN = Pattern.compile(".*Expired -.*");
    private static final Pattern FULFILLED_PATTERN = Pattern.compile(".*Fulfilled -.*");

    public static void beginFrame() {
        animationState.beginFrame();
    }

    public static void render(GuiGraphics graphics, AbstractContainerScreen<?> screen, int mouseX, int mouseY) {
        if (!WynnWeaponViewer.shouldRenderZoom()) {
            animationState.reset();
            return;
        }

        ModConfig.Config config = ModConfig.getConfig();

        if (!(screen instanceof ContainerScreenAccessor accessor)) {
            animationState.reset();
            return;
        }

        if (isLeftSideBlocked(screen, accessor) && config.horizontalAlignment == HorizontalAlignment.LEFT) {
            animationState.reset();
            return;
        }

        // Determine the item to render (cursor item takes priority, then hovered slot)
        ItemStack renderStack = determineRenderStack(screen, accessor, mouseX, mouseY);

        if (renderStack.isEmpty()) {
            animationState.reset();
            return;
        }

        animationState.update(renderStack);

        renderZoomedItem(graphics, screen, accessor, renderStack, config);
    }

    private static ItemStack determineRenderStack(AbstractContainerScreen<?> screen, ContainerScreenAccessor accessor, int mouseX, int mouseY) {
        // Check cursor stack first
        ItemStack cursorStack = screen.getMenu().getCarried();
        if (!cursorStack.isEmpty() && isValidWynncraftItem(cursorStack) && !isTradeMarketSoldItem(cursorStack)) {
            return cursorStack;
        }

        // Then check hovered slot
        Slot hoveredSlot = accessor.wynnweapon$getHoveredSlot();
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
                .orElse(false) || (config.enableUnidentified && isUnidentifiedItem(itemStack));
    }

    private static boolean isUnidentifiedItem(ItemStack itemStack) {
        return Models.Item.asWynnItem(itemStack, GearBoxItem.class).isPresent();
    }

    private static void renderZoomedItem(GuiGraphics graphics, AbstractContainerScreen<?> screen, ContainerScreenAccessor accessor, ItemStack stack, ModConfig.Config config) {
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
        int textGap = 12;
        float textY = itemY + itemSize + textGap;

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

        boolean showInfo = config.showItemInfo && animationState.shouldShowInfo(config.infoDelaySeconds);
        float textAlpha = showInfo ? easeOutCubic(animationState.getTextAppearProgress(config.infoDelaySeconds)) : 0;

        mc.getItemModelResolver().updateForTopItem(itemRenderState, stack, ItemDisplayContext.GUI, mc.level, mc.player, 0);

        GuiRenderState guiRenderState = ((GuiGraphicsAccessor) graphics).wynnweapon$getGuiRenderState();

        float x0 = finalItemX;
        float y0 = finalItemY;
        float x1 = finalItemX + scaledSize;
        float y1 = finalItemY + scaledSize;

        ZoomedItemRenderState state = new ZoomedItemRenderState(
                itemRenderState,
                x0, y0, x1, y1,
                idleAngle,
                alpha,
                scissor
        );

        ((GuiRenderStateAccessor) guiRenderState).wynnweapon$submitPictureInPicture(state);

        if (showInfo && textAlpha > 0) {
            renderItemInfo(graphics, mc, stack, finalTextX, finalTextY, itemSize, textAlpha * alpha);
        }
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
                                       float x, float y, int itemSize, float alpha) {
        if (alpha <= 0) return;

        Font font = mc.font;
        Item.TooltipContext tooltipContext = Item.TooltipContext.of(mc.level);
        List<Component> tooltip = stack.getTooltipLines(tooltipContext, mc.player, TooltipFlag.Default.NORMAL);
        if (tooltip.isEmpty()) return;

        int textAlphaInt = (int) (alpha * 255);

        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int availableHeight = (int) (screenHeight - y - 10);

        ItemEnchantments enchantments = stack.getEnchantments();
        int enchantmentCount = enchantments.size();
        int essentialLines = 1 + enchantmentCount;

        int maxWidth = Math.max(itemSize, 100);

        int totalHeight = calculateTextHeight(font, tooltip, tooltip.size(), maxWidth);
        boolean showExtras = totalHeight <= availableHeight;
        int linesToRender = showExtras ? tooltip.size() : essentialLines;

        float currentY = y;
        int sourceLineIndex = 0;

        for (int i = 0; i < tooltip.size() && sourceLineIndex < linesToRender; i++) {
            Component line = tooltip.get(i);
            String plainText = line.getString();

            if (plainText.trim().isEmpty()) {
                if (i < linesToRender) {
                    currentY += 6;
                }
                continue;
            }

            if (i >= essentialLines && !showExtras) {
                break;
            }

            int baseColor;
            float lineScale;

            if (i == 0) {
                baseColor = 0xFFFFFF;
                lineScale = 1.0f;
            } else if (i < essentialLines) {
                baseColor = 0xAAAAAA;
                lineScale = 1.0f;
            } else {
                baseColor = 0x888888;
                lineScale = 0.75f;
            }

            int color = ARGB.color(textAlphaInt, ARGB.red(baseColor), ARGB.green(baseColor), ARGB.blue(baseColor));
            int scaledMaxWidth = (int) (maxWidth / lineScale);

            List<FormattedCharSequence> wrappedLines = font.split(line, scaledMaxWidth);

            for (FormattedCharSequence wrappedLine : wrappedLines) {
                int lineWidth = font.width(wrappedLine);
                float drawX = x + (itemSize - lineWidth * lineScale) / 2;

                if (lineScale != 1.0f) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(drawX, currentY);
                    graphics.pose().scale(lineScale, lineScale);
                    graphics.drawString(font, wrappedLine, 0, 0, color, false);
                    graphics.pose().popMatrix();
                    currentY += (int) (10 * lineScale);
                } else {
                    graphics.drawString(font, wrappedLine, (int) drawX, (int) currentY, color, false);
                    currentY += 10;
                }
            }

            sourceLineIndex++;
        }
    }

    private static int calculateTextHeight(Font font, List<Component> tooltip, int maxSourceLines, int maxWidth) {
        int height = 0;

        for (int i = 0; i < Math.min(tooltip.size(), maxSourceLines); i++) {
            Component line = tooltip.get(i);
            String plainText = line.getString();

            if (plainText.trim().isEmpty()) {
                height += 6;
                continue;
            }

            float lineScale = (i == 0 || i < maxSourceLines) ? 1.0f : 0.75f;
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
        animationState.reset();
    }

    public static void onScreenClose() {
        animationState.reset();
    }
}
