package com.xiaoshi2022.kamenrider_kaijin_craft.mixin;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinRace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 书之怪物形态下血量上限可达 300，原版心形血条会排成多行压到其它 HUD 上。
 * <p>
 * NeoForge 1.21.1 把原版 HUD 拆成了独立图层，{@code PLAYER_HEALTH} 图层直接调用
 * {@code Gui.renderHealthLevel}，而不是 {@code renderPlayerHealth}（后者只用于整组
 * 血量/护甲/食物/氧气的一次性重绘）。因此必须注入 {@code renderHealthLevel}，
 * 既隐藏心形血条，又不会连带把护甲、食物、氧气条一起抹掉。
 */
@Mixin(Gui.class)
public abstract class GuiHealthMixin {
    @Inject(method = "renderHealthLevel", at = @At("HEAD"), cancellable = true)
    private void kaijin$hideVanillaHealth(GuiGraphics graphics, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null && BookKaijinRace.get(player).bookKaijin()) {
            ci.cancel();
        }
    }
}
