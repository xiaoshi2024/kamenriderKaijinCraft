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
            if (aura.data.channel().startsWith("slime")) {
                // 卡律布狄斯的黏液吞噬：锚点团块 + 无锚点溅射
                if (aura.data.anchor() >= 0) {
                    Entity entity = world.getEntity(aura.data.anchor());
                    if (entity != null) {
                        slimeEngulf(stack, glow, aura, entity.getPosition(partialTick), entity.getBbWidth(),
                                entity.getBbHeight(), partialTick, right, up, cameraPos);
                    }
                } else {
                    slimeSplash(stack, glow, aura, center, partialTick, right, up);
                }
                continue;
            }
            if (aura.data.channel().startsWith("gold")) {
                // 拉结尔的变身气焰：外层黑烟包裹、内层金光，最后爆开
                if (aura.data.anchor() >= 0) {
                    Entity entity = world.getEntity(aura.data.anchor());
                    if (entity != null) {
                        goldEngulf(stack, glow, aura, entity.getPosition(partialTick), entity.getBbWidth(),
                                entity.getBbHeight(), partialTick, right, up);
                    }
                }
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

    // ------------------------------------------------------------ 卡律布狄斯：黏液吞噬

    /** 吞噬身体的大团块数 / 朝向镜头的高光数 / 顶端尖峰数 / 溅射液滴数 */
    private static final int SLIME_BLOBS = 26;
    private static final int SLIME_HIGHLIGHTS = 12;
    private static final int SLIME_SPIKES = 7;
    private static final int SLIME_DROPS = 34;

    /**
     * 卡律布狄斯变异：紫色黏液先在地面铺开，随后鼓成比玩家还大的一团把人整个吞掉，
     * 表面用暗紫 / 正紫 / 亮紫三层叠出体积和油亮高光，顶端拉出几条尖峰，
     * 最后在 1 秒多的时间里缩回脚下、让怪人形态露出来。
     */
    private static void slimeEngulf(PoseStack stack, VertexConsumer out, Aura aura, Vec3 base,
                                    float bodyWidth, double bodyHeight, float partialTick,
                                    Vector3f right, Vector3f up, Vec3 cameraPos) {
        long now = world.getGameTime();
        float t = Math.min(1.0F, Math.max(0.0F,
                (now + partialTick - aura.born) / (float) Math.max(1, aura.data.ticks())));

        // 前 1/3 时间鼓起并吞没，中间稳定，最后一点时间缩回去
        float grow;
        float opacity;
        if (t < 0.32F) {
            float k = t / 0.32F;
            grow = 0.25F + 0.75F * easeOut(k);
            opacity = 0.35F + 0.65F * k;
        } else if (t < 0.72F) {
            grow = 1.0F;
            opacity = 1.0F;
        } else {
            float k = (t - 0.72F) / 0.28F;
            grow = 1.0F - 0.85F * k;
            opacity = 1.0F - k;
        }
        if (opacity <= 0.02F) {
            return;
        }

        int color = aura.data.color();
        int darkR = ((color >> 16) & 255) * 42 / 100;
        int darkG = ((color >> 8) & 255) * 42 / 100;
        int darkB = (color & 255) * 52 / 100;
        int midR = (color >> 16) & 255;
        int midG = (color >> 8) & 255;
        int midB = color & 255;
        int hiR = Math.min(255, midR + 120);
        int hiG = Math.min(255, midG + 120);
        int hiB = Math.min(255, midB + 35);

        double time = now + partialTick;
        float radius = Math.max(0.85F, bodyWidth * 1.05F + 0.3F) * grow;
        float height = (float) Math.max(1.7D, bodyHeight * 1.18D) * grow;
        Matrix4f pose = stack.last().pose();

        // 1) 地面的黏液摊开
        for (int index = 0; index < 8; index++) {
            float angle = hash(index * 19 + 5) * (float) (Math.PI * 2);
            float ring = 0.6F + 0.9F * hash(index * 23 + 9);
            float size = (0.35F + 0.35F * hash(index * 29 + 3)) * grow;
            billboard(out, pose, right, up,
                    (float) (base.x + Math.cos(angle) * radius * ring),
                    (float) (base.y + 0.10D),
                    (float) (base.z + Math.sin(angle) * radius * ring),
                    size, darkR, darkG, darkB, (int) (opacity * 150));
        }

        // 2) 吞掉全身的团块：每个位置两层，外暗内亮
        for (int index = 0; index < SLIME_BLOBS; index++) {
            float seedA = hash(index * 17 + 1);
            float seedB = hash(index * 31 + 7);
            float seedC = hash(index * 53 + 11);
            float seedD = hash(index * 71 + 5);
            float angle = seedA * (float) (Math.PI * 2)
                    + (float) (time * 0.014) * (index % 3 == 0 ? 1.0F : -1.0F);
            float ring = 0.25F + 0.8F * seedB;
            float wobble = (float) Math.sin(time * 0.2 + index * 1.7);
            float px = (float) (base.x + Math.cos(angle) * radius * ring * (1.0F + 0.1F * wobble));
            float pz = (float) (base.z + Math.sin(angle) * radius * ring * (1.0F + 0.1F * wobble));
            float py = (float) (base.y + 0.18D + height * (0.04F + 0.92F * seedC));
            float size = (0.30F + 0.28F * seedD) * grow * (1.0F + 0.12F * wobble);
            billboard(out, pose, right, up, px, py, pz, size, darkR, darkG, darkB,
                    (int) (opacity * 205));
            billboard(out, pose, right, up, px, py, pz, size * 0.64F, midR, midG, midB,
                    (int) (opacity * 220));
        }

        // 3) 朝向镜头一侧的油亮高光
        float camAngle = (float) Math.atan2(cameraPos.z - base.z, cameraPos.x - base.x);
        for (int index = 0; index < SLIME_HIGHLIGHTS; index++) {
            float seedA = hash(index * 13 + 2);
            float seedB = hash(index * 41 + 6);
            float angle = camAngle + (seedA - 0.5F) * 2.4F;
            float ring = 0.55F + 0.45F * seedB;
            float px = (float) (base.x + Math.cos(angle) * radius * ring);
            float pz = (float) (base.z + Math.sin(angle) * radius * ring);
            float py = (float) (base.y + 0.3D + height * (0.22F + 0.72F * hash(index * 61 + 8)));
            float size = (0.09F + 0.10F * seedB) * grow;
            billboard(out, pose, right, up, px, py, pz, size, hiR, hiG, hiB,
                    (int) (opacity * 235));
        }

        // 4) 顶端拉出的黏液尖峰
        for (int index = 0; index < SLIME_SPIKES; index++) {
            float angle = hash(index * 37 + 4) * (float) (Math.PI * 2) + (float) (time * 0.012);
            float ring = 0.3F + 0.65F * hash(index * 43 + 10);
            float startY = (float) (base.y + height * (0.35D + 0.25D * hash(index * 67 + 1)));
            float endY = (float) (base.y + height * (1.0D + 0.6D * hash(index * 73 + 9)));
            float px = (float) (base.x + Math.cos(angle) * radius * ring);
            float pz = (float) (base.z + Math.sin(angle) * radius * ring);
            for (int step = 0; step < 4; step++) {
                float k = step / 3.0F;
                billboard(out, pose, right, up, px, startY + (endY - startY) * k, pz,
                        (0.26F - 0.16F * k) * grow, midR, midG, midB, (int) (opacity * (200 - 40 * k)));
            }
        }
    }

    /** 黏液溅射：一圈向外飞散、受重力下坠的紫色液滴与深色碎屑。 */
    private static void slimeSplash(PoseStack stack, VertexConsumer out, Aura aura, Vec3 center,
                                    float partialTick, Vector3f right, Vector3f up) {
        long now = world.getGameTime();
        float elapsed = now + partialTick - aura.born;
        float life = Math.min(1.0F, elapsed / (float) Math.max(1, aura.data.ticks()));
        float opacity = life < 0.8F ? 1.0F : (1.0F - life) / 0.2F;
        if (opacity <= 0.02F) {
            return;
        }

        int color = aura.data.color();
        int midR = (color >> 16) & 255;
        int midG = (color >> 8) & 255;
        int midB = color & 255;
        int hiR = Math.min(255, midR + 110);
        int hiG = Math.min(255, midG + 90);
        int hiB = Math.min(255, midB + 30);
        int darkR = midR * 35 / 100;
        int darkG = midG * 35 / 100;
        int darkB = midB * 45 / 100;

        Matrix4f pose = stack.last().pose();
        for (int index = 0; index < SLIME_DROPS; index++) {
            float seedA = hash(index * 23 + 3);
            float seedB = hash(index * 47 + 1);
            float seedC = hash(index * 67 + 13);
            float angle = seedA * (float) (Math.PI * 2);
            float dist = elapsed * (0.025F + 0.055F * seedB) * (0.55F + 0.9F * seedB);
            float rise = elapsed * (0.035F + 0.06F * seedC);
            float fall = elapsed * elapsed * 0.0011F;
            float px = (float) (center.x + Math.cos(angle) * dist);
            float pz = (float) (center.z + Math.sin(angle) * dist);
            float py = (float) (center.y + 0.85D + rise - fall);
            float size = (0.035F + 0.055F * seedA) * (1.0F - 0.35F * life);
            int alpha = (int) (opacity * 165);
            if (index % 4 == 0) {
                // 混在里面的深色碎屑
                billboard(out, pose, right, up, px, py, pz, size * 0.75F,
                        darkR, darkG, darkB, Math.min(255, alpha + 30));
            } else {
                billboard(out, pose, right, up, px, py, pz, size, hiR, hiG, hiB, alpha);
            }
        }
    }

    // ------------------------------------------------------------ 拉结尔：黑烟金焰变身

    /** 外层黑烟团块数 / 内层金光团块数 / 火焰条纹数 */
    private static final int GOLD_SHELLS = 32;
    private static final int GOLD_CORES = 26;
    private static final int GOLD_STREAKS = 14;

    /**
     * 拉结尔变异：外层黑烟先裹住全身，内层金色光焰亮起，
     * 停留一瞬后整个气团向外爆开。
     */
    private static void goldEngulf(PoseStack stack, VertexConsumer out, Aura aura, Vec3 base,
                                   float bodyWidth, double bodyHeight, float partialTick,
                                   Vector3f right, Vector3f up) {
        long now = world.getGameTime();
        float t = Math.min(1.0F, Math.max(0.0F,
                (now + partialTick - aura.born) / (float) Math.max(1, aura.data.ticks())));

        // 前 20%：黑烟鼓起包住身体；中段稳定；后 42%：向外爆开并消散
        float grow;
        float opacity;
        float burst;
        if (t < 0.20F) {
            float k = t / 0.20F;
            grow = 0.28F + 0.72F * easeOut(k);
            opacity = 0.35F + 0.65F * k;
            burst = 0.0F;
        } else if (t < 0.58F) {
            grow = 1.0F;
            opacity = 1.0F;
            burst = 0.0F;
        } else {
            float k = (t - 0.58F) / 0.42F;
            grow = 1.0F + 2.1F * easeOut(k);
            opacity = 1.0F - k;
            burst = k;
        }
        if (opacity <= 0.02F) {
            return;
        }

        int color = aura.data.color();
        int goldR = (color >> 16) & 255;
        int goldG = (color >> 8) & 255;
        int goldB = color & 255;

        double time = now + partialTick;
        float radius = Math.max(0.55F, bodyWidth * 0.62F) * grow;
        float height = (float) Math.max(1.8D, bodyHeight * 1.12D) * (1.0F + 0.12F * burst);
        Matrix4f pose = stack.last().pose();

        // 1) 外层黑烟：比金光大一圈，先把整个人裹住
        for (int index = 0; index < GOLD_SHELLS; index++) {
            float seedA = hash(index * 17 + 3);
            float seedB = hash(index * 31 + 5);
            float seedC = hash(index * 53 + 7);
            float seedD = hash(index * 71 + 11);
            float angle = seedA * (float) (Math.PI * 2)
                    + (float) (time * 0.045) * (index % 2 == 0 ? 1.0F : -1.0F);
            float ring = 0.72F + 0.5F * seedB;
            float wobble = (float) Math.sin(time * 0.22 + index * 1.9);
            float px = (float) (base.x + Math.cos(angle) * radius * ring * (1.0F + 0.08F * wobble));
            float pz = (float) (base.z + Math.sin(angle) * radius * ring * (1.0F + 0.08F * wobble));
            float py = (float) (base.y + 0.12D + height * (0.02F + 0.96F * seedC));
            float size = (0.30F + 0.26F * seedD) * grow * (1.0F + 0.12F * wobble);
            billboard(out, pose, right, up, px, py, pz, size, 10, 8, 6,
                    (int) (opacity * 200));
            // 贴内一圈暗棕，避免黑边在夜里完全看不见
            billboard(out, pose, right, up, px, py, pz, size * 0.72F, 46, 30, 14,
                    (int) (opacity * 120));
        }

        // 2) 内层金光：贴着身体核心，亮金 + 白心形成发光感
        for (int index = 0; index < GOLD_CORES; index++) {
            float seedA = hash(index * 23 + 1);
            float seedB = hash(index * 41 + 9);
            float seedC = hash(index * 61 + 4);
            float seedD = hash(index * 79 + 13);
            float angle = seedA * (float) (Math.PI * 2) + (float) (time * 0.05);
            float ring = 0.12F + 0.55F * seedB;
            float wobble = (float) Math.sin(time * 0.26 + index * 2.3);
            float px = (float) (base.x + Math.cos(angle) * radius * ring * (1.0F + 0.1F * wobble));
            float pz = (float) (base.z + Math.sin(angle) * radius * ring * (1.0F + 0.1F * wobble));
            float py = (float) (base.y + 0.16D + height * (0.06F + 0.9F * seedC));
            float size = (0.20F + 0.20F * seedD) * grow * (1.0F + 0.15F * wobble);
            billboard(out, pose, right, up, px, py, pz, size, goldR, goldG, goldB,
                    (int) (opacity * 215));
            billboard(out, pose, right, up, px, py, pz, size * 0.56F, 255, 246, 202,
                    (int) (opacity * 240));
        }

        // 3) 由下往上窜的火焰条纹：黑边金心
        for (int index = 0; index < GOLD_STREAKS; index++) {
            float seedA = hash(index * 37 + 6);
            float seedB = hash(index * 43 + 2);
            float angle = seedA * (float) (Math.PI * 2) + (float) (time * 0.03);
            float ring = 0.82F + 0.32F * seedB;
            float sway = (float) Math.sin(time * 0.24 + index * 1.3) * 0.12F;
            float bottom = (float) (base.y + 0.08D);
            float top = (float) (base.y + height * (0.72D + 0.42D * hash(index * 67 + 10)));
            for (int step = 0; step < 3; step++) {
                float k0 = step / 3.0F;
                float k1 = (step + 1) / 3.0F;
                float angle0 = angle + sway * k0;
                float angle1 = angle + sway * k1;
                float x0 = (float) (base.x + Math.cos(angle0) * radius * ring);
                float z0 = (float) (base.z + Math.sin(angle0) * radius * ring);
                float x1 = (float) (base.x + Math.cos(angle1) * radius * ring);
                float z1 = (float) (base.z + Math.sin(angle1) * radius * ring);
                float y0 = bottom + (top - bottom) * k0;
                float y1 = bottom + (top - bottom) * k1;
                float width = (0.11F - 0.05F * k0) * grow;
                ribbon(out, pose, right, x0, y0, z0, x1, y1, z1, width, 10, 8, 6,
                        (int) (opacity * 175));
                ribbon(out, pose, right, x0, y0, z0, x1, y1, z1, width * 0.45F, goldR, goldG, goldB,
                        (int) (opacity * 225));
            }
        }

        // 4) 爆开瞬间：金色火舌向外炸散，中心闪一层白光
        if (burst > 0.04F) {
            for (int index = 0; index < GOLD_STREAKS * 2; index++) {
                float seedA = hash(index * 29 + 3);
                float seedB = hash(index * 47 + 7);
                float angle = seedA * (float) (Math.PI * 2) + (float) (time * 0.06);
                float distance = radius * (0.9F + 1.5F * burst) * (0.7F + 0.6F * seedB);
                float px = (float) (base.x + Math.cos(angle) * distance);
                float pz = (float) (base.z + Math.sin(angle) * distance);
                float py = (float) (base.y + 0.2D + height * (0.15F + 0.8F * hash(index * 71 + 5)));
                float size = (0.14F + 0.12F * seedB) * (1.0F - 0.4F * burst);
                billboard(out, pose, right, up, px, py, pz, size, goldR, goldG, goldB,
                        (int) (opacity * 200));
                billboard(out, pose, right, up, px, py, pz, size * 0.5F, 255, 248, 210,
                        (int) (opacity * 220));
            }

            float flash = (float) Math.sin(Math.PI * Math.min(1.0F, burst * 1.15F));
            if (flash > 0.02F) {
                for (int index = 0; index < 5; index++) {
                    float px = (float) (base.x + (hash(index * 13 + 2) - 0.5F) * radius);
                    float pz = (float) (base.z + (hash(index * 19 + 8) - 0.5F) * radius);
                    float py = (float) (base.y + height * (0.3D + 0.4D * hash(index * 23 + 5)));
                    float size = (0.55F + 0.45F * hash(index * 31 + 6)) * grow;
                    billboard(out, pose, right, up, px, py, pz, size, 255, 251, 230,
                            (int) (flash * 190));
                }
            }
        }
    }

    /** 先快后慢的缓出，用于黏液鼓起。 */
    private static float easeOut(float k) {
        float inv = 1.0F - k;
        return 1.0F - inv * inv * inv;
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
