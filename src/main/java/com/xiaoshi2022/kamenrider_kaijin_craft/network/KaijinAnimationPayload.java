package com.xiaoshi2022.kamenrider_kaijin_craft.network;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 让指定实体播放一段一次性 GeckoLib 动作（当前用于卡律布狄斯吞噬时的 eat）。
 * ticks 只作参考，动作本身由动画文件里的长度决定。
 */
public record KaijinAnimationPayload(int entity, String animation, int ticks) implements CustomPacketPayload {
    public static final Type<KaijinAnimationPayload> TYPE = new Type<>(KaijinMOD.id("kaijin_animation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KaijinAnimationPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KaijinAnimationPayload::entity,
            ByteBufCodecs.STRING_UTF8, KaijinAnimationPayload::animation,
            ByteBufCodecs.VAR_INT, KaijinAnimationPayload::ticks,
            KaijinAnimationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
