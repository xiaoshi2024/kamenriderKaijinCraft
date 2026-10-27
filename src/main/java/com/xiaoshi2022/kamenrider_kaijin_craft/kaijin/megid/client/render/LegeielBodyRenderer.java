package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 拉结尔怪人形态的整身 Geo 绘制器。
 * <p>
 * 第三人称与 {@link StoriousBodyRenderer} 同一套实现：不在实体渲染注册表里替换玩家，
 * 而是在 {@code RenderPlayerEvent.Pre} 里 cancel 掉原版玩家渲染后直接绘制。
 * 第一人称手臂沿用 {@link CharybdisBodyRenderer} 的实测空间校正。
 * 区别是拉结尔的手臂骨骼名为 {@code Right Arm / Left Arm}（带空格）。
 */
public class LegeielBodyRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayer, LegeielAnimatable> {
    /** 模型里承载手臂网格的骨骼（注意空格） */
    public static final String HAND_RIGHT = "Right Arm";
    public static final String HAND_LEFT = "Left Arm";
    /** 手臂子树的顶层根骨骼就是手臂本身 */
    private static final String ARM_ROOT_RIGHT = HAND_RIGHT;
    private static final String ARM_ROOT_LEFT = HAND_LEFT;
    /**
     * 第一人称手臂的换算：事件给的 PoseStack 是原版人形模型的骨骼空间，
     * Geo 模型是 y 向上、脚在 y=0。与 {@code GeoArmorRenderer.actuallyRender} 一致：
     * 抬高 1.5 格 + x/y 取负。
     */
    private static final double FIRST_PERSON_ARM_Y = 1.5D;

    private static final Map<UUID, LegeielBodyRenderer> RENDERERS = new HashMap<>();
    private static EntityRendererProvider.Context context;

    private final LegeielAnimatable animatable;

    private LegeielBodyRenderer(EntityRendererProvider.Context context) {
        this(context, new LegeielAnimatable());
    }

    private LegeielBodyRenderer(EntityRendererProvider.Context context, LegeielAnimatable animatable) {
        super(context, new LegeielEntityModel(), animatable);
        this.animatable = animatable;
        this.shadowRadius = 0.8F;

        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, LegeielAnimatable animatable) {
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
                                              LegeielAnimatable animatable, MultiBufferSource bufferSource,
                                              float partialTick, int packedLight, int packedOverlay) {
                AbstractClientPlayer player = getCurrentEntity();
                if (player == null || stack.isEmpty()) {
                    return;
                }
                boolean left = HAND_LEFT.equals(bone.getName());
                ItemDisplayContext context = left
                        ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                        : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                // 抵消 GeckoLib 对目标骨骼重复应用的一次枢轴旋转
                poseStack.mulPose(Axis.XP.rotation(-bone.getRotX()));
                poseStack.mulPose(Axis.YP.rotation(-bone.getRotY()));
                poseStack.mulPose(Axis.ZP.rotation(-bone.getRotZ()));

                poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(0.0F));
                poseStack.translate((left ? -0.3D : 0.3D) / 16.0D, 0.1D, -0.6D);
                Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, context, left,
                        poseStack, bufferSource, player.level(), packedLight, packedOverlay,
                        player.getId() + context.ordinal());
            }
        });
    }

    /** 在 EntityRenderersEvent.AddLayers 里保存上下文，绘制器按玩家惰性创建。 */
    public static void create(EntityRendererProvider.Context context) {
        LegeielBodyRenderer.context = context;
    }

    public static @Nullable LegeielBodyRenderer get(AbstractClientPlayer player) {
        if (context == null) {
            return null;
        }
        return RENDERERS.computeIfAbsent(player.getUUID(), id -> new LegeielBodyRenderer(context));
    }

    @Override
    public void render(AbstractClientPlayer entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        animatable.setPartialTick(partialTick);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * 第一人称手臂：只保留当前手臂子树，直接在事件给的 PoseStack 里画。
     * 手臂与身体同属 Waist 的子骨骼，因此必须遍历完整骨骼树来隐藏非手臂骨骼。
     * <p>
     * 空间校正直接沿用卡律布狄斯的实测参数。
     */
    public void renderFirstPersonArm(PoseStack poseStack, AbstractClientPlayer player, HumanoidArm arm,
                                     float partialTick, MultiBufferSource bufferSource, int packedLight) {
        String armRoot = arm == HumanoidArm.RIGHT ? ARM_ROOT_RIGHT : ARM_ROOT_LEFT;
        BakedGeoModel model = getGeoModel().getBakedModel(getGeoModel().getModelResource(this.animatable, this));
        GeoBone armBone = model.getBone(armRoot).orElse(null);
        if (armBone == null) {
            return;
        }

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
            // 将模型抬高到第一人称高度
            poseStack.translate(0.0D, FIRST_PERSON_ARM_Y, 0.0D);

            // Geo 模型 Y 轴向上，第一人称相机 Y 轴向下
            poseStack.scale(-1.0F, -1.0F, 1.0F);

            // 推到镜头前
            float offsetX = arm == HumanoidArm.RIGHT ? 0.4F : -0.4F;
            float offsetY = -0.1F;
            float offsetZ = -0.2F;
            poseStack.translate(offsetX, offsetY, offsetZ);

            // 缩小模型
            poseStack.scale(0.5F, 0.5F, 0.5F);

            RenderType renderType = RenderType.entityTranslucent(LegeielEntityModel.TEXTURE);
            VertexConsumer buffer = bufferSource.getBuffer(renderType);

            // 把模型肩膀高度（Y=22）重新归零
            poseStack.translate(0.0D, 1.375D, 0.0D);

            // 只从手臂根骨骼递归绘制一次：拉结尔的手臂带有装饰子骨骼，
            // 逐个骨骼单独绘制会让装饰件脱离手臂。
            this.renderRecursively(poseStack, this.animatable, armBone,
                    renderType, bufferSource, buffer,
                    false, partialTick, packedLight, OverlayTexture.NO_OVERLAY, -1);
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
        return LegeielEntityModel.TEXTURE;
    }
}
