package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.render.KaijinSkillRenderer;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.render.QiAuraRenderer;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.render.StoriousBodyRenderer;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.KaijinSkillPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.ToggleFormPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.skill.StoriousSkill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KaijinMOD.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private ClientGameEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        QiAuraRenderer.tick();
        KaijinSkillRenderer.tick();
        Minecraft minecraft = Minecraft.getInstance();

        // G：拟态 / 解放
        while (KaijinKeys.TOGGLE_FORM.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null) {
                continue;
            }
            if (!BookKaijinRace.get(minecraft.player).bookKaijin()) {
                continue;
            }
            PacketDistributor.sendToServer(ToggleFormPayload.INSTANCE);
        }

        // V：技能盘（按住打开，松开关闭）
        while (KaijinKeys.SKILL_WHEEL.consumeClick()) {
            if (minecraft.player == null) {
                continue;
            }
            if (minecraft.screen instanceof StoriousSkillWheelScreen) {
                minecraft.setScreen(null);
            } else if (minecraft.screen == null && BookKaijinRace.get(minecraft.player).bookKaijin()) {
                minecraft.setScreen(new StoriousSkillWheelScreen());
            }
        }

        // 1 / 2 / 3：释放快捷技能槽里绑定的技能
        for (int slot = 0; slot < KaijinKeys.QUICK_SKILLS.length; slot++) {
            while (KaijinKeys.QUICK_SKILLS[slot].consumeClick()) {
                if (minecraft.player == null || minecraft.screen != null) {
                    continue;
                }
                if (!BookKaijinRace.get(minecraft.player).bookKaijin()) {
                    continue;
                }
                StoriousSkill skill = ClientSkillBindings.getSkill(slot);
                if (skill != null) {
                    PacketDistributor.sendToServer(new KaijinSkillPayload(skill.ordinal()));
                }
            }
        }
    }

    /** 怪人形态：整身替换成斯特利乌斯。 */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player instanceof AbstractClientPlayer clientPlayer) || !BookKaijinRace.isMonster(clientPlayer)) {
            return;
        }
        StoriousBodyRenderer renderer = StoriousBodyRenderer.get(clientPlayer);
        if (renderer == null) {
            return;
        }
        event.setCanceled(true);
        renderer.render(clientPlayer, clientPlayer.getYRot(), event.getPartialTick(), event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight());
    }

    /** 变异后第一人称：只画怪人模型的当前手臂，替换原版手臂。 */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        if (!BookKaijinRace.isMonster(player)) {
            return;
        }
        StoriousBodyRenderer renderer = StoriousBodyRenderer.get(player);
        if (renderer == null) {
            return;
        }
        event.setCanceled(true);

        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        try {
            renderer.renderFirstPersonArm(poseStack, player, event.getArm(), partialTick,
                    event.getMultiBufferSource(), event.getPackedLight());
        } finally {
            poseStack.popPose();
        }
    }

    /** 红气与技能光效：在实体与半透明方块之后画。 */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        QiAuraRenderer.render(poseStack, buffers, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        KaijinSkillRenderer.render(poseStack, buffers, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        buffers.endBatch();
        poseStack.popPose();
    }
}
