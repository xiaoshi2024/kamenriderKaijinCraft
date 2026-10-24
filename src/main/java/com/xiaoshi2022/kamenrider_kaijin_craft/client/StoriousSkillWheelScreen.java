package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.KaijinSkillPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.skill.StoriousSkill;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class StoriousSkillWheelScreen extends Screen {
    private static final StoriousSkill[] SKILLS = StoriousSkill.values();
    private static final float GAP_DEG = 1.8F;
    private static final float OPEN_ANIMATION_LENGTH = 0.15F;

    private static final int BACKDROP = 0xEE100810;
    private static final int RIM_COLOR = 0xFF7A1424;
    private static final int SLICE_COLOR = 0xF04A0C16;
    private static final int SLICE_COOLDOWN = 0xF0240E14;
    private static final int SLICE_HIGHLIGHT = 0xF0A01832;

    private int centerX;
    private int centerY;
    private float innerRadius;
    private float outerRadius;
    private float stepDeg;
    private int hovered = -1;
    private float totalTime;

    public StoriousSkillWheelScreen() {
        super(Component.translatable("gui.kamenrider_kaijin_craft.skill_wheel"));
    }

    @Override
    protected void init() {
        centerX = width / 2;
        centerY = height / 2 - 6;
        outerRadius = Math.min(132.0F, Math.min(width, height) * 0.31F);
        innerRadius = outerRadius * 0.36F;
        stepDeg = 360.0F / SKILLS.length;
    }

    /**
     * 【关键修复】重写背景，禁止调用原版逻辑。
     * 1.21.1 的 Screen#renderBackground 会执行 GameRenderer#processBlurEffect，
     * 把帧缓冲（世界 + HUD + 本界面已绘制内容）整体做高斯模糊。
     * 尸兄轮盘同样重写该方法为纯色填充，因此画面清晰。
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKDROP);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        totalTime += partialTick / 20.0F;
        float progress = Mth.clamp(totalTime / OPEN_ANIMATION_LENGTH, 0.0F, 1.0F);
        progress = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        float drawInner = innerRadius * progress;
        float drawOuter = outerRadius * progress;

        hovered = sectorAt(mouseX, mouseY);

        // ============ 多边形渲染（完全移植尸兄模组） ============
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        drawRing(graphics, drawInner - 2.0F, drawOuter + 3.0F, RIM_COLOR);

        for (int index = 0; index < SKILLS.length; index++) {
            boolean cooling = ClientSkillCooldowns.remainingTicks(SKILLS[index]) > 0;
            drawSector(graphics, midAngle(index), drawInner, drawOuter, cooling ? SLICE_COOLDOWN : SLICE_COLOR);
        }

        if (hovered >= 0 && hovered < SKILLS.length) {
            drawSector(graphics, midAngle(hovered), drawInner - 1.0F, drawOuter + 1.0F, SLICE_HIGHLIGHT);
        }

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        // =======================================================

        // 图标和文字
        for (int index = 0; index < SKILLS.length; index++) {
            StoriousSkill skill = SKILLS[index];
            double radians = Math.toRadians(midAngle(index));
            float iconRadius = (drawInner + drawOuter) * 0.5F + 4.0F;
            int iconX = (int) Math.round(centerX + Math.cos(radians) * iconRadius) - 8;
            int iconY = (int) Math.round(centerY + Math.sin(radians) * iconRadius) - 8;

            graphics.fill(iconX - 3, iconY - 3, iconX + 19, iconY + 19, 0xFF140A10);
            graphics.fill(iconX - 2, iconY - 2, iconX + 18, iconY + 18,
                    index == hovered ? 0xFF54101E : 0xFF2A0A12);

            // 【修复】不再做 1.01 缩放，纹理按整数像素绘制，保持像素锐利
            graphics.blit(iconTexture(skill), iconX, iconY, 0.0F, 0.0F, 16, 16, 16, 16);

            int remaining = ClientSkillCooldowns.remainingTicks(skill);
            if (remaining > 0) {
                graphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0xCC000000);
                // 【修复 3】移除末尾的 true（1.21.1 drawCenteredString 恒定带阴影）
                graphics.drawCenteredString(font, Integer.toString((remaining + 19) / 20),
                        iconX + 8, iconY + 4, 0xFFFF5555);
            }

            int textColor = remaining > 0 ? 0xFF9A9A9A : 0xFFFFFFFF;
            // 【修复 3】移除末尾的 true
            graphics.drawCenteredString(font, skillName(skill), iconX + 8, iconY + 22, textColor);

            int bound = boundSlot(skill);
            if (bound >= 0) {
                graphics.drawCenteredString(font, "[" + (bound + 1) + "]",
                        iconX + 8, iconY - 14, 0xFFFFD36A);
            }
        }

        // 中心面板
        int panelWidth = 156;
        int panelHeight = 42;
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - panelHeight / 2;
        graphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFF7A1424);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF080408);

        if (hovered >= 0 && hovered < SKILLS.length) {
            StoriousSkill skill = SKILLS[hovered];

            // 【修复】去掉 1.05 缩放，直接按整数坐标绘制文字，避免重采样发虚
            graphics.drawCenteredString(font, skillName(skill).copy().withStyle(ChatFormatting.BOLD),
                    centerX, centerY - 13, 0xFFFFE8EE);

            int remaining = ClientSkillCooldowns.remainingTicks(skill);
            if (remaining > 0) {
                graphics.drawCenteredString(font,
                        Component.translatable("gui.kamenrider_kaijin_craft.skill_wheel.cooldown", (remaining + 19) / 20),
                        centerX, centerY + 1, 0xFFFF5555);
            } else {
                graphics.drawCenteredString(font,
                        Component.translatable("gui.kamenrider_kaijin_craft.skill_wheel.ready"),
                        centerX, centerY + 1, 0xFF55FF88);
            }
            int bound = boundSlot(skill);
            if (bound >= 0) {
                graphics.drawCenteredString(font,
                        Component.translatable("gui.kamenrider_kaijin_craft.skill_wheel.bound_slot", bound + 1),
                        centerX, centerY + 12, 0xFFFFD36A);
            }
        } else {
            graphics.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD),
                    centerX, centerY - 8, 0xFFFFE8EE);
            graphics.drawCenteredString(font,
                    Component.translatable("gui.kamenrider_kaijin_craft.skill_wheel.hint"),
                    centerX, centerY + 6, 0xFFCCCCCC);
        }

        graphics.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD),
                centerX, (int) (centerY - outerRadius) - 22, 0xFFFFE8EE);
        graphics.drawCenteredString(font,
                Component.translatable(hovered >= 0
                        ? "hud.kamenrider_kaijin_craft.skill_slot.bind_hint"
                        : "gui.kamenrider_kaijin_craft.skill_wheel.hint"),
                centerX, (int) (centerY + outerRadius) + 16, 0xFFFFFFFF);
        // 注意：这里不能调用 super.render()，否则会再次触发 Screen#renderBackground 的原版模糊
    }

    private void drawRing(GuiGraphics graphics, float rIn, float rOut, int color) {
        int segments = 120;
        float angleStep = (float) Math.toRadians(360.0f / segments);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        MultiBufferSource.BufferSource bufferSource = graphics.bufferSource();
        BufferBuilder buffer = (BufferBuilder) bufferSource.getBuffer(RenderType.gui());
        Matrix4f matrix = graphics.pose().last().pose();

        for (int i = 0; i < segments; i++) {
            float angle1 = i * angleStep;
            float angle2 = (i + 1) * angleStep;

            float x1 = centerX + rIn * Mth.cos(angle1);
            float y1 = centerY + rIn * Mth.sin(angle1);
            float x2 = centerX + rOut * Mth.cos(angle1);
            float y2 = centerY + rOut * Mth.sin(angle1);
            float x3 = centerX + rOut * Mth.cos(angle2);
            float y3 = centerY + rOut * Mth.sin(angle2);
            float x4 = centerX + rIn * Mth.cos(angle2);
            float y4 = centerY + rIn * Mth.sin(angle2);

            buffer.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x3, y3, 0).setColor(r, g, b, a);

            buffer.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x3, y3, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x4, y4, 0).setColor(r, g, b, a);
        }

        bufferSource.endBatch();
    }

    private void drawSector(GuiGraphics graphics, float midAngleDeg, float rIn, float rOut, int color) {
        float halfSpan = (stepDeg - GAP_DEG * 2.0F) * 0.5F;
        float startAngle = midAngleDeg - halfSpan;
        float endAngle = midAngleDeg + halfSpan;

        int segments = Math.max(16, (int) (halfSpan * 2 / 5.0f));
        float angleStep = (float) Math.toRadians((endAngle - startAngle) / segments);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        MultiBufferSource.BufferSource bufferSource = graphics.bufferSource();
        BufferBuilder buffer = (BufferBuilder) bufferSource.getBuffer(RenderType.gui());
        Matrix4f matrix = graphics.pose().last().pose();

        float startRad = (float) Math.toRadians(startAngle);

        for (int i = 0; i < segments; i++) {
            float angle1 = startRad + i * angleStep;
            float angle2 = startRad + (i + 1) * angleStep;

            float x1 = centerX + rIn * Mth.cos(angle1);
            float y1 = centerY + rIn * Mth.sin(angle1);
            float x2 = centerX + rOut * Mth.cos(angle1);
            float y2 = centerY + rOut * Mth.sin(angle1);
            float x3 = centerX + rOut * Mth.cos(angle2);
            float y3 = centerY + rOut * Mth.sin(angle2);
            float x4 = centerX + rIn * Mth.cos(angle2);
            float y4 = centerY + rIn * Mth.sin(angle2);

            buffer.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x2, y2, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x3, y3, 0).setColor(r, g, b, a);

            buffer.addVertex(matrix, x1, y1, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x3, y3, 0).setColor(r, g, b, a);
            buffer.addVertex(matrix, x4, y4, 0).setColor(r, g, b, a);
        }

        bufferSource.endBatch();
    }

    private static Component skillName(StoriousSkill skill) {
        return Component.translatable("skill.kamenrider_kaijin_craft." + skill.name().toLowerCase());
    }

    private static ResourceLocation iconTexture(StoriousSkill skill) {
        return KaijinMOD.id("textures/hud/" + skill.name().toLowerCase() + ".png");
    }

    private static int boundSlot(StoriousSkill skill) {
        for (int slot = 0; slot < 3; slot++) {
            if (ClientSkillBindings.getSkill(slot) == skill) {
                return slot;
            }
        }
        return -1;
    }

    private float midAngle(int index) {
        return -90.0F + stepDeg * index;
    }

    private int sectorAt(int mouseX, int mouseY) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.hypot(dx, dy);
        if (distance < innerRadius || distance > outerRadius) {
            return -1;
        }
        double angleDeg = Math.toDegrees(Math.atan2(dy, dx));
        double relative = angleDeg + 90.0D;
        relative = (relative % 360.0D + 360.0D) % 360.0D;
        relative += stepDeg / 2.0D;
        relative = relative % 360.0D;
        return (int) (relative / stepDeg);
    }

    private void cast(StoriousSkill skill) {
        PacketDistributor.sendToServer(new KaijinSkillPayload(skill.ordinal()));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int index = sectorAt((int) mouseX, (int) mouseY);
            if (index >= 0 && index < SKILLS.length) {
                cast(SKILLS[index]);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_3
                && hovered >= 0 && hovered < SKILLS.length) {
            int slot = keyCode - GLFW.GLFW_KEY_1;
            StoriousSkill skill = SKILLS[hovered];
            ClientSkillBindings.bind(slot, skill);
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "hud.kamenrider_kaijin_craft.skill_slot.bound", slot + 1, skillName(skill)), true);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (KaijinKeys.SKILL_WHEEL.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}