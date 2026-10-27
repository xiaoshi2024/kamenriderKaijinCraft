package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.hud;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinState;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinType;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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
        BookKaijinType type = state.type();
        int borderRgb;
        int glowRgb;
        int background;
        ItemStack icon;
        switch (type) {
            case CHARYBDIS -> {
                borderRgb = 0xE09A28;
                glowRgb = 0xB06A14;
                background = 0xB0160A00;
                icon = ModItems.CHARYBDIS_BOOK.get().getDefaultInstance();
            }
            case LEGEIEL -> {
                borderRgb = 0xE8C34A;
                glowRgb = 0xB0882A;
                background = 0xB0160E00;
                icon = ModItems.LEGEIEL_BOOK.get().getDefaultInstance();
            }
            default -> {
                borderRgb = 0xE01428;
                glowRgb = 0xB01227;
                background = 0xB0100006;
                icon = ModItems.STORIOUS_BOOK.get().getDefaultInstance();
            }
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        int pulse = (int) (Math.sin((player.tickCount + partialTick) * 0.35) * 0.5 + 0.5);
        int x = graphics.guiWidth() / 2 + 94;
        int y = graphics.guiHeight() - SIZE - 24;
        // 边框 / 内光按怪人种类换色
        int border = ((0x80 + pulse * 0x70) << 24) | borderRgb;
        int glow = ((0x20 + pulse * 0x28) << 24) | glowRgb;

        graphics.fill(x, y, x + SIZE, y + SIZE, background);
        graphics.fill(x, y, x + SIZE, y + 1, border);
        graphics.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, border);
        graphics.fill(x, y, x + 1, y + SIZE, border);
        graphics.fill(x + SIZE - 1, y, x + SIZE, y + SIZE, border);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, glow);

        graphics.renderItem(icon, x + 2, y + 2);
    }
}
