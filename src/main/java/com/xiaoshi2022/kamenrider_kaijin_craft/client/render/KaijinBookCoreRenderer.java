package com.xiaoshi2022.kamenrider_kaijin_craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.entity.KaijinBookCoreEntity;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModItems;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 肚子里的实体核心：一本悬空浮动的卡律布狄斯之书。
 * <p>
 * 直接借原版物品渲染器画书（书的物品模型就是 Geo 的），比再写一套实体 Geo 模型更省事。
 */
public class KaijinBookCoreRenderer extends EntityRenderer<KaijinBookCoreEntity> {
    private static final ResourceLocation TEXTURE = KaijinMOD.id("textures/item/charybdis_book.png");

    private final ItemRenderer itemRenderer;
    private final ItemStack book = new ItemStack(ModItems.CHARYBDIS_BOOK.get());

    public KaijinBookCoreRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(KaijinBookCoreEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        float bob = Mth.sin((entity.tickCount + partialTick) * 0.12F) * 0.12F;
        poseStack.translate(0.0D, 1.0D + bob, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        poseStack.scale(1.8F, 1.8F, 1.8F);
        // 肚子里没有光源，按全亮画，不然书是一团黑
        this.itemRenderer.renderStatic(this.book, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, poseStack, bufferSource, entity.level(), entity.getId());
        poseStack.popPose();
        // 普通 EntityRenderer 不会自己画名字，这里补一次，玩家一眼能认出是「核心」
        renderNameTag(entity, entity.getDisplayName(), poseStack, bufferSource,
                LightTexture.FULL_BRIGHT, partialTick);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(KaijinBookCoreEntity entity) {
        return TEXTURE;
    }
}
