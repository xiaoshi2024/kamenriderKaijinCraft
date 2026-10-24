package com.xiaoshi2022.kamenrider_kaijin_craft.registry;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinState;
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

    /** 怪人融合槽里放着的变异道具（独立槽位，无锚点、不跟背包排序走）。 */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ItemStack>> FUSION_ITEM =
            ATTACHMENTS.register("fusion_item", () -> AttachmentType.builder(() -> ItemStack.EMPTY)
                    .serialize(ItemStack.OPTIONAL_CODEC)
                    .sync(ItemStack.OPTIONAL_STREAM_CODEC)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
