package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.shocker.client.render;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * 修卡怪人的整身 Geo 模型：沿用 bbmodel 导出的 shocker.geo.json 与 shocker.png。
 */
public class ShockerEntityModel extends GeoModel<ShockerAnimatable> {
    public static final ResourceLocation TEXTURE = KaijinMOD.id("textures/entity/shocker.png");
    private static final ResourceLocation MODEL = KaijinMOD.id("geo/entity/shocker.geo.json");
    private static final ResourceLocation ANIMATION = KaijinMOD.id("animations/entity/shocker.animation.json");

    @Override
    public ResourceLocation getModelResource(ShockerAnimatable animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(ShockerAnimatable animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(ShockerAnimatable animatable) {
        return ANIMATION;
    }
}
