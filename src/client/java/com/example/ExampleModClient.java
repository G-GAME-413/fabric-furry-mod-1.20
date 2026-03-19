package com.example;

import com.example.entity.client.FurryFoxRenderer;
import com.example.registry.ModBlocks;
import com.example.registry.ModEntities;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.RenderLayer;

public class ExampleModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        EntityRendererRegistry.register(
                ModEntities.FURRY_FOX,
                FurryFoxRenderer::new
        );

        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CENTER_FURRY_VILLAGE, RenderLayer.getCutout());
    }

}