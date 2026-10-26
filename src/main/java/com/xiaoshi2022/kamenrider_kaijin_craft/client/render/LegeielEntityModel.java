package com.xiaoshi2022.kamenrider_kaijin_craft.client.render;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 拉结尔怪人的实体模型（整身替换玩家）。 */
public class LegeielEntityModel extends GeoModel<LegeielAnimatable> {
    public static final ResourceLocation TEXTURE = KaijinMOD.id("textures/entity/legeiel.png");
    private static final ResourceLocation MODEL = KaijinMOD.id("geo/entity/legeiel.geo.json");
    private static final ResourceLocation ANIMATION = KaijinMOD.id("animations/entity/legeiel.animation.json");

    @Override
    public ResourceLocation getModelResource(LegeielAnimatable animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LegeielAnimatable animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LegeielAnimatable animatable) {
        return ANIMATION;
    }
}
