package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.render.QiAuraRenderer;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.render.StoriousBodyRenderer;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.ToggleFormPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinRace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = KaijinMOD.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private ClientGameEvents() {
    }

    /** G 键：请求服务端在拟态与怪人形态之间切换。 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        QiAuraRenderer.tick();
        Minecraft minecraft = Minecraft.getInstance();
        while (KaijinKeys.TOGGLE_FORM.consumeClick()) {
            if (minecraft.player == null || minecraft.screen != null) {
                continue;
            }
            if (!BookKaijinRace.get(minecraft.player).bookKaijin()) {
                continue;
            }
            PacketDistributor.sendToServer(ToggleFormPayload.INSTANCE);
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

    /** 红色气焰 / 气雾：在实体与半透明方块之后画，保证不会被模型挡住。 */
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
        buffers.endBatch();
        poseStack.popPose();
    }
}
