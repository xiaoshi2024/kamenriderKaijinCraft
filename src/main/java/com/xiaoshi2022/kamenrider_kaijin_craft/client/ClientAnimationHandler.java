package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render.CharybdisBodyRenderer;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.KaijinAnimationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * 一次性动作请求的客户端入口：目前只有卡律布狄斯的吞噬（eat）。
 * <p>
 * 直接触发 GeckoLib 的 triggerableAnim，动作播完会自动回到 idle / attack。
 */
public final class ClientAnimationHandler {
    private ClientAnimationHandler() {
    }

    public static void accept(KaijinAnimationPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level.getEntity(payload.entity()) instanceof AbstractClientPlayer player) {
            CharybdisBodyRenderer renderer = CharybdisBodyRenderer.get(player);
            if (renderer != null) {
                renderer.playTriggered(payload.animation());
            }
        }
    }
}
