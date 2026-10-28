package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.shocker.race;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.race.AmazonRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.shocker.item.ShockerEmblemItem;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 修卡怪人种族：身上带着修卡徽章的玩家按 G 接受改造，
 * 变身为完整的修卡怪人；再按 G 在人类拟态与怪人形态之间切换。
 * 徽章是维持改造的「心脏」，彻底丢失后就会恢复人类。
 */
public final class ShockerRace {
    private ShockerRace() {
    }

    public static ShockerState get(Player player) {
        return player.getData(ModAttachments.SHOCKER);
    }

    public static boolean isShocker(Player player) {
        return get(player).shocker();
    }

    /** 当前是否以完整的修卡怪人形态出现（双端可用）。 */
    public static boolean isMonster(Player player) {
        ShockerState state = get(player);
        return state.shocker() && state.monster();
    }

    public static void set(Player player, ShockerState state) {
        player.setData(ModAttachments.SHOCKER, state);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(ModAttachments.SHOCKER);
        }
    }

    /** G 键：未改造时接受改造；改造后切换拟态 / 怪人形态。 */
    public static void pressG(ServerPlayer player) {
        ShockerState state = get(player);
        if (!state.shocker()) {
            if (!hasEmblem(player)) {
                player.displayClientMessage(Component.translatable(
                        "message.kamenrider_kaijin_craft.shocker_need_emblem"), true);
                return;
            }
            if (AmazonRace.isAmazon(player) || BookKaijinRace.get(player).bookKaijin()) {
                player.displayClientMessage(Component.translatable(
                        "message.kamenrider_kaijin_craft.shocker_already_kaijin"), true);
                return;
            }
            set(player, new ShockerState(true, true));
            player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                    SoundSource.PLAYERS, 1.0F, 0.6F);
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.shocker_unleash"), true);
            return;
        }
        boolean monster = !state.monster();
        set(player, state.withMonster(monster));
        player.level().playSound(null, player.blockPosition(),
                monster ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.0F, monster ? 0.6F : 1.5F);
        player.displayClientMessage(Component.translatable(monster
                ? "message.kamenrider_kaijin_craft.shocker_unleash"
                : "message.kamenrider_kaijin_craft.shocker_mimic"), true);
    }

    /** 修卡徽章是否还在身上（背包 / 光标 / 融合槽）。 */
    public static boolean hasEmblem(Player player) {
        if (isEmblem(player.getData(ModAttachments.FUSION_ITEM))) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isEmblem(inventory.getItem(slot))) {
                return true;
            }
        }
        return player.containerMenu != null && isEmblem(player.containerMenu.getCarried());
    }

    private static boolean isEmblem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ShockerEmblemItem;
    }

    /** 徽章彻底消失后，退回人类种族。 */
    public static void validateEmblem(ServerPlayer player) {
        ShockerState state = get(player);
        if (!state.shocker() || hasEmblem(player)) {
            return;
        }
        set(player, ShockerState.HUMAN);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.0F, 1.4F);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.shocker_race_lost"), true);
    }
}
