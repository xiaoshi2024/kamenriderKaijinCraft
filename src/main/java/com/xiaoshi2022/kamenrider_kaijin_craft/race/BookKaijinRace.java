package com.xiaoshi2022.kamenrider_kaijin_craft.race;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.item.IKaijinMutationItem;
import com.xiaoshi2022.kamenrider_kaijin_craft.network.QiAuraPayload;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModAttachments;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
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

/**
 * 书之怪物种族的服务端逻辑：翻开类驾驭书即变异，G 键在拟态（人类）与怪人形态之间切换。
 * 两个方向都会放出斯特利乌斯标志性的红色气焰。
 */
public final class BookKaijinRace {
    /** 视频里斯特利乌斯变异时那股偏暗的红色气 */
    public static final int QI_COLOR = 0xC51A36;

    private BookKaijinRace() {
    }

    public static BookKaijinState get(Player player) {
        return player.getData(ModAttachments.BOOK_KAIJIN);
    }

    public static void set(Player player, BookKaijinState state) {
        BookKaijinState previous = get(player);
        player.setData(ModAttachments.BOOK_KAIJIN, state);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(ModAttachments.BOOK_KAIJIN);
            if (previous.bookKaijin() != state.bookKaijin() || previous.monster() != state.monster()) {
                refreshAttributes(serverPlayer);
            }
            // 第一次获得书之怪物种族时，蓝条从满开始
            if (!previous.bookKaijin() && state.bookKaijin()) {
                KaijinMana.refill(serverPlayer);
            }
        }
    }

    /** 当前是否以斯特利乌斯怪人的姿态出现（双端可用） */
    public static boolean isMonster(Player player) {
        BookKaijinState state = get(player);
        return state.bookKaijin() && state.monster();
    }

    // ---------------------------------------------------------------- 干部面板

    /** 干部形态的固定修饰符 id：幂等挂载/移除，不会和装备或其它 mod 的加成冲突。 */
    private static final ResourceLocation MAX_HEALTH_MODIFIER = KaijinMOD.id("monster/max_health");
    private static final ResourceLocation ATTACK_DAMAGE_MODIFIER = KaijinMOD.id("monster/attack_damage");
    private static final ResourceLocation ARMOR_MODIFIER = KaijinMOD.id("monster/armor");
    private static final ResourceLocation ARMOR_TOUGHNESS_MODIFIER = KaijinMOD.id("monster/armor_toughness");
    private static final ResourceLocation KNOCKBACK_RESISTANCE_MODIFIER = KaijinMOD.id("monster/knockback_resistance");
    private static final ResourceLocation MOVEMENT_SPEED_MODIFIER = KaijinMOD.id("monster/movement_speed");
    private static final ResourceLocation INTERACTION_RANGE_MODIFIER = KaijinMOD.id("monster/interaction_range");
    /** 人类拟态形态：只有血量加成，没有肉搏面板 */
    private static final ResourceLocation MIMIC_MAX_HEALTH_MODIFIER = KaijinMOD.id("mimic/max_health");

    private static final double MAX_HEALTH_BONUS = 280.0D;
    private static final double ATTACK_DAMAGE_BONUS = 9.0D;
    private static final double ARMOR_BONUS = 12.0D;
    private static final double ARMOR_TOUGHNESS_BONUS = 6.0D;
    private static final double KNOCKBACK_RESISTANCE_BONUS = 0.6D;
    private static final double MOVEMENT_SPEED_BONUS = 0.03D;
    private static final double INTERACTION_RANGE_BONUS = 0.5D;
    /** 拟态成人类时的血量：20 基础 + 40 = 60 */
    private static final double MIMIC_MAX_HEALTH_BONUS = 40.0D;

    /**
     * 干部级身体素质：300 血（20 基础 + 280）、空手 10 伤害、护甲/韧性、击退抗性、
     * 略快的身法与更长的交互距离。仅怪人形态生效，拟态成人类时只剩 60 血。
     */
    private static void applyMonsterModifiers(ServerPlayer player) {
        applyModifier(player, Attributes.MAX_HEALTH, MAX_HEALTH_MODIFIER, MAX_HEALTH_BONUS);
        applyModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER, ATTACK_DAMAGE_BONUS);
        applyModifier(player, Attributes.ARMOR, ARMOR_MODIFIER, ARMOR_BONUS);
        applyModifier(player, Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS_MODIFIER, ARMOR_TOUGHNESS_BONUS);
        applyModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_MODIFIER,
                KNOCKBACK_RESISTANCE_BONUS);
        applyModifier(player, Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_MODIFIER, MOVEMENT_SPEED_BONUS);
        applyModifier(player, Attributes.ENTITY_INTERACTION_RANGE, INTERACTION_RANGE_MODIFIER,
                INTERACTION_RANGE_BONUS);
    }

    private static void clearMonsterModifiers(ServerPlayer player) {
        removeModifier(player, Attributes.MAX_HEALTH, MAX_HEALTH_MODIFIER);
        removeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER);
        removeModifier(player, Attributes.ARMOR, ARMOR_MODIFIER);
        removeModifier(player, Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS_MODIFIER);
        removeModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE_MODIFIER);
        removeModifier(player, Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_MODIFIER);
        removeModifier(player, Attributes.ENTITY_INTERACTION_RANGE, INTERACTION_RANGE_MODIFIER);
    }

    private static void applyModifier(ServerPlayer player, Holder<Attribute> attribute,
                                      ResourceLocation id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(
                    new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void removeModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    /**
     * 形态切换时重算面板：按当前血量百分比换算，满血变身就是满血，退回人类也不会
     * 因为上限掉回 20 而直接暴毙（至少保留 1 点）。
     */
    private static void refreshAttributes(ServerPlayer player) {
        float ratio = player.getHealth() / player.getMaxHealth();
        applyAttributes(player);
        float maxHealth = player.getMaxHealth();
        player.setHealth(Mth.clamp(ratio * maxHealth, 1.0F, maxHealth));
        syncAttributesToClient(player);
    }

    /**
     * 手动挂上的修饰符不会随实体元数据自动同步，这里主动把属性表推给客户端，
     * 否则客户端拿到的上限还是 20，自定义血条会算错比例。
     */
    private static void syncAttributesToClient(ServerPlayer player) {
        if (player.connection != null) {
            player.connection.send(new ClientboundUpdateAttributesPacket(
                    player.getId(), player.getAttributes().getSyncableAttributes()));
        }
    }

    /** 三种状态的面板：怪人 300 血、拟态 60 血、失去种族回到普通人。 */
    private static void applyAttributes(ServerPlayer player) {
        BookKaijinState state = get(player);
        if (state.bookKaijin() && state.monster()) {
            removeModifier(player, Attributes.MAX_HEALTH, MIMIC_MAX_HEALTH_MODIFIER);
            applyMonsterModifiers(player);
            return;
        }
        clearMonsterModifiers(player);
        if (state.bookKaijin()) {
            applyModifier(player, Attributes.MAX_HEALTH, MIMIC_MAX_HEALTH_MODIFIER, MIMIC_MAX_HEALTH_BONUS);
        } else {
            removeModifier(player, Attributes.MAX_HEALTH, MIMIC_MAX_HEALTH_MODIFIER);
        }
    }

    /**
     * 定期对齐：死亡重生 / 换维度后属性表会被重置，这里补回修饰符。
     * 只在「该有加成却没有」或「不该有却残留」时才动血量，避免每半秒改写血量。
     */
    public static void syncAttributes(ServerPlayer player) {
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        BookKaijinState state = get(player);
        boolean expectMonster = state.bookKaijin() && state.monster();
        boolean expectMimic = state.bookKaijin() && !state.monster();
        boolean hasMonster = maxHealth.getModifier(MAX_HEALTH_MODIFIER) != null;
        boolean hasMimic = maxHealth.getModifier(MIMIC_MAX_HEALTH_MODIFIER) != null;
        if (hasMonster != expectMonster || hasMimic != expectMimic) {
            refreshAttributes(player);
        }
    }

    /** 翻开书：第一次授予书之怪物种族并直接变异，之后每次翻书都重新解放怪人形态。 */
    public static void openBook(ServerPlayer player) {
        BookKaijinState state = get(player);
        boolean firstMutation = !state.bookKaijin();
        if (firstMutation) {
            set(player, new BookKaijinState(true, true));
            player.displayClientMessage(Component.translatable("message.kamenrider_kaijin_craft.mutate"), true);
        } else {
            set(player, state.withMonster(true));
            player.displayClientMessage(Component.translatable("message.kamenrider_kaijin_craft.unleash"), true);
        }
        // award 本身幂等：第一次翻开时弹出「觉醒吧！掌管古诗的书之魔物」，之后不会再弹
        awardFirstMutation(player);
        burst(player, true, true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL,
                SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /** 初次变异成就：「觉醒吧！掌管古诗的书之魔物」 */
    private static void awardFirstMutation(ServerPlayer player) {
        AdvancementHolder advancement = player.server.getAdvancements().get(KaijinMOD.id("first_mutation"));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "mutated");
        }
    }

    /**
     * 变异道具是否还在这位玩家身上（融合槽 / 背包 / 光标）。
     * 这是怪人种族的维持条件：道具被销毁、丢弃、清空后就不再满足。
     */
    public static boolean hasMutationSource(Player player) {
        if (sustains(player, player.getData(ModAttachments.FUSION_ITEM))) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (sustains(player, inventory.getItem(slot))) {
                return true;
            }
        }
        return player.containerMenu != null && sustains(player, player.containerMenu.getCarried());
    }

    private static boolean sustains(Player player, ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof IKaijinMutationItem mutationItem
                && mutationItem.sustainsRace(player, stack);
    }

    /** 服务端定期校验：变异道具彻底消失后，退回人类种族。 */
    public static void validateMutationSource(ServerPlayer player) {
        BookKaijinState state = get(player);
        if (!state.bookKaijin() || hasMutationSource(player)) {
            return;
        }
        set(player, BookKaijinState.HUMAN);
        burst(player, false, false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.0F, 1.4F);
        player.displayClientMessage(Component.translatable(
                "message.kamenrider_kaijin_craft.race_lost"), true);
    }

    /** G 键：拟态 ⇄ 怪人。 */
    public static void toggle(ServerPlayer player) {
        BookKaijinState state = get(player);
        if (!state.bookKaijin()) {
            player.displayClientMessage(Component.translatable("message.kamenrider_kaijin_craft.not_book_kaijin"), true);
            return;
        }
        boolean monster = !state.monster();
        set(player, state.withMonster(monster));
        burst(player, monster, false);
        player.level().playSound(null, player.blockPosition(),
                monster ? SoundEvents.EVOKER_CAST_SPELL : SoundEvents.ILLUSIONER_MIRROR_MOVE,
                SoundSource.PLAYERS, 1.0F, monster ? 0.7F : 1.4F);
        player.displayClientMessage(Component.translatable(monster
                ? "message.kamenrider_kaijin_craft.unleash"
                : "message.kamenrider_kaijin_craft.mimic"), true);
    }

    /**
     * 放气：锚定在玩家身上的气焰 + （变异时）脚下一圈外扩的气雾。
     * 实现照搬尸兄模组的 QiAuraPayload / QiAuraRenderer。
     */
    private static void burst(ServerPlayer player, boolean monster, boolean mutation) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                player.getId(),
                mutation ? "mutate" : "mimic",
                player.position(),
                QI_COLOR,
                monster ? 1.9F : 1.3F,
                monster ? 50 : 40));

        if (mutation) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new QiAuraPayload(
                    -1, "mutate_mist", player.position(), QI_COLOR, 3.2F, 45));
        }
    }
}
