package com.example.registry;

import com.example.block.CenterFurryVillageBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block CENTER_FURRY_VILLAGE = Registry.register(
            Registries.BLOCK,
            new Identifier("examplemod", "center_furry_village"),
            new CenterFurryVillageBlock(
                    AbstractBlock.Settings.copy(Blocks.STONE)
                            .strength(3.0f, 6.0f)
                            .sounds(BlockSoundGroup.STONE)
                            .requiresTool()
                            .nonOpaque()
            )
    );

    public static final Item CENTER_FURRY_VILLAGE_ITEM = Registry.register(
            Registries.ITEM,
            new Identifier("examplemod", "center_furry_village"),
            new BlockItem(CENTER_FURRY_VILLAGE, new Item.Settings())
    );

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            entries.add(CENTER_FURRY_VILLAGE_ITEM);
        });
    }
}