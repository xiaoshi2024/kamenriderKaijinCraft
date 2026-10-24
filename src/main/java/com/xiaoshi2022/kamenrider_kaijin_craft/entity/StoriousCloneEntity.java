package com.xiaoshi2022.kamenrider_kaijin_craft.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * 斯特利乌斯的分身：不会移动、不会被伤害，只负责吸引附近怪物的仇恨，
 * 一段时间后自行消散。
 */
public class StoriousCloneEntity extends PathfinderMob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;
    private int life = 160;

    public StoriousCloneEntity(EntityType<? extends StoriousCloneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public void setLife(int life) {
        this.life = life;
    }

    @Override
    protected void registerGoals() {
        // 分身不移动、不攻击，只站着吸引仇恨
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (--life <= 0) {
            discard();
            return;
        }
        if (tickCount % 20 == 0) {
            for (var mob : level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
                    getBoundingBox().inflate(16.0D), candidate -> candidate != this && candidate.isAlive()
                            && candidate instanceof Enemy)) {
                LivingEntity target = mob.getTarget();
                if (target == null || (owner != null && owner.equals(target.getUUID()))) {
                    mob.setTarget(this);
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return tickCount;
    }
}
