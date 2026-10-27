package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.entity.StoriousCloneEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** 斯特利乌斯分身，直接复用本体的 geo / 贴图 / idle 动画。 */
public class StoriousCloneRenderer extends GeoEntityRenderer<StoriousCloneEntity> {
    public StoriousCloneRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(KaijinMOD.id("storious")));
        this.shadowRadius = 0.6F;
    }
}
