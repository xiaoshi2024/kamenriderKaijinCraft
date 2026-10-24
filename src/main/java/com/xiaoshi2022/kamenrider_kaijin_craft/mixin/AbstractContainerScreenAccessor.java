package com.xiaoshi2022.kamenrider_kaijin_craft.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 读取容器界面的 menu，用于在创造模式背包里重新摆放融合槽。 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("menu")
    AbstractContainerMenu kaijin$getMenu();
}
