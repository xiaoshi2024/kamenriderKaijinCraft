package com.xiaoshi2022.kamenrider_kaijin_craft.mixin;

import com.xiaoshi2022.kamenrider_kaijin_craft.inventory.FusionSlotContainer;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 创造模式的「生存背包」页会按通用公式重排玩家背包的所有槽位，
 * 融合槽会被排到面板外。这里把它挪到右侧销毁槽上方 (173, 90)。
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
    @Inject(method = "selectTab", at = @At("TAIL"))
    private void kaijin$placeFusionSlot(CreativeModeTab tab, CallbackInfo ci) {
        AbstractContainerMenu menu = ((AbstractContainerScreenAccessor) (Object) this).kaijin$getMenu();
        for (Slot slot : menu.slots) {
            if (slot.container instanceof FusionSlotContainer) {
                SlotAccessor accessor = (SlotAccessor) (Object) slot;
                accessor.kaijin$setX(173);
                accessor.kaijin$setY(90);
            }
        }
    }
}
