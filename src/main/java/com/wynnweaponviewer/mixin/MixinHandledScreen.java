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

@Mixin(HandledScreen.class)
public abstract class MixinHandledScreen extends Screen {
	@Shadow
	protected abstract Slot getSlotAt(double xPosition, double yPosition);

	@Shadow
	protected abstract void drawItem(DrawContext drawContext, ItemStack stack, int xPosition, int yPosition, String amountText);

	@Shadow
	protected int x;

	@Shadow @Final protected ScreenHandler handler;

	protected MixinHandledScreen(Text title) {
		super(title);
	}

	@Inject(method = "render", at = @At("RETURN"))
	public void onRendered(DrawContext drawContext, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (WynnWeaponViewer.shouldRenderZoom()) {
			ItemStack renderStack = determineRenderStack(mouseX, mouseY);
			if (!renderStack.isEmpty()) {
				renderZoomedWeapon(drawContext, renderStack);
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
		if (!cursorStack.isEmpty() && isWynncraftWeapon(cursorStack)) {
			return cursorStack;
		}

		Slot slot = getSlotAt(mouseX, mouseY);
		if (slot != null) {
			ItemStack slotStack = slot.getStack();
			if (!slotStack.isEmpty() && isWynncraftWeapon(slotStack)) {
				return slotStack;
			}
		}

		return ItemStack.EMPTY;
	}

	@Unique
	private boolean isWynncraftWeapon(ItemStack itemStack) {
		return Models.Item.asWynnItem(itemStack, GearItem.class)
				.map(gearItem -> gearItem.getGearType().isWeapon())
				.orElse(false);
	}

	@Unique
	private void renderZoomedWeapon(DrawContext drawContext, ItemStack stack) {
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