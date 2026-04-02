package com.wynnweaponviewer.accessor;

import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

public interface ContainerScreenAccessor {

    @Nullable
    Slot wynnweapon$getHoveredSlot();

    int wynnweapon$getLeftPos();

    int wynnweapon$getTopPos();

    int wynnweapon$getImageWidth();

    int wynnweapon$getImageHeight();
}
