package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 溶原性细胞：亚马逊怪人的感染细胞。
 * <p>
 * 持有它（背包、光标或融合槽均可）的玩家按 G 即可变异成千翼的 Neo Amazon Origin。
 * 细胞同时是种族的维持条件，彻底失去后会退回人类。
 */
public class LysogenicCellItem extends Item {
    public LysogenicCellItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip.passive")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
