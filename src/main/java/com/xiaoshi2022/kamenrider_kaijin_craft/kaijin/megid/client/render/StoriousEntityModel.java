package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 斯特利乌斯怪人的实体模型（整身替换玩家）。 */
public class StoriousEntityModel extends GeoModel<StoriousAnimatable> {
    public static final ResourceLocation TEXTURE = KaijinMOD.id("textures/entity/storious.png");
    private static final ResourceLocation MODEL = KaijinMOD.id("geo/entity/storious.geo.json");
    private static final ResourceLocation ANIMATION = KaijinMOD.id("animations/entity/storious.animation.json");

    @Override
    public ResourceLocation getModelResource(StoriousAnimatable animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(StoriousAnimatable animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(StoriousAnimatable animatable) {
        return ANIMATION;
    }
}
