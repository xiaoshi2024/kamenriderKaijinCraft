package com.xiaoshi2022.kamenrider_kaijin_craft.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 书之怪物种族状态。
 *
 * @param bookKaijin 是否已经成为书之怪物（获得种族，人类的拟态形态也算）
 * @param monster    当前是否解放为斯特利乌斯怪人形态
 */
public record BookKaijinState(boolean bookKaijin, boolean monster) {
    public static final BookKaijinState HUMAN = new BookKaijinState(false, false);

    public static final Codec<BookKaijinState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("book_kaijin").orElse(false).forGetter(BookKaijinState::bookKaijin),
            Codec.BOOL.fieldOf("monster").orElse(false).forGetter(BookKaijinState::monster)
    ).apply(instance, BookKaijinState::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BookKaijinState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BookKaijinState::bookKaijin,
            ByteBufCodecs.BOOL, BookKaijinState::monster,
            BookKaijinState::new);

    public BookKaijinState withMonster(boolean value) {
        return new BookKaijinState(bookKaijin, value);
    }
}
