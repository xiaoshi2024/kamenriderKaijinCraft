package com.xiaoshi2022.kamenrider_kaijin_craft.race;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.item.IKaijinMutationItem;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.QiAuraPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModAttachments;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 书之怪物种族的服务端逻辑：翻开类驾驭书即变异，G 键在拟态（人类）与怪人形态之间切换。
 * 两个方向都会放出斯特利乌斯标志性的红色气焰。
 */
public final class BookKaijinRace {
    /** 视频里斯特利乌斯变异时那股偏暗的红色气 */
    public static final int QI_COLOR = 0xC51A36;

    private BookKaijinRace() {
    }

    public static BookKaijinState get(Player player) {
        return player.getData(ModAttachments.BOOK_KAIJIN);
    }

    public static void set(Player player, BookKaijinState state) {
        player.setData(ModAttachments.BOOK_KAIJIN, state);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(ModAttachments.BOOK_KAIJIN);
        }
    }

    /** 当前是否以斯特利乌斯怪人的姿态出现（双端可用） */
    public static boolean isMonster(Player player) {
        BookKaijinState state = get(player);
        return state.bookKaijin() && state.monster();
    }

    /** 翻开书：第一次授予书之怪物种族并直接变异，之后每次翻书都重新解放怪人形态。 */
    public static void openBook(ServerPlayer player) {
        BookKaijinState state = get(player);
        boolean firstMutation = !state.bookKaijin();
        if (firstMutation) {
            set(player, new BookKaijinState(true, true));
            player.displayClientMessage(Component.translatable("message.kamenrider_kaijin_craft.mutate"), true);
        } else {
            set(player, state.withMonster(true));
            player.displayClientMessage(Component.translatable("message.kamenrider_kaijin_craft.unleash"), true);
        }
        // award 本身幂等：第一次翻开时弹出「觉醒吧！掌管古诗的书之魔物」，之后不会再弹
        awardFirstMutation(player);
        burst(player, true, true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** 初次变异成就：「觉醒吧！掌管古诗的书之魔物」 */
    private static void awardFirstMutation(ServerPlayer player) {
        AdvancementHolder advancement = player.server.getAdvancements().get(KaijinMOD.id("first_mutation"));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "mutated");
        }
    }

    /**
     * 变异道具是否还在这位玩家身上（融合槽 / 背包 / 光标）。
     * 这是怪人种族的维持条件：道具被销毁、丢弃、清空后就不再满足。
     */
    public static boolean hasMutationSource(Player player) {
        if (sustains(player, player.getData(ModAttachments.FUSION_ITEM))) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (sustains(player, inventory.getItem(slot))) {
                return true;
            }
        }
        return player.containerMenu != null && sustains(player, player.containerMenu.getCarried());
    }

    private static boolean sustains(Player player, ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IKaijinMutationItem mutationItem
                && mutationItem.sustainsRace(player, stack);
    }

    /** 服务端定期校验：变异道具彻底消失后，退回人类种族。 */
    public static void validateMutationSource(ServerPlayer player) {
        BookKaijinState state = get(player);
        if (!state.bookKaijin() || hasMutationSource(player)) {
            return;
        }
        set(player, BookKaijinState.HUMAN);
        burst(player, false, false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.0F, 1.4F);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.race_lost"), true);
    }

    /** G 键：拟态 ⇄ 怪人。 */
    public static void toggle(ServerPlayer player) {
        BookKaijinState state = get(player);
        if (!state.bookKaijin()) {
            player.displayClientMessage(Component.translatable("message.kamenrider_kaijin_craft.not_book_kaijin"), true);
            return;
        }
        boolean monster = !state.monster();
        set(player, state.withMonster(monster));
        burst(player, monster, false);
        player.level().playSound(null, player.blockPosition(),
                monster ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.0F, monster ? 0.7F : 1.4F);
        player.displayClientMessage(Component.translatable(monster
                ? "message.kamenrider_kaijin_craft.unleash"
                : "message.kamenrider_kaijin_craft.mimic"), true);
    }

    /**
     * 放气：锚定在玩家身上的气焰 + （变异时）脚下一圈外扩的气雾。
     * 实现照搬尸兄模组的 QiAuraPayload / QiAuraRenderer。
     */
    private static void burst(ServerPlayer player, boolean monster, boolean mutation) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                player.getId(),
                mutation ? "mutate" : "mimic",
                player.position(),
                QI_COLOR,
                monster ? 1.9F : 1.3F,
                monster ? 50 : 40));

        if (mutation) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                    -1, "mutate_mist", player.position(), QI_COLOR, 3.2F, 45));
        }
    }
}
