package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 给背包里的「怪人融合槽」画一个独立的暗红边框，并在空槽悬停时给出说明。
 * 槽位本体由 InventoryMenuMixin 追加：
 * <ul>
 *   <li>生存背包：坐标 (77, 44)，副手槽正上方</li>
 *   <li>创造模式生存背包页：被 CreativeModeInventoryScreenMixin 挪到 (173, 90)，销毁槽上方</li>
 * </ul>
 */
@EventBusSubscriber(modid = KaijinMOD.MOD_ID, value = Dist.CLIENT)
public final class FusionSlotScreenEvents {
    private FusionSlotScreenEvents() {
    }

    @SubscribeEvent
    public static void onBackgroundRendered(ScreenEvent.BackgroundRendered event) {
        Screen screen = event.getScreen();
        int[] slot = slotPosition(screen);
        if (slot == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int x = slot[0] - 1;
        int y = slot[1] - 1;
        graphics.fill(x, y, x + 18, y + 18, 0xFF7A1424);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        graphics.fill(x + 2, y + 2, x + 5, y + 5, 0xFFB01B2C);
    }

    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        int[] slot = slotPosition(screen);
        if (slot == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int x = slot[0];
        int y = slot[1];
        if (event.getMouseX() < x || event.getMouseX() >= x + 16
                || event.getMouseY() < y || event.getMouseY() >= y + 16) {
            return;
        }
        if (!minecraft.player.getData(ModAttachments.FUSION_ITEM).isEmpty()) {
            return;   // 有物品时交给原版画物品 tooltip
        }
        event.getGuiGraphics().renderTooltip(minecraft.font,
                Component.translatable("gui.kamenrider_kaijin_craft.fusion_slot"),
                event.getMouseX(), event.getMouseY());
    }

    /** 返回融合槽在屏幕上的左上角坐标；当前界面不显示融合槽时返回 null。 */
    private static int[] slotPosition(Screen screen) {
        if (screen instanceof InventoryScreen inventoryScreen) {
            int left = (inventoryScreen.width - 176) / 2;
            int top = (inventoryScreen.height - 166) / 2;
            return new int[]{left + 77, top + 44};
        }
        if (screen instanceof CreativeModeInventoryScreen creativeScreen && creativeScreen.isInventoryOpen()) {
            int left = (creativeScreen.width - 195) / 2;
            int top = (creativeScreen.height - 136) / 2;
            return new int[]{left + 173, top + 90};
        }
        return null;
    }
}
