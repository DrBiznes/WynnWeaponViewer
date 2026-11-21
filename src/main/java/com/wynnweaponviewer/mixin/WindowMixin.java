package com.wynnweaponviewer.mixin;

import com.mojang.blaze3d.platform.Window;
import com.wynnweaponviewer.share.OffscreenGuiContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reports the offscreen canvas as the window while an {@link OffscreenGuiContext} pass runs. */
@Mixin(Window.class)
public class WindowMixin {

    @Inject(method = "getWidth", at = @At("HEAD"), cancellable = true)
    private void wynnweapon$offscreenWidth(CallbackInfoReturnable<Integer> cir) {
        if (OffscreenGuiContext.isActive()) cir.setReturnValue(OffscreenGuiContext.width());
    }

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void wynnweapon$offscreenHeight(CallbackInfoReturnable<Integer> cir) {
        if (OffscreenGuiContext.isActive()) cir.setReturnValue(OffscreenGuiContext.height());
    }

    @Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true)
    private void wynnweapon$offscreenGuiScale(CallbackInfoReturnable<Integer> cir) {
        if (OffscreenGuiContext.isActive()) cir.setReturnValue(OffscreenGuiContext.guiScale());
    }

    @Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
    private void wynnweapon$offscreenGuiScaledWidth(CallbackInfoReturnable<Integer> cir) {
        if (OffscreenGuiContext.isActive()) {
            cir.setReturnValue(OffscreenGuiContext.width() / OffscreenGuiContext.guiScale());
        }
    }

    @Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
    private void wynnweapon$offscreenGuiScaledHeight(CallbackInfoReturnable<Integer> cir) {
        if (OffscreenGuiContext.isActive()) {
            cir.setReturnValue(OffscreenGuiContext.height() / OffscreenGuiContext.guiScale());
        }
    }
}
