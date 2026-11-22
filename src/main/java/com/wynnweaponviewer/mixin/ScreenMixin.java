package com.wynnweaponviewer.mixin;

import com.wynnweaponviewer.render.ZoomedItemRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the zoomed item between the screen and its tooltips. Fabric's afterRender fires after the deferred tooltip
 * has already been drawn, which put the model, card and share hint on top of every tooltip.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Inject(method = "renderWithTooltipAndSubtitles", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;renderDeferredElements()V"))
    private void wynnweapon$renderBeforeTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                                CallbackInfo ci) {
        if ((Object) this instanceof AbstractContainerScreen<?> screen) {
            // Our own stratum: above everything the screen drew, below the tooltip's (renderDeferredElements
            // starts a new one for it).
            graphics.nextStratum();
            ZoomedItemRenderer.render(graphics, screen, mouseX, mouseY);
        }
    }
}
