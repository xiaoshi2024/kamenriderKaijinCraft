package com.xiaoshi2022.kamenrider_kaijin_craft.inventory;

import com.xiaoshi2022.kamenrider_kaijin_craft.item.IKaijinMutationItem;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 怪人融合槽的容器实现：内容直接读写玩家附件，天然持久化 + 同步。
 */
public class FusionSlotContainer implements Container {
    private final Player player;

    public FusionSlotContainer(Player player) {
        this.player = player;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return getItem(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return player.getData(ModAttachments.FUSION_ITEM);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack current = getItem(0);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (current.getCount() <= amount) {
            setItem(0, ItemStack.EMPTY);
            return current;
        }
        ItemStack split = current.split(amount);
        setItem(0, current);
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = getItem(0);
        setItem(0, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack previous = player.getData(ModAttachments.FUSION_ITEM);
        ItemStack stored = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        player.setData(ModAttachments.FUSION_ITEM, stored);

        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.syncData(ModAttachments.FUSION_ITEM);

        // 只在「空槽 → 放入变异道具」时触发一次融合
        if (previous.isEmpty() && !stored.isEmpty()
                && stored.getItem() instanceof IKaijinMutationItem mutationItem) {
            mutationItem.onFused(serverPlayer, stored);
            serverPlayer.syncData(ModAttachments.FUSION_ITEM);
        }
    }

    @Override
    public void setChanged() {
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(ModAttachments.FUSION_ITEM);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        setItem(0, ItemStack.EMPTY);
    }
}
