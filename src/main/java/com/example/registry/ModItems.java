package com.example.registry;

import com.example.ExampleMod;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;

public class ModItems {

    public static final Item FURRY_FOX_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            new Identifier("examplemod", "furry_fox_spawn_egg"),
            new SpawnEggItem(
                    ModEntities.FURRY_FOX,
                    0xD9480F,   // основной цвет
                    0xFFFFFF,   // пятна
                    new Item.Settings()
            )
    );

    public static final Item FURRY_SOUL = Registry.register(
            Registries.ITEM,
            new Identifier("examplemod", "furry_soul"),
            new Item(new Item.Settings())
    );

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
            entries.add(FURRY_FOX_SPAWN_EGG);
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> {
            entries.add(FURRY_SOUL);
        });
    }

}