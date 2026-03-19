package com.example.entity.goal;

import com.example.entity.FurryFox;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;

public class ReturnToVillageCenterGoal extends Goal {

    private final FurryFox fox;
    private final double speed;

    public ReturnToVillageCenterGoal(FurryFox fox, double speed) {
        this.fox = fox;
        this.speed = speed;
    }

    @Override
    public boolean canStart() {
        return fox.isVillageBound()
                && fox.getVillageCenter() != null
                && fox.isTooFarFromVillage()
                && !fox.getNavigation().isFollowingPath();
    }

    @Override
    public void start() {
        BlockPos center = fox.getVillageCenter();
        if (center != null) {
            fox.getNavigation().startMovingTo(
                    center.getX() + 0.5,
                    center.getY(),
                    center.getZ() + 0.5,
                    speed
            );
        }
    }

    @Override
    public boolean shouldContinue() {
        return fox.isVillageBound()
                && fox.getVillageCenter() != null
                && fox.isTooFarFromVillage()
                && fox.getNavigation().isFollowingPath();
    }
}