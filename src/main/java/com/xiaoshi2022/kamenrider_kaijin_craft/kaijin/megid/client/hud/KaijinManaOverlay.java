package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.hud;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.KaijinMana;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * 怪人技能蓝条（叙事值）HUD。
 * <p>
 * 紧贴自定义血条下方的一条 160×4 细条，与血条同宽同轴，不会压到护甲、食物或快捷栏。
 * 低于两成时边框转为脉动红色，提醒再放技能怪人道具就会爆掉。
 */
public final class KaijinManaOverlay {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 4;
    private static final float WARNING_RATIO = 0.2F;

    private KaijinManaOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()
                || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()) {
            return;
        }
        if (!BookKaijinRace.get(player).bookKaijin()) {
            return;
        }

        float max = Math.max(1.0F, KaijinMana.max(player));
        float current = Mth.clamp(KaijinMana.current(player), 0.0F, max);
        float ratio = current / max;
        boolean warning = ratio <= WARNING_RATIO;

        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        // 紧贴在抬高的血条下方，两条一起悬在护甲条上方
        int y = graphics.guiHeight() - 55;

        int border;
        if (warning) {
            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
            int pulse = (int) (Math.sin((player.tickCount + partialTick) * 0.5) * 0.5 + 0.5);
            border = ((0x90 + pulse * 0x60) << 24) | 0xE0303A;
        } else {
            border = 0xFF4466D8;
        }

        graphics.fill(x, y, x + WIDTH, y + HEIGHT, border);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, 0xFF070B1A);

        int innerWidth = WIDTH - 2;
        int fillWidth = Math.round(innerWidth * ratio);
        if (fillWidth > 0) {
            graphics.fillGradient(x + 1, y + 1, x + 1 + fillWidth, y + HEIGHT - 1,
                    warning ? 0xFF8A1A2A : 0xFF1E3A8A, warning ? 0xFFE05060 : 0xFF7FB4FF);
        }
    }
}
