package com.example.entity.client;

import com.example.entity.FurryFox;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class FurryFoxRenderer extends GeoEntityRenderer<FurryFox> {

    public FurryFoxRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new FurryFoxModel());
        this.shadowRadius = 0.6f;
    }

}