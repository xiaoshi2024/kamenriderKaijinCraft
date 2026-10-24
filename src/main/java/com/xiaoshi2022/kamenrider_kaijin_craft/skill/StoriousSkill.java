package com.xiaoshi2022.kamenrider_kaijin_craft.skill;

/** 斯特利乌斯怪人形态的技能，与客户端按键一一对应。 */
public enum StoriousSkill {
    /** 徒手制造黑色 / 深红色的黑暗能量箭 */
    DARK_ARROW(60),
    /** 双眼投射的失明光线 */
    BLINDING_RAY(160),
    /** 鹿角产生的黄色能量卷须 */
    ANTLER_TENDRILS(200),
    /** 两体分身 */
    CLONES(320),
    /** 瞬间传送 */
    BLINK(100);

    public final int cooldownTicks;

    StoriousSkill(int cooldownTicks) {
        this.cooldownTicks = cooldownTicks;
    }

    public static StoriousSkill byId(int id) {
        StoriousSkill[] values = values();
        return id >= 0 && id < values.length ? values[id] : DARK_ARROW;
    }
}
