package com.xiaoshi2022.kamenrider_kaijin_craft.entity;

import com.xiaoshi2022.kamenrider_kaijin_craft.race.KaijinMana;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * 斯特利乌斯的分身：不会被伤害，存活期间可以跟随本体移动、
 * 主动攻击附近的敌对生物，并持续吸引怪物仇恨，时间到了自行消散。
 */
public class StoriousCloneEntity extends PathfinderMob implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;
    private int life = 240;

    public StoriousCloneEntity(EntityType<? extends StoriousCloneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public void setLife(int life) {
        this.life = life;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15D, false));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                candidate -> candidate instanceof Enemy && !(candidate instanceof StoriousCloneEntity)));
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

        // 没有敌人时回到本体身边
        Player ownerPlayer = owner == null ? null : ((ServerLevel) level()).getPlayerByUUID(owner);
        if (getTarget() == null && ownerPlayer != null && ownerPlayer.isAlive()) {
            double distanceSqr = distanceToSqr(ownerPlayer);
            if (distanceSqr > 25.0D) {
                if (getNavigation().isDone()) {
                    getNavigation().moveTo(ownerPlayer, 1.1D);
                }
            } else if (distanceSqr < 9.0D) {
                getNavigation().stop();
            }
            getLookControl().setLookAt(ownerPlayer, 10.0F, 10.0F);
        }

        // 吸引附近敌对生物的仇恨
        if (tickCount % 20 == 0) {
            for (Mob mob : level().getEntitiesOfClass(Mob.class,
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
        // 分身不吃伤害，但敌人被分身骗走火力就算一次「剧情交互」：给本体回蓝
        if (!level().isClientSide() && owner != null
                && ((ServerLevel) level()).getPlayerByUUID(owner) instanceof ServerPlayer ownerPlayer) {
            KaijinMana.award(ownerPlayer, "clone_decoy", 10.0F, 100);
        }
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
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (this.swinging) {
                return state.setAndContinue(ATTACK);
            }
            boolean moving = this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D;
            return state.setAndContinue(moving ? RUN : IDLE);
        }));
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
