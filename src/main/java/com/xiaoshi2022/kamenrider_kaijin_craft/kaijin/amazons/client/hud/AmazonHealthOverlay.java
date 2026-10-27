package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.client.hud;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.race.AmazonRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.race.AmazonState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * 亚马逊怪人的自定义血条。
 * <p>
 * 千翼形态有 300 点血，原版心形血条会排成十几行并压住食物/护甲 HUD。
 * 这里取消原版血条（见 {@code GuiHealthMixin}），换成银色条状血条，
 * 位置与书之怪物的血条一致。
 */
public final class AmazonHealthOverlay {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 10;

    private AmazonHealthOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()
                || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer()) {
            return;
        }
        AmazonState state = AmazonRace.get(player);
        if (!state.amazon()) {
            return;
        }

        float maxHealth = Math.max(1.0F, player.getMaxHealth());
        float health = Mth.clamp(player.getHealth(), 0.0F, maxHealth);
        boolean monster = state.monster();

        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        int y = graphics.guiHeight() - 66;

        // 千翼：银白金属配色
        int border = monster ? 0xFFDCE1E6 : 0xFF8A9096;
        int empty = monster ? 0xFF23272B : 0xFF1B1E21;
        int from = monster ? 0xFF8A939B : 0xFF5F666C;
        int to = monster ? 0xFFF7F9FB : 0xFFAEB6BD;
        int textColor = monster ? 0xFFFFFFFF : 0xFFE8ECEF;

        graphics.fill(x, y, x + WIDTH, y + HEIGHT, border);
        graphics.fill(x + 1, y + 1, x + WIDTH - 1, y + HEIGHT - 1, empty);

        int innerWidth = WIDTH - 2;
        int innerHeight = HEIGHT - 2;
        int fillWidth = Math.round(innerWidth * health / maxHealth);
        if (fillWidth > 0) {
            graphics.fillGradient(x + 1, y + 1, x + 1 + fillWidth, y + 1 + innerHeight, from, to);
        }

        for (int section = 1; section < 4; section++) {
            int tickX = x + WIDTH * section / 4;
            graphics.fill(tickX, y + 1, tickX + 1, y + HEIGHT - 1, 0x50000000);
        }

        String text = (int) Math.ceil(health) + " / " + (int) maxHealth;
        graphics.drawCenteredString(minecraft.font, text, x + WIDTH / 2, y + 1, textColor);
    }
}
