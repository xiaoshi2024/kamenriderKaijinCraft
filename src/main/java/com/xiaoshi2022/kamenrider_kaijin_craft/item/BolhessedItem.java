package com.xiaoshi2022.kamenrider_kaijin_craft.item;

import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinType;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.KaijinMana;
import com.xiaoshi2022.kamenrider_kaijin_craft.skill.BolhessedPowers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import java.util.List;

/**
 * 拉结尔专武「波尔赫斯德」斧剑。
 * <p>
 * 物性上同时是剑与斧：挥砍按剑结算，砍树 / 劈木按斧结算。
 * 右键挥出吸收周围元素的剑气，潜行右键掷出冰块与岩石；
 * 全元素操控、强化跳跃、围巾攻防与攻击反弹放在技能盘里。
 * 只有拉结尔的怪人姿态才能驱动这把剑的力量。
 */
public class BolhessedItem extends SwordItem {
    private static final int WAVE_COOLDOWN = 24;
    private static final int VOLLEY_COOLDOWN = 55;
    /** 右键剑气 / 潜行右键弹幕各自消耗的叙事值。 */
    private static final float WAVE_MANA = 4.0F;
    private static final float VOLLEY_MANA = 7.0F;

    public BolhessedItem(Item.Properties properties) {
        super(Tiers.NETHERITE, properties, createTool());
    }

    /** 剑 + 斧双修的挖掘规则：木头按斧的速度与掉落结算，蛛网按剑结算。 */
    private static Tool createTool() {
        return new Tool(List.of(
                Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_AXE, 9.0F),
                Tool.Rule.overrideSpeed(BlockTags.SWORD_EFFICIENT, 1.5F),
                Tool.Rule.minesAndDrops(List.of(Blocks.COBWEB), 15.0F)
        ), 1.0F, 1);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(itemAbility)
                || ItemAbilities.DEFAULT_AXE_ACTIONS.contains(itemAbility);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 专武只认拉结尔：拟态成人类或别的怪人时只能当普通斧剑用
        if (!BookKaijinRace.isMonster(player)
                || BookKaijinRace.typeOf(player) != BookKaijinType.LEGEIEL) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.kamenrider_kaijin_craft.bolhessed_need_legeiel"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        boolean volley = player.isShiftKeyDown();
        float cost = volley ? VOLLEY_MANA : WAVE_MANA;
        if (player instanceof ServerPlayer serverPlayer) {
            // 蓝条只认服务端数值，客户端不做判定，避免不同步时放不出技能
            if (!KaijinMana.hasEnough(serverPlayer, cost)) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.kamenrider_kaijin_craft.mana_not_enough"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (volley) {
                BolhessedPowers.elementVolley(serverPlayer);
            } else {
                BolhessedPowers.swordWave(serverPlayer);
            }
            // 技能照常结算后再扣蓝；扣到见底就爆裂退回人类
            if (KaijinMana.consume(serverPlayer, cost)) {
                KaijinMana.explode(serverPlayer);
            }
        }
        player.getCooldowns().addCooldown(this, volley ? VOLLEY_COOLDOWN : WAVE_COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip.slash")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip.throw")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip.skills")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
