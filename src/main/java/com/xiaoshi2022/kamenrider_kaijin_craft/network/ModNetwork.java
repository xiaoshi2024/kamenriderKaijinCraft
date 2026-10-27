package com.xiaoshi2022.kamenrider_kaijin_craft.network;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.skill.StoriousSkill;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.skill.StoriousSkills;
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

        registrar.playToServer(KaijinSkillPayload.TYPE, KaijinSkillPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        StoriousSkills.use(player, StoriousSkill.byId(payload.skill()));
                    }
                }));

        registrar.playToClient(QiAuraPayload.TYPE, QiAuraPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        com.xiaoshi2022.kamenrider_kaijin_craft.client.ClientAuraHandler.accept(payload);
                    }
                }));

        registrar.playToClient(DarkBeamPayload.TYPE, DarkBeamPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        com.xiaoshi2022.kamenrider_kaijin_craft.client.ClientSkillHandler.acceptBeam(payload);
                    }
                }));

        registrar.playToClient(KaijinAnimationPayload.TYPE, KaijinAnimationPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        com.xiaoshi2022.kamenrider_kaijin_craft.client.ClientAnimationHandler.accept(payload);
                    }
                }));

        registrar.playToClient(AntlerTendrilsPayload.TYPE, AntlerTendrilsPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        com.xiaoshi2022.kamenrider_kaijin_craft.client.ClientSkillHandler.acceptTendrils(payload);
                    }
                }));

        registrar.playToClient(SkillCooldownPayload.TYPE, SkillCooldownPayload.CODEC, (payload, context) ->
                context.enqueueWork(() -> {
                    if (FMLEnvironment.dist.isClient()) {
                        com.xiaoshi2022.kamenrider_kaijin_craft.client.ClientSkillHandler.acceptCooldown(payload);
                    }
                }));
    }
}
