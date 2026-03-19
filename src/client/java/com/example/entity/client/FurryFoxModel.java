package com.example.entity.client;

import com.example.entity.FurryFox;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class FurryFoxModel extends GeoModel<FurryFox> {

    @Override
    public Identifier getModelResource(FurryFox animatable) {
        return new Identifier("examplemod", "geo/furry_fox.geo.json");
    }

    @Override
    public Identifier getTextureResource(FurryFox animatable) {
        // Изменяем текстуру в зависимости от профессии
        switch (animatable.getCurrentSpecialization()) {
            case BLACKSMITH:
                return new Identifier("examplemod", "textures/entity/furry_fox_blacksmith.png");
            case FARMER:
                return new Identifier("examplemod", "textures/entity/furry_fox_farmer.png");
            default:
                return new Identifier("examplemod", "textures/entity/furry_fox.png");
        }
    }

    @Override
    public Identifier getAnimationResource(FurryFox animatable) {
        return new Identifier("examplemod", "animations/furry_fox.animation.json");
    }

    @Override
    public void setCustomAnimations(FurryFox animatable, long instanceId, AnimationState<FurryFox> animationState) {

        GeoBone head = this.getAnimationProcessor().getBone("Head");

        if (head != null) {

            EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);

            head.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
            head.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));

        }
    }
}