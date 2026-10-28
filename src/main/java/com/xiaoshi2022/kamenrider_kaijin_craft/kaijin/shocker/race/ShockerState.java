package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.shocker.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 修卡怪人种族状态。
 *
 * @param shocker 是否接受过修卡改造（人类拟态也算）
 * @param monster 当前是否以修卡怪人的完整形态出现
 */
public record ShockerState(boolean shocker, boolean monster) {
    /** 未接受改造时的默认状态。 */
    public static final ShockerState HUMAN = new ShockerState(false, false);

    public static final Codec<ShockerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("shocker").orElse(false).forGetter(ShockerState::shocker),
            Codec.BOOL.fieldOf("monster").orElse(false).forGetter(ShockerState::monster)
    ).apply(instance, ShockerState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShockerState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ShockerState::shocker,
            ByteBufCodecs.BOOL, ShockerState::monster,
            ShockerState::new);

    public ShockerState withMonster(boolean value) {
        return new ShockerState(shocker, value);
    }
}
