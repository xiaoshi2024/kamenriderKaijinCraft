package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.shocker.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 修卡徽章：修卡改造人的证明。
 * <p>
 * 携带它（背包、光标或融合槽均可）的玩家按 G 即可变身为完整的修卡怪人；
 * 徽章同时是种族的维持条件，彻底丢失后会退回人类。
 */
public class ShockerEmblemItem extends Item {
    public ShockerEmblemItem(Properties properties) {
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
