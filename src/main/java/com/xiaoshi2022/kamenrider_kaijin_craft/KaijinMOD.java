package com.xiaoshi2022.kamenrider_kaijin_craft;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.entity.KaijinBookCoreEntity;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.entity.StoriousCloneEntity;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.ModNetwork;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModAttachments;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModCreativeTabs;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModDataComponents;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModEntities;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(KaijinMOD.MOD_ID)
public final class KaijinMOD {
    public static final String MOD_ID = "kamenrider_kaijin_craft";

    public KaijinMOD(IEventBus modBus) {
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModDataComponents.COMPONENTS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        ModAttachments.ATTACHMENTS.register(modBus);
        modBus.addListener(ModNetwork::register);
        modBus.addListener(KaijinMOD::registerAttributes);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.STORIOUS_CLONE.get(), StoriousCloneEntity.createAttributes().build());
        event.put(ModEntities.BOOK_CORE.get(), KaijinBookCoreEntity.createAttributes().build());
    }
}
