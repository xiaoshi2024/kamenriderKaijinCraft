package com.xiaoshi2022.kamenrider_kaijin_craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.QiAuraPayload;
import net.minecraft.client.Camera;
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
import java.util.Comparator;
import java.util.List;

/**
 * 尸兄模组「气」的 1.21.1 粒子化移植。
 * <p>
 * 每一缕气都是一颗贴在螺线上的红色粒子：<br>
 * {@code angle = 起始相位 + u * 圈数 * 2π}，{@code radius = R * f(u)}，{@code y = H * g(u)}。<br>
 * 变异时 {@code f} 从内向外、{@code g} 自下而上，气浪向外炸开；
 * 拟态时反过来，螺线向内收束，气被吸回体内。相邻粒子之间再补一条短拖尾，
 * 形成截图里那种螺旋状扫过来的红色气纹。
 */
public final class QiAuraRenderer {
    private static final ResourceLocation QI_MIST = KaijinMOD.id("textures/effect/qi_mist.png");
    private static final List<Aura> AURAS = new ArrayList<>();
    private static ClientLevel world;

    /** 螺旋臂数与每条臂上的粒子数 */
    private static final int ARMS = 3;
    private static final int PARTICLES_PER_ARM = 34;
    /** 螺线总圈数（2.2 圈） */
    private static final float TURNS = 4.4F;
    /** 火星数量 */
    private static final int EMBERS = 18;

    private static final class Aura {
        QiAuraPayload data;
        long until;
        final long born;

        Aura(QiAuraPayload data, long now) {
            this.data = data;
            this.born = now;
            this.until = now + data.ticks();
        }
    }

    private QiAuraRenderer() {
    }

    private static void world() {
        ClientLevel current = Minecraft.getInstance().level;
        if (world != current) {
            AURAS.clear();
            world = current;
        }
    }

    private static boolean outward(Aura aura) {
        return switch (aura.data.channel()) {
            case "mutate", "mutate_mist" -> true;
            default -> aura.data.anchor() < 0;
        };
    }

    public static void accept(QiAuraPayload data) {
        world();
        if (world == null || !Float.isFinite(data.radius()) || data.radius() <= 0
                || data.radius() > 16 || data.ticks() <= 0) {
            return;
        }
        long now = world.getGameTime();
        if (data.anchor() >= 0) {
            for (Aura aura : AURAS) {
                if (aura.data.anchor() == data.anchor() && aura.data.channel().equals(data.channel())) {
                    aura.data = data;
                    aura.until = now + Math.min(data.ticks(), 1200);
                    return;
                }
            }
        }
        if (AURAS.size() >= 128) {
            AURAS.remove(0);
        }
        AURAS.add(new Aura(new QiAuraPayload(data.anchor(), data.channel(), data.center(), data.color(),
                data.radius(), Math.min(data.ticks(), 1200)), now));
    }

    public static void tick() {
        world();
        if (world == null) {
            return;
        }
        AURAS.removeIf(aura -> aura.until <= world.getGameTime() || aura.data.anchor() >= 0
                && (world.getEntity(aura.data.anchor()) == null || !world.getEntity(aura.data.anchor()).isAlive()));
    }

    private static Vec3 center(Aura aura) {
        Entity entity = world.getEntity(aura.data.anchor());
        return entity == null ? aura.data.center() : entity.getBoundingBox().getCenter();
    }

    private static float fade(Aura aura) {
        return (float) Math.min(1, Math.max(0, (aura.until - world.getGameTime()) / 6.0));
    }

    public static void render(PoseStack stack, MultiBufferSource.BufferSource buffers, float partialTick) {
        world();
        if (world == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();
        Vector3f up = new Vector3f(camera.getUpVector());
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(QI_MIST));

        List<Aura> sorted = new ArrayList<>(AURAS);
        sorted.sort(Comparator.comparingDouble((Aura aura) -> center(aura).distanceToSqr(cameraPos)).reversed());
        for (Aura aura : sorted) {
            Vec3 center = center(aura);
            if (center.distanceToSqr(cameraPos) > 9216) {
                continue;
            }
            if (aura.data.anchor() >= 0) {
                Entity entity = world.getEntity(aura.data.anchor());
                if (entity != null && !(entity == minecraft.player
                        && minecraft.options.getCameraType().isFirstPerson())) {
                    spiral(stack, glow, aura, entity.getPosition(partialTick), entity.getBbHeight(),
                            partialTick, right, up, false);
                }
            } else {
                spiral(stack, glow, aura, center, aura.data.radius() * 1.1, partialTick, right, up, true);
            }
        }
    }

    /**
     * 螺线粒子主体。
     *
     * @param base       螺线原点（实体脚下 / 气团中心）
     * @param bodyHeight 螺线上升高度
     * @param expanding  无锚点的气团会先快速外扩一下
     */
    private static void spiral(PoseStack stack, VertexConsumer out, Aura aura, Vec3 base, double bodyHeight,
                               float partialTick, Vector3f right, Vector3f up, boolean expanding) {
        float opacity = fade(aura);
        if (opacity <= 0) {
            return;
        }
        boolean release = outward(aura);
        int color = aura.data.color();
        int baseR = (color >> 16) & 255;
        int baseG = (color >> 8) & 255;
        int baseB = color & 255;

        double time = world.getGameTime() + partialTick;
        float radius = Math.max(0.6F, Math.min(3.4F, aura.data.radius()));
        float height = (float) Math.max(1.1, Math.min(3.4, bodyHeight));
        float spin = expanding
                ? (float) (time * 0.02)
                : (float) (time * (release ? 0.05 : 0.04));

        // 无锚点气团刚生成时快速鼓起
        float grow = 1.0F;
        if (expanding) {
            float life = Math.min(1.0F, (world.getGameTime() + partialTick - aura.born) / 10.0F);
            grow = 0.35F + 0.65F * life;
        }
        radius *= grow;

        Matrix4f pose = stack.last().pose();
        for (int arm = 0; arm < ARMS; arm++) {
            float armPhase = arm * (float) (Math.PI * 2.0 / ARMS);
            float prevX = 0, prevY = 0, prevZ = 0;
            boolean hasPrev = false;

            for (int index = 0; index < PARTICLES_PER_ARM; index++) {
                // u 沿螺线从头流到尾，并随时间整体流动
                float u = (index / (float) PARTICLES_PER_ARM
                        + (float) (time * (release ? 0.012 : 0.009))) % 1.0F;
                float angle = armPhase + u * TURNS + spin;

                float r;
                float y;
                if (release) {
                    // 由内向外、自下而上地炸开
                    r = radius * (0.18F + 0.9F * u);
                    y = height * (0.08F + 0.95F * u * u);
                } else {
                    // 由外向内、自上而下地收束
                    r = radius * (1.05F - 0.92F * u);
                    y = height * (0.98F - 0.8F * u);
                }

                // 轻微扰动，避免螺线过于规整
                float wobble = (float) Math.sin(time * 0.18 + arm * 2.1 + index * 0.7);
                r *= 1.0F + 0.06F * wobble;
                y += 0.07F * (float) Math.sin(angle * 2.0 + time * 0.12 + index);

                float px = (float) (base.x + Math.cos(angle) * r);
                float pz = (float) (base.z + Math.sin(angle) * r);
                float py = (float) (base.y + 0.15 + y);

                float envelope = (float) Math.sin(Math.PI * u);
                int alpha = (int) (opacity * envelope * (release ? 155 : 120));
                if (alpha <= 0) {
                    continue;
                }
                float size = (0.10F + 0.13F * (1.0F - u)) * (release ? 1.2F : 1.0F)
                        * (1.0F + 0.25F * wobble);

                // 拖尾：连接上一颗粒子，形成扫过来的气纹
                if (hasPrev) {
                    float dx = px - prevX;
                    float dy = py - prevY;
                    float dz = pz - prevZ;
                    if (dx * dx + dy * dy + dz * dz < 0.36F) {
                        ribbon(out, pose, right, prevX, prevY, prevZ, px, py, pz, size * 0.42F,
                                baseR, baseG, baseB, (int) (alpha * 0.45F));
                    }
                }

                // 外层暗红轮廓 + 内层高亮核心
                billboard(out, pose, right, up, px, py, pz, size, baseR, baseG, baseB,
                        (int) (alpha * 0.55F));
                billboard(out, pose, right, up, px, py, pz, size * 0.52F,
                        Math.min(255, baseR + 95), Math.min(255, baseG + 38), Math.min(255, baseB + 38), alpha);

                prevX = px;
                prevY = py;
                prevZ = pz;
                hasPrev = true;
            }
        }

        // 零星上升的火星
        for (int index = 0; index < EMBERS; index++) {
            float seed = hash(index * 7 + 3);
            float life = (float) ((time * 0.012 + seed) % 1.0);
            float angle = (float) (seed * Math.PI * 2.0 + time * 0.03);
            float emberRadius = radius * (0.3F + 0.8F * hash(index * 13 + 1));
            float emberY = height * (0.1F + 0.95F * life);
            int alpha = (int) (Math.sin(Math.PI * life) * 0.9F * opacity * 150);
            if (alpha <= 0) {
                continue;
            }
            billboard(out, pose, right, up,
                    (float) (base.x + Math.cos(angle) * emberRadius), (float) (base.y + emberY),
                    (float) (base.z + Math.sin(angle) * emberRadius),
                    0.05F + 0.05F * seed,
                    Math.min(255, baseR + 85), Math.min(255, baseG + 30), Math.min(255, baseB + 30), alpha);
        }
    }

    /** 朝向摄像机的小方形粒子（qi_mist 贴图就是一张软雾） */
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

    /** 粒子之间的拖尾（宽度沿摄像机右向量展开） */
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

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float z,
                               float u, float v, int r, int g, int b, int a) {
        out.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0, 1, 0);
    }

    /** 稳定伪随机（0..1） */
    private static float hash(int value) {
        float x = (float) Math.sin(value * 12.9898) * 43758.5453F;
        return x - (float) Math.floor(x);
    }
}
