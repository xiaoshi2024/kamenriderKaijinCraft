package com.xiaoshi2022.kamenrider_kaijin_craft.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 暴露 AbstractContainerMenu 的 protected addSlot，供 InventoryMenu 混入追加融合槽。 */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuAccessor {
    @Invoker("addSlot")
    Slot kaijin$addSlot(Slot slot);
}
