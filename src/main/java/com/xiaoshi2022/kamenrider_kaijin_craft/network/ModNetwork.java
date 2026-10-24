package com.xiaoshi2022.kamenrider_kaijin_craft.network;

import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinRace;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(ToggleFormPayload.TYPE, ToggleFormPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BookKaijinRace.toggle(player);
                    }
                }));

        registrar.playToClient(QiAuraPayload.TYPE, QiAuraPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        com.xiaoshi2022.kamenrider_kaijin_craft.client.ClientAuraHandler.accept(payload);
                    }
                }));
    }
}
