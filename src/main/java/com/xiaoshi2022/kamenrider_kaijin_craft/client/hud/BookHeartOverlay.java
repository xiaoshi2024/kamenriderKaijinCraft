package com.xiaoshi2022.kamenrider_kaijin_craft.client.hud;

import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinState;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinType;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

/**
 * 书之怪物的额外心脏框。
 * <p>
 * 参考尸兄黑金心脏的展示方式：在血条旁常驻一个带脉动红光的框，
 * 只不过书之怪物的心脏是斯特利乌斯·类驾驭书本身。
 */
public final class BookHeartOverlay {
    private static final int SIZE = 20;

    private BookHeartOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        BookKaijinState state = BookKaijinRace.get(player);
        if (!state.bookKaijin()) {
            return;
        }
        boolean charybdis = state.type() == BookKaijinType.CHARYBDIS;

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        int pulse = (int) (Math.sin((player.tickCount + partialTick) * 0.35) * 0.5 + 0.5);
        int x = graphics.guiWidth() / 2 + 94;
        int y = graphics.guiHeight() - SIZE - 24;
        // 边框 / 内光按怪人种类换色：斯特利乌斯暗红，卡律布狄斯橙金
        int border = ((0x80 + pulse * 0x70) << 24) | (charybdis ? 0xE09A28 : 0xE01428);
        int glow = ((0x20 + pulse * 0x28) << 24) | (charybdis ? 0xB06A14 : 0xB01227);

        graphics.fill(x, y, x + SIZE, y + SIZE, charybdis ? 0xB0160A00 : 0xB0100006);
        graphics.fill(x, y, x + SIZE, y + 1, border);
        graphics.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, border);
        graphics.fill(x, y, x + 1, y + SIZE, border);
        graphics.fill(x + SIZE - 1, y, x + SIZE, y + SIZE, border);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, glow);

        graphics.renderItem((charybdis ? ModItems.CHARYBDIS_BOOK : ModItems.STORIOUS_BOOK)
                .get().getDefaultInstance(), x + 2, y + 2);
    }
}
