package com.xiaoshi2022.kamenrider_kaijin_craft.network;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端请求释放斯特利乌斯技能，skill 为 {@code StoriousSkill} 的序号。 */
public record KaijinSkillPayload(int skill) implements CustomPacketPayload {
    public static final Type<KaijinSkillPayload> TYPE = new Type<>(KaijinMOD.id("use_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KaijinSkillPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KaijinSkillPayload::skill,
            KaijinSkillPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
