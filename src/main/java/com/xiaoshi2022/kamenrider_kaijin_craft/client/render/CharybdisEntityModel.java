package com.xiaoshi2022.kamenrider_kaijin_craft.client.render;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 卡律布狄斯怪人的实体模型（整身替换玩家）。 */
public class CharybdisEntityModel extends GeoModel<CharybdisAnimatable> {
    public static final ResourceLocation TEXTURE = KaijinMOD.id("textures/entity/charybdis.png");
    private static final ResourceLocation MODEL = KaijinMOD.id("geo/entity/charybdis.geo.json");
    private static final ResourceLocation ANIMATION = KaijinMOD.id("animations/entity/charybdis.animation.json");

    @Override
    public ResourceLocation getModelResource(CharybdisAnimatable animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CharybdisAnimatable animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CharybdisAnimatable animatable) {
        return ANIMATION;
    }
}
