package com.xiaoshi2022.kamenrider_kaijin_craft.skill;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.entity.StoriousCloneEntity;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.AntlerTendrilsPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.DarkBeamPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.KaijinAnimationPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.QiAuraPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.SkillCooldownPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinType;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.KaijinMana;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.Stomach;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModEntities;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
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
            case DEVOUR -> devour(player, type);
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
        Vec3 direction = player.getLookAngle();
        Vec3 eye = player.getEyePosition();
        // 沿视线一直射到「看得见的最远处」，撞墙则停在墙前
        Vec3 intended = eye.add(direction.scale(maxBlinkDistance(player)));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 endpoint = blockHit.getType() == HitResult.Type.MISS
                ? intended
                : blockHit.getLocation().subtract(direction.scale(1.5D));
        Vec3 destination = clingToGround(level, player, endpoint);

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
     * 把落点吸附到下方地面：向落点下方的方块做一次射线，
     * 命中就站在方块顶面，避免传送到半空或直接嵌进山体。
     */
    private static Vec3 clingToGround(ServerLevel level, ServerPlayer player, Vec3 point) {
        BlockHitResult ground = level.clip(new ClipContext(point.add(0.0D, 2.0D, 0.0D),
                point.add(0.0D, -BLINK_GROUND_SEARCH, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 feet = ground.getType() == HitResult.Type.MISS
                ? point
                : ground.getLocation().add(0.0D, 0.05D, 0.0D);
        // 落点顶部被卡住时往上抬，直到玩家的碰撞箱放得下
        AABB box = player.getBoundingBox().move(feet.subtract(player.position()));
        for (int attempt = 0; attempt < 4 && !level.noCollision(player, box); attempt++) {
            feet = feet.add(0.0D, 1.0D, 0.0D);
            box = box.move(0.0D, 1.0D, 0.0D);
        }
        return feet;
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
