package com.xiaoshi2022.kamenrider_kaijin_craft.client;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render.KaijinSkillRenderer;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.AntlerTendrilsPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.DarkBeamPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.SkillCooldownPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.skill.StoriousSkill;

/** 技能网络包 → 客户端渲染缓存 / 冷却缓存，避免服务端加载任何客户端类。 */
public final class ClientSkillHandler {
    private ClientSkillHandler() {
    }

    public static void acceptBeam(DarkBeamPayload payload) {
        KaijinSkillRenderer.acceptBeam(payload);
    }

    public static void acceptTendrils(AntlerTendrilsPayload payload) {
        KaijinSkillRenderer.acceptTendrils(payload);
    }

    public static void acceptCooldown(SkillCooldownPayload payload) {
        ClientSkillCooldowns.update(StoriousSkill.byId(payload.skill()), payload.ticks());
    }
}
