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
 * 拉结尔形态的玩家动画宿主，每个被渲染的玩家各持一份实例。
 * <p>
 * 播放优先级：attack &gt; crouch &gt; run &gt; idle。
 * 下蹲先播一次蹲下过渡（crouch），再循环蹲姿（crouch_idle）。
 */
public final class LegeielAnimatable implements GeoAnimatable {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation CROUCH = RawAnimation.begin()
            .thenPlay("crouch")
            .thenLoop("crouch_idle");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float partialTick;

    public void setPartialTick(float partialTick) {
        this.partialTick = partialTick;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            if (!(state.getData(DataTickets.ENTITY) instanceof LivingEntity living)) {
                return state.setAndContinue(IDLE);
            }
            if (living.swinging) {
                return state.setAndContinue(ATTACK);
            }
            if (living.isCrouching()) {
                return state.setAndContinue(CROUCH);
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
