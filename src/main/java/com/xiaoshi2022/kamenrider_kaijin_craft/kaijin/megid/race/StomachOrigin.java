package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 被吞玩家进肚子之前所在的位置：击碎核心后按这个坐标把人送回去。
 * <p>
 * {@code present} 用来区分「没记录」与「记录在世界原点」，没记录时不传送。
 */
public record StomachOrigin(boolean present, ResourceKey<Level> dimension, Vec3 pos) {
    public static final StomachOrigin NONE = new StomachOrigin(false, Level.OVERWORLD, Vec3.ZERO);

    public static final Codec<StomachOrigin> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("present").orElse(false).forGetter(StomachOrigin::present),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").orElse(Level.OVERWORLD)
                    .forGetter(StomachOrigin::dimension),
            Vec3.CODEC.fieldOf("pos").orElse(Vec3.ZERO).forGetter(StomachOrigin::pos)
    ).apply(instance, StomachOrigin::new));

    public static StomachOrigin of(Level level, Vec3 pos) {
        return new StomachOrigin(true, level.dimension(), pos);
    }
}
