package com.wynnweaponviewer.mixin;

import com.wynnweaponviewer.render.ZoomedItemPIPRenderer;
import com.wynnweaponviewer.render.ZoomedItemRenderState;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(GuiRenderer.class)
public class GuiRendererMixin {

    @Shadow
    @Final
    private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;

    @Shadow
    @Final
    private MultiBufferSource.BufferSource bufferSource;

    @Unique
    private ZoomedItemPIPRenderer wynnweapon$zoomedItemRenderer;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void wynnweapon$onInit(GuiRenderState guiRenderState, MultiBufferSource.BufferSource bufferSource, SubmitNodeCollector submitNodeCollector, FeatureRenderDispatcher featureRenderDispatcher, List<PictureInPictureRenderer<?>> list, CallbackInfo ci) {
        wynnweapon$zoomedItemRenderer = new ZoomedItemPIPRenderer(this.bufferSource);
        ((Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>>) pictureInPictureRenderers)
                .put(ZoomedItemRenderState.class, wynnweapon$zoomedItemRenderer);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void wynnweapon$onClose(CallbackInfo ci) {
        if (wynnweapon$zoomedItemRenderer != null) {
            wynnweapon$zoomedItemRenderer.close();
        }
    }
}
