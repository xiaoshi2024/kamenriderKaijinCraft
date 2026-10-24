package com.xiaoshi2022.kamenrider_kaijin_craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 斯特利乌斯怪人形态的整身 Geo 绘制器。
 * <p>
 * 参考尸兄模组的 {@code ZuoGuardianBodyRenderer}：不在实体渲染注册表里替换玩家，
 * 而是在 {@code RenderPlayerEvent.Pre} 里 cancel 掉原版玩家渲染后直接绘制。
 * 区别是这里为每位玩家各建一个绘制器（各自的动画宿主），多人同屏时动画不会串台。
 */
public class StoriousBodyRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayer, StoriousAnimatable> {
    private static final Map<UUID, StoriousBodyRenderer> RENDERERS = new HashMap<>();
    private static EntityRendererProvider.Context context;

    private final StoriousAnimatable animatable;

    private StoriousBodyRenderer(EntityRendererProvider.Context context) {
        this(context, new StoriousAnimatable());
    }

    private StoriousBodyRenderer(EntityRendererProvider.Context context, StoriousAnimatable animatable) {
        super(context, new StoriousEntityModel(), animatable);
        this.animatable = animatable;
        this.shadowRadius = 0.8F;
    }

    /** 在 EntityRenderersEvent.AddLayers 里保存上下文，绘制器按玩家惰性创建。 */
    public static void create(EntityRendererProvider.Context context) {
        StoriousBodyRenderer.context = context;
    }

    public static @Nullable StoriousBodyRenderer get(AbstractClientPlayer player) {
        if (context == null) {
            return null;
        }
        return RENDERERS.computeIfAbsent(player.getUUID(), id -> new StoriousBodyRenderer(context));
    }

    @Override
    public void render(AbstractClientPlayer entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        animatable.setPartialTick(partialTick);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer entity) {
        return StoriousEntityModel.TEXTURE;
    }
}
