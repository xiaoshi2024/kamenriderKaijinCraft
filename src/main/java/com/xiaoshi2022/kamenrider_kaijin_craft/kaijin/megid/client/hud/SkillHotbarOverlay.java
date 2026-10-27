package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.hud;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.mojang.blaze3d.vertex.PoseStack;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.ClientSkillBindings;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.ClientSkillCooldowns;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.skill.StoriousSkill;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 三个快捷技能槽的 HUD，布局与尸兄的 SkillHotbarOverlay 相同：
 * 左侧竖排三个 104×28 的槽位，显示按键编号、技能名与冷却。
 */
public final class SkillHotbarOverlay {
    private static final ResourceLocation SLOT_TEXTURE = KaijinMOD.id("textures/gui/skill_slot.png");
    private static final ResourceLocation SLOT_STATUS = KaijinMOD.id("textures/gui/skill_slot_status.png");
    private static final ResourceLocation SLOT_COOLDOWN = KaijinMOD.id("textures/gui/skill_slot_cooldown.png");

    private static final int WIDTH = 104;
    private static final int HEIGHT = 28;
    private static final int SLOTS = 3;
    private static final int SPACING = 4;

    private SkillHotbarOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        if (!BookKaijinRace.get(player).bookKaijin()) {
            return;
        }

        int baseX = 8;
        int baseY = Math.max(8, (graphics.guiHeight() - (HEIGHT * SLOTS + SPACING * (SLOTS - 1)) - 8) / 2);

        for (int slot = 0; slot < SLOTS; slot++) {
            int x = baseX;
            int y = baseY + slot * (HEIGHT + SPACING);
            StoriousSkill skill = ClientSkillBindings.getSkill(slot);
            int remaining = skill == null ? 0 : ClientSkillCooldowns.remainingTicks(skill);

            graphics.blit(SLOT_TEXTURE, x, y, 0.0F, 0.0F, WIDTH, HEIGHT, WIDTH, HEIGHT);
            int statusPart = skill == null ? 0 : remaining > 0 ? 1 : 2;
            graphics.blit(SLOT_STATUS, x, y, 0.0F, statusPart * HEIGHT, 3, HEIGHT, 3, HEIGHT * 3);

            if (skill != null && remaining > 0) {
                int total = Math.max(1, skill.cooldownTicks);
                int shadeWidth = Math.min(WIDTH - 3, 3 + remaining * (WIDTH - 3) / total) - 3;
                if (shadeWidth > 0) {
                    graphics.blit(SLOT_COOLDOWN, x + 3, y, 0.0F, 0.0F, shadeWidth, HEIGHT, WIDTH - 3, HEIGHT);
                }
            }

            // 文字区再压一层暗底，避免在亮地形 / 各种材质上糊成一片
            graphics.fill(x + 26, y + 3, x + WIDTH - 3, y + HEIGHT - 3, 0x4A000000);

            // 快捷键编号放大一点并改成金色，方便一眼看清
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(x + 13, y + 9, 0.0F);
            pose.scale(1.25F, 1.25F, 1.0F);
            graphics.drawCenteredString(minecraft.font, Integer.toString(slot + 1), 0, 0, 0xFFFFE08A);
            pose.popPose();

            Component name = skill == null
                    ? Component.translatable("hud.kamenrider_kaijin_craft.skill_slot.empty")
                    : skillName(skill);
            String displayName = minecraft.font.plainSubstrByWidth(name.getString(), WIDTH - 33);
            graphics.drawString(minecraft.font, displayName, x + 29, y + 5,
                    skill == null ? 0xFFBBBBBB : 0xFFFFFFFF, true);

            if (skill != null) {
                Component status = remaining > 0
                        ? Component.translatable("hud.kamenrider_kaijin_craft.skill_slot.cooldown", (remaining + 19) / 20)
                        : Component.translatable("gui.kamenrider_kaijin_craft.skill_wheel.ready");
                graphics.drawString(minecraft.font, status, x + 29, y + 16,
                        remaining > 0 ? 0xFFFF8888 : 0xFF88FFAA, true);
            }
        }
    }

    public static Component skillName(StoriousSkill skill) {
        return Component.translatable("skill.kamenrider_kaijin_craft." + skill.name().toLowerCase());
    }
}
