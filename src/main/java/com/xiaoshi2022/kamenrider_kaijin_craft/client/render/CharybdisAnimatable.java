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

import java.util.ArrayList;
import java.util.List;

/**
 * 卡律布狄斯形态的玩家动画宿主，每个被渲染的玩家各持一份实例。
 * <p>
 * idle / run / swim / shift（蹲下）/ attack 按实体状态自动切换；
 * eat 是吞噬技能触发的一次性动作，播完自动回到上面的循环状态。
 */
public final class CharybdisAnimatable implements GeoAnimatable {
    private static final String MAIN = "main";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation SHIFT = RawAnimation.begin().thenLoop("shift");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation EAT = RawAnimation.begin().thenPlay("eat");
    /** 水平速度超过这个平方值就算在跑（约 0.05 格/刻）。 */
    private static final double RUN_SPEED_SQR = 0.0025D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** 整身与第一人称手臂各有一个 manager，因此可能注册出多个 controller。 */
    private final List<AnimationController<CharybdisAnimatable>> controllers = new ArrayList<>();
    private float partialTick;

    public void setPartialTick(float partialTick) {
        this.partialTick = partialTick;
    }

    /** 播放一次性动作；返回是否至少有一个 controller 接受了这次触发。 */
    public boolean playTriggered(String animation) {
        boolean triggered = false;
        for (AnimationController<CharybdisAnimatable> controller : controllers) {
            triggered |= controller.tryTriggerAnimation(animation);
        }
        return triggered;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<CharybdisAnimatable> controller =
                new AnimationController<>(this, MAIN, 3, state -> {
                    if (!(state.getData(DataTickets.ENTITY) instanceof LivingEntity living)) {
                        return state.setAndContinue(IDLE);
                    }
                    if (living.swinging) {
                        return state.setAndContinue(ATTACK);
                    }
                    if (living.isInWater()) {
                        return state.setAndContinue(SWIM);
                    }
                    if (living.isCrouching()) {
                        return state.setAndContinue(SHIFT);
                    }
                    if (living.isSprinting()
                            || living.getDeltaMovement().horizontalDistanceSqr() > RUN_SPEED_SQR) {
                        return state.setAndContinue(RUN);
                    }
                    return state.setAndContinue(IDLE);
                }).triggerableAnim("eat", EAT);
        this.controllers.add(controller);
        controllers.add(controller);
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
