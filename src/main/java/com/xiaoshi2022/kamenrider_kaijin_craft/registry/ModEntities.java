package com.xiaoshi2022.kamenrider_kaijin_craft.registry;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.entity.StoriousCloneEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, KaijinMOD.MOD_ID);

    /** 斯特利乌斯的分身，只用于迷惑与吸引仇恨，持续时间结束后自行消失。 */
    public static final DeferredHolder<EntityType<?>, EntityType<StoriousCloneEntity>> STORIOUS_CLONE =
            ENTITIES.register("storious_clone", () -> EntityType.Builder
                    .of(StoriousCloneEntity::new, MobCategory.MISC)
                    .sized(0.7F, 2.4F)
                    .clientTrackingRange(10)
                    .build("storious_clone"));

    private ModEntities() {
    }
}
