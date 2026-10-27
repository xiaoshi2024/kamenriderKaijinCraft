package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * 卡律布狄斯肚子里正在消化的一批东西。
 *
 * @param victims     被送进肚子维度的生物 UUID
 * @param drops       吞噬方块时就已经结算好的掉落物
 * @param finishTick  消化完成的世界时间
 */
public record StomachDigestion(List<UUID> victims, List<ItemStack> drops, long finishTick) {
    public static final Codec<StomachDigestion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.listOf().fieldOf("victims").orElse(List.of()).forGetter(StomachDigestion::victims),
            ItemStack.CODEC.listOf().fieldOf("drops").orElse(List.of()).forGetter(StomachDigestion::drops),
            Codec.LONG.fieldOf("finish").orElse(0L).forGetter(StomachDigestion::finishTick)
    ).apply(instance, StomachDigestion::new));
}
