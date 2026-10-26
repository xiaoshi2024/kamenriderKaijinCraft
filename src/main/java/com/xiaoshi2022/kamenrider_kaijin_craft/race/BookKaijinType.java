package com.xiaoshi2022.kamenrider_kaijin_craft.race;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * 书之怪人的种类：决定变异后解放的是哪一只怪人（渲染、气焰、变异提示）。
 * <p>
 * 种族状态里只存「当前是哪一种」，因此同名之书之间互斥：换书即换形态。
 */
public enum BookKaijinType implements StringRepresentable {
    /** 掌管故事的书之魔物，红色气焰 */
    STORIOUS("storious", 0xC51A36, "message.kamenrider_kaijin_craft.mutate"),
    /** 海之书之怪人，紫罗兰黏液气焰 */
    CHARYBDIS("charybdis", 0x6B3CD8, "message.kamenrider_kaijin_craft.mutate_charybdis"),
    /** 米吉多干部拉结尔，金色气焰 */
    LEGEIEL("legeiel", 0xE8C34A, "message.kamenrider_kaijin_craft.mutate_legeiel");

    public static final Codec<BookKaijinType> CODEC = StringRepresentable.fromEnum(BookKaijinType::values);
    /** 网络同步：按序号写入，越界时夹回合法范围，避免旧包把客户端打崩。 */
    public static final StreamCodec<ByteBuf, BookKaijinType> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            id -> values()[Math.floorMod(id, values().length)], BookKaijinType::ordinal);

    private final String name;
    private final int qiColor;
    private final String mutationMessageKey;

    BookKaijinType(String name, int qiColor, String mutationMessageKey) {
        this.name = name;
        this.qiColor = qiColor;
        this.mutationMessageKey = mutationMessageKey;
    }

    /** 变异 / 形态切换时放出的气焰颜色 */
    public int qiColor() {
        return qiColor;
    }

    public String mutationMessageKey() {
        return mutationMessageKey;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
