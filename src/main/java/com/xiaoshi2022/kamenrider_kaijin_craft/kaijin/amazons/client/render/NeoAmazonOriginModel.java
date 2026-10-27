package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.client.render;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 千翼 · Neo Amazon Origin 的实体模型（整身替换玩家）。 */
public class NeoAmazonOriginModel extends GeoModel<NeoAmazonOriginAnimatable> {
    public static final ResourceLocation TEXTURE = KaijinMOD.id("textures/entity/neo_amazon_origin.png");
    private static final ResourceLocation MODEL = KaijinMOD.id("geo/entity/neo_amazon_origin.geo.json");
    private static final ResourceLocation ANIMATION =
            KaijinMOD.id("animations/entity/neo_amazon_origin.animation.json");

    @Override
    public ResourceLocation getModelResource(NeoAmazonOriginAnimatable animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(NeoAmazonOriginAnimatable animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(NeoAmazonOriginAnimatable animatable) {
        return ANIMATION;
    }
}
