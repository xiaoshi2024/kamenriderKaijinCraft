package com.xiaoshi2022.kamenrider_kaijin_craft.network;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端按 G 键请求切换拟态 / 怪人形态。 */
public record ToggleFormPayload() implements CustomPacketPayload {
    public static final ToggleFormPayload INSTANCE = new ToggleFormPayload();
    public static final Type<ToggleFormPayload> TYPE = new Type<>(KaijinMOD.id("toggle_form"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFormPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
