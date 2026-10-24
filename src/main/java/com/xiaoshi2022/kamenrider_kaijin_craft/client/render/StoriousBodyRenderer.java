package com.xiaoshi2022.kamenrider_kaijin_craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

import java.util.HashMap;
import java.util.List;
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
    /** 模型里承载手臂网格的骨骼（枢轴在肩部） */
    public static final String HAND_RIGHT = "RightArm2";
    public static final String HAND_LEFT = "LeftArm2";
    /**
     * 手臂子树的顶层根骨骼。RightArm2 与碎甲装饰 bone37/38 都挂在它下面，
     * 第一人称只隐藏这个根以外的顶层骨骼即可（setHidden 会连带隐藏子骨骼）。
     */
    private static final String ARM_ROOT_RIGHT = "RightArm";
    private static final String ARM_ROOT_LEFT = "LeftArm";
    /**
     * 第一人称手臂的垂直偏移。1.5 是「脖子对齐」的理论值，实际看起来偏低，
     * 往上抬一点；每 0.0625 相当于模型里的 1 像素。
     */
    private static final double FIRST_PERSON_ARM_Y = 1.75D;

    private static final Map<UUID, StoriousBodyRenderer> RENDERERS = new HashMap<>();
    private static EntityRendererProvider.Context context;

    private final StoriousAnimatable animatable;
    /**
     * 专门用于第一人称手臂的绘制器：GeoReplacedEntityRenderer 的 actuallyRender 会施加
     * 实体旋转/平移（第三人称整身用），直接拿来画第一人称手臂会被推到摄像机外；
     * GeoObjectRenderer 只做骨骼动画 + 递归绘制，正好适合。
     */
    private final GeoObjectRenderer<StoriousAnimatable> armRenderer;

    private StoriousBodyRenderer(EntityRendererProvider.Context context) {
        this(context, new StoriousAnimatable());
    }

    private StoriousBodyRenderer(EntityRendererProvider.Context context, StoriousAnimatable animatable) {
        super(context, new StoriousEntityModel(), animatable);
        this.animatable = animatable;
        this.armRenderer = new GeoObjectRenderer<>(getGeoModel());
        this.shadowRadius = 0.8F;

        // 参考 KamenRiderBossYOUandME 的 StoriousRender：把手持物品挂到手臂骨骼上
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, StoriousAnimatable animatable) {
                AbstractClientPlayer player = getCurrentEntity();
                if (player == null) {
                    return null;
                }
                if (HAND_RIGHT.equals(bone.getName())) {
                    return player.getMainHandItem();
                }
                if (HAND_LEFT.equals(bone.getName())) {
                    return player.getOffhandItem();
                }
                return null;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack,
                                                                  StoriousAnimatable animatable) {
                return HAND_LEFT.equals(bone.getName())
                        ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                        : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack,
                                              StoriousAnimatable animatable, MultiBufferSource bufferSource,
                                              float partialTick, int packedLight, int packedOverlay) {
                // 骨骼枢轴在肩部，沿手臂往下偏移到手掌位置
                poseStack.translate(HAND_LEFT.equals(bone.getName()) ? -0.125F : 0.125F, -0.62F, 0.0F);
                super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource,
                        partialTick, packedLight, packedOverlay);
            }
        });
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

    /**
     * 第一人称手臂：只保留当前手臂子树，直接在事件给的 PoseStack 里画。
     * <p>
     * 用 {@link GeoObjectRenderer} 的 actuallyRender 而不是本类的：后者会施加实体旋转/平移，
     * 手臂会被推出屏幕。isReRender 传 false 让骨骼动画（idle 等）正常更新。
     */
    public void renderFirstPersonArm(PoseStack poseStack, AbstractClientPlayer player, HumanoidArm arm,
                                     float partialTick, MultiBufferSource bufferSource, int packedLight) {
        String armRoot = arm == HumanoidArm.RIGHT ? ARM_ROOT_RIGHT : ARM_ROOT_LEFT;

        BakedGeoModel model = getGeoModel()
                .getBakedModel(getGeoModel().getModelResource(this.animatable, this));

        // 只留下手臂子树的顶层根（RightArm2、bone37/38 都在它下面），其余顶层骨骼隐藏
        List<GeoBone> bones = model.topLevelBones();
        boolean[] previousHidden = new boolean[bones.size()];
        for (int index = 0; index < bones.size(); index++) {
            GeoBone bone = bones.get(index);
            previousHidden[index] = bone.isHidden();
            bone.setHidden(!armRoot.equals(bone.getName()));
        }

        this.animatable.setPartialTick(partialTick);
        poseStack.pushPose();
        try {
            // Geo 模型是 y 向上（脚在 0、脖子在 1.5 格），而第一人称手臂的 PoseStack 是原版
            // 模型空间（y 向下、脖子在 0），这里抬高并翻转 y 与之一致
            poseStack.translate(0.0D, FIRST_PERSON_ARM_Y, 0.0D);
            poseStack.scale(1.0F, -1.0F, 1.0F);

            RenderType renderType = RenderType.entityTranslucent(StoriousEntityModel.TEXTURE);
            this.armRenderer.actuallyRender(poseStack, this.animatable, model, renderType, bufferSource,
                    bufferSource.getBuffer(renderType), false, partialTick, packedLight,
                    OverlayTexture.NO_OVERLAY, -1);
        } finally {
            poseStack.popPose();
            for (int index = 0; index < bones.size(); index++) {
                bones.get(index).setHidden(previousHidden[index]);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer entity) {
        return StoriousEntityModel.TEXTURE;
    }
}
