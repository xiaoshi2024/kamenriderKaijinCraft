package com.xiaoshi2022.kamenrider_kaijin_craft.registry;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.render.CharybdisBookModel;
import com.xiaoshi2022.kamenrider_kaijin_craft.client.render.StoriousBookModel;
import com.xiaoshi2022.kamenrider_kaijin_craft.item.AlterRideBookItem;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinType;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KaijinMOD.MOD_ID);

    public static final DeferredItem<Item> STORIOUS_BOOK = ITEMS.register("storious_book",
            () -> new AlterRideBookItem(new Item.Properties().stacksTo(1),
                    BookKaijinType.STORIOUS, StoriousBookModel::new));

    public static final DeferredItem<Item> CHARYBDIS_BOOK = ITEMS.register("charybdis_book",
            () -> new AlterRideBookItem(new Item.Properties().stacksTo(1),
                    BookKaijinType.CHARYBDIS, CharybdisBookModel::new));

    private ModItems() {
    }
}
