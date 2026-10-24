package com.xiaoshi2022.kamenrider_kaijin_craft.mixin;

import com.xiaoshi2022.kamenrider_kaijin_craft.inventory.FusionSlot;
import com.xiaoshi2022.kamenrider_kaijin_craft.inventory.FusionSlotContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在原版玩家背包菜单末尾追加「怪人融合槽」。
 * 位置 (77, 44) 在副手槽正上方，紧挨着背包的额外槽位区域。
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void kaijin$addFusionSlot(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        ((AbstractContainerMenuAccessor) (Object) this).kaijin$addSlot(
                new FusionSlot(new FusionSlotContainer(owner), owner, 0, 77, 44));
    }
}
