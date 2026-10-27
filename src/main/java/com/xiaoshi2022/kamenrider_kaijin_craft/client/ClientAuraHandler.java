package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render.QiAuraRenderer;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.QiAuraPayload;

/** 网络线程 → 客户端主线程的桥接，保证服务端不会加载任何客户端类。 */
public final class ClientAuraHandler {
    private ClientAuraHandler() {
    }

    public static void accept(QiAuraPayload payload) {
        QiAuraRenderer.accept(payload);
    }
}
