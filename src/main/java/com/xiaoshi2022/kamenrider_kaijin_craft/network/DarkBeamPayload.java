package com.xiaoshi2022.kamenrider_kaijin_craft.network;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * 一次性光束的客户端表现（黑暗能量箭 / 失明光线共用）。
 * style 0 = 黑色暗红能量箭，1 = 眼睛的强光。
 */
public record DarkBeamPayload(int playerId, Vec3 origin, Vec3 direction, float length, int style, int ticks)
        implements CustomPacketPayload {

    public static final Type<DarkBeamPayload> TYPE = new Type<>(KaijinMOD.id("dark_beam"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DarkBeamPayload> CODEC = StreamCodec.ofMember(
            (payload, buffer) -> {
                buffer.writeInt(payload.playerId);
                buffer.writeDouble(payload.origin.x);
                buffer.writeDouble(payload.origin.y);
                buffer.writeDouble(payload.origin.z);
                buffer.writeDouble(payload.direction.x);
                buffer.writeDouble(payload.direction.y);
                buffer.writeDouble(payload.direction.z);
                buffer.writeFloat(payload.length);
                buffer.writeVarInt(payload.style);
                buffer.writeVarInt(payload.ticks);
            },
            buffer -> new DarkBeamPayload(
                    buffer.readInt(),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    buffer.readFloat(),
                    buffer.readVarInt(),
                    buffer.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
