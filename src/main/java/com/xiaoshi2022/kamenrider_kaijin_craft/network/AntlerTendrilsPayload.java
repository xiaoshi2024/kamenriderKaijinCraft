package com.xiaoshi2022.kamenrider_kaijin_craft.network;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 鹿角卷须命中列表，用于客户端把黄色能量触须连到目标身上。 */
public record AntlerTendrilsPayload(int playerId, int[] targets, int ticks) implements CustomPacketPayload {
    public static final Type<AntlerTendrilsPayload> TYPE = new Type<>(KaijinMOD.id("antler_tendrils"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AntlerTendrilsPayload> CODEC = StreamCodec.ofMember(
            (payload, buffer) -> {
                buffer.writeInt(payload.playerId);
                buffer.writeVarInt(payload.targets.length);
                for (int target : payload.targets) {
                    buffer.writeInt(target);
                }
                buffer.writeVarInt(payload.ticks);
            },
            buffer -> {
                int playerId = buffer.readInt();
                int count = buffer.readVarInt();
                int[] targets = new int[count];
                for (int index = 0; index < count; index++) {
                    targets[index] = buffer.readInt();
                }
                return new AntlerTendrilsPayload(playerId, targets, buffer.readVarInt());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
