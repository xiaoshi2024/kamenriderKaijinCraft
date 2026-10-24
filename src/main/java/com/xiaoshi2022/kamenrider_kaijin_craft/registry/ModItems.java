package com.xiaoshi2022.kamenrider_kaijin_craft.registry;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.item.AlterRideBookItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KaijinMOD.MOD_ID);

    public static final DeferredItem<Item> STORIOUS_BOOK = ITEMS.register("storious_book",
            () -> new AlterRideBookItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
