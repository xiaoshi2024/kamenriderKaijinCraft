package com.xiaoshi2022.kamenrider_kaijin_craft.client.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 被替换渲染的玩家动画宿主：GeoReplacedEntityRenderer 会在每帧把玩家实体
 * 通过 {@link DataTickets#ENTITY} 塞进来，控制器据此在 idle / run / attack 之间切换。
 * <p>
 * 每个被渲染的玩家各持一份实例（见 {@link StoriousBodyRenderer}），
 * 否则多人同时处于怪人形态时动画会互相串台。
 */
public final class StoriousAnimatable implements GeoAnimatable {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float partialTick;

    void setPartialTick(float partialTick) {
        this.partialTick = partialTick;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            if (state.getData(DataTickets.ENTITY) instanceof LivingEntity living && living.swinging) {
                return state.setAndContinue(ATTACK);
            }
            return state.isMoving() ? state.setAndContinue(RUN) : state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return object instanceof Entity entity ? entity.tickCount + partialTick : partialTick;
    }
}
