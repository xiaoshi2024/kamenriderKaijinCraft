package com.xiaoshi2022.kamenrider_kaijin_craft.registry;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinState;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.KaijinManaState;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.StomachDigestion;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.StomachOrigin;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, KaijinMOD.MOD_ID);

    /** 书之怪物种族与拟态/怪人形态，随玩家死亡保留，并同步给附近客户端。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BookKaijinState>> BOOK_KAIJIN =
            ATTACHMENTS.register("book_kaijin", () -> AttachmentType.builder(() -> BookKaijinState.HUMAN)
                    .serialize(BookKaijinState.CODEC)
                    .sync(BookKaijinState.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    /** 怪人技能蓝条（叙事值），随玩家死亡保留并同步给客户端绘制 HUD。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<KaijinManaState>> KAIJIN_MANA =
            ATTACHMENTS.register("kaijin_mana", () -> AttachmentType.builder(() -> KaijinManaState.FULL)
                    .serialize(KaijinManaState.CODEC)
                    .sync(KaijinManaState.STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    /** 怪人融合槽里放着的变异道具（独立槽位，无锚点、不跟背包排序走）。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ItemStack>> FUSION_ITEM =
            ATTACHMENTS.register("fusion_item", () -> AttachmentType.builder(() -> ItemStack.EMPTY)
                    .serialize(ItemStack.OPTIONAL_CODEC)
                    .sync(ItemStack.OPTIONAL_STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    /** 卡律布狄斯肚子里正在消化的批次（随玩家死亡保留，否则被吞的玩家会永远留在肚子里）。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<java.util.List<StomachDigestion>>> STOMACH =
            ATTACHMENTS.register("stomach", () -> AttachmentType.<java.util.List<StomachDigestion>>builder(() -> java.util.List.<StomachDigestion>of())
                    .serialize(StomachDigestion.CODEC.listOf())
                    .copyOnDeath()
                    .build());

    /** 被吞玩家进肚子之前的位置：击碎核心后按它送回去，死亡时不保留。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<StomachOrigin>> STOMACH_ORIGIN =
            ATTACHMENTS.register("stomach_origin", () -> AttachmentType.builder(() -> StomachOrigin.NONE)
                    .serialize(StomachOrigin.CODEC)
                    .build());

    private ModAttachments() {
    }
}
