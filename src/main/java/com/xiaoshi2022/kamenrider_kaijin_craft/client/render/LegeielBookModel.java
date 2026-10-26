package com.xiaoshi2022.kamenrider_kaijin_craft.client.render;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.item.AlterRideBookItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 拉结尔·类驾驭书的物品模型。 */
public class LegeielBookModel extends GeoModel<AlterRideBookItem> {
    private static final ResourceLocation MODEL = KaijinMOD.id("geo/item/legeiel_book.geo.json");
    private static final ResourceLocation TEXTURE = KaijinMOD.id("textures/item/legeiel_book.png");
    private static final ResourceLocation ANIMATION = KaijinMOD.id("animations/item/legeiel_book.animation.json");

    @Override
    public ResourceLocation getModelResource(AlterRideBookItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(AlterRideBookItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(AlterRideBookItem animatable) {
        return ANIMATION;
    }
}
