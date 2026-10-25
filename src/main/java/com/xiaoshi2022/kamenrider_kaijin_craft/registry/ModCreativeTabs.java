package com.xiaoshi2022.kamenrider_kaijin_craft.registry;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, KaijinMOD.MOD_ID);

    /** 模组专属创造物品栏 */
    public static final Supplier<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.kamenrider_kaijin_craft.main"))
            .icon(() -> ModItems.STORIOUS_BOOK.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.STORIOUS_BOOK.get());
                output.accept(ModItems.CHARYBDIS_BOOK.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
