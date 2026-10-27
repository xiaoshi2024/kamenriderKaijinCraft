package com.xiaoshi2022.kamenrider_kaijin_craft.inventory;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.item.IKaijinMutationItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 背包里的怪人融合独立槽位，只接受变异道具，且一次只放一件。 */
public class FusionSlot extends Slot {
    private final Player player;

    public FusionSlot(Container container, Player player, int index, int x, int y) {
        super(container, index, x, y);
        this.player = player;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return stack.getItem() instanceof IKaijinMutationItem mutationItem && mutationItem.canFuse(player, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
