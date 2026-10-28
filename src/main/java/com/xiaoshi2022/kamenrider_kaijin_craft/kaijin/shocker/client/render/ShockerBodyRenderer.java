package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.shocker.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.shocker.race.ShockerRace;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 修卡怪人的整身绘制器：动作全部交给 shocker.animation.json 的
 * idle / walk / attack / swim，变身瞬间叠加一次 yi；头部额外跟随玩家视线。
 */
public class ShockerBodyRenderer
        extends GeoReplacedEntityRenderer<AbstractClientPlayer, ShockerAnimatable> {
    private static final String BONE_HEAD = "bipedHead";
    private static final String BONE_RIGHT_ARM = "bipedRightArm";
    private static final String BONE_LEFT_ARM = "bipedLeftArm";
    /** 模型肩高 Y=22，换算成方块高度。 */
    private static final double FIRST_PERSON_ARM_Y = 1.5D;

    private static final Map<UUID, ShockerBodyRenderer> RENDERERS = new HashMap<>();
    /** 上一 tick 处于修卡怪人形态的玩家，用来识别「变身瞬间」。 */
    private static final Set<UUID> MONSTER_PLAYERS = new HashSet<>();
    private static EntityRendererProvider.Context context;

    private final ShockerAnimatable animatable;
    private float headYaw;
    private float headPitch;

    private ShockerBodyRenderer(EntityRendererProvider.Context context) {
        this(context, new ShockerAnimatable());
    }

    private ShockerBodyRenderer(EntityRendererProvider.Context context, ShockerAnimatable animatable) {
        super(context, new ShockerEntityModel(), animatable);
        this.animatable = animatable;
        this.shadowRadius = 0.5F;
    }

    public static void create(EntityRendererProvider.Context context) {
        ShockerBodyRenderer.context = context;
    }

    public static @Nullable ShockerBodyRenderer get(AbstractClientPlayer player) {
        if (context == null) {
            return null;
        }
        return RENDERERS.computeIfAbsent(player.getUUID(), id -> new ShockerBodyRenderer(context));
    }

    /** 客户端每 tick：识别变身瞬间触发 yi 动作，并推进动作倒计时。 */
    public static void tick(@Nullable ClientLevel level) {
        if (level == null) {
            MONSTER_PLAYERS.clear();
            return;
        }
        for (AbstractClientPlayer player : level.players()) {
            UUID id = player.getUUID();
            if (!ShockerRace.isMonster(player)) {
                MONSTER_PLAYERS.remove(id);
                continue;
            }
            ShockerBodyRenderer renderer = get(player);
            if (renderer == null) {
                continue;
            }
            if (!MONSTER_PLAYERS.contains(id)) {
                renderer.animatable.playTransform();
            }
            renderer.animatable.tick();
            MONSTER_PLAYERS.add(id);
        }
    }

    @Override
    public void render(AbstractClientPlayer entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        animatable.setPartialTick(partialTick);
        animatable.setState(entity.swinging, entity.isSwimming(), entity.walkAnimation.speed(partialTick));
        // GeckoLib 的模型空间在 X 轴上是镜像的，偏航方向要取反才与视线一致
        this.headYaw = -Mth.clamp(Mth.wrapDegrees(entity.getYHeadRot() - entity.yBodyRot),
                -75.0F, 75.0F) * Mth.DEG_TO_RAD;
        this.headPitch = Mth.clamp(entity.getXRot(), -60.0F, 60.0F) * Mth.DEG_TO_RAD;
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** 在动画骨骼姿势之上叠加头部视线转动，画完立刻还原，避免逐帧累加。 */
    @Override
    public void renderRecursively(PoseStack poseStack, ShockerAnimatable animatable, GeoBone bone,
                                  RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                                  int colour) {
        boolean head = !isReRender && BONE_HEAD.equals(bone.getName());
        float oldX = 0.0F;
        float oldY = 0.0F;
        if (head) {
            oldX = bone.getRotX();
            oldY = bone.getRotY();
            bone.updateRotation(oldX + this.headPitch, oldY + this.headYaw, bone.getRotZ());
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
        if (head) {
            bone.updateRotation(oldX, oldY, bone.getRotZ());
        }
    }

    /** 第一人称：只保留当前手臂子树，替换原版手臂。 */
    public void renderFirstPersonArm(PoseStack poseStack, AbstractClientPlayer player, HumanoidArm arm,
                                     float partialTick, MultiBufferSource bufferSource, int packedLight) {
        String armRoot = arm == HumanoidArm.RIGHT ? BONE_RIGHT_ARM : BONE_LEFT_ARM;
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

        animatable.setPartialTick(partialTick);
        poseStack.pushPose();
        try {
            poseStack.translate(0.0D, FIRST_PERSON_ARM_Y, 0.0D);
            poseStack.scale(-1.0F, -1.0F, 1.0F);

            float offsetX = arm == HumanoidArm.RIGHT ? 0.4F : -0.4F;
            poseStack.translate(offsetX, -0.1F, -0.2F);
            poseStack.scale(0.5F, 0.5F, 0.5F);

            RenderType renderType = RenderType.entityCutoutNoCull(ShockerEntityModel.TEXTURE);
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
        return ShockerEntityModel.TEXTURE;
    }
}
