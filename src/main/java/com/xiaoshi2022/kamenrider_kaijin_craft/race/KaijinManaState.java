package com.xiaoshi2022.kamenrider_kaijin_craft.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * 怪人技能蓝条（叙事值）状态。
 * <p>
 * 所有怪人种族共用同一条蓝条：技能按消耗扣蓝，蓝条耗尽时怪人道具爆裂；
 * 回蓝的方式由各族的「剧情来源」决定，书之怪物是通过推进故事、制造冲突来叙事回蓝。
 *
 * @param current 当前蓝量
 * @param max     蓝量上限
 */
public record KaijinManaState(float current, float max) {
    public static final float DEFAULT_MAX = 100.0F;
    public static final KaijinManaState FULL = new KaijinManaState(DEFAULT_MAX, DEFAULT_MAX);

    public static final Codec<KaijinManaState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("current").orElse(DEFAULT_MAX).forGetter(KaijinManaState::current),
            Codec.FLOAT.fieldOf("max").orElse(DEFAULT_MAX).forGetter(KaijinManaState::max)
    ).apply(instance, KaijinManaState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, KaijinManaState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, KaijinManaState::current,
            ByteBufCodecs.FLOAT, KaijinManaState::max,
            KaijinManaState::new);

    public KaijinManaState withCurrent(float value) {
        return new KaijinManaState(Mth.clamp(value, 0.0F, max), max);
    }
}
