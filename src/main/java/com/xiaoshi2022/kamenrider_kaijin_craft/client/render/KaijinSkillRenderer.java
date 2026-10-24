package com.xiaoshi2022.kamenrider_kaijin_craft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.AntlerTendrilsPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.DarkBeamPayload;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 斯特利乌斯技能的客户端表现：
 * <ul>
 *   <li>黑暗能量箭 / 失明光线：一排带抖动的方块（黑色外壳 + 红白高亮核心），对应截图里那种块状能量束</li>
 *   <li>鹿角卷须：从头部连向目标的黄色波浪触须</li>
 * </ul>
 */
public final class KaijinSkillRenderer {
    /**
     * 世界层盒体专用 1×1 纯白贴图。
     * <p>
     * 不用 {@code textures/misc/white.png}：那是 16×16 且参与 mipmap，
     * 大盒体 / 远处会采样到不同 mip 导致边缘发虚。1×1 只有一级 mip，
     * 再配合每面铺满 0..1 的 UV，就是稳定的纯色面。
     */
    private static final ResourceLocation SOLID_WHITE = KaijinMOD.id("textures/effect/solid_white.png");
    private static final List<Beam> BEAMS = new ArrayList<>();
    private static final List<Tendrils> TENDRILS = new ArrayList<>();
    private static ClientLevel world;

    private record Beam(int playerId, Vec3 origin, Vec3 direction, float length, int style, int ticks, long born) {
    }

    private record Tendrils(int playerId, int[] targets, int ticks, long born) {
    }

    private KaijinSkillRenderer() {
    }

    private static void world() {
        ClientLevel current = Minecraft.getInstance().level;
        if (world != current) {
            BEAMS.clear();
            TENDRILS.clear();
            world = current;
        }
    }

    public static void acceptBeam(DarkBeamPayload payload) {
        world();
        if (world == null || payload.direction().lengthSqr() < 1.0E-6D || payload.length() <= 0.0F) {
            return;
        }
        if (BEAMS.size() >= 64) {
            BEAMS.remove(0);
        }
        BEAMS.add(new Beam(payload.playerId(), payload.origin(), payload.direction().normalize(),
                payload.length(), payload.style(), Math.max(1, payload.ticks()), world.getGameTime()));
    }

    public static void acceptTendrils(AntlerTendrilsPayload payload) {
        world();
        if (world == null || payload.targets().length == 0) {
            return;
        }
        if (TENDRILS.size() >= 32) {
            TENDRILS.remove(0);
        }
        TENDRILS.add(new Tendrils(payload.playerId(), payload.targets().clone(),
                Math.max(1, payload.ticks()), world.getGameTime()));
    }

    public static void tick() {
        world();
        if (world == null) {
            return;
        }
        long now = world.getGameTime();
        BEAMS.removeIf(beam -> now > beam.born + beam.ticks());
        TENDRILS.removeIf(tendrils -> now > tendrils.born + tendrils.ticks());
    }

    public static void render(PoseStack stack, MultiBufferSource.BufferSource buffers, float partialTick) {
        world();
        if (world == null) {
            return;
        }
        for (Beam beam : BEAMS) {
            renderBeam(stack, buffers, beam, partialTick);
        }
        for (Tendrils tendrils : TENDRILS) {
            renderTendrils(stack, buffers, tendrils, partialTick);
        }
    }

    // ==================== 能量束 ====================

    private static void renderBeam(PoseStack stack, MultiBufferSource.BufferSource buffers,
                                   Beam beam, float partialTick) {
        double age = world.getGameTime() + partialTick - beam.born;
        if (age < 0 || age > beam.ticks) {
            return;
        }
        float fade = (float) Math.max(0.0D, Math.min(1.0D, (beam.ticks - age) / 4.0D));
        Matrix4f pose = stack.last().pose();

        Vec3 direction = beam.direction;
        Vec3 side = perpendicular(direction);
        Vec3 up = side.cross(direction).normalize();
        int segments = Math.max(6, (int) (beam.length / 0.5D));
        float muzzle = 1.0F - (float) Math.min(1.0D, age / 3.0D);

        // 共享 BufferSource 同一时刻只保留一个非 fixed builder：
        // 先写完所有黑色外壳，再取发光 builder 写核心，不能交替持有两个 consumer。
        if (beam.style == 0) {
            VertexConsumer dark = buffers.getBuffer(RenderType.entityCutoutNoCull(SOLID_WHITE));
            for (int index = 0; index < segments; index++) {
                double distance = 0.4D + index * 0.5D;
                if (distance > beam.length) {
                    break;
                }
                float noise = hash(index * 37 + beam.playerId);
                float noise2 = hash(index * 19 + 11);
                Vec3 center = beam.origin.add(direction.scale(distance))
                        .add(side.scale((noise - 0.5D) * 0.85D))
                        .add(up.scale((noise2 - 0.5D) * 0.85D));
                float half = 0.40F + 0.38F * noise;
                int shade = 6 + (int) (noise * 26);
                box(dark, pose, center, half, shade, 3, 6, 255);
            }
            if (muzzle > 0.0F) {
                box(dark, pose, beam.origin, 0.55F * muzzle + 0.2F, 12, 4, 8, 255);
            }
        }

        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(SOLID_WHITE));
        for (int index = 0; index < segments; index++) {
            double distance = 0.4D + index * 0.5D;
            if (distance > beam.length) {
                break;
            }
            float noise = hash(index * 37 + beam.playerId);
            float noise2 = hash(index * 19 + 11);
            Vec3 center = beam.origin.add(direction.scale(distance))
                    .add(side.scale((noise - 0.5D) * 0.85D))
                    .add(up.scale((noise2 - 0.5D) * 0.85D));
            float half = 0.40F + 0.38F * noise;

            if (beam.style == 0) {
                if (noise2 > 0.72F) {
                    box(glow, pose, center, half * 0.6F, 190, 10, 26, (int) (255 * fade));
                }
            } else {
                box(glow, pose, center, half * 0.55F, 255, 70, 80, (int) (170 * fade));
            }

            float coreHalf = beam.style == 0 ? 0.15F + 0.06F * noise : 0.10F;
            Vec3 core = beam.origin.add(direction.scale(distance));
            box(glow, pose, core, coreHalf, 255, 246, 246, (int) (255 * fade));
        }
        if (muzzle > 0.0F) {
            box(glow, pose, beam.origin, 0.35F * muzzle + 0.1F, 255, 225, 225, (int) (255 * fade * muzzle));
        }
    }

    // ==================== 鹿角卷须 ====================

    private static void renderTendrils(PoseStack stack, MultiBufferSource.BufferSource buffers,
                                       Tendrils tendrils, float partialTick) {
        Entity caster = world.getEntity(tendrils.playerId);
        if (caster == null) {
            return;
        }
        double age = world.getGameTime() + partialTick - tendrils.born;
        if (age < 0 || age > tendrils.ticks) {
            return;
        }
        float fade = (float) Math.max(0.0D, Math.min(1.0D, (tendrils.ticks - age) / 6.0D));
        Vec3 start = caster.getEyePosition(partialTick).add(0.0D, 0.45D, 0.0D);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(SOLID_WHITE));
        Matrix4f pose = stack.last().pose();

        for (int targetId : tendrils.targets) {
            Entity target = world.getEntity(targetId);
            if (target == null) {
                continue;
            }
            Vec3 end = target.getBoundingBox().getCenter();
            Vec3 delta = end.subtract(start);
            double length = delta.length();
            if (length < 0.1D) {
                continue;
            }
            Vec3 direction = delta.scale(1.0D / length);
            Vec3 side = perpendicular(direction);
            int points = Math.max(8, (int) (length * 2.0D));
            for (int index = 0; index <= points; index++) {
                double t = index / (double) points;
                Vec3 point = start.add(delta.scale(t));
                double wave = Math.sin(t * 10.0D + age * 0.35D) * 0.3D * Math.sin(Math.PI * t);
                point = point.add(side.scale(wave));
                float half = (float) (0.10D * (1.0D - 0.4D * t));
                int alpha = (int) (215.0D * fade * Math.sin(Math.PI * Math.min(1.0D, t * 1.1D)));
                if (alpha <= 0) {
                    continue;
                }
                box(glow, pose, point, half, 255, 208, 40, alpha);
            }
        }
    }

    // ==================== 几何工具 ====================

    private static Vec3 perpendicular(Vec3 direction) {
        Vec3 side = direction.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-4D) {
            side = direction.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        return side.normalize();
    }

    private static void box(VertexConsumer out, Matrix4f pose, Vec3 center, float half,
                            int r, int g, int b, int a) {
        float x1 = (float) center.x - half;
        float x2 = (float) center.x + half;
        float y1 = (float) center.y - half;
        float y2 = (float) center.y + half;
        float z1 = (float) center.z - half;
        float z2 = (float) center.z + half;

        quad(out, pose, r, g, b, a, 0, 0, -1, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(out, pose, r, g, b, a, 0, 0, 1, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2);
        quad(out, pose, r, g, b, a, -1, 0, 0, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        quad(out, pose, r, g, b, a, 1, 0, 0, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2);
        quad(out, pose, r, g, b, a, 0, -1, 0, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        quad(out, pose, r, g, b, a, 0, 1, 0, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1);
    }

    private static void quad(VertexConsumer out, Matrix4f pose, int r, int g, int b, int a,
                             float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        // 四角 UV 铺满整个 1×1 贴图，避免单点 UV 在 mip 下取到模糊 texel
        vertex(out, pose, x1, y1, z1, 0.0F, 1.0F, r, g, b, a, nx, ny, nz);
        vertex(out, pose, x2, y2, z2, 1.0F, 1.0F, r, g, b, a, nx, ny, nz);
        vertex(out, pose, x3, y3, z3, 1.0F, 0.0F, r, g, b, a, nx, ny, nz);
        vertex(out, pose, x4, y4, z4, 0.0F, 0.0F, r, g, b, a, nx, ny, nz);
    }

    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float z,
                               float u, float v, int r, int g, int b, int a,
                               float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }

    private static float hash(int value) {
        float result = (float) Math.sin(value * 12.9898D) * 43758.5453F;
        return result - (float) Math.floor(result);
    }
}
