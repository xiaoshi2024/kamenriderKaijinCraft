package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.item.AlterRideBookItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** 类驾驭书（斯特利乌斯之书）的物品模型。 */
public class StoriousBookModel extends GeoModel<AlterRideBookItem> {
    private static final ResourceLocation MODEL = KaijinMOD.id("geo/item/storious_book.geo.json");
    private static final ResourceLocation TEXTURE = KaijinMOD.id("textures/item/storious_book.png");
    private static final ResourceLocation ANIMATION = KaijinMOD.id("animations/item/storious_book.animation.json");

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
