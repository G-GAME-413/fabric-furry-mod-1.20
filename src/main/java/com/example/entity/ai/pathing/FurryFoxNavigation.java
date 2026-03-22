package com.example.entity.ai.pathing;

import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;

public class FurryFoxNavigation extends MobNavigation {

    public FurryFoxNavigation(MobEntity mob, World world) {
        super(mob, world);
    }

    @Override
    protected PathNodeNavigator createPathNodeNavigator(int range) {
        this.nodeMaker = new FurryFoxPathNodeMaker();

        this.nodeMaker.setCanEnterOpenDoors(true);
        this.nodeMaker.setCanOpenDoors(false);
        this.nodeMaker.setCanSwim(true);
        this.nodeMaker.setCanWalkOverFences(false);

        return new PathNodeNavigator(this.nodeMaker, range);
    }
}