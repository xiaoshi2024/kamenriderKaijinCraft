package com.xiaoshi2022.kamenrider_kaijin_craft.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Slot 的 x / y 在 1.21.1 是 final，这里放开以便在创造模式背包里重排融合槽。 */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void kaijin$setX(int x);

    @Mutable
    @Accessor("y")
    void kaijin$setY(int y);
}
