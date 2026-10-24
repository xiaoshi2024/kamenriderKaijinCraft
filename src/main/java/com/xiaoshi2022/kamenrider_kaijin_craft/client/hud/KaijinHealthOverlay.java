package com.xiaoshi2022.kamenrider_kaijin_craft.client.hud;

import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * 书之怪物的自定义血条。
 * <p>
 * 怪人形态有 300 点血，原版心形血条会排成十几行并压到食物/护甲等 HUD 上。
 * 这里取消原版血条（见 {@code GuiHealthMixin}），改为一条 160×10 的条形血条，
 * 位置与原来的心形血条一致（血条中心在屏幕中线、底边对齐原版血量槽），
 * 宽度控制在食物条与护甲条之间，不会穿模。
 */
public final class KaijinHealthOverlay {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 10;

    private KaijinHealthOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()
                || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()) {
            return;
        }
        BookKaijinState state = BookKaijinRace.get(player);
        if (!state.bookKaijin()) {
            return;
        }

        float maxHealth = Math.max(1.0F, player.getMaxHealth());
        float health = Mth.clamp(player.getHealth(), 0.0F, maxHealth);
        boolean monster = state.monster();

        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        // 抬到护甲/氧气条上方，避免和原版左下角那一排 HUD 叠在一起
        int y = graphics.guiHeight() - 66;

        int border = monster ? 0xFFE0254A : 0xFF8C1B2C;
        int empty = monster ? 0xFF3A0510 : 0xFF28060E;
        int from = monster ? 0xFFC51A36 : 0xFF6E1020;
        int to = monster ? 0xFFFF3B5C : 0xFFA81A30;

        graphics.fill(x, y, x + WIDTH, y + HEIGHT, border);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, empty);

        int innerWidth = WIDTH - 2;
        int innerHeight = HEIGHT - 2;
        int fillWidth = Math.round(innerWidth * health / maxHealth);
        if (fillWidth > 0) {
            graphics.fillGradient(x + 1, y + 1, x + 1 + fillWidth, y + 1 + innerHeight, from, to);
        }

        // 四等分的刻度，方便一眼估出剩余血量
        for (int section = 1; section < 4; section++) {
            int tickX = x + WIDTH * section / 4;
            graphics.fill(tickX, y + 1, tickX + 1, y + HEIGHT - 1, 0x50000000);
        }

        String text = (int) Math.ceil(health) + " / " + (int) maxHealth;
        graphics.drawCenteredString(minecraft.font, text, x + WIDTH / 2, y + 1,
                monster ? 0xFFFFE0E6 : 0xFFF0D0D6);
    }
}
