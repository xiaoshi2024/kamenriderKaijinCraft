package com.xiaoshi2022.kamenrider_kaijin_craft.registry;

import com.mojang.serialization.Codec;
import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, KaijinMOD.MOD_ID);

    /** 类驾驭书的唯一持有者（首次翻开时绑定）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> BOOK_OWNER =
            COMPONENTS.registerComponentType("book_owner", builder -> builder
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC));

    /** 持有者名字，仅用于物品提示。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BOOK_OWNER_NAME =
            COMPONENTS.registerComponentType("book_owner_name", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private ModDataComponents() {
    }
}
