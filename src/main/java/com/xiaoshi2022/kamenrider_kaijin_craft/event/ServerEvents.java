package com.xiaoshi2022.kamenrider_kaijin_craft.event;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.inventory.FusionSlotContainer;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.race.AmazonRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinType;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.KaijinMana;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.Stomach;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModAttachments;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModDataComponents;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModItems;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.skill.BolhessedPowers;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.skill.StoriousSkills;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.shocker.race.ShockerRace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 变异道具是书之怪物的「心脏」：每半秒检查一次玩家是否还持有它，
 * 被销毁 / 丢弃 / 清空后立即退回人类种族。
 * <p>
 * 同时负责与「假面骑士工艺」(kamenridercraft) 的软联动：只用物品 ID / 类名判断，
 * 没装那个模组时不会加载它的任何类，也不会生效。
 */
@EventBusSubscriber(modid = KaijinMOD.MOD_ID)
public final class ServerEvents {
    private static final int CHECK_INTERVAL = 10;

    /** 假面骑士工艺里的「斯特利乌斯魔改驾驭书」。 */
    private static final ResourceLocation KRC_STORIOUS_BOOK =
            ResourceLocation.fromNamespaceAndPath("kamenridercraft", "storious_alter_ride_book");
    private static final String KRC_PACKAGE_PREFIX = "com.kelco.kamenridercraft.";
    private static final String KRC_STORIOUS_PREFIX = "Storious";
    /** 假面骑士工艺里的杂兵「死美」(shimi)。 */
    private static final ResourceLocation KRC_SHIMI =
            ResourceLocation.fromNamespaceAndPath("kamenridercraft", "shimi");
    private static final String KRC_SHIMI_PREFIX = "Shimi";
    /** 假面骑士工艺里的「亚马逊细胞突变器」。 */
    private static final ResourceLocation KRC_AMAZON_CELL_MUTATOR =
            ResourceLocation.fromNamespaceAndPath("kamenridercraft", "amazon_cell_mutator");
    /** 假面骑士工艺里的「亚马逊细胞瓶」。 */
    private static final ResourceLocation KRC_AMAZON_CELL_VIAL =
            ResourceLocation.fromNamespaceAndPath("kamenridercraft", "amazon_cell_vial");
    /** 清掉旧仇恨的搜索半径。 */
    private static final double AGGRO_CLEAR_RADIUS = 48.0D;

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 变身气焰先行：每 tick 检查一次，气焰铺完才真正完成变异
        BookKaijinRace.tickPendingMutation(player);
        AmazonRace.tickPending(player);
        AmazonRace.tickPassive(player);
        if (player.tickCount % CHECK_INTERVAL == 0) {
            BookKaijinRace.validateMutationSource(player);
            BookKaijinRace.syncAttributes(player);
            AmazonRace.validateCell(player);
            AmazonRace.syncAttributes(player);
            ShockerRace.validateEmblem(player);
            clearKrcAggro(player);
            Stomach.tick(player);
        }
    }

    /** 干部体魄：怪人形态下免疫摔落伤害。 */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player
                && (BookKaijinRace.isMonster(player) || AmazonRace.isMonster(player)
                || ShockerRace.isMonster(player))) {
            event.setCanceled(true);
        }
    }

    /**
     * 叙事回蓝：书之怪物每一次「造成伤害」都算推进剧情，回 5 点（同来源 1 秒冷却）。
     * 只认玩家自己打出的伤害，环境伤害或持续伤害不会刷。
     */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            KaijinMana.award(player, "damage", 5.0F, 20);
        }
    }

    /**
     * 拉结尔的攻击反弹：反弹窗口内，任何带来源的攻击都由波尔赫斯德原样弹回。
     * 摔落、岩浆、窒息这类无来源伤害不算「攻击」，仍然照常结算。
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !BolhessedPowers.isReflecting(player)) {
            return;
        }
        if (event.getSource().getEntity() == null && event.getSource().getDirectEntity() == null) {
            return;
        }
        event.setCanceled(true);
        BolhessedPowers.reflect(player, event.getSource(), event.getAmount());
    }

    /** 玩家下线时清掉技能冷却缓存 */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 气焰阶段下线：直接补完变身，避免下次登录时卡在未变身状态
            BookKaijinRace.completePendingMutation(player);
            AmazonRace.completePendingMutation(player);
            AmazonRace.clear(player.getUUID());
            StoriousSkills.clear(player.getUUID());
            KaijinMana.clear(player.getUUID());
        }
    }

    // ==================== 假面骑士工艺软联动 ====================

    /**
     * 右键「斯特利乌斯魔改驾驭书」时，就地把它软替换成我们模组的类驾驭书：
     * 认主 → 收进融合槽 → 变异，和直接翻我们自己的书是同一条流程。
     * 物品只用 ID 判断，所以没装假面骑士工艺时这段代码根本不会触发。
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!isKrcStoriousBook(stack)) {
            return;
        }
        // 已经是书之怪物：这本留给假面骑士工艺自己的 Boss 方块合成
        if (BookKaijinRace.get(player).bookKaijin()) {
            return;
        }
        if (!player.getData(ModAttachments.FUSION_ITEM).isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.kamenrider_kaijin_craft.fusion_occupied"), true);
            return;
        }

        ItemStack book = new ItemStack(ModItems.STORIOUS_BOOK.get());
        book.set(ModDataComponents.BOOK_OWNER.get(), player.getUUID());
        book.set(ModDataComponents.BOOK_OWNER_NAME.get(), player.getGameProfile().getName());
        if (stack.getCount() <= 1) {
            player.setItemInHand(event.getHand(), ItemStack.EMPTY);
        } else {
            stack.shrink(1);
        }
        // setItem 会触发 onFused → 翻开书、变异
        new FusionSlotContainer(player).setItem(0, book);

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /**
     * 软联动：在假面骑士工艺的「亚马逊细胞突变器」上右键「亚马逊细胞瓶」，
     * 就地把它改写成我们模组的溶原性细胞，从而走「感染 → 按 G 变异」的流程。
     * 方块与物品都只用 ID 判断，没装假面骑士工艺时这段代码不会触发。
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!isKrcAmazonCellVial(stack)) {
            return;
        }
        if (!isKrcAmazonCellMutator(event.getLevel().getBlockState(event.getPos()).getBlock())) {
            return;
        }

        if (stack.getCount() <= 1) {
            player.setItemInHand(event.getHand(), ItemStack.EMPTY);
        } else {
            stack.shrink(1);
        }
        ItemStack cell = new ItemStack(ModItems.LYSOGENIC_CELL.get());
        if (!player.getInventory().add(cell)) {
            player.drop(cell, false);
        }
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.amazon_cell_mutated"), true);

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** 假面骑士工艺的斯特利乌斯怪人把怪人形态的玩家当同类，不再主动攻击。 */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)
                || !BookKaijinRace.isMonster(player)) {
            return;
        }
        if (isKrcStoriousEntity(event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        // 斯特利乌斯 / 拉结尔是米吉多干部，死美不会对他们动手
        if (isMegiddoGeneral(player) && isKrcShimiEntity(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** 变身瞬间可能已经有怪锁定玩家：半秒一次清掉这些旧仇恨。 */
    private static void clearKrcAggro(ServerPlayer player) {
        if (!BookKaijinRace.isMonster(player)) {
            return;
        }
        boolean general = isMegiddoGeneral(player);
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(AGGRO_CLEAR_RADIUS), mob -> mob.getTarget() == player)) {
            if (isKrcStoriousEntity(mob) || (general && isKrcShimiEntity(mob))) {
                mob.setTarget(null);
            }
        }
    }

    /**
     * 斯特利乌斯与拉结尔是米吉多干部，死美视他们为上司；
     * 卡律布狄斯只是书之怪人，不在这个豁免名单里。
     */
    private static boolean isMegiddoGeneral(ServerPlayer player) {
        if (!BookKaijinRace.isMonster(player)) {
            return false;
        }
        BookKaijinType type = BookKaijinRace.typeOf(player);
        return type == BookKaijinType.STORIOUS || type == BookKaijinType.LEGEIEL;
    }

    private static boolean isKrcStoriousBook(ItemStack stack) {
        return !stack.isEmpty() && KRC_STORIOUS_BOOK.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static boolean isKrcAmazonCellVial(ItemStack stack) {
        return !stack.isEmpty() && KRC_AMAZON_CELL_VIAL.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private static boolean isKrcAmazonCellMutator(Block block) {
        return KRC_AMAZON_CELL_MUTATOR.equals(BuiltInRegistries.BLOCK.getKey(block));
    }

    /** 只用类名判断，避免在没装假面骑士工艺时加载它的类。 */
    private static boolean isKrcStoriousEntity(Entity entity) {
        String name = entity.getClass().getName();
        return name.startsWith(KRC_PACKAGE_PREFIX)
                && name.substring(name.lastIndexOf('.') + 1).startsWith(KRC_STORIOUS_PREFIX);
    }

    /**
     * 判断是不是假面骑士工艺的杂兵「死美」。
     * <p>
     * 优先按注册表 ID 匹配（最准，且不用加载对方的类），
     * 认不出时再用类名兜底，兼容 ID 带后缀的变体。
     * <p>
     * 技能系统（号令死美）也会调用，所以对外可见。
     */
    public static boolean isKrcShimiEntity(Entity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id != null && KRC_SHIMI.getNamespace().equals(id.getNamespace())
                && (KRC_SHIMI.getPath().equals(id.getPath())
                || id.getPath().startsWith(KRC_SHIMI.getPath() + "_"))) {
            return true;
        }
        String name = entity.getClass().getName();
        return name.startsWith(KRC_PACKAGE_PREFIX)
                && name.substring(name.lastIndexOf('.') + 1).contains(KRC_SHIMI_PREFIX);
    }
}
