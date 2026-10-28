package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.shocker.client.render;

import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 修卡怪人形态的玩家动画宿主。
 * <p>
 * 按玩家状态在 idle / walk / attack / swim 之间切换；变身瞬间额外播一次 {@code yi}。
 */
public final class ShockerAnimatable implements GeoAnimatable {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    /** 变身动作：shocker.animation.json 中的 yi（时长 2 秒）。 */
    private static final RawAnimation TRANSFORM = RawAnimation.begin().thenPlay("yi");
    private static final int TRANSFORM_TICKS = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float partialTick;
    private int transformTicks;
    private boolean attacking;
    private boolean swimming;
    private float walkSpeed;

    public void setPartialTick(float partialTick) {
        this.partialTick = partialTick;
    }

    /** 由渲染器每帧写入玩家当前状态。 */
    public void setState(boolean attacking, boolean swimming, float walkSpeed) {
        this.attacking = attacking;
        this.swimming = swimming;
        this.walkSpeed = walkSpeed;
    }

    /** 变身瞬间触发一次 yi 动作。 */
    public void playTransform() {
        this.transformTicks = TRANSFORM_TICKS;
    }

    /** 由客户端每 tick 调用，倒计时结束即让位给常态动作。 */
    public void tick() {
        if (this.transformTicks > 0) {
            this.transformTicks--;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            if (this.transformTicks > 0) {
                return state.setAndContinue(TRANSFORM);
            }
            if (this.attacking) {
                return state.setAndContinue(ATTACK);
            }
            if (this.swimming) {
                return state.setAndContinue(SWIM);
            }
            return state.setAndContinue(this.walkSpeed > 0.03F ? WALK : IDLE);
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
