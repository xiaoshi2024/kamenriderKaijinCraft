package com.xiaoshi2022.kamenrider_kaijin_craft.event;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.KaijinMana;
import com.xiaoshi2022.kamenrider_kaijin_craft.skill.StoriousSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 变异道具是书之怪物的「心脏」：每半秒检查一次玩家是否还持有它，
 * 被销毁 / 丢弃 / 清空后立即退回人类种族。
 */
@EventBusSubscriber(modid = KaijinMOD.MOD_ID)
public final class ServerEvents {
    private static final int CHECK_INTERVAL = 10;

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_INTERVAL == 0) {
            BookKaijinRace.validateMutationSource(player);
            BookKaijinRace.syncAttributes(player);
        }
    }

    /** 干部体魄：怪人形态下免疫摔落伤害。 */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && BookKaijinRace.isMonster(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * 叙事回蓝：书之怪物每一次「造成伤害」都算推进剧情，回 5 点（同来源 1 秒冷却）。
     * 只认玩家自己打出的伤害，环境伤害或持续伤害不会刷。
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            KaijinMana.award(player, "damage", 5.0F, 20);
        }
    }

    /** 玩家下线时清掉技能冷却缓存 */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StoriousSkills.clear(player.getUUID());
            KaijinMana.clear(player.getUUID());
        }
    }
}
