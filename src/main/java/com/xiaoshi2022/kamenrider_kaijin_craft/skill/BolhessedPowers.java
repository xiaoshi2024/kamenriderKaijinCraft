package com.xiaoshi2022.kamenrider_kaijin_craft.skill;

import com.xiaoshi2022.kamenrider_kaijin_craft.network.DarkBeamPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.QiAuraPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinType;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.KaijinMana;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 拉结尔专武「波尔赫斯德」的能力结算。
 * <p>
 * 剑气会一路吸走附近的火、雷、冰、岩，四种元素越多剑气越重；
 * 元素弹幕、强化跳跃、围巾攻防与攻击反弹都从这里统一落地，
 * 物品右键与技能盘走的是同一套逻辑。
 */
public final class BolhessedPowers {
    /** 剑气射程 / 判定半径 / 基础伤害 */
    private static final double WAVE_RANGE = 26.0D;
    private static final double WAVE_RADIUS = 1.5D;
    private static final float WAVE_DAMAGE = 14.0F;
    /** 每吸收一种元素给剑气加多少伤害 */
    private static final float ELEMENT_DAMAGE_BONUS = 3.0F;
    /** 吸收元素的扫描半径（水平）与上下范围 */
    private static final int ELEMENT_SCAN_RADIUS = 6;
    private static final int ELEMENT_SCAN_HEIGHT = 3;
    /** 攻击反弹的持续时间 */
    private static final int REFLECT_TICKS = 160;

    private static final Map<UUID, Long> REFLECT_UNTIL = new ConcurrentHashMap<>();

    private enum Element {
        FIRE, LIGHTNING, ICE, ROCK
    }

    private BolhessedPowers() {
    }

    public static void clear(UUID playerId) {
        REFLECT_UNTIL.remove(playerId);
    }

    // ---------------------------------------------------------------- 剑气

    /**
     * 挥出一道横扫剑气：沿途吸收周围的火、雷、冰、岩，
     * 命中敌人后按吸收到的元素追加燃烧 / 感电 / 冻伤 / 冲击。
     */
    public static void swordWave(ServerPlayer player) {
        EnumSet<Element> elements = gatherElements(player);
        float damage = WAVE_DAMAGE + ELEMENT_DAMAGE_BONUS * elements.size();
        StoriousSkills.BeamHit hit = StoriousSkills.beam(player, player.getLookAngle(),
                WAVE_RANGE, WAVE_RADIUS, damage, false);
        ServerLevel level = player.serverLevel();
        for (LivingEntity target : hit.hits()) {
            applyElements(player, level, target, hit.direction(), elements);
        }
        Vec3 impact = hit.origin().add(hit.direction().scale(hit.length()));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new DarkBeamPayload(
                player.getId(), hit.origin(), hit.direction(), (float) hit.length(), 2, 12));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", impact, BookKaijinType.LEGEIEL.qiColor(), 1.8F, 18));
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0F, 1.3F);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    /** 扫描玩家周围实际存在的元素方块与雷雨天，剑把这些元素一起卷进剑气。 */
    private static EnumSet<Element> gatherElements(ServerPlayer player) {
        EnumSet<Element> found = EnumSet.noneOf(Element.class);
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-ELEMENT_SCAN_RADIUS, -ELEMENT_SCAN_HEIGHT, -ELEMENT_SCAN_RADIUS),
                origin.offset(ELEMENT_SCAN_RADIUS, ELEMENT_SCAN_HEIGHT, ELEMENT_SCAN_RADIUS))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES)
                    || state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)) {
                found.add(Element.FIRE);
            } else if (state.is(Blocks.LIGHTNING_ROD)) {
                found.add(Element.LIGHTNING);
            } else if (state.is(BlockTags.ICE) || state.is(BlockTags.SNOW)
                    || state.is(Blocks.POWDER_SNOW)) {
                found.add(Element.ICE);
            } else if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)
                    || state.is(Blocks.DEEPSLATE)) {
                found.add(Element.ROCK);
            }
            if (found.size() == Element.values().length) {
                break;
            }
        }
        if (level.isThundering()) {
            found.add(Element.LIGHTNING);
        }
        return found;
    }

    /** 按剑气卷进来的元素给命中目标叠状态。 */
    private static void applyElements(ServerPlayer player, ServerLevel level, LivingEntity target,
                                      Vec3 direction, EnumSet<Element> elements) {
        double y = target.getY() + target.getBbHeight() * 0.5D;
        if (elements.contains(Element.FIRE)) {
            target.igniteForSeconds(6.0F);
            level.sendParticles(ParticleTypes.FLAME,
                    target.getX(), y, target.getZ(), 12, 0.4D, 0.4D, 0.4D, 0.02D);
        }
        if (elements.contains(Element.LIGHTNING)) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0), player);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    target.getX(), y, target.getZ(), 16, 0.5D, 0.5D, 0.5D, 0.2D);
            level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        if (elements.contains(Element.ICE)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2), player);
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), 140));
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    target.getX(), y, target.getZ(), 14, 0.5D, 0.5D, 0.5D, 0.03D);
        }
        if (elements.contains(Element.ROCK)) {
            Vec3 push = direction.scale(0.7D);
            target.push(push.x, 0.25D, push.z);
            target.hurtMarked = true;
            level.sendParticles(ParticleTypes.CRIT,
                    target.getX(), y, target.getZ(), 10, 0.4D, 0.4D, 0.4D, 0.1D);
        }
    }

    // ---------------------------------------------------------------- 全元素操控

    /** 全元素操控：扇形掷出两发冰块与一发岩石，冰块冻住目标、岩石把目标撞飞。 */
    public static void elementVolley(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        ServerLevel level = player.serverLevel();
        for (int index = 0; index < 3; index++) {
            boolean ice = index != 1;
            Vec3 direction = rotateYaw(look, (index - 1) * 10.0F);
            StoriousSkills.BeamHit hit = StoriousSkills.beam(player, direction, 20.0D, 1.2D, 10.0F, true);
            for (LivingEntity target : hit.hits()) {
                if (ice) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2), player);
                    target.setTicksFrozen(Math.max(target.getTicksFrozen(), 160));
                }
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new DarkBeamPayload(
                    player.getId(), hit.origin(), hit.direction(), (float) hit.length(),
                    ice ? 3 : 4, 14));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.STONE_BREAK,
                SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private static Vec3 rotateYaw(Vec3 direction, float degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(direction.x * cos + direction.z * sin, direction.y,
                -direction.x * sin + direction.z * cos);
    }

    // ---------------------------------------------------------------- 强化跳跃

    /** 强化跳跃：沿视线方向长距离跃出，落地伤害已由干部体魄免疫。 */
    public static void enhancedJump(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(look.x * 1.45D, Math.max(0.55D, look.y * 0.6D + 0.55D), look.z * 1.45D);
        player.hurtMarked = true;
        player.resetFallDistance();

        ServerLevel level = player.serverLevel();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                player.getId(), "mutate_mist", player.position(),
                BookKaijinType.LEGEIEL.qiColor(), 1.4F, 18));
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP,
                SoundSource.PLAYERS, 0.7F, 1.6F);
    }

    // ---------------------------------------------------------------- 围巾攻防

    /** 围巾攻防：左肩的白色围巾向前扫开敌人，同时缠身护住自己。 */
    public static void scarf(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        int hitCount = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(5.0D), e -> e != player && e.isAlive())) {
            Vec3 offset = target.position().subtract(player.position());
            if (offset.lengthSqr() > 25.0D) {
                continue;
            }
            if (offset.lengthSqr() > 0.01D && offset.normalize().dot(look) < 0.15D) {
                continue;
            }
            target.hurt(player.damageSources().playerAttack(player), 9.0F);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), player);
            Vec3 push = target.position().subtract(player.position());
            if (push.lengthSqr() > 0.01D) {
                push = push.normalize().scale(0.7D);
                target.push(push.x, 0.3D, push.z);
                target.hurtMarked = true;
            }
            hitCount++;
        }

        // 围巾回防：短时间内获得抗性提升
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1), player);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                player.getId(), "mimic", player.position(), 0xF2F2F2, 2.4F, 30));
        if (hitCount > 0) {
            KaijinMana.award(player, "control", 8.0F, 30);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.0F, 1.5F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0F, 1.1F);
    }

    // ---------------------------------------------------------------- 攻击反弹

    /** 攻击反弹：进入反弹窗口，这段时间内任何来袭伤害都会被弹回攻击者。 */
    public static void activateReflect(ServerPlayer player) {
        REFLECT_UNTIL.put(player.getUUID(), player.level().getGameTime() + REFLECT_TICKS);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.bolhessed_reflect"), true);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                player.getId(), "mimic", player.position(), 0xFFE9A8, 2.2F, 24));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.8F, 1.7F);
    }

    public static boolean isReflecting(ServerPlayer player) {
        Long until = REFLECT_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    /** 把这次伤害原样弹回去：有攻击者就反伤并推开，没有实体来源就只抵消。 */
    public static void reflect(ServerPlayer player, DamageSource source, float amount) {
        Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        ServerLevel level = player.serverLevel();
        if (attacker instanceof LivingEntity living && living != player && living.isAlive()) {
            living.hurt(player.damageSources().thorns(player), Math.max(4.0F, amount));
            Vec3 push = living.position().subtract(player.position());
            if (push.lengthSqr() > 0.01D) {
                push = push.normalize().scale(0.6D);
                living.push(push.x, 0.35D, push.z);
                living.hurtMarked = true;
            }
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                    living.getId(), "mimic", living.position(), 0xFFE082, 1.5F, 16));
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                player.getId(), "mimic", player.position(), 0xFFF4CC, 2.0F, 18));
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 1.2F, 1.35F);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.bolhessed_reflected"), true);
    }
}
