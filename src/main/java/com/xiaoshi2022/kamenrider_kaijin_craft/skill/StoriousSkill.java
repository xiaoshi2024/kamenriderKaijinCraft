package com.xiaoshi2022.kamenrider_kaijin_craft.skill;

/** 斯特利乌斯怪人形态的技能，与客户端按键一一对应。 */
public enum StoriousSkill {
    /** 徒手制造黑色 / 深红色的黑暗能量箭 */
    DARK_ARROW(60, 15),
    /** 双眼投射的失明光线 */
    BLINDING_RAY(160, 25),
    /** 鹿角产生的黄色能量卷须 */
    ANTLER_TENDRILS(200, 30),
    /** 两体分身 */
    CLONES(320, 45),
    /** 瞬间传送 */
    BLINK(100, 20);

    public final int cooldownTicks;
    /** 施放一次消耗的叙事值（蓝量）。 */
    public final float manaCost;

    StoriousSkill(int cooldownTicks, float manaCost) {
        this.cooldownTicks = cooldownTicks;
        this.manaCost = manaCost;
    }

    public static StoriousSkill byId(int id) {
        StoriousSkill[] values = values();
        return id >= 0 && id < values.length ? values[id] : DARK_ARROW;
    }
}
