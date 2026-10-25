package com.xiaoshi2022.kamenrider_kaijin_craft.client.render;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.item.AlterRideBookItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 卡律布狄斯·类驾驭书的物品模型。 */
public class CharybdisBookModel extends GeoModel<AlterRideBookItem> {
    private static final ResourceLocation MODEL = KaijinMOD.id("geo/item/charybdis_book.geo.json");
    private static final ResourceLocation TEXTURE = KaijinMOD.id("textures/item/charybdis_book.png");
    private static final ResourceLocation ANIMATION = KaijinMOD.id("animations/item/charybdis_book.animation.json");

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
