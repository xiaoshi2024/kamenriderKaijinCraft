package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.skill.StoriousSkill;

import java.util.Arrays;

/** 客户端技能冷却缓存，仅用于技能盘显示，实际判定仍在服务端。 */
public final class ClientSkillCooldowns {
    private static final long[] ENDS = new long[StoriousSkill.values().length];

    private ClientSkillCooldowns() {
    }

    public static void update(StoriousSkill skill, int ticks) {
        int index = skill.ordinal();
        if (index >= 0 && index < ENDS.length) {
            ENDS[index] = System.currentTimeMillis() + Math.max(0, ticks) * 50L;
        }
    }

    public static int remainingTicks(StoriousSkill skill) {
        int index = skill.ordinal();
        if (index < 0 || index >= ENDS.length) {
            return 0;
        }
        long remainingMs = ENDS[index] - System.currentTimeMillis();
        return (int) Math.max(0L, (remainingMs + 49L) / 50L);
    }

    public static void clear() {
        Arrays.fill(ENDS, 0L);
    }
}
