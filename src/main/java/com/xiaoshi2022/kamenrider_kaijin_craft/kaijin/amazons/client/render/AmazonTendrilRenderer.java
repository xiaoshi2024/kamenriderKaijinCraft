package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.AmazonTendrilsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 千翼被动触手的纯视觉表现。
 * <p>
 * 每根触手是一条朝向摄像机的渐细带子，从玩家全身各处（不同高度、不同方位）
 * 甩向被刺穿的目标，末端收成针尖；命中点再补一圈暗红小点表示被掏穿的伤口。
 * 整体做成「天线宝宝那根天线」的纤细弯曲感，而不是粗大的触手。
 */
public final class AmazonTendrilRenderer {
    private static final ResourceLocation SPIKE = KaijinMOD.id("textures/effect/solid_white.png");
    private static final int SEGMENTS = 10;
    /** 每个目标分裂出的触手数。 */
    private static final int TENDRILS_PER_TARGET = 3;

    private record Lash(int anchor, List<Vec3> targets, long born, int ticks) {
    }

    private static final List<Lash> LASHES = new ArrayList<>();
    private static ClientLevel world;

    private AmazonTendrilRenderer() {
    }

    private static void world() {
        ClientLevel current = Minecraft.getInstance().level;
        if (world != current) {
            LASHES.clear();
            world = current;
        }
    }

    public static void accept(AmazonTendrilsPayload payload) {
        world();
        if (world == null || payload.targets().isEmpty() || payload.ticks() <= 0) {
            return;
        }
        if (LASHES.size() >= 64) {
            LASHES.remove(0);
        }
        LASHES.add(new Lash(payload.anchor(), List.copyOf(payload.targets()),
                world.getGameTime(), Math.min(payload.ticks(), 60)));
    }

    public static void tick() {
        world();
        if (world == null) {
            return;
        }
        long now = world.getGameTime();
        LASHES.removeIf(lash -> lash.born() + lash.ticks() <= now
                || world.getEntity(lash.anchor()) == null);
    }

    public static void render(PoseStack stack, MultiBufferSource.BufferSource buffers, float partialTick) {
        world();
        if (world == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentEmissive(SPIKE));
        Matrix4f pose = stack.last().pose();

        double time = world.getGameTime() + partialTick;
        for (Lash lash : LASHES) {
            Entity entity = world.getEntity(lash.anchor());
            if (entity == null) {
                continue;
            }
            float life = (float) ((time - lash.born()) / Math.max(1, lash.ticks()));
            if (life < 0.0F || life > 1.0F) {
                continue;
            }
            // 快速抽出、短暂停留、末端迅速消散
            float opacity = Math.min(1.0F, life * 6.0F) * Math.min(1.0F, (1.0F - life) * 2.2F);
            if (opacity <= 0.02F) {
                continue;
            }
            Vec3 base = entity.getPosition(partialTick);
            if (base.distanceToSqr(cameraPos) > 9216) {
                continue;
            }
            float bodyHeight = entity.getBbHeight();
            float yaw = entity.getYRot();

            for (int targetIndex = 0; targetIndex < lash.targets().size(); targetIndex++) {
                Vec3 tip = lash.targets().get(targetIndex);
                for (int tendril = 0; tendril < TENDRILS_PER_TARGET; tendril++) {
                    int seed = targetIndex * 131 + tendril * 37 + (int) (lash.born() & 0xFFFF);
                    // 触手从身体不同高度、不同方位的点甩出
                    float angle = yaw + hash(seed * 17 + 3) * (float) (Math.PI * 2);
                    float startRadius = 0.18F + 0.35F * hash(seed * 31 + 5);
                    float startY = bodyHeight * (0.18F + 0.62F * hash(seed * 53 + 7));
                    Vec3 start = base.add(Math.cos(angle) * startRadius, startY,
                            Math.sin(angle) * startRadius);

                    // 二次贝塞尔控制点：向侧上方拱起，做出「细长天线」的弯
                    Vec3 mid = start.add(tip).scale(0.5D);
                    Vec3 dir = tip.subtract(start);
                    Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
                    if (side.lengthSqr() < 1.0E-6D) {
                        side = new Vec3(1.0D, 0.0D, 0.0D);
                    }
                    side = side.normalize();
                    double curl = (hash(seed * 67 + 11) - 0.5D) * 1.7D;
                    Vec3 control = mid.add(side.scale(curl))
                            .add(0.0D, 0.25D + 0.55D * hash(seed * 79 + 13), 0.0D);

                    float previousX = (float) start.x;
                    float previousY = (float) start.y;
                    float previousZ = (float) start.z;
                    for (int step = 1; step <= SEGMENTS; step++) {
                        float k = step / (float) SEGMENTS;
                        float pinch = 1.0F - k;
                        float width = 0.052F * pinch * pinch + 0.004F;
                        Vec3 point = bezier(start, control, tip, k);
                        int alpha = (int) (opacity * (60 + 165 * (1.0F - k) * (1.0F - k) + 30));
                        // 越靠根部越偏冷灰，尖端亮白
                        int shade = (int) (200 + 55 * k);
                        ribbon(out, pose, right,
                                previousX, previousY, previousZ,
                                (float) point.x, (float) point.y, (float) point.z,
                                width, shade, Math.min(255, shade + 4), Math.min(255, shade + 6),
                                Math.min(255, alpha));
                        previousX = (float) point.x;
                        previousY = (float) point.y;
                        previousZ = (float) point.z;
                    }

                    // 被掏出洞的伤口：一圈暗红碎点 + 一点白热
                    for (int speck = 0; speck < 4; speck++) {
                        float ox = (hash(seed * 11 + speck * 29) - 0.5F) * 0.34F;
                        float oy = (hash(seed * 23 + speck * 41) - 0.5F) * 0.34F;
                        float oz = (hash(seed * 37 + speck * 53) - 0.5F) * 0.34F;
                        billboard(out, pose, right, up,
                                (float) tip.x + ox, (float) tip.y + oy, (float) tip.z + oz,
                                0.035F + 0.03F * hash(seed * 43 + speck),
                                118, 10, 18, (int) (opacity * 220));
                    }
                    billboard(out, pose, right, up, (float) tip.x, (float) tip.y, (float) tip.z,
                            0.055F, 255, 248, 240, (int) (opacity * 230));
                }
            }
        }
    }

    private static Vec3 bezier(Vec3 start, Vec3 control, Vec3 end, float k) {
        double inv = 1.0D - k;
        return start.scale(inv * inv)
                .add(control.scale(2.0D * inv * k))
                .add(end.scale((double) k * k));
    }

    private static void ribbon(VertexConsumer out, Matrix4f pose, Vector3f right,
                               float x1, float y1, float z1, float x2, float y2, float z2,
                               float width, int r, int g, int b, int a) {
        float rx = right.x() * width;
        float ry = right.y() * width;
        float rz = right.z() * width;
        vertex(out, pose, x1 - rx, y1 - ry, z1 - rz, 0.0F, 1.0F, r, g, b, a);
        vertex(out, pose, x2 - rx, y2 - ry, z2 - rz, 1.0F, 1.0F, r, g, b, a);
        vertex(out, pose, x2 + rx, y2 + ry, z2 + rz, 1.0F, 0.0F, r, g, b, a);
        vertex(out, pose, x1 + rx, y1 + ry, z1 + rz, 0.0F, 0.0F, r, g, b, a);
    }

    private static void billboard(VertexConsumer out, Matrix4f pose, Vector3f right, Vector3f up,
                                  float x, float y, float z, float size, int r, int g, int b, int a) {
        float rx = right.x() * size;
        float ry = right.y() * size;
        float rz = right.z() * size;
        float ux = up.x() * size;
        float uy = up.y() * size;
        float uz = up.z() * size;
        vertex(out, pose, x - rx - ux, y - ry - uy, z - rz - uz, 0.0F, 1.0F, r, g, b, a);
        vertex(out, pose, x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, 1.0F, r, g, b, a);
        vertex(out, pose, x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 0.0F, r, g, b, a);
        vertex(out, pose, x - rx + ux, y - ry + uy, z - rz + uz, 0.0F, 0.0F, r, g, b, a);
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float z,
                               float u, float v, int r, int g, int b, int a) {
        out.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0, 1, 0);
    }

    private static float hash(int value) {
        float x = (float) Math.sin(value * 12.9898) * 43758.5453F;
        return x - (float) Math.floor(x);
    }
}
