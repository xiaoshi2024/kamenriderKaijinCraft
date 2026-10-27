package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.client.render;

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
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 千翼 · Neo Amazon Origin 的整身 Geo 绘制器。
 * <p>
 * 模型有六只手臂，主手持有物品的是 "Right Arm" / "Left Arm" 两条根骨骼，
 * 其余四条（Right_Arm2/3、Left_Arm2/3）是装饰肢体。
 */
public class NeoAmazonOriginBodyRenderer
        extends GeoReplacedEntityRenderer<AbstractClientPlayer, NeoAmazonOriginAnimatable> {
    public static final String HAND_RIGHT = "Right Arm";
    public static final String HAND_LEFT = "Left Arm";

    private static final String ARM_ROOT_RIGHT = "Right Arm";
    private static final String ARM_ROOT_LEFT = "Left Arm";
    private static final double FIRST_PERSON_ARM_Y = 1.5D;

    private static final Map<UUID, NeoAmazonOriginBodyRenderer> RENDERERS = new HashMap<>();
    private static EntityRendererProvider.Context context;

    private final NeoAmazonOriginAnimatable animatable;
    private final GeoObjectRenderer<NeoAmazonOriginAnimatable> armRenderer;

    private NeoAmazonOriginBodyRenderer(EntityRendererProvider.Context context) {
        this(context, new NeoAmazonOriginAnimatable());
    }

    public boolean playTriggered(String animation) {
        return animatable.playTriggered(animation);
    }

    private NeoAmazonOriginBodyRenderer(EntityRendererProvider.Context context,
                                        NeoAmazonOriginAnimatable animatable) {
        super(context, new NeoAmazonOriginModel(), animatable);
        this.animatable = animatable;
        this.armRenderer = new GeoObjectRenderer<>(getGeoModel());
        this.shadowRadius = 0.8F;

        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, NeoAmazonOriginAnimatable animatable) {
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
                                              NeoAmazonOriginAnimatable animatable,
                                              MultiBufferSource bufferSource, float partialTick,
                                              int packedLight, int packedOverlay) {
                AbstractClientPlayer player = getCurrentEntity();
                if (player == null || stack.isEmpty()) {
                    return;
                }
                boolean left = HAND_LEFT.equals(bone.getName());
                ItemDisplayContext context = left
                        ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                        : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

                // 抵消 GeckoLib 的二次旋转
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

    public static void create(EntityRendererProvider.Context context) {
        NeoAmazonOriginBodyRenderer.context = context;
    }

    public static @Nullable NeoAmazonOriginBodyRenderer get(AbstractClientPlayer player) {
        if (context == null) {
            return null;
        }
        return RENDERERS.computeIfAbsent(player.getUUID(),
                id -> new NeoAmazonOriginBodyRenderer(context));
    }

    @Override
    public void render(AbstractClientPlayer entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        animatable.setPartialTick(partialTick);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * 第一人称手臂：只保留当前手臂子树，直接在事件给的 PoseStack 里画。
     * 空间校正沿用卡律布狄斯的实测参数（模型肩高 Y=22）。
     */
    public void renderFirstPersonArm(PoseStack poseStack, AbstractClientPlayer player, HumanoidArm arm,
                                     float partialTick, MultiBufferSource bufferSource, int packedLight) {
        String armRoot = arm == HumanoidArm.RIGHT ? ARM_ROOT_RIGHT : ARM_ROOT_LEFT;
        BakedGeoModel model = getGeoModel().getBakedModel(
                getGeoModel().getModelResource(this.animatable, this));
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
            poseStack.translate(0.0D, FIRST_PERSON_ARM_Y, 0.0D);
            poseStack.scale(-1.0F, -1.0F, 1.0F);

            float offsetX = arm == HumanoidArm.RIGHT ? 0.4F : -0.4F;
            poseStack.translate(offsetX, -0.1F, -0.2F);
            poseStack.scale(0.5F, 0.5F, 0.5F);

            RenderType renderType = RenderType.entityTranslucent(NeoAmazonOriginModel.TEXTURE);
            VertexConsumer buffer = bufferSource.getBuffer(renderType);

            // 把模型肩膀高度（Y=22）重新归零
            poseStack.translate(0.0D, 1.375D, 0.0D);

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
        return NeoAmazonOriginModel.TEXTURE;
    }
}
