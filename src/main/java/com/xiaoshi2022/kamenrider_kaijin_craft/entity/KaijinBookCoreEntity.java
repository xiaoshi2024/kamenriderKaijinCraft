package com.xiaoshi2022.kamenrider_kaijin_craft.entity;

import com.xiaoshi2022.kamenrider_kaijin_craft.race.Stomach;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 卡律布狄斯肚子里的「实体核心」——也就是他怪人书的具象。
 * <p>
 * 被吞噬的玩家在消化结束前击碎它，就能从肚子里逃出去；打不碎就被消化到点。
 * 核心自己只会攻击肚子维度里的玩家（不拦创造/旁观），消化时间一到自动消失。
 */
public class KaijinBookCoreEntity extends PathfinderMob {
    private UUID devourer;
    private final List<UUID> victims = new ArrayList<>();
    /** 剩余消化 tick：到 0 就自毁，真正的击杀交给 {@link Stomach} 结算。 */
    private int digestLeft = -1;

    public KaijinBookCoreEntity(EntityType<? extends KaijinBookCoreEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setCustomName(Component.translatable("entity.kamenrider_kaijin_craft.kaijin_book_core"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    /** 绑定这一批被吞的玩家：核心碎掉时只放走这些人。 */
    public void setBatch(UUID devourer, List<UUID> victims, int digestTicks) {
        this.devourer = devourer;
        this.victims.clear();
        this.victims.addAll(victims);
        this.digestLeft = digestTicks;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, false));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                candidate -> candidate instanceof ServerPlayer player
                        && player.isAlive()
                        && !player.isCreative()
                        && !player.isSpectator()
                        && Stomach.DIMENSION.equals(player.level().dimension())));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (digestLeft >= 0 && --digestLeft <= 0) {
            // 消化到点：被吞的人由 Stomach 击杀结算，核心跟着消失
            discard();
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide()) {
            releaseVictims();
        }
        super.die(source);
    }

    /** 核心被打碎：把还关在肚子里的玩家放到吞噬者身前，并从吞噬者的消化队列里划掉。 */
    private void releaseVictims() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (UUID id : victims) {
            ServerPlayer victim = serverLevel.getServer().getPlayerList().getPlayer(id);
            if (victim == null || !victim.isAlive()) {
                continue;
            }
            if (!Stomach.DIMENSION.equals(victim.level().dimension())) {
                continue;
            }
            Stomach.escape(victim, devourer);
            victim.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.stomach_escaped"), false);
        }
        ServerPlayer owner = devourer == null ? null
                : serverLevel.getServer().getPlayerList().getPlayer(devourer);
        if (owner != null) {
            Stomach.forget(owner, victims);
            owner.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.stomach_core_lost"), true);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (devourer != null) {
            tag.putUUID("Devourer", devourer);
        }
        tag.putInt("DigestLeft", digestLeft);
        ListTag list = new ListTag();
        for (UUID id : victims) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            list.add(entry);
        }
        tag.put("Victims", list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        devourer = tag.hasUUID("Devourer") ? tag.getUUID("Devourer") : null;
        digestLeft = tag.getInt("DigestLeft");
        victims.clear();
        ListTag list = tag.getList("Victims", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (entry.hasUUID("Id")) {
                victims.add(entry.getUUID("Id"));
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
