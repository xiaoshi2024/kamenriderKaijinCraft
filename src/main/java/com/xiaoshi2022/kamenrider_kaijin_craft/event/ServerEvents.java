package com.xiaoshi2022.kamenrider_kaijin_craft.event;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinRace;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
        }
    }
}
