package com.wynnweaponviewer.mixin;

import com.wynnweaponviewer.WynnWeaponViewer;
import com.wynnweaponviewer.config.ModConfig;
import com.wynnweaponviewer.HorizontalAlignment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.wynntils.core.components.Models;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.gear.type.GearType;
import com.wynntils.models.items.items.game.GearBoxItem;

import java.util.Arrays;
import java.util.List;

@Mixin(HandledScreen.class)
public abstract class MixinHandledScreen extends Screen {
	@Shadow
	protected abstract Slot getSlotAt(double xPosition, double yPosition);

	@Shadow
	protected abstract void drawItem(DrawContext drawContext, ItemStack stack, int xPosition, int yPosition, String amountText);

	@Shadow
	protected int x;

	@Shadow @Final protected ScreenHandler handler;

	@Unique
	private static final List<String> CORKIAN_AMPLIFIER_NAMES = Arrays.asList(
			"Corkian Insulator",
			"Corkian Amplifier I",
			"Corkian Amplifier II",
			"Corkian Amplifier III",
			"Corkian Simulator"
	);

	protected MixinHandledScreen(Text title) {
		super(title);
	}

	@Inject(method = "render", at = @At("RETURN"))
	public void onRendered(DrawContext drawContext, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (WynnWeaponViewer.shouldRenderZoom()) {
			ItemStack renderStack = determineRenderStack(mouseX, mouseY);
			if (!renderStack.isEmpty()) {
				renderZoomedItem(drawContext, renderStack);
			}
		}

		// Always draw the tooltip last, ensuring it's on top
		Slot hoveredSlot = getSlotAt(mouseX, mouseY);
		if (hoveredSlot != null && !hoveredSlot.getStack().isEmpty()) {
			drawContext.drawItemTooltip(this.textRenderer, hoveredSlot.getStack(), mouseX, mouseY);
		}
	}

	@Unique
	private ItemStack determineRenderStack(int mouseX, int mouseY) {
		ItemStack cursorStack = handler.getCursorStack();
		if (!cursorStack.isEmpty() && isValidWynncraftItem(cursorStack)) {
			return cursorStack;
		}

		Slot slot = getSlotAt(mouseX, mouseY);
		if (slot != null) {
			ItemStack slotStack = slot.getStack();
			if (!slotStack.isEmpty() && isValidWynncraftItem(slotStack)) {
				return slotStack;
			}
		}

		return ItemStack.EMPTY;
	}

	@Unique
	private boolean isValidWynncraftItem(ItemStack itemStack) {
		return Models.Item.asWynnItem(itemStack, GearItem.class)
				.map(gearItem -> {
					GearType gearType = gearItem.getGearType();
					if (gearType.isWeapon()) {
						return ModConfig.isWeaponsEnabled();
					} else if (gearType.isArmor()) {
						return ModConfig.isArmorEnabled();
					} else if (gearType == GearType.RING || gearType == GearType.BRACELET || gearType == GearType.NECKLACE) {
						return ModConfig.isAccessoriesEnabled();
					}
					return false;
				})
				.orElse(false)
				|| (ModConfig.isUnidentifiedEnabled() && isUnidentifiedItem(itemStack))
				|| (ModConfig.isCorkianAmplifiersEnabled() && isCorkianAmplifier(itemStack));
	}

	@Unique
	private boolean isUnidentifiedItem(ItemStack itemStack) {
		return Models.Item.asWynnItem(itemStack, GearBoxItem.class).isPresent();
	}

	@Unique
	private boolean isCorkianAmplifier(ItemStack itemStack) {
		String displayName = itemStack.getName().getString();
		return CORKIAN_AMPLIFIER_NAMES.stream().anyMatch(displayName::contains);
	}

	@Unique
	private void renderZoomedItem(DrawContext drawContext, ItemStack stack) {
		float scale = ModConfig.getScale();
		float size = Math.min(x * scale, this.height * scale);
		float itemScale = size / 16;

		double ix = (x - size) / 2F;
		if (ModConfig.getHorizontalAlignment() == HorizontalAlignment.RIGHT) {
			ix = width - ix - size;
		}
		double iy = (height - size) / 2F;

		ix += ModConfig.getXOffset();
		iy += ModConfig.getYOffset();

		drawContext.getMatrices().push();
		drawContext.getMatrices().translate(ix, iy, 0);  // Set z-index to 0
		drawContext.getMatrices().scale(itemScale, itemScale, 1f);
		drawItem(drawContext, stack, 0, 0, "");
		drawContext.getMatrices().pop();
	}
}