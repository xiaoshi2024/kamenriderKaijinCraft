package com.xiaoshi2022.kamenrider_kaijin_craft.network;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 服务端同步技能剩余冷却（tick），供技能盘显示。 */
public record SkillCooldownPayload(int skill, int ticks) implements CustomPacketPayload {
    public static final Type<SkillCooldownPayload> TYPE = new Type<>(KaijinMOD.id("skill_cooldown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkillCooldownPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SkillCooldownPayload::skill,
            ByteBufCodecs.VAR_INT, SkillCooldownPayload::ticks,
            SkillCooldownPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
