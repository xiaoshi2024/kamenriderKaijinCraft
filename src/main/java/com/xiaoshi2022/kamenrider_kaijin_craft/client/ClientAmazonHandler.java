package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.client.render.AmazonTendrilRenderer;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.AmazonTendrilsPayload;

/** 亚马逊怪人网络包 → 客户端视觉缓存。 */
public final class ClientAmazonHandler {
    private ClientAmazonHandler() {
    }

    public static void accept(AmazonTendrilsPayload payload) {
        AmazonTendrilRenderer.accept(payload);
    }
}
