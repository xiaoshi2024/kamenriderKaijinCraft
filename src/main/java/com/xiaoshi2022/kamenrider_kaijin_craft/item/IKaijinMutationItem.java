package com.xiaoshi2022.kamenrider_kaijin_craft.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 可放入「怪人融合槽」的变异道具。
 * <p>
 * 以后新增其它假面骑士怪人的变异道具时，只要实现这个接口，
 * 就能直接塞进融合槽并触发各自的融合逻辑，无需再改槽位 / 菜单代码。
 * <p>
 * 变异道具同时是维持怪人种族的「心脏」：一旦玩家彻底失去它（被销毁 / 丢弃 / 清空），
 * 就会退回人类种族。
 */
public interface IKaijinMutationItem {
    /** 是否允许这位玩家把该物品放进融合槽（用于绑定类道具的主人校验）。 */
    default boolean canFuse(Player player, ItemStack stack) {
        return true;
    }

    /**
     * 这件物品是否仍在维持玩家的怪人种族。
     * 默认与 {@link #canFuse} 一致；绑定类道具可以收紧为「必须是这位玩家认主的那一件」。
     */
    default boolean sustainsRace(Player player, ItemStack stack) {
        return canFuse(player, stack);
    }

    /** 物品被放入融合槽时调用（仅服务端，且只在从空槽放入时触发一次）。 */
    void onFused(ServerPlayer player, ItemStack stack);
}
