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
		if (WynnWeaponViewer.shallRender()) {
			Slot slot = getSlotAt(mouseX, mouseY);
			ItemStack stack;

			if (slot != null && !slot.getStack().isEmpty()) {
				stack = slot.getStack();
			} else {
				stack = handler.getCursorStack();
				if (stack == null || stack.isEmpty()) {
					return;
				}
			}

			// Check if the item is a Wynncraft weapon
			if (isWynncraftWeapon(stack)) {
				float scale = ModConfig.getScale();
				float size = Math.min(x * scale, this.height * scale);
				float itemScale = size / 16;

				double ix = (x - size) / 2F;
				if (ModConfig.getHorizontalAlignment() == HorizontalAlignment.RIGHT) {
					ix = width - ix - size;
				}
				double iy = (height - size) / 2F;

				// Apply offsets
				ix += ModConfig.getXOffset();
				iy += ModConfig.getYOffset();

				drawContext.getMatrices().push();
				drawContext.getMatrices().translate(ix, iy, 100);
				drawContext.getMatrices().scale(itemScale, itemScale, Math.min(itemScale, 20f));
				drawItem(drawContext, stack, 0, 0, "");
				drawContext.getMatrices().pop();
			}
		}
	}

	private boolean isWynncraftWeapon(ItemStack itemStack) {
		return Models.Item.asWynnItem(itemStack, GearItem.class)
				.map(gearItem -> gearItem.getGearType().isWeapon())
				.orElse(false);
	}
}