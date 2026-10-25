package com.xiaoshi2022.kamenrider_kaijin_craft.race;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.entity.KaijinBookCoreEntity;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModAttachments;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 卡律布狄斯的「肚子维度」：被吞噬的玩家先扔进去等着，消化结束后击杀、结算掉落。
 * <p>
 * 普通生物在吞噬当场就被击杀并把掉落截取下来，和方块掉落一起等到消化结束再交给吞噬者
 * （肚子维度在没有玩家时区块很快卸载，事后回去找实体并不可靠）。只有玩家会真的进维度：
 * 一进来就会看到一本悬空的「怪人书核心」，在消化结束前打碎它就能逃回吞噬者身前，
 * 打不碎就被消化到点、连同背包一起被吞噬者收走。消化进度按玩家存进数据附件，下线回来仍会继续结算。
 */
public final class Stomach {
    /** 肚子维度（data/kamenrider_kaijin_craft/dimension/stomach.json）。 */
    public static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, KaijinMOD.id("stomach"));
    /** 消化时间：15 秒。 */
    public static final long DIGEST_TICKS = 300L;
    /** 肚子里的落点（基岩在 y=0，生物站在 y=1）。 */
    private static final BlockPos DROP_SPOT = new BlockPos(8, 1, 8);
    /** 核心的落点：玩家正前方几格，一进来就照面。 */
    private static final BlockPos CORE_SPOT = new BlockPos(8, 1, 12);

    private Stomach() {
    }

    public static @Nullable ServerLevel level(ServerPlayer player) {
        return player.server.getLevel(DIMENSION);
    }

    /**
     * 把吞噬到的玩家送进肚子，普通生物与方块掉落当场结算好等消化；没有可以吞的东西时不记录。
     *
     * @return 是否真的吞下了东西
     */
    public static boolean swallow(ServerPlayer player, List<LivingEntity> victims, List<ItemStack> blockDrops) {
        List<ItemStack> carried = new ArrayList<>(blockDrops);
        List<UUID> imprisoned = new ArrayList<>();
        ServerLevel stomach = level(player);

        for (LivingEntity victim : victims) {
            if (victim instanceof ServerPlayer victimPlayer) {
                // 记下被吞之前的位置，打碎核心后按这里送回去
                victimPlayer.setData(ModAttachments.STOMACH_ORIGIN,
                        StomachOrigin.of(victimPlayer.level(), victimPlayer.position()));
                if (stomach != null && sendToStomach(victimPlayer, stomach, imprisoned.size())) {
                    imprisoned.add(victimPlayer.getUUID());
                    continue;
                }
                victimPlayer.removeData(ModAttachments.STOMACH_ORIGIN);
            }
            // 普通生物当场击杀并截取掉落：肚子维度没玩家时区块很快卸载，
            // 事后按 UUID 再去肚子里把它找回来并不可靠，掉落就这样丢了
            carried.addAll(killAndCollect(victim, player));
        }
        if (imprisoned.isEmpty() && carried.isEmpty()) {
            return false;
        }

        long finishTick = player.level().getGameTime() + DIGEST_TICKS;
        List<StomachDigestion> list = new ArrayList<>(get(player));
        list.add(new StomachDigestion(List.copyOf(imprisoned), carried, finishTick));
        player.setData(ModAttachments.STOMACH, list);
        if (stomach != null && !imprisoned.isEmpty()) {
            spawnCore(stomach, player.getUUID(), imprisoned);
        }
        return true;
    }

    /** 到点的批次：结算肚子里的玩家、发放已截取的掉落、回满饱食度。 */
    public static void tick(ServerPlayer player) {
        List<StomachDigestion> list = get(player);
        if (list.isEmpty()) {
            return;
        }
        long now = player.level().getGameTime();
        List<StomachDigestion> remaining = new ArrayList<>();
        List<ItemStack> delivered = new ArrayList<>();
        boolean digested = false;

        for (StomachDigestion entry : list) {
            if (entry.finishTick() > now) {
                remaining.add(entry);
                continue;
            }
            digested = true;
            delivered.addAll(entry.drops());
            for (UUID id : entry.victims()) {
                ServerPlayer online = player.server.getPlayerList().getPlayer(id);
                if (online != null && online.isAlive()
                        && DIMENSION.equals(online.level().dimension())) {
                    delivered.addAll(killPlayer(online, player));
                }
            }
        }
        if (!digested) {
            return;
        }

        player.setData(ModAttachments.STOMACH, remaining);
        int count = deliver(player, delivered);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.devour_digested", count), true);
    }

    private static List<StomachDigestion> get(ServerPlayer player) {
        return new ArrayList<>(player.getData(ModAttachments.STOMACH));
    }

    private static boolean sendToStomach(ServerPlayer victim, ServerLevel stomach, int index) {
        double x = DROP_SPOT.getX() + 0.5D + (index % 2) * 1.5D;
        double z = DROP_SPOT.getZ() + 0.5D + (index / 2) * 1.5D;
        // 先把落点分块拉起来，否则换维度时实体可能落进未加载的区块里直接消失
        stomach.getChunk(BlockPos.containing(x, DROP_SPOT.getY(), z));
        // 玩家要靠打碎核心逃出去，所以不套禁移动的负面效果，只提示一句
        victim.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.stomach_enter"), false);
        Entity moved = victim.changeDimension(new DimensionTransition(stomach,
                new Vec3(x, DROP_SPOT.getY(), z), Vec3.ZERO, victim.getYRot(), victim.getXRot(),
                DimensionTransition.DO_NOTHING));
        return moved != null;
    }

    /** 在肚子里放一本「怪人书核心」，它只守这一批被吞的玩家。 */
    private static void spawnCore(ServerLevel stomach, UUID devourer, List<UUID> victims) {
        KaijinBookCoreEntity core = ModEntities.BOOK_CORE.get().create(stomach);
        if (core == null) {
            return;
        }
        core.setBatch(devourer, victims, (int) DIGEST_TICKS);
        // 先把分块拉起来，否则在未加载区块里 addFreshEntity 会落空
        stomach.getChunk(CORE_SPOT);
        core.moveTo(CORE_SPOT.getX() + 0.5D, CORE_SPOT.getY(), CORE_SPOT.getZ() + 0.5D, 0.0F, 0.0F);
        stomach.addFreshEntity(core);
    }

    /**
     * 核心被击碎：把被吞的玩家放回吞噬者身前的空地上（身前 6 格内）；
     * 吞噬者不在线时退回被吞之前的位置。
     */
    public static void escape(ServerPlayer victim, @Nullable UUID devourer) {
        StomachOrigin origin = victim.getData(ModAttachments.STOMACH_ORIGIN);
        victim.removeData(ModAttachments.STOMACH_ORIGIN);

        ServerPlayer owner = devourer == null ? null : victim.server.getPlayerList().getPlayer(devourer);
        if (owner != null && owner.level() instanceof ServerLevel target) {
            victim.changeDimension(new DimensionTransition(target, frontSpot(target, owner), Vec3.ZERO,
                    victim.getYRot(), victim.getXRot(), DimensionTransition.DO_NOTHING));
            return;
        }
        if (!origin.present()) {
            return;
        }
        ServerLevel target = victim.server.getLevel(origin.dimension());
        if (target == null) {
            return;
        }
        victim.changeDimension(new DimensionTransition(target, origin.pos(), Vec3.ZERO,
                victim.getYRot(), victim.getXRot(), DimensionTransition.DO_NOTHING));
    }

    /** 在吞噬者水平朝向前 2~6 格之间找一块能站人的地方，优先离得最远。 */
    private static Vec3 frontSpot(ServerLevel level, ServerPlayer owner) {
        Vec3 dir = Vec3.directionFromRotation(0.0F, owner.getYRot());
        for (int distance = 6; distance >= 2; distance--) {
            Vec3 spot = owner.position().add(dir.scale(distance));
            BlockPos pos = BlockPos.containing(spot);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                return spot;
            }
        }
        return owner.position().add(dir.scale(2.0D));
    }

    /** 逃出来的玩家从吞噬者的消化队列里划掉，免得消化到点再被算一次。 */
    public static void forget(ServerPlayer devourer, List<UUID> released) {
        List<StomachDigestion> updated = new ArrayList<>();
        for (StomachDigestion entry : get(devourer)) {
            List<UUID> remaining = new ArrayList<>(entry.victims());
            remaining.removeAll(released);
            if (remaining.isEmpty() && entry.drops().isEmpty()) {
                continue;
            }
            updated.add(new StomachDigestion(remaining, entry.drops(), entry.finishTick()));
        }
        devourer.setData(ModAttachments.STOMACH, updated);
    }

    /** 击杀被吞的玩家。玩家掉落不走 captureDrops，先搬空背包再死，避免物品掉在肚子里丢失。 */
    private static List<ItemStack> killPlayer(ServerPlayer victim, ServerPlayer devourer) {
        victim.removeData(ModAttachments.STOMACH_ORIGIN);
        List<ItemStack> drops = new ArrayList<>();
        Inventory inventory = victim.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            drops.add(stack.copy());
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        Level level = victim.level();
        Vec3 spot = victim.position();
        victim.hurt(devourer.damageSources().playerAttack(devourer), Float.MAX_VALUE);
        // 死亡时可能还有别的模组往地上丢东西，一并收走；只扫肚子维度，免得把野外散落物吸走
        if (DIMENSION.equals(level.dimension())) {
            collectGroundDrops(level, spot, drops);
        }
        return drops;
    }

    /** 击杀并截取掉落（不让它们掉在地上）。 */
    private static List<ItemStack> killAndCollect(LivingEntity victim, ServerPlayer player) {
        List<ItemEntity> captured = new ArrayList<>();
        victim.captureDrops(captured);
        // 玩家多半刚打过它，受击无敌帧还在，不重置的话这一下打不死、也就没有掉落
        victim.invulnerableTime = 0;
        victim.hurt(player.damageSources().playerAttack(player), Float.MAX_VALUE);
        victim.captureDrops(null);

        List<ItemStack> drops = new ArrayList<>();
        for (ItemEntity item : captured) {
            ItemStack stack = item.getItem();
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        return drops;
    }

    /** 把肚子维度里躺在地上的掉落物收走（有些模组不经过 spawnAtLocation，捕获不到）。 */
    private static void collectGroundDrops(Level level, Vec3 spot, List<ItemStack> drops) {
        AABB area = new AABB(spot.x - 3.0D, spot.y - 2.0D, spot.z - 3.0D,
                spot.x + 3.0D, spot.y + 3.0D, spot.z + 3.0D);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            ItemStack stack = item.getItem();
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
            item.discard();
        }
    }

    private static int deliver(ServerPlayer player, List<ItemStack> drops) {
        int count = 0;
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
            count++;
        }
        return count;
    }
}
