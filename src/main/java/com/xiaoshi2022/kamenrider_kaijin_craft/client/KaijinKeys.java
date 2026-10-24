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

    /** 技能盘（V）：按住打开，松开关闭 */
    public static final KeyMapping SKILL_WHEEL = new KeyMapping(
            "key.kamenrider_kaijin_craft.skill_wheel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    /** 三个快捷技能槽（1 / 2 / 3），绑定方式与尸兄技能盘一致 */
    public static final KeyMapping[] QUICK_SKILLS = new KeyMapping[3];

    static {
        for (int slot = 0; slot < QUICK_SKILLS.length; slot++) {
            QUICK_SKILLS[slot] = new KeyMapping(
                    "key.kamenrider_kaijin_craft.quick_skill_" + (slot + 1),
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_1 + slot,
                    CATEGORY);
        }
    }

    private KaijinKeys() {
    }
}
