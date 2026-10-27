package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
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

import java.util.ArrayList;
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
     * 第一人称手臂的换算：事件给的 PoseStack 是原版人形模型的骨骼空间
     * （1 单位 = 1 格 / 16、y 向下、头顶在 y=0），Geo 模型是 y 向上、脚在 y=0。
     * 与 {@code GeoArmorRenderer.actuallyRender} 完全一致的换算就是抬高 1.5 格 + x/y 取负。
     */
    private static final double FIRST_PERSON_ARM_Y = 1.5D;

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
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack,
                                              StoriousAnimatable animatable, MultiBufferSource bufferSource,
                                              float partialTick, int packedLight, int packedOverlay) {
                AbstractClientPlayer player = getCurrentEntity();
                if (player == null || stack.isEmpty()) {
                    return;
                }
                boolean left = HAND_LEFT.equals(bone.getName());
                ItemDisplayContext context = left
                        ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                        : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                // GeckoLib 的 renderForBone 会在 renderRecursively 已经 prep 好的骨骼矩阵上
                // 再叠一次枢轴+旋转，等于这一根骨骼的旋转被应用了两次（R²）；
                // 先抵消多出来的这一份，物品才会真正贴在掌心，而不是被多转一份偏进身体。
                poseStack.mulPose(Axis.XP.rotation(-bone.getRotX()));
                poseStack.mulPose(Axis.YP.rotation(-bone.getRotY()));
                poseStack.mulPose(Axis.ZP.rotation(-bone.getRotZ()));

                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(0.0F));
                poseStack.translate((left ? -0.3D : 0.3D) / 16.0D, 0.1D, -0.6D);
                // GeckoLib 默认实现把左手标志写死成 false，左手物品不会镜像，这里按原版传进去
                Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, context, left,
                        poseStack, bufferSource, player.level(), packedLight, packedOverlay,
                        player.getId() + context.ordinal());
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

        GeoBone armBone = model.getBone(armRoot).orElse(null);

        // 只留下手臂子树，其余骨骼全部隐藏；同时记录手臂子树姿态并锁定为静止姿势，
        // 否则 idle 等动画会把手臂转出第一人称视野。
        List<GeoBone> bones = new ArrayList<>();
        collectBones(model.topLevelBones(), bones);
        boolean[] previousHidden = new boolean[bones.size()];
        float[][] previousPose = new float[bones.size()][];
        for (int index = 0; index < bones.size(); index++) {
            GeoBone bone = bones.get(index);
            previousHidden[index] = bone.isHidden();
            previousPose[index] = new float[] {
                    bone.getRotX(), bone.getRotY(), bone.getRotZ(),
                    bone.getPosX(), bone.getPosY(), bone.getPosZ()
            };
            boolean armBoneOrChild = inSubtree(bone, armBone);
            bone.setHidden(!armBoneOrChild);
            if (armBoneOrChild) {
                bone.updateRotation(0.0F, 0.0F, 0.0F);
                bone.updatePosition(0.0F, 0.0F, 0.0F);
            }
        }

        this.animatable.setPartialTick(partialTick);
        poseStack.pushPose();
        try {
            poseStack.translate(0.0D, FIRST_PERSON_ARM_Y, 0.0D);
            poseStack.scale(-1.0F, -1.0F, 1.0F);

            RenderType renderType = RenderType.entityTranslucent(StoriousEntityModel.TEXTURE);
            this.armRenderer.actuallyRender(poseStack, this.animatable, model, renderType, bufferSource,
                    bufferSource.getBuffer(renderType), false, partialTick, packedLight,
                    OverlayTexture.NO_OVERLAY, -1);
        } finally {
            poseStack.popPose();
            for (int index = 0; index < bones.size(); index++) {
                GeoBone bone = bones.get(index);
                float[] pose = previousPose[index];
                bone.updateRotation(pose[0], pose[1], pose[2]);
                bone.updatePosition(pose[3], pose[4], pose[5]);
                bone.setHidden(previousHidden[index]);
            }
        }
    }

    private static void collectBones(List<GeoBone> source, List<GeoBone> target) {
        for (GeoBone bone : source) {
            target.add(bone);
            collectBones(bone.getChildBones(), target);
        }
    }

    /** bone 是否就是 root 本身或 root 的后代。 */
    private static boolean inSubtree(GeoBone bone, @Nullable GeoBone root) {
        if (root == null) {
            return false;
        }
        for (GeoBone current = bone; current != null; current = current.getParent()) {
            if (current == root) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer entity) {
        return StoriousEntityModel.TEXTURE;
    }
}
