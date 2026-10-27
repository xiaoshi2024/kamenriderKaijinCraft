package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render;

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
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        // 前 2 tick 光束从枪口快速伸出，之后保持全长
        float extend = (float) Math.min(1.0D, age / 2.0D);
        float pulse = 0.9F + 0.1F * (float) Math.sin(age * 1.7D + beam.playerId);

        Vec3 start = beam.origin.add(direction.scale(0.25D));
        Vec3 end = beam.origin.add(direction.scale(beam.length * extend));
        // 0 黑暗箭、1 失明光线、2 金色剑气、3 冰弹、4 岩弹
        boolean dark = beam.style == 0;

        // 碎块与爆闪的配色跟着光束样式走
        int[] sparkHot = {255, 235, 250};
        int[] sparkCool = {255, 235, 250};
        if (dark) {
            sparkHot = new int[]{255, 245, 245};
            sparkCool = new int[]{255, 60, 70};
        } else if (beam.style == 2) {
            sparkHot = new int[]{255, 255, 240};
            sparkCool = new int[]{255, 198, 58};
        } else if (beam.style == 3) {
            sparkHot = new int[]{245, 255, 255};
            sparkCool = new int[]{96, 200, 255};
        } else if (beam.style == 4) {
            sparkHot = new int[]{240, 228, 210};
            sparkCool = new int[]{150, 128, 106};
        }

        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(SOLID_WHITE));

        // 由外到内叠四层面向相机的光带：深色轮廓 -> 辉光 -> 亮色 -> 白色核心
        if (dark) {
            ribbon(glow, pose, start, end, camera, 0.62F * pulse, 0.02F, 26, 2, 8, (int) (230 * fade));
            ribbon(glow, pose, start, end, camera, 0.46F * pulse, 0.04F, 255, 32, 48, (int) (170 * fade));
            ribbon(glow, pose, start, end, camera, 0.28F * pulse, 0.06F, 255, 88, 96, (int) (215 * fade));
            ribbon(glow, pose, start, end, camera, 0.12F, 0.08F, 255, 248, 248, (int) (255 * fade));
        } else if (beam.style == 2) {
            // 金色剑气：扁而宽的刃光
            ribbon(glow, pose, start, end, camera, 0.86F * pulse, 0.02F, 84, 56, 8, (int) (210 * fade));
            ribbon(glow, pose, start, end, camera, 0.62F * pulse, 0.04F, 255, 194, 42, (int) (170 * fade));
            ribbon(glow, pose, start, end, camera, 0.34F * pulse, 0.06F, 255, 232, 150, (int) (215 * fade));
            ribbon(glow, pose, start, end, camera, 0.12F, 0.08F, 255, 255, 238, (int) (255 * fade));
        } else if (beam.style == 3) {
            // 冰弹：窄而冷的晶蓝轨迹
            ribbon(glow, pose, start, end, camera, 0.42F * pulse, 0.02F, 16, 58, 104, (int) (190 * fade));
            ribbon(glow, pose, start, end, camera, 0.28F * pulse, 0.04F, 92, 196, 255, (int) (185 * fade));
            ribbon(glow, pose, start, end, camera, 0.15F, 0.06F, 176, 238, 255, (int) (220 * fade));
            ribbon(glow, pose, start, end, camera, 0.08F, 0.08F, 244, 255, 255, (int) (255 * fade));
        } else if (beam.style == 4) {
            // 岩弹：厚重、带尘土感的灰褐轨迹
            ribbon(glow, pose, start, end, camera, 0.64F * pulse, 0.02F, 38, 32, 28, (int) (220 * fade));
            ribbon(glow, pose, start, end, camera, 0.46F * pulse, 0.04F, 126, 104, 86, (int) (190 * fade));
            ribbon(glow, pose, start, end, camera, 0.26F, 0.06F, 176, 154, 132, (int) (215 * fade));
            ribbon(glow, pose, start, end, camera, 0.12F, 0.08F, 232, 218, 198, (int) (255 * fade));
        } else {
            ribbon(glow, pose, start, end, camera, 0.40F * pulse, 0.02F, 255, 210, 225, (int) (170 * fade));
            ribbon(glow, pose, start, end, camera, 0.22F, 0.04F, 255, 240, 255, (int) (220 * fade));
            ribbon(glow, pose, start, end, camera, 0.10F, 0.06F, 255, 255, 255, (int) (255 * fade));
        }

        // 沿光束漂浮的像素碎块（截图里那种块状高光）
        int specks = Math.max(8, (int) (beam.length * 2.0D));
        for (int index = 0; index < specks; index++) {
            float t = (index + 0.5F) / specks * extend;
            float noise = hash(index * 37 + beam.playerId);
            float noise2 = hash(index * 19 + 11);
            Vec3 center = beam.origin.add(direction.scale(beam.length * t))
                    .add(side.scale((noise - 0.5D) * 1.5D))
                    .add(up.scale((noise2 - 0.5D) * 1.5D));
            float size = 0.05F + 0.11F * noise2;
            if (beam.style >= 2) {
                int[] color = noise > 0.55F ? sparkHot : sparkCool;
                box(glow, pose, center, size, color[0], color[1], color[2], (int) (225 * fade));
            } else if (dark) {
                if (noise > 0.55F) {
                    box(glow, pose, center, size, 255, 245, 245, (int) (255 * fade));
                } else {
                    box(glow, pose, center, size, 255, 60, 70, (int) (200 * fade));
                }
            } else {
                box(glow, pose, center, size, 255, 235, 250, (int) (220 * fade));
            }
        }

        // 枪口闪光
        float muzzle = 1.0F - (float) Math.min(1.0D, age / 6.0D);
        if (muzzle > 0.0F) {
            box(glow, pose, beam.origin.add(direction.scale(0.3D)), 0.45F * muzzle + 0.15F,
                    sparkHot[0], sparkHot[1], sparkHot[2], (int) (255 * fade * muzzle));
        }

        // 命中点的爆闪
        if (extend >= 1.0F) {
            float burst = 1.0F - (float) Math.min(1.0D, (age - 2.0D) / 6.0D);
            if (burst > 0.0F) {
                box(glow, pose, end, 0.3F + 0.4F * burst,
                        sparkCool[0], sparkCool[1], sparkCool[2], (int) (220 * burst * fade));
            }
        }
    }

    /**
     * 画一条面向相机的光带：宽度方向同时垂直于光束与视线，
     * {@code forward} 把内层稍微推向相机，避免多层同深度互相遮挡。
     */
    private static void ribbon(VertexConsumer out, Matrix4f pose, Vec3 from, Vec3 to, Vec3 camera,
                               float half, float forward, int r, int g, int b, int a) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0E-4D || a <= 0) {
            return;
        }
        Vec3 dir = delta.scale(1.0D / length);
        Vec3 view = camera.subtract(from.add(to).scale(0.5D));
        if (view.lengthSqr() < 1.0E-6D) {
            return;
        }
        view = view.normalize();
        Vec3 right = dir.cross(view);
        if (right.lengthSqr() < 1.0E-6D) {
            return;
        }
        right = right.normalize().scale(half);
        Vec3 push = view.scale(forward);

        Vec3 a1 = from.add(push).subtract(right);
        Vec3 a2 = from.add(push).add(right);
        Vec3 b1 = to.add(push).subtract(right);
        Vec3 b2 = to.add(push).add(right);
        float nx = (float) -view.x;
        float ny = (float) -view.y;
        float nz = (float) -view.z;

        vertex(out, pose, (float) a1.x, (float) a1.y, (float) a1.z, 0.0F, 1.0F, r, g, b, a, nx, ny, nz);
        vertex(out, pose, (float) b1.x, (float) b1.y, (float) b1.z, 1.0F, 1.0F, r, g, b, a, nx, ny, nz);
        vertex(out, pose, (float) b2.x, (float) b2.y, (float) b2.z, 1.0F, 0.0F, r, g, b, a, nx, ny, nz);
        vertex(out, pose, (float) a2.x, (float) a2.y, (float) a2.z, 0.0F, 0.0F, r, g, b, a, nx, ny, nz);
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
