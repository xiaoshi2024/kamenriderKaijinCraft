package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KaijinKeys {
    public static final String CATEGORY = "key.categories.kamenrider_kaijin_craft";

    /** 拟态 / 解放（G） */
    public static final KeyMapping TOGGLE_FORM = new KeyMapping(
            "key.kamenrider_kaijin_craft.toggle_form",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    private KaijinKeys() {
    }
}
