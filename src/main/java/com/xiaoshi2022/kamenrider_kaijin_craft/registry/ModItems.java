package com.xiaoshi2022.kamenrider_kaijin_craft.registry;

import com.xiaoshi2022.kamenrider_kaijin_craft.KaijinMOD;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render.CharybdisBookModel;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render.LegeielBookModel;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.client.render.StoriousBookModel;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.item.AlterRideBookItem;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.item.BolhessedItem;
import com.xiaoshi2022.kamenrider_kaijin_craft.kaijin.megid.race.BookKaijinType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
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

    public static final DeferredItem<Item> LEGEIEL_BOOK = ITEMS.register("legeiel_book",
            () -> new AlterRideBookItem(new Item.Properties().stacksTo(1),
                    BookKaijinType.LEGEIEL, LegeielBookModel::new));

    /**
     * 拉结尔专武「波尔赫斯德」斧剑：剑斧双修，右键放剑气、潜行右键掷冰岩。
     * 首次变异为拉结尔时自动获得。
     */
    public static final DeferredItem<Item> BOLHESSED = ITEMS.register("bolhessed",
            () -> new BolhessedItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 9, -2.4F))));

    private ModItems() {
    }
}
