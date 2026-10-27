package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 亚马逊怪人种族状态。
 *
 * @param amazon  是否已被溶原性细胞感染（获得亚马逊怪人种族，人类拟态也算）
 * @param monster 当前是否解放为怪人形态（千翼 · Neo Amazon Origin）
 */
public record AmazonState(boolean amazon, boolean monster) {
    /** 未感染时的默认状态。 */
    public static final AmazonState HUMAN = new AmazonState(false, false);

    public static final Codec<AmazonState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("amazon").orElse(false).forGetter(AmazonState::amazon),
            Codec.BOOL.fieldOf("monster").orElse(false).forGetter(AmazonState::monster)
    ).apply(instance, AmazonState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AmazonState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AmazonState::amazon,
            ByteBufCodecs.BOOL, AmazonState::monster,
            AmazonState::new);

    public AmazonState withMonster(boolean value) {
        return new AmazonState(amazon, value);
    }
}
