package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.hud.BookHeartOverlay;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.hud.SkillHotbarOverlay;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.render.StoriousBodyRenderer;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.render.StoriousCloneRenderer;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModEntities;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = KaijinMOD.MOD_ID, value = Dist.CLIENT)
public final class ClientSetup {
    private static final ResourceLocation BOOK_HEART_LAYER = KaijinMOD.id("book_heart");
    private static final ResourceLocation SKILL_HOTBAR_LAYER = KaijinMOD.id("skill_hotbar");

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        StoriousBodyRenderer.create(event.getContext());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STORIOUS_CLONE.get(), StoriousCloneRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KaijinKeys.TOGGLE_FORM);
        event.register(KaijinKeys.SKILL_WHEEL);
        for (var quickSkill : KaijinKeys.QUICK_SKILLS) {
            event.register(quickSkill);
        }
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, BOOK_HEART_LAYER, BookHeartOverlay::render);
        event.registerAboveAll(SKILL_HOTBAR_LAYER, SkillHotbarOverlay::render);
    }
}
