package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.skill;

import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.race.AmazonRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.KaijinMana;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.AmazonTendrilsPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.KaijinAnimationPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 千翼的被动技能：全身涌出无数纤细触手，刺穿半径 6 格内的生物。
 * <p>
 * 完全自动发动——只要处于 Neo Amazon Origin 形态，每秒都会向周围最近的目标
 * 甩出一轮触手；被刺中的生物会掉血，并在伤口处迸出血液粒子，做出「被掏出一个洞」的观感。
 */
public final class AmazonTendrils {
    /** 触手覆盖半径。 */
    public static final double RADIUS = 6.0D;
    /** 发动间隔。 */
    public static final int INTERVAL_TICKS = 30;
    /** 每轮对单个目标的伤害。 */
    public static final float DAMAGE = 8.0F;
    /** 每轮最多同时刺穿的目标数（控制网络包与视觉开销）。 */
    public static final int MAX_TARGETS = 8;
    /** 每轮发动消耗的蓝量（叙事值）。 */
    public static final float MANA_COST = 8.0F;
    /** 每多少次发动额外消耗一次饱食度。 */
    public static final int HUNGER_EVERY = 3;
    /** 每轮触手额外消耗的饱食度（食物点）。 */
    public static final int HUNGER_COST = 6;
    /** 饱食度低于该值时，怪人形态被迫收回、退回人类拟态。 */
    public static final int HUNGER_REVERT_THRESHOLD = 4;

    private static final Map<UUID, Long> NEXT_LASH = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LASH_COUNT = new ConcurrentHashMap<>();

    private AmazonTendrils() {
    }

    public static void tick(ServerPlayer player) {
        if (!AmazonRace.isMonster(player)) {
            return;
        }
        // 饱食度跌破临界点：怪人形态被迫收回，退回人类拟态
        if (player.getFoodData().getFoodLevel() < HUNGER_REVERT_THRESHOLD) {
            AmazonRace.forceMimic(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        if (NEXT_LASH.getOrDefault(player.getUUID(), 0L) > now) {
            return;
        }
        if (!KaijinMana.hasEnough(player, MANA_COST)) {
            return;
        }
        List<LivingEntity> targets = findTargets(player);
        if (targets.isEmpty()) {
            return;
        }
        NEXT_LASH.put(player.getUUID(), now + INTERVAL_TICKS);
        KaijinMana.consume(player, MANA_COST);
        int count = LASH_COUNT.merge(player.getUUID(), 1, Integer::sum);
        if (count % HUNGER_EVERY == 0) {
            player.getFoodData().setFoodLevel(Math.max(0,
                    player.getFoodData().getFoodLevel() - HUNGER_COST));
        }
        lash(player, level, targets);
    }

    private static List<LivingEntity> findTargets(ServerPlayer player) {
        AABB area = player.getBoundingBox().inflate(RADIUS);
        List<LivingEntity> candidates = new ArrayList<>(player.serverLevel().getEntitiesOfClass(
                LivingEntity.class, area, entity -> isTarget(player, entity)));
        candidates.sort(Comparator.comparingDouble(player::distanceToSqr));
        if (candidates.size() > MAX_TARGETS) {
            return candidates.subList(0, MAX_TARGETS);
        }
        return candidates;
    }

    private static boolean isTarget(ServerPlayer player, LivingEntity entity) {
        if (entity == player || !entity.isAlive() || entity.isSpectator() || !entity.isAttackable()) {
            return false;
        }
        // 触手从身上甩出去，隔着墙壁不该刺到人
        if (!player.hasLineOfSight(entity)) {
            return false;
        }
        if (entity instanceof Player other) {
            return !other.isCreative() && !other.isSpectator();
        }
        // 不误伤自己驯服的宠物
        return !(entity instanceof OwnableEntity ownable
                && player.getUUID().equals(ownable.getOwnerUUID()));
    }

    private static void lash(ServerPlayer player, ServerLevel level, List<LivingEntity> targets) {
        List<Vec3> points = new ArrayList<>(targets.size());
        for (LivingEntity target : targets) {
            Vec3 center = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            points.add(center);
            target.hurt(player.damageSources().playerAttack(player), DAMAGE);
            // 伤口：血液与碎肉粒子，做出被触手掏穿一个洞的感觉
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, center.x, center.y, center.z,
                    8, 0.22D, 0.22D, 0.22D, 0.08D);
            level.sendParticles(ParticleTypes.CRIT, center.x, center.y, center.z,
                    12, 0.32D, 0.32D, 0.32D, 0.35D);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new AmazonTendrilsPayload(player.getId(), points, INTERVAL_TICKS));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new KaijinAnimationPayload(player.getId(), "special", INTERVAL_TICKS));
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0F, 0.45F);
    }

    public static void clear(UUID playerId) {
        NEXT_LASH.remove(playerId);
        LASH_COUNT.remove(playerId);
    }
}
