package com.xiaoshi2022.kamenrider_kaijin_craft.skill;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.entity.StoriousCloneEntity;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.AntlerTendrilsPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.DarkBeamPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.QiAuraPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.SkillCooldownPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 斯特利乌斯怪人形态的技能服务端逻辑。
 * <p>
 * 黑暗能量箭 / 失明光线是瞬发光束：服务端立刻结算伤害并下发一条短暂的光束给客户端渲染；
 * 鹿角卷须是范围控制；分身会吸引仇恨；瞬移用坐标偏移 + 红气收尾。
 */
public final class StoriousSkills {
    private static final Map<UUID, Map<StoriousSkill, Long>> COOLDOWNS = new ConcurrentHashMap<>();

    private StoriousSkills() {
    }

    public static void clear(UUID playerId) {
        COOLDOWNS.remove(playerId);
    }

    public static void use(ServerPlayer player, StoriousSkill skill) {
        if (!BookKaijinRace.isMonster(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.skill_need_monster"), true);
            return;
        }
        long now = player.level().getGameTime();
        Map<StoriousSkill, Long> cooldowns = COOLDOWNS.computeIfAbsent(player.getUUID(),
                id -> new EnumMap<>(StoriousSkill.class));
        long readyAt = cooldowns.getOrDefault(skill, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.skill_cooldown", seconds), true);
            PacketDistributor.sendToPlayer(player,
                    new SkillCooldownPayload(skill.ordinal(), (int) (readyAt - now)));
            return;
        }
        cooldowns.put(skill, now + skill.cooldownTicks);
        PacketDistributor.sendToPlayer(player, new SkillCooldownPayload(skill.ordinal(), skill.cooldownTicks));

        switch (skill) {
            case DARK_ARROW -> darkArrow(player);
            case BLINDING_RAY -> blindingRay(player);
            case ANTLER_TENDRILS -> antlerTendrils(player);
            case CLONES -> clones(player);
            case BLINK -> blink(player);
        }
    }

    // ==================== 黑暗能量箭 ====================

    private static void darkArrow(ServerPlayer player) {
        BeamHit hit = beam(player, 24.0D, 1.4D, 12.0F, true);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new DarkBeamPayload(
                player.getId(), hit.origin(), hit.direction(), (float) hit.length(), 0, 8));
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.7F, 1.6F);
    }

    // ==================== 失明光线 ====================

    private static void blindingRay(ServerPlayer player) {
        BeamHit hit = beam(player, 16.0D, 1.2D, 2.0F, false);
        for (LivingEntity target : hit.hits()) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0), player);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), player);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new DarkBeamPayload(
                player.getId(), hit.origin(), hit.direction(), (float) hit.length(), 1, 10));
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0F, 1.9F);
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    // ==================== 鹿角卷须 ====================

    private static void antlerTendrils(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(6.0D), e -> e != player && e.isAlive())) {
            Vec3 offset = target.position().subtract(player.position());
            if (offset.lengthSqr() > 36.0D) {
                continue;
            }
            if (offset.lengthSqr() > 0.01D && offset.normalize().dot(look) < 0.35D) {
                continue;
            }
            targets.add(target);
            if (targets.size() >= 3) {
                break;
            }
        }

        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), 2.0F);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), player);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 1), player);
            Vec3 pull = player.position().add(0.0D, 0.2D, 0.0D).subtract(target.position());
            if (pull.lengthSqr() > 0.01D) {
                pull = pull.normalize().scale(0.35D);
                target.push(pull.x, 0.12D, pull.z);
                target.hurtMarked = true;
            }
        }

        if (!targets.isEmpty()) {
            int[] ids = targets.stream().mapToInt(Entity::getId).toArray();
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    new AntlerTendrilsPayload(player.getId(), ids, 30));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.SHULKER_BULLET_HIT,
                SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    // ==================== 分身 ====================

    private static void clones(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int index = 0; index < 2; index++) {
            StoriousCloneEntity clone = ModEntities.STORIOUS_CLONE.get().create(level);
            if (clone == null) {
                continue;
            }
            double angle = Math.toRadians(player.getYRot() + (index == 0 ? -40 : 40));
            double x = player.getX() - Math.sin(angle) * 1.8D;
            double z = player.getZ() + Math.cos(angle) * 1.8D;
            clone.moveTo(x, player.getY(), z, player.getYRot(), 0.0F);
            clone.setOwner(player.getUUID());
            clone.setLife(160);
            level.addFreshEntity(clone);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                player.getId(), "mimic", player.position(), BookKaijinRace.QI_COLOR, 1.5F, 25));
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    // ==================== 瞬间传送 ====================

    private static void blink(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.position();
        Vec3 direction = player.getLookAngle();
        Vec3 intended = start.add(direction.scale(12.0D));
        BlockHitResult blockHit = level.clip(new ClipContext(start.add(0.0D, 1.0D, 0.0D), intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 destination = blockHit.getType() == HitResult.Type.MISS
                ? intended
                : blockHit.getLocation().subtract(direction.scale(1.0D));

        player.teleportTo(destination.x, destination.y, destination.z);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", start, BookKaijinRace.QI_COLOR, 1.6F, 25));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", destination, BookKaijinRace.QI_COLOR, 1.6F, 25));
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    // ==================== 光束结算 ====================

    private record BeamHit(Vec3 origin, Vec3 direction, double length, List<LivingEntity> hits) {
    }

    private static BeamHit beam(ServerPlayer player, double maxLength, double radius, float damage, boolean knockback) {
        ServerLevel level = player.serverLevel();
        Vec3 origin = player.getEyePosition().add(player.getLookAngle().scale(0.35D));
        Vec3 direction = player.getLookAngle();
        Vec3 intended = origin.add(direction.scale(maxLength));
        BlockHitResult blockHit = level.clip(new ClipContext(origin, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double length = blockHit.getType() == HitResult.Type.MISS
                ? maxLength
                : origin.distanceTo(blockHit.getLocation());
        Vec3 rayEnd = origin.add(direction.scale(length));

        List<LivingEntity> hits = new ArrayList<>();
        AABB area = new AABB(origin, rayEnd).inflate(radius + 2.0D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive())) {
            double reach = radius + target.getBbWidth() * 0.5D;
            if (distanceToSegmentSqr(target.getBoundingBox().getCenter(), origin, rayEnd) > reach * reach) {
                continue;
            }
            hits.add(target);
            if (target.hurt(player.damageSources().playerAttack(player), damage) && knockback) {
                target.push(direction.x * 1.1D, 0.3D, direction.z * 1.1D);
                target.hurtMarked = true;
            }
        }
        return new BeamHit(origin, direction, length, hits);
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        double t = lengthSqr < 1.0E-9D ? 0.0D : point.subtract(start).dot(segment) / lengthSqr;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return point.distanceToSqr(start.add(segment.scale(t)));
    }
}
