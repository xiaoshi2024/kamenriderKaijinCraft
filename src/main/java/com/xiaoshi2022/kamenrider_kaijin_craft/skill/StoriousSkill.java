package com.xiaoshi2022.kamenrider_kaijin_craft.skill;

import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinType;

/**
 * 书之怪人形态的技能，与客户端按键一一对应。
 * <p>
 * {@code onlyFor} 决定这个技能属于哪只书之怪人：技能盘只显示当前形态自己的技能，
 * 服务端也会拒绝不属于当前形态的施放请求。目前斯特利乌斯 5 个、卡律布狄斯 2 个、拉结尔 4 个。
 */
public enum StoriousSkill {
    /** 徒手制造黑色 / 深红色的黑暗能量箭 */
    DARK_ARROW(60, 6, BookKaijinType.STORIOUS),
    /** 双眼投射的失明光线 */
    BLINDING_RAY(160, 10, BookKaijinType.STORIOUS),
    /** 鹿角产生的黄色能量卷须 */
    ANTLER_TENDRILS(200, 12, BookKaijinType.STORIOUS),
    /** 两体分身 */
    CLONES(320, 18, BookKaijinType.STORIOUS),
    /** 瞬间传送 */
    BLINK(100, 8, BookKaijinType.STORIOUS),
    /** 卡律布狄斯：黏液把前方的敌人整个吞下 */
    DEVOUR(200, 10, BookKaijinType.CHARYBDIS),
    /** 卡律布狄斯：磨掉正前方一段空间，贴到目标身前或直接赶路 */
    DEVOUR_SPACE(80, 7, BookKaijinType.CHARYBDIS),
    /** 拉结尔：全元素操控，扇形掷出冰块与岩石 */
    BOLHESSED_ELEMENT(120, 10, BookKaijinType.LEGEIEL),
    /** 拉结尔：强化跳跃，长距离跃出 */
    BOLHESSED_JUMP(100, 7, BookKaijinType.LEGEIEL),
    /** 拉结尔：围巾攻防，扫开正前方的敌人并短暂护体 */
    BOLHESSED_SCARF(160, 9, BookKaijinType.LEGEIEL),
    /** 拉结尔：攻击反弹，短时间内把受到的攻击弹回攻击者 */
    BOLHESSED_REFLECT(260, 12, BookKaijinType.LEGEIEL);

    public final int cooldownTicks;
    /** 施放一次消耗的叙事值（蓝量）。 */
    public final float manaCost;
    /** 归属的怪人种类。 */
    private final BookKaijinType onlyFor;

    StoriousSkill(int cooldownTicks, float manaCost, BookKaijinType onlyFor) {
        this.cooldownTicks = cooldownTicks;
        this.manaCost = manaCost;
        this.onlyFor = onlyFor;
    }

    /** 当前怪人形态能否看到 / 释放这个技能。 */
    public boolean availableFor(BookKaijinType type) {
        return onlyFor == type;
    }

    public static StoriousSkill byId(int id) {
        StoriousSkill[] values = values();
        return id >= 0 && id < values.length ? values[id] : DARK_ARROW;
    }
}
