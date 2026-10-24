package com.xiaoshi2022.kamenrider_kaijin_craft.network;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * 尸兄模组「气」的网络包移植：由服务端下发一段带锚点的红色气焰 / 无锚点的气雾。
 * anchor >= 0 表示跟随实体，否则固定在 center。
 */
public record QiAuraPayload(int anchor, String channel, Vec3 center, int color, float radius, int ticks)
        implements CustomPacketPayload {

    public static final Type<QiAuraPayload> TYPE = new Type<>(KaijinMOD.id("qi_aura"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QiAuraPayload> CODEC = StreamCodec.ofMember(
            (payload, buffer) -> {
                buffer.writeInt(payload.anchor);
                buffer.writeUtf(payload.channel, 64);
                buffer.writeDouble(payload.center.x);
                buffer.writeDouble(payload.center.y);
                buffer.writeDouble(payload.center.z);
                buffer.writeInt(payload.color);
                buffer.writeFloat(payload.radius);
                buffer.writeVarInt(payload.ticks);
            },
            buffer -> new QiAuraPayload(
                    buffer.readInt(),
                    buffer.readUtf(64),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    buffer.readInt(),
                    buffer.readFloat(),
                    buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
