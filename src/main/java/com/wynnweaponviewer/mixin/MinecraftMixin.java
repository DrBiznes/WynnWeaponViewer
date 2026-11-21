package com.wynnweaponviewer.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.wynnweaponviewer.share.OffscreenGuiContext;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Points the GUI renderer at the offscreen canvas while an {@link OffscreenGuiContext} pass runs. */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void wynnweapon$offscreenTarget(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget target = OffscreenGuiContext.target();
        if (target != null) cir.setReturnValue(target);
    }
}
