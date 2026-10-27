package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.client.render;

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
 * Neo Amazon Origin 形态的玩家动画宿主。
 * <p>
 * idle / run / shift（蹲下）/ attack 按实体状态自动切换；
 * special 是触手被动发动时的一次性动作，播完自动回到循环状态。
 */
public final class NeoAmazonOriginAnimatable implements GeoAnimatable {
    private static final String MAIN = "main";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation SHIFT = RawAnimation.begin().thenLoop("shift");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation SPECIAL = RawAnimation.begin().thenPlay("special");
    /** 水平速度超过这个平方值就算在跑（约 0.05 格/刻）。 */
    private static final double RUN_SPEED_SQR = 0.0025D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<AnimationController<NeoAmazonOriginAnimatable>> controllers = new ArrayList<>();
    private float partialTick;

    public void setPartialTick(float partialTick) {
        this.partialTick = partialTick;
    }

    public boolean playTriggered(String animation) {
        boolean triggered = false;
        for (AnimationController<NeoAmazonOriginAnimatable> controller : controllers) {
            triggered |= controller.tryTriggerAnimation(animation);
        }
        return triggered;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        AnimationController<NeoAmazonOriginAnimatable> controller =
                new AnimationController<>(this, MAIN, 3, state -> {
                    if (!(state.getData(DataTickets.ENTITY) instanceof LivingEntity living)) {
                        return state.setAndContinue(IDLE);
                    }
                    if (living.swinging) {
                        return state.setAndContinue(ATTACK);
                    }
                    if (living.isCrouching()) {
                        return state.setAndContinue(SHIFT);
                    }
                    if (living.isSprinting()
                            || living.getDeltaMovement().horizontalDistanceSqr() > RUN_SPEED_SQR) {
                        return state.setAndContinue(RUN);
                    }
                    return state.setAndContinue(IDLE);
                }).triggerableAnim("special", SPECIAL);
        this.controllers.add(controller);
        registrar.add(controller);
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
