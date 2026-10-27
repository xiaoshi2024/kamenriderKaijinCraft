package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.skill;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.entity.StoriousCloneEntity;
import com.xiaoshi2022.kamenrider_kaijin_craft.event.ServerEvents;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.AntlerTendrilsPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.DarkBeamPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.KaijinAnimationPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.QiAuraPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.SkillCooldownPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinType;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.KaijinMana;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.Stomach;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
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
        BolhessedPowers.clear(playerId);
    }

    public static void use(ServerPlayer player, StoriousSkill skill) {
        // 拟态成人类时也保留技能：只要还是书之怪物种族即可施放
        if (!BookKaijinRace.get(player).bookKaijin()) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.skill_need_monster"), true);
            return;
        }
        BookKaijinType type = BookKaijinRace.typeOf(player);
        if (!skill.availableFor(type)) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.skill_wrong_form"), true);
            return;
        }
        // 号令死美：附近没有死美或没锁定目标时只给提示，不扣蓝也不进入冷却
        if (skill == StoriousSkill.COMMAND_SHIMI && !canCommandShimi(player)) {
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
        if (!KaijinMana.hasEnough(player, skill.manaCost)) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.mana_not_enough"), true);
            return;
        }
        cooldowns.put(skill, now + skill.cooldownTicks);
        PacketDistributor.sendToPlayer(player, new SkillCooldownPayload(skill.ordinal(), skill.cooldownTicks));
        boolean depleted = KaijinMana.consume(player, skill.manaCost);

        switch (skill) {
            case DARK_ARROW -> darkArrow(player);
            case BLINDING_RAY -> blindingRay(player);
            case ANTLER_TENDRILS -> antlerTendrils(player);
            case CLONES -> clones(player);
            case BLINK -> blink(player);
            case BLINK_GROUP -> blinkGroup(player);
            case DEVOUR -> devour(player, type);
            case DEVOUR_SPACE -> devourSpace(player, type);
            case BOLHESSED_ELEMENT -> BolhessedPowers.elementVolley(player);
            case BOLHESSED_JUMP -> BolhessedPowers.enhancedJump(player);
            case BOLHESSED_SCARF -> BolhessedPowers.scarf(player);
            case BOLHESSED_REFLECT -> BolhessedPowers.activateReflect(player);
            case COMMAND_SHIMI -> commandShimi(player);
        }

        // 蓝条见底：这一发技能照常结算，随后怪人道具爆裂
        if (depleted) {
            KaijinMana.explode(player);
        }
    }

    // ==================== 黑暗能量箭 ====================

    private static void darkArrow(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BeamHit hit = beam(player, 26.0D, 1.5D, 20.0F, true);
        for (LivingEntity target : hit.hits()) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), player);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), player);
        }

        // 光束尽头引爆：没被光束贯穿命中的敌人再吃一次范围伤害
        Vec3 impact = hit.origin().add(hit.direction().scale(hit.length()));
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(impact, impact).inflate(2.6D), e -> e != player && e.isAlive())) {
            if (hit.hits().contains(target)) {
                continue;
            }
            target.hurt(player.damageSources().playerAttack(player), 10.0F);
            Vec3 push = target.position().subtract(impact);
            if (push.lengthSqr() > 0.01D) {
                push = push.normalize().scale(0.6D);
                target.push(push.x, 0.35D, push.z);
                target.hurtMarked = true;
            }
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new DarkBeamPayload(
                player.getId(), hit.origin(), hit.direction(), (float) hit.length(), 0, 12));
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.7F, 1.6F);
    }

    // ==================== 失明光线 ====================

    private static void blindingRay(ServerPlayer player) {
        BeamHit hit = beam(player, 20.0D, 1.4D, 9.0F, false);
        for (LivingEntity target : hit.hits()) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 240, 0), player);
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0), player);
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0), player);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1), player);
            target.push(hit.direction().x * 0.5D, 0.2D, hit.direction().z * 0.5D);
            target.hurtMarked = true;
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new DarkBeamPayload(
                player.getId(), hit.origin(), hit.direction(), (float) hit.length(), 1, 12));
        // 剧情交互：用致盲光线命中对手 → 叙事回蓝
        if (!hit.hits().isEmpty()) {
            KaijinMana.award(player, "control", 8.0F, 30);
        }
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
                player.getBoundingBox().inflate(7.0D), e -> e != player && e.isAlive())) {
            Vec3 offset = target.position().subtract(player.position());
            if (offset.lengthSqr() > 49.0D) {
                continue;
            }
            if (offset.lengthSqr() > 0.01D && offset.normalize().dot(look) < 0.3D) {
                continue;
            }
            targets.add(target);
            if (targets.size() >= 5) {
                break;
            }
        }

        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().playerAttack(player), 8.0F);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 2), player);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1), player);
            Vec3 pull = player.position().add(0.0D, 0.2D, 0.0D).subtract(target.position());
            if (pull.lengthSqr() > 0.01D) {
                pull = pull.normalize().scale(0.5D);
                target.push(pull.x, 0.18D, pull.z);
                target.hurtMarked = true;
            }
        }

        if (!targets.isEmpty()) {
            // 剧情交互：用触手控住对手 → 叙事回蓝
            KaijinMana.award(player, "control", 8.0F, 30);
            int[] ids = targets.stream().mapToInt(Entity::getId).toArray();
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    new AntlerTendrilsPayload(player.getId(), ids, 40));
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
            clone.setLife(240);
            level.addFreshEntity(clone);
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                player.getId(), "mimic", player.position(), BookKaijinRace.QI_COLOR, 1.5F, 25));
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    // ==================== 瞬间传送 ====================

    /** 落点向下找地面的最大搜索深度。 */
    private static final double BLINK_GROUND_SEARCH = 48.0D;

    private static void blink(ServerPlayer player) {
        // 潜行 + 瞬移：不看向哪里，直接撕开空间回到出生点
        if (player.isShiftKeyDown()) {
            blinkToSpawn(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 start = player.position();
        // 身后有怪正盯着自己时用瞬移脱离，算一次「躲开攻击」的剧情交互
        boolean threatened = !level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8.0D),
                mob -> mob.isAlive() && mob.getTarget() == player).isEmpty();
        Vec3 destination = computeBlinkDestination(level, player);
        // 落点没变说明周围全是实心，撕不开空间
        if (destination.distanceToSqr(start) < 0.25D) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.blink_blocked"), true);
            return;
        }

        player.teleportTo(destination.x, destination.y, destination.z);
        player.resetFallDistance();

        // 落点撕裂：范围内的敌人受到伤害并被推离
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(3.0D), e -> e != player && e.isAlive())) {
            target.hurt(player.damageSources().playerAttack(player), 10.0F);
            Vec3 push = target.position().subtract(destination);
            if (push.lengthSqr() > 0.01D) {
                push = push.normalize().scale(0.8D);
                target.push(push.x, 0.4D, push.z);
                target.hurtMarked = true;
            }
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", start, BookKaijinRace.QI_COLOR, 1.6F, 25));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", destination, BookKaijinRace.QI_COLOR, 1.6F, 25));
        if (threatened) {
            KaijinMana.award(player, "dodge", 10.0F, 60);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    /** 瞬移距离跟着客户端渲染距离走，最远刚好贴住「看得见」的边界。 */
    private static double maxBlinkDistance(ServerPlayer player) {
        int chunks = Mth.clamp(player.requestedViewDistance(), 4, 16);
        return chunks * 16.0D - 16.0D;
    }

    /**
     * 天空保护：瞬移落点的 Y 坐标不能比玩家当前位置高出 1.5 格以上。
     * <p>
     * 没有这个上限时，朝天空看会把 intended 射到几格远的空中，
     * {@link #clingToGround} 下搜 48 格找不到地面就直接返回空中，
     * 玩家瞬移后再掉下来摔死。
     */
    private static final double BLINK_MAX_HEIGHT_ABOVE_SOURCE = 1.5D;

    /**
     * 瞬移落点的共享计算：沿视线射到最远处 / 撞墙停下，
     * 限制 Y 不超过玩家头顶 1.5 格，再吸附到下方地面。
     * <p>
     * 单人 {@link #blink} 和群体 {@link #blinkGroup} 都用这个方法，
     * 保证落点逻辑完全一致，不会一个修了另一个忘了。
     */
    private static Vec3 computeBlinkDestination(ServerLevel level, ServerPlayer player) {
        double maxDistance = maxBlinkDistance(player);
        // 撞墙 / 嵌进山体时距离逐级缩短重试，直到找到碰撞箱放得下的落点
        for (double factor = 1.0D; factor > 0.0D; factor -= 0.25D) {
            Vec3 candidate = blinkCandidate(level, player, maxDistance * factor);
            if (candidate != null) {
                return candidate;
            }
        }
        // 全部失败：留在原地，宁可不传送也不能把人塞进墙里
        return player.position();
    }

    /** 按指定距离算出一个候选落点；落点被墙吞掉时返回 null。 */
    private static Vec3 blinkCandidate(ServerLevel level, ServerPlayer player, double distance) {
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getLookAngle();
        Vec3 intended = eye.add(direction.scale(distance));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 endpoint = blockHit.getType() == HitResult.Type.MISS
                ? intended
                : blockHit.getLocation().subtract(direction.scale(1.5D));
        // 天空保护：Y 上限拍平，防止看向天空时落到半空
        endpoint = new Vec3(endpoint.x, Math.min(endpoint.y, player.getY() + BLINK_MAX_HEIGHT_ABOVE_SOURCE), endpoint.z);
        return findSafeGround(level, player, endpoint);
    }

    /**
     * 把落点吸附到下方地面，并确认实体的整个碰撞箱放得下。
     * <p>
     * 下方 48 格内没有方块（深渊 / 虚空）直接判为不安全；
     * 碰撞箱放不下时先向上抬最多 4 格，仍然放不下也返回 null，
     * 由调用方决定是缩短距离重试还是干脆不传送。
     */
    private static Vec3 findSafeGround(ServerLevel level, Entity entity, Vec3 point) {
        Vec3 feet = snapToGround(level, entity, point);
        if (feet == null) {
            return null;
        }
        AABB box = collisionBoxAt(entity, feet);
        for (int attempt = 0; attempt < 4 && !level.noCollision(entity, box); attempt++) {
            feet = feet.add(0.0D, 1.0D, 0.0D);
            box = box.move(0.0D, 1.0D, 0.0D);
        }
        return level.noCollision(entity, box) ? feet : null;
    }

    /** 向下打一条射线吸附地面；下方 48 格都没有方块时返回 null，不采用空中落点。 */
    private static Vec3 snapToGround(ServerLevel level, Entity entity, Vec3 point) {
        BlockHitResult ground = level.clip(new ClipContext(point.add(0.0D, 2.0D, 0.0D),
                point.add(0.0D, -BLINK_GROUND_SEARCH, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        return ground.getType() == HitResult.Type.MISS
                ? null
                : ground.getLocation().add(0.0D, 0.05D, 0.0D);
    }

    /** 实体的碰撞箱平移到某个落脚点后的样子，用于碰撞校验。 */
    private static AABB collisionBoxAt(Entity entity, Vec3 feet) {
        return entity.getBoundingBox().move(feet.subtract(entity.position()));
    }

    /**
     * 把落点吸附到下方地面；找不到安全位置时退回实体原地。
     * 出生点瞬移、吞噬空间、拽猎物等旧逻辑继续使用这个方法。
     */
    private static Vec3 clingToGround(ServerLevel level, Entity entity, Vec3 point) {
        Vec3 safe = findSafeGround(level, entity, point);
        return safe == null ? entity.position() : safe;
    }

    /** 潜行瞬移：回到床 / 重生锚记录的重生点，没有记录则回到主世界出生点。 */
    private static void blinkToSpawn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ResourceKey<Level> dimension = player.getRespawnDimension();
        BlockPos respawn = player.getRespawnPosition();
        ServerLevel targetLevel = dimension == null ? null : server.getLevel(dimension);
        if (targetLevel == null || respawn == null) {
            targetLevel = server.overworld();
            respawn = targetLevel.getSharedSpawnPos();
        }

        ServerLevel sourceLevel = player.serverLevel();
        Vec3 start = player.position();
        Vec3 destination = clingToGround(targetLevel, player, Vec3.atBottomCenterOf(respawn));

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", start, BookKaijinRace.QI_COLOR, 1.6F, 25));
        sourceLevel.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 0.6F);

        player.stopRiding();
        player.teleportTo(targetLevel, destination.x, destination.y, destination.z,
                player.getYRot(), player.getXRot());
        player.resetFallDistance();

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", destination, BookKaijinRace.QI_COLOR, 1.6F, 25));
        targetLevel.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.blink_spawn"), true);
    }

    // ==================== 群体传送 ====================

    /** 群体传送半径：以施法者为中心 3 格内的活物一起带走。 */
    private static final double BLINK_GROUP_RADIUS = 3.0D;

    /**
     * 群体传送：自己先瞬移到目标落点，再把半径内的活物按各自相对偏移传过去。
     * <p>
     * 每个被传送的实体都会经过地面吸附 + {@link Entity#resetFallDistance()}，
     * 落地时不会摔飞，也不会受到任何伤害或击退（自己也不会）——
     * 纯位移技能，用来救队友或一次性换位置。
     */
    private static void blinkGroup(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 start = player.position();
        Vec3 destination = computeBlinkDestination(level, player);
        if (destination.distanceToSqr(start) < 0.25D) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.blink_blocked"), true);
            return;
        }

        List<LivingEntity> companions = new ArrayList<>();
        List<Vec3> offsets = new ArrayList<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(BLINK_GROUP_RADIUS),
                e -> e != player && e.isAlive() && !isBlinkGroupExcluded(e))) {
            companions.add(target);
            offsets.add(target.position().subtract(start));
        }

        // 先把自己传过去
        player.teleportTo(destination.x, destination.y, destination.z);
        player.resetFallDistance();

        // 同伴按相对偏移摆放：偏移把人塞进墙里就向施法者收拢，实在放不下就留在原地
        int moved = 0;
        for (int i = 0; i < companions.size(); i++) {
            LivingEntity target = companions.get(i);
            Vec3 safeDest = companionDestination(level, target, destination, offsets.get(i));
            if (safeDest == null) {
                continue;
            }
            target.stopRiding();
            target.teleportTo(safeDest.x, safeDest.y, safeDest.z);
            target.resetFallDistance();
            moved++;
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", start, BookKaijinRace.QI_COLOR, 1.6F, 25));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", destination, BookKaijinRace.QI_COLOR, 1.6F, 25));
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        if (moved > 0) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.blink_group", moved), true);
        }
    }

    /**
     * 同伴落点：先按原相对偏移摆放，卡墙时把偏移按 1.0 → 0.5 向施法者收拢；
     * 都不安全就返回 null，让这名同伴留在原地，绝不塞进墙里。
     */
    private static Vec3 companionDestination(ServerLevel level, Entity entity,
                                             Vec3 destination, Vec3 offset) {
        for (double factor = 1.0D; factor > 0.0D; factor -= 0.5D) {
            Vec3 safe = findSafeGround(level, entity, destination.add(offset.scale(factor)));
            if (safe != null) {
                return safe;
            }
        }
        return null;
    }

    /** 群体传送排除创造 / 旁观模式玩家（不能把管理员强行带走）。 */
    private static boolean isBlinkGroupExcluded(LivingEntity target) {
        return target instanceof Player p && (p.isCreative() || p.isSpectator());
    }

    // ==================== 号令死美（米吉多干部） ====================

    /** 号令半径：这个范围内的死美都会听令。 */
    private static final double SHIMI_COMMAND_RADIUS = 24.0D;
    /** 目标必须在视线前方这个夹角内，避免随手一指就全军扑错方向。 */
    private static final double SHIMI_COMMAND_CONE = 0.5D;
    /** 一次最多指挥几只，防止一群死美被一口气拉成无限军队。 */
    private static final int SHIMI_COMMAND_LIMIT = 8;

    /**
     * 号令死美：把附近死美的仇恨统一换成视线前方的目标。
     * <p>
     * 只认假面骑士工艺的 {@code kamenridercraft:shimi}，不碰别的模组的怪；
     * 一次最多 8 只，且必须先锁定目标，失败不扣蓝不进入冷却。
     */
    private static void commandShimi(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LivingEntity target = shimiCommandTarget(level, player);
        if (target == null) {
            return;
        }
        List<Mob> shimis = new ArrayList<>(level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(SHIMI_COMMAND_RADIUS), ServerEvents::isKrcShimiEntity));
        shimis.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
        int commanded = 0;
        for (Mob mob : shimis) {
            if (commanded >= SHIMI_COMMAND_LIMIT) {
                break;
            }
            mob.setTarget(target);
            commanded++;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR,
                SoundSource.PLAYERS, 0.7F, 1.4F);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.command_shimi", commanded, target.getDisplayName()), true);
    }

    /** 号令的前置检查：没有死美 / 没有目标时只提示，不消耗资源。 */
    private static boolean canCommandShimi(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        boolean hasShimi = !level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(SHIMI_COMMAND_RADIUS), ServerEvents::isKrcShimiEntity).isEmpty();
        if (!hasShimi) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.command_shimi_no_shimi"), true);
            return false;
        }
        if (shimiCommandTarget(level, player) == null) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.command_shimi_no_target"), true);
            return false;
        }
        return true;
    }

    /**
     * 视线前方最近的活物：死美自己不算（不指挥自己人打自己人），
     * 创造 / 旁观玩家也不算（打了也没用）。
     */
    private static LivingEntity shimiCommandTarget(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SHIMI_COMMAND_RADIUS),
                e -> e != player && e.isAlive()
                        && !ServerEvents.isKrcShimiEntity(e)
                        && !(e instanceof Player other && (other.isCreative() || other.isSpectator())))) {
            Vec3 offset = candidate.position().subtract(player.position());
            double distance = offset.length();
            if (distance >= bestDistance || offset.normalize().dot(look) < SHIMI_COMMAND_CONE) {
                continue;
            }
            best = candidate;
            bestDistance = distance;
        }
        return best;
    }

    // ==================== 吞噬（卡律布狄斯） ====================

    /** 吞噬的判定距离与最多同时吞下的敌人数。 */
    private static final double DEVOUR_RANGE = 3.6D;
    private static final int DEVOUR_MAX_TARGETS = 4;
    /** 一次最多吞掉的方块数。 */
    private static final int DEVOUR_MAX_BLOCKS = 16;

    /**
     * 吞噬：前方近处的生物和方块一起被吞掉。
     * <p>
     * 生物被送进肚子维度等消化，消化结束后击杀、掉落物入袋并回满饱食度；
     * 方块当场结算掉落。自身同时被黏液盖住，并播放 eat 动作。
     */
    private static void devour(ServerPlayer player, BookKaijinType type) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        List<LivingEntity> victims = new ArrayList<>();
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(DEVOUR_RANGE),
                e -> e != player && e.isAlive() && !isUnswallowable(e))) {
            Vec3 offset = target.position().subtract(player.position());
            if (offset.lengthSqr() > DEVOUR_RANGE * DEVOUR_RANGE) {
                continue;
            }
            if (offset.lengthSqr() > 0.01D && offset.normalize().dot(look) < 0.35D) {
                continue;
            }
            victims.add(target);
            if (victims.size() >= DEVOUR_MAX_TARGETS) {
                break;
            }
        }

        List<ItemStack> blockDrops = new ArrayList<>();
        int eatenBlocks = eatBlocks(player, look, blockDrops);

        if (victims.isEmpty() && eatenBlocks == 0) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.devour_empty"), true);
            return;
        }

        for (LivingEntity target : victims) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                    -1, "slime_splash", target.position(), type.qiColor(), 1.2F, 30));
        }
        Stomach.swallow(player, victims, blockDrops);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.devour_swallowed", victims.size(), eatenBlocks), true);
        // 剧情交互：吞噬敌人 → 叙事回蓝
        KaijinMana.award(player, "damage", 8.0F, 40);

        // 自身被黏液盖住，配合 eat 动作
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                player.getId(), "slime_engulf", player.position(), type.qiColor(), 1.5F, 42));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new KaijinAnimationPayload(player.getId(), "eat", 45));
        level.playSound(null, player.blockPosition(), SoundEvents.SLIME_ATTACK,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.9F, 0.8F);
    }

    /** 吞不掉创造/旁观模式的玩家：既杀不死，也不该把管理员关进肚子里。 */
    private static boolean isUnswallowable(LivingEntity target) {
        return target instanceof Player player && (player.isCreative() || player.isSpectator());
    }

    /** 吞掉正前方一小片方块，返回吞掉的方块数，掉落放进 drops。 */
    private static int eatBlocks(ServerPlayer player, Vec3 look, List<ItemStack> drops) {
        ServerLevel level = player.serverLevel();
        BlockPos center = BlockPos.containing(player.position().add(look.scale(1.8D)));
        int eaten = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            if (eaten >= DEVOUR_MAX_BLOCKS) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()
                    || state.getDestroySpeed(level, pos) < 0.0F) {
                continue;
            }
            LootParams.Builder loot = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, player.getMainHandItem())
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, player);
            drops.addAll(state.getDrops(loot));
            level.destroyBlock(pos.immutable(), false);
            eaten++;
        }
        return eaten;
    }

    // ==================== 吞噬空间（卡律布狄斯） ====================

    /** 吞噬空间的搜索 / 赶路距离。 */
    private static final double SPACE_GRIND_RANGE = 20.0D;
    /** 贴到猎物身前时留出的空隙，避免落点卡进对方身体。 */
    private static final double SPACE_GRIND_GAP = 1.1D;
    /** 视线锥：只磨削正前方一条走廊里的目标。 */
    private static final double SPACE_GRIND_CONE = 0.55D;

    /**
     * 吞噬空间：把正前方那一截空间整个磨掉。
     * <p>
     * 前方有生物时，它和自己之间的空间被啃穿，目标整个被拽到面前；
     * 没有目标时磨掉自己正前方的空间，用来直线赶路。
     * 途中的方块仍旧阻挡，落点复用瞬移的地面吸附逻辑。
     */
    private static void devourSpace(ServerPlayer player, BookKaijinType type) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        LivingEntity prey = nearestAhead(level, player, look);

        if (prey != null) {
            pullPrey(player, level, type, prey, look);
            return;
        }

        Vec3 start = player.position();
        Vec3 intended = player.getEyePosition().add(look.scale(SPACE_GRIND_RANGE));
        Vec3 destination = grindEndpoint(level, player, intended);
        if (destination.distanceToSqr(start) < 0.25D) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.devour_space_blocked"), true);
            return;
        }

        grindMarks(player, type, start, destination);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", start, type.qiColor(), 1.6F, 22));

        player.teleportTo(destination.x, destination.y, destination.z);
        player.resetFallDistance();

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", destination, type.qiColor(), 1.6F, 22));
        finishGrind(player, level);
    }

    /** 磨掉猎物与自身之间的空间，把它整个拽到面前。 */
    private static void pullPrey(ServerPlayer player, ServerLevel level, BookKaijinType type,
                                 LivingEntity prey, Vec3 look) {
        Vec3 from = prey.position();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);
        forward = forward.lengthSqr() > 1.0E-4D
                ? forward.normalize()
                : new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 point = player.position().add(forward.scale(1.2D
                + (player.getBbWidth() + prey.getBbWidth()) * 0.5D));
        Vec3 destination = clingToGround(level, prey, point);

        prey.stopRiding();
        prey.teleportTo(destination.x, destination.y, destination.z);
        prey.resetFallDistance();
        prey.hurtMarked = true;

        grindMarks(player, type, from, destination);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", from, type.qiColor(), 1.6F, 22));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                -1, "mutate_mist", destination, type.qiColor(), 1.6F, 22));
        finishGrind(player, level);
    }

    /** 被磨掉的那段空间上留下一串紫色裂口。 */
    private static void grindMarks(ServerPlayer player, BookKaijinType type, Vec3 from, Vec3 to) {
        Vec3 span = to.subtract(from);
        int marks = Mth.clamp((int) Math.round(span.length() / 3.0D), 1, 6);
        for (int index = 1; index <= marks; index++) {
            Vec3 point = from.add(span.scale(index / (double) (marks + 1)));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                    -1, "slime_splash", point, type.qiColor(), 1.15F, 18));
        }
    }

    /** 收尾：eat 动作 + 空间被啃穿的音效。 */
    private static void finishGrind(ServerPlayer player, ServerLevel level) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new KaijinAnimationPayload(player.getId(), "eat", 35));
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, player.blockPosition(), SoundEvents.SLIME_ATTACK,
                SoundSource.PLAYERS, 0.9F, 0.6F);
    }

    /** 正前方视线锥内最近的生物；贴脸的目标不算，避免「原地磨削」。 */
    private static LivingEntity nearestAhead(ServerLevel level, ServerPlayer player, Vec3 look) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SPACE_GRIND_RANGE),
                e -> e != player && e.isAlive()
                        && !(e instanceof Player other && other.isSpectator()))) {
            Vec3 offset = target.position().subtract(player.position());
            double distance = offset.length();
            if (distance < SPACE_GRIND_GAP + 1.0D || distance > SPACE_GRIND_RANGE
                    || distance >= bestDistance) {
                continue;
            }
            if (offset.normalize().dot(look) < SPACE_GRIND_CONE) {
                continue;
            }
            best = target;
            bestDistance = distance;
        }
        return best;
    }

    /** 沿被磨掉的方向取落点：撞墙就停在墙前，最后吸附到地面。 */
    private static Vec3 grindEndpoint(ServerLevel level, ServerPlayer player, Vec3 intended) {
        Vec3 eye = player.getEyePosition();
        Vec3 delta = intended.subtract(eye);
        if (delta.lengthSqr() < 1.0E-6D) {
            return player.position();
        }
        BlockHitResult blockHit = level.clip(new ClipContext(eye, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 endpoint = blockHit.getType() == HitResult.Type.MISS
                ? intended
                : blockHit.getLocation().subtract(delta.normalize().scale(1.0D));
        return clingToGround(level, player, endpoint);
    }

    // ==================== 光束结算 ====================

    record BeamHit(Vec3 origin, Vec3 direction, double length, List<LivingEntity> hits) {
    }

    static BeamHit beam(ServerPlayer player, double maxLength, double radius, float damage, boolean knockback) {
        return beam(player, player.getLookAngle(), maxLength, radius, damage, knockback);
    }

    /**
     * 沿指定方向结算一条瞬时贯穿光束。
     * 波尔赫斯德的剑气与冰岩弹幕会传入自己的方向，其余技能沿用玩家的视线。
     */
    static BeamHit beam(ServerPlayer player, Vec3 aim, double maxLength, double radius,
                        float damage, boolean knockback) {
        return beam(player, aim, maxLength, radius, damage, knockback, false);
    }

    /**
     * 同上，{@code shatter} 为 true 时沿途把玻璃打碎并继续向前，
     * 剑气与冰岩弹幕靠它做到「远程技能能破玻璃」。
     */
    static BeamHit beam(ServerPlayer player, Vec3 aim, double maxLength, double radius,
                        float damage, boolean knockback, boolean shatter) {
        ServerLevel level = player.serverLevel();
        Vec3 direction = aim.normalize();
        Vec3 origin = player.getEyePosition().add(direction.scale(0.35D));
        double length = shatter
                ? shatterLength(level, player, origin, direction, maxLength)
                : solidLength(level, player, origin, direction, maxLength);
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

    /** 遇到第一个实心方块就停下的普通射线长度。 */
    private static double solidLength(ServerLevel level, ServerPlayer player,
                                      Vec3 origin, Vec3 direction, double maxLength) {
        BlockHitResult blockHit = level.clip(new ClipContext(origin, origin.add(direction.scale(maxLength)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return blockHit.getType() == HitResult.Type.MISS
                ? maxLength
                : origin.distanceTo(blockHit.getLocation());
    }

    /**
     * 会打碎玻璃的射线长度：沿射线每 0.1 格采样，
     * 碰到玻璃就砸掉继续前进，碰到第一个实心方块才停下。
     */
    private static double shatterLength(ServerLevel level, ServerPlayer player,
                                        Vec3 origin, Vec3 direction, double maxLength) {
        boolean sounded = false;
        for (double travelled = 0.0D; travelled <= maxLength; travelled += 0.1D) {
            BlockPos pos = BlockPos.containing(origin.add(direction.scale(travelled)));
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (isShatterable(state)) {
                level.destroyBlock(pos, false, player);
                if (!sounded) {
                    sounded = true;
                    level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
                }
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                        pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                        10, 0.3D, 0.3D, 0.3D, 0.08D);
                continue;
            }
            if (!state.getCollisionShape(level, pos).isEmpty()) {
                return travelled;
            }
        }
        return maxLength;
    }

    /** 玻璃类方块：普通玻璃、染色玻璃、遮光玻璃以及各种玻璃板。 */
    private static boolean isShatterable(BlockState state) {
        Block block = state.getBlock();
        return block instanceof TransparentBlock
                || block instanceof StainedGlassPaneBlock
                || state.is(Blocks.GLASS_PANE);
    }

    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        double t = lengthSqr < 1.0E-9D ? 0.0D : point.subtract(start).dot(segment) / lengthSqr;
        t = Math.max(0.0D, Math.min(1.0D, t));
        return point.distanceToSqr(start.add(segment.scale(t)));
    }
}
