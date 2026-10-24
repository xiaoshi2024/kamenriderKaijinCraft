package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.hud.BookHeartOverlay;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.render.StoriousBodyRenderer;
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

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        StoriousBodyRenderer.create(event.getContext());
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KaijinKeys.TOGGLE_FORM);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, BOOK_HEART_LAYER, BookHeartOverlay::render);
    }
}
