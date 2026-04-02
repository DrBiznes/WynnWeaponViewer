package com.wynnweaponviewer.mixin;

import com.wynnweaponviewer.accessor.ContainerScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin implements ContainerScreenAccessor {

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    protected int imageWidth;

    @Shadow
    protected int imageHeight;

    @Override
    public @Nullable Slot wynnweapon$getHoveredSlot() {
        return this.hoveredSlot;
    }

    @Override
    public int wynnweapon$getLeftPos() {
        return this.leftPos;
    }

    @Override
    public int wynnweapon$getTopPos() {
        return this.topPos;
    }

    @Override
    public int wynnweapon$getImageWidth() {
        return this.imageWidth;
    }

    @Override
    public int wynnweapon$getImageHeight() {
        return this.imageHeight;
    }
}
