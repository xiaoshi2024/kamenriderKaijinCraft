package com.xiaoshi2022.kamenrider_kaijin_craft.network;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 千翼的被动触手：服务端把「从玩家身上刺向哪些目标」下发给客户端做纯视觉表现。
 * 每次最多 8 个目标，每个目标再分裂出数根纤细触手。
 */
public record AmazonTendrilsPayload(int anchor, List<Vec3> targets, int ticks) implements CustomPacketPayload {
    public static final Type<AmazonTendrilsPayload> TYPE = new Type<>(KaijinMOD.id("amazon_tendrils"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AmazonTendrilsPayload> CODEC =
            StreamCodec.ofMember(
                    (payload, buffer) -> {
                        buffer.writeVarInt(payload.anchor);
                        buffer.writeVarInt(payload.targets.size());
                        for (Vec3 target : payload.targets) {
                            buffer.writeDouble(target.x);
                            buffer.writeDouble(target.y);
                            buffer.writeDouble(target.z);
                        }
                        buffer.writeVarInt(payload.ticks);
                    },
                    buffer -> {
                        int anchor = buffer.readVarInt();
                        int size = buffer.readVarInt();
                        List<Vec3> targets = new ArrayList<>(size);
                        for (int index = 0; index < size; index++) {
                            targets.add(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
                        }
                        return new AmazonTendrilsPayload(anchor, targets, buffer.readVarInt());
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
