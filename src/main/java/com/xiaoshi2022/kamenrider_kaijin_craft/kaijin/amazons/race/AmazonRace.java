package com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.race;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.item.LysogenicCellItem;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.amazons.skill.AmazonTendrils;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.KaijinMana;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.QiAuraPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 亚马逊怪人种族：被溶原性细胞感染的玩家按 G 变异为千翼的 Neo Amazon Origin。
 * <p>
 * 第一次按 G 时先用一团白雾把玩家整个罩住，约两秒后才真正完成变异；
 * 之后 G 键在拟态（人类）与怪人形态之间切换。溶原性细胞是维持种族的「心脏」，
 * 彻底失去它就会退回人类。
 */
public final class AmazonRace {
    /** 具名形态：千翼在不使用驱动器的情况下变身的亚马逊态。 */
    public static final String FORM_ID = "neo_amazon_origin";

    /** 白雾与触手的配色（近白的冷灰）。 */
    public static final int QI_COLOR = 0xEDEDED;

    /** 白雾先行时长：雾铺开两秒后怪人形态才显现。 */
    public static final int MUTATION_DELAY_TICKS = 40;

    private static final Map<UUID, Long> PENDING_MUTATIONS = new ConcurrentHashMap<>();

    private AmazonRace() {
    }

    public static AmazonState get(Player player) {
        return player.getData(ModAttachments.AMAZON);
    }

    public static boolean isAmazon(Player player) {
        return get(player).amazon();
    }

    /** 当前是否以 Neo Amazon Origin 的姿态出现（双端可用）。 */
    public static boolean isMonster(Player player) {
        AmazonState state = get(player);
        return state.amazon() && state.monster();
    }

    public static void set(Player player, AmazonState state) {
        AmazonState previous = get(player);
        player.setData(ModAttachments.AMAZON, state);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(ModAttachments.AMAZON);
            if (previous.amazon() != state.amazon() || previous.monster() != state.monster()) {
                refreshAttributes(serverPlayer);
            }
        }
    }

    // ---------------------------------------------------------------- 面板

    private static final ResourceLocation MAX_HEALTH_MODIFIER = KaijinMOD.id("amazon/max_health");
    private static final ResourceLocation ATTACK_DAMAGE_MODIFIER = KaijinMOD.id("amazon/attack_damage");
    private static final ResourceLocation ARMOR_MODIFIER = KaijinMOD.id("amazon/armor");
    private static final ResourceLocation ARMOR_TOUGHNESS_MODIFIER = KaijinMOD.id("amazon/armor_toughness");
    private static final ResourceLocation KNOCKBACK_RESISTANCE_MODIFIER = KaijinMOD.id("amazon/knockback_resistance");
    private static final ResourceLocation MOVEMENT_SPEED_MODIFIER = KaijinMOD.id("amazon/movement_speed");
    private static final ResourceLocation MIMIC_MAX_HEALTH_MODIFIER = KaijinMOD.id("amazon/mimic_max_health");

    private static final double MAX_HEALTH_BONUS = 280.0D;
    private static final double ATTACK_DAMAGE_BONUS = 9.0D;
    private static final double ARMOR_BONUS = 12.0D;
    private static final double ARMOR_TOUGHNESS_BONUS = 6.0D;
    private static final double KNOCKBACK_RESISTANCE_BONUS = 0.6D;
    private static final double MOVEMENT_SPEED_BONUS = 0.04D;
    /** 拟态成人类时的血量：20 基础 + 40 = 60 */
    private static final double MIMIC_MAX_HEALTH_BONUS = 40.0D;

    private static void applyMonsterModifiers(ServerPlayer player) {
        applyModifier(player, Attributes.MAX_HEALTH, MAX_HEALTH_MODIFIER, MAX_HEALTH_BONUS);
        applyModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER, ATTACK_DAMAGE_BONUS);
        applyModifier(player, Attributes.ARMOR, ARMOR_MODIFIER, ARMOR_BONUS);
        applyModifier(player, Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS_MODIFIER, ARMOR_TOUGHNESS_BONUS);
        applyModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_MODIFIER,
                KNOCKBACK_RESISTANCE_BONUS);
        applyModifier(player, Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_MODIFIER, MOVEMENT_SPEED_BONUS);
    }

    private static void clearMonsterModifiers(ServerPlayer player) {
        removeModifier(player, Attributes.MAX_HEALTH, MAX_HEALTH_MODIFIER);
        removeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER);
        removeModifier(player, Attributes.ARMOR, ARMOR_MODIFIER);
        removeModifier(player, Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS_MODIFIER);
        removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_MODIFIER);
        removeModifier(player, Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_MODIFIER);
    }

    private static void applyModifier(ServerPlayer player, net.minecraft.core.Holder<Attribute> attribute,
                                      ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(
                    new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeModifier(ServerPlayer player, net.minecraft.core.Holder<Attribute> attribute,
                                       ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    /** 形态切换时按血量百分比换算，避免高上限掉回低上限时直接暴毙。 */
    private static void refreshAttributes(ServerPlayer player) {
        float ratio = player.getHealth() / player.getMaxHealth();
        applyAttributes(player);
        float maxHealth = player.getMaxHealth();
        player.setHealth(Mth.clamp(ratio * maxHealth, 1.0F, maxHealth));
        if (player.connection != null) {
            player.connection.send(new ClientboundUpdateAttributesPacket(
                    player.getId(), player.getAttributes().getSyncableAttributes()));
        }
    }

    private static void applyAttributes(ServerPlayer player) {
        AmazonState state = get(player);
        if (state.amazon() && state.monster()) {
            removeModifier(player, Attributes.MAX_HEALTH, MIMIC_MAX_HEALTH_MODIFIER);
            applyMonsterModifiers(player);
            return;
        }
        clearMonsterModifiers(player);
        if (state.amazon()) {
            applyModifier(player, Attributes.MAX_HEALTH, MIMIC_MAX_HEALTH_MODIFIER, MIMIC_MAX_HEALTH_BONUS);
        } else {
            removeModifier(player, Attributes.MAX_HEALTH, MIMIC_MAX_HEALTH_MODIFIER);
        }
    }

    /** 定期对齐：死亡重生 / 换维度后属性表会被重置，这里补回修饰符。 */
    public static void syncAttributes(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        AmazonState state = get(player);
        boolean expectMonster = state.amazon() && state.monster();
        boolean expectMimic = state.amazon() && !state.monster();
        boolean hasMonster = maxHealth.getModifier(MAX_HEALTH_MODIFIER) != null;
        boolean hasMimic = maxHealth.getModifier(MIMIC_MAX_HEALTH_MODIFIER) != null;
        if (hasMonster != expectMonster || hasMimic != expectMimic) {
            refreshAttributes(player);
        }
    }

    // ---------------------------------------------------------------- 变异

    /** G 键：未感染时开始变异；感染后切换拟态 / 怪人形态。 */
    public static void pressG(ServerPlayer player) {
        AmazonState state = get(player);
        if (!state.amazon()) {
            if (!hasCell(player)) {
                player.displayClientMessage(Component.translatable(
                        "message.kamenrider_kaijin_craft.amazon_need_cell"), true);
                return;
            }
            if (BookKaijinRace.get(player).bookKaijin()) {
                player.displayClientMessage(Component.translatable(
                        "message.kamenrider_kaijin_craft.amazon_already_book"), true);
                return;
            }
            if (PENDING_MUTATIONS.containsKey(player.getUUID())) {
                return;
            }
            PENDING_MUTATIONS.put(player.getUUID(),
                    player.level().getGameTime() + MUTATION_DELAY_TICKS);
            burst(player, true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK,
                    SoundSource.PLAYERS, 1.0F, 0.6F);
            return;
        }
        if (PENDING_MUTATIONS.containsKey(player.getUUID())) {
            return;
        }
        boolean monster = !state.monster();
        set(player, state.withMonster(monster));
        if (monster) {
            KaijinMana.refill(player);
        }
        burst(player, false);
        player.level().playSound(null, player.blockPosition(),
                monster ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.0F, monster ? 0.6F : 1.5F);
        player.displayClientMessage(Component.translatable(monster
                ? "message.kamenrider_kaijin_craft.amazon_unleash"
                : "message.kamenrider_kaijin_craft.amazon_mimic"), true);
    }

    /** 每 tick 推进待完成的变异：白雾铺完才真正变形。 */
    public static void tickPending(ServerPlayer player) {
        Long readyAt = PENDING_MUTATIONS.get(player.getUUID());
        if (readyAt == null) {
            return;
        }
        if (!player.isAlive() || !hasCell(player)) {
            PENDING_MUTATIONS.remove(player.getUUID());
            return;
        }
        if (player.level().getGameTime() < readyAt) {
            return;
        }
        PENDING_MUTATIONS.remove(player.getUUID());
        set(player, new AmazonState(true, true));
        KaijinMana.refill(player);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.amazon_mutate"), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 1.0F, 0.55F);
    }

    /** 下线时若还停在白雾阶段，直接完成变形，避免登录后卡住。 */
    public static void completePendingMutation(ServerPlayer player) {
        Long readyAt = PENDING_MUTATIONS.remove(player.getUUID());
        if (readyAt != null) {
            set(player, new AmazonState(true, true));
            KaijinMana.refill(player);
        }
    }

    /** 每 tick 推进被动触手。 */
    public static void tickPassive(ServerPlayer player) {
        AmazonTendrils.tick(player);
    }

    /** 饱食度跌破临界点时，怪人形态被迫收回，退回人类拟态。 */
    public static void forceMimic(ServerPlayer player) {
        AmazonState state = get(player);
        if (!state.amazon() || !state.monster()) {
            return;
        }
        set(player, state.withMonster(false));
        burst(player, false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.0F, 1.5F);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.amazon_hungry_mimic"), true);
    }

    // ---------------------------------------------------------------- 维持条件

    /** 溶原性细胞是否还在身上（背包 / 光标 / 融合槽）。 */
    public static boolean hasCell(Player player) {
        if (isCell(player.getData(ModAttachments.FUSION_ITEM))) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isCell(inventory.getItem(slot))) {
                return true;
            }
        }
        return player.containerMenu != null && isCell(player.containerMenu.getCarried());
    }

    private static boolean isCell(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof LysogenicCellItem;
    }

    /** 溶原性细胞彻底消失后，退回人类种族。 */
    public static void validateCell(ServerPlayer player) {
        AmazonState state = get(player);
        if (!state.amazon() || hasCell(player)) {
            return;
        }
        PENDING_MUTATIONS.remove(player.getUUID());
        set(player, AmazonState.HUMAN);
        burst(player, false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.0F, 1.4F);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.amazon_race_lost"), true);
    }

    public static void clear(UUID playerId) {
        PENDING_MUTATIONS.remove(playerId);
        AmazonTendrils.clear(playerId);
    }

    /** 白雾：变异时一整团雾把玩家包住，形态切换时只放一小圈。 */
    private static void burst(ServerPlayer player, boolean mutation) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                player.getId(), mutation ? "white_mist" : "white_smoke", player.position(),
                QI_COLOR, mutation ? 2.1F : 1.2F, mutation ? 55 : 32));
    }
}
