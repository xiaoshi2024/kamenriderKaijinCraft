package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.item.IKaijinMutationItem;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.QiAuraPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModAttachments;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 怪人技能蓝条（叙事值）。
 * <p>
 * 所有怪人种族共用一条蓝条：施放技能扣蓝，扣到 0 时怪人道具爆裂、强制退回人类。
 * 回蓝不做成「击杀奖励」，而是由各族的剧情行为触发（书之怪物靠制造冲突与推进故事）：
 * 造成伤害、控制命中、分身骗到对手、瞬移躲开攻击，各来源有独立的内在冷却，防止一次技能回满。
 */
public final class KaijinMana {
    /** 每个叙事来源单独限流的最近触发时间。 */
    private static final Map<UUID, Map<String, Long>> LAST_AWARD = new ConcurrentHashMap<>();

    private KaijinMana() {
    }

    public static KaijinManaState get(Player player) {
        return player.getData(ModAttachments.KAIJIN_MANA);
    }

    public static float current(Player player) {
        return get(player).current();
    }

    public static float max(Player player) {
        return get(player).max();
    }

    public static void set(Player player, KaijinManaState state) {
        player.setData(ModAttachments.KAIJIN_MANA, state);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(ModAttachments.KAIJIN_MANA);
        }
    }

    /** 重新获得怪人种族时把蓝条补满。 */
    public static void refill(Player player) {
        set(player, KaijinManaState.FULL);
    }

    public static boolean hasEnough(Player player, float cost) {
        return current(player) >= cost;
    }

    /**
     * 扣除蓝量，返回扣完后是否已经见底。
     * 见底由调用方在技能结算完再触发 {@link #explode(ServerPlayer)}，
     * 这样「最后一下」技能仍然放得出来。
     */
    public static boolean consume(ServerPlayer player, float cost) {
        KaijinManaState state = get(player);
        KaijinManaState next = state.withCurrent(state.current() - cost);
        set(player, next);
        return next.current() <= 0.0F;
    }

    /**
     * 叙事回蓝。同一次「剧情交互」按来源限流，避免多目标技能一笔回满。
     *
     * @param source       剧情来源标识（damage / control / clone_decoy / dodge ...）
     * @param amount       回复点数
     * @param cooldownTicks 该来源的内在冷却
     */
    public static void award(ServerPlayer player, String source, float amount, int cooldownTicks) {
        if (amount <= 0.0F || !BookKaijinRace.get(player).bookKaijin()) {
            return;
        }
        long now = player.level().getGameTime();
        Map<String, Long> sources = LAST_AWARD.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        if (sources.getOrDefault(source, 0L) > now) {
            return;
        }
        sources.put(source, now + cooldownTicks);

        KaijinManaState state = get(player);
        if (state.current() >= state.max()) {
            return;
        }
        set(player, state.withCurrent(state.current() + amount));
    }

    public static void clear(UUID playerId) {
        LAST_AWARD.remove(playerId);
    }

    /**
     * 蓝量耗尽：怪人道具当场爆裂并被销毁，玩家强制退回人类。
     * 道具销毁后无法再翻书变身，必须重新合成。
     */
    public static void explode(ServerPlayer player) {
        BookKaijinType type = BookKaijinRace.typeOf(player);
        destroyMutationItems(player);
        BookKaijinRace.set(player, BookKaijinState.HUMAN);
        refill(player);

        ServerLevel level = player.serverLevel();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                player.getId(), "mutate", player.position(), type.qiColor(), 2.2F, 60));
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0D, player.getZ(),
                8, 0.8D, 0.8D, 0.8D, 0.0D);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.PLAYERS, 1.5F, 0.8F);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.mana_depleted"), true);
        player.containerMenu.broadcastChanges();
    }

    /** 销毁融合槽、背包、光标里的所有怪人道具。 */
    private static void destroyMutationItems(ServerPlayer player) {
        ItemStack fused = player.getData(ModAttachments.FUSION_ITEM);
        if (isMutationItem(fused)) {
            player.setData(ModAttachments.FUSION_ITEM, ItemStack.EMPTY);
            player.syncData(ModAttachments.FUSION_ITEM);
        }

        Inventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isMutationItem(stack)) {
                inventory.setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            inventory.setChanged();
        }

        if (player.containerMenu != null && isMutationItem(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    private static boolean isMutationItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IKaijinMutationItem;
    }
}
