package com.example.trading;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;

public class FurryFoxTrades {

    public static TradeOfferList createBlacksmithTrades() {

        TradeOfferList offers = new TradeOfferList();

        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 5),
                new ItemStack(Items.IRON_SWORD, 1),
                10,
                5,
                0.05f
        ));

        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 3),
                new ItemStack(Items.IRON_PICKAXE, 1),
                10,
                5,
                0.05f
        ));

        offers.add(new TradeOffer(
                new TradedItem(Items.EMERALD, 2),
                new ItemStack(Items.IRON_INGOT, 3),
                10,
                2,
                0.05f
        ));

        return offers;
    }
}