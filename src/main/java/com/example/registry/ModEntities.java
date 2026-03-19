package com.example.registry;

import com.example.entity.FurryFox;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.entity.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {

    public static final EntityType<FurryFox> FURRY_FOX =
            Registry.register(
                    Registries.ENTITY_TYPE,
                    new Identifier("examplemod", "furry_fox"),
                    FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, FurryFox::new)
                            .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                            .build()
            );

    public static void register() {

        FabricDefaultAttributeRegistry.register(
                FURRY_FOX,
                FurryFox.createAttributes()
        );

    }
}