package com.example.entity.goal;

import com.example.entity.FurryFox;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ComposterBlock;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;

public class ComposterWorkGoal extends Goal {

    private final FurryFox fox;
    private final double speed;

    private static final int WORK_ANIMATION_DURATION = 40;

    private boolean performingWork = false;
    private int workFinishTick = 0;

    public ComposterWorkGoal(FurryFox fox, double speed) {
        this.fox = fox;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return fox.isFarmer()
                && fox.isVillageBound()
                && fox.hasComposter()
                && fox.getComposterPos() != null
                && (fox.isFarmerInventoryFull() || fox.countExcessCompostables() >= 8 || shouldCollectBoneMeal());
    }

    @Override
    public boolean shouldContinue() {
        return fox.isFarmer()
                && fox.hasComposter()
                && fox.getComposterPos() != null
                && (performingWork || fox.hasExcessCompostables() || shouldCollectBoneMeal());
    }

    @Override
    public void start() {
        BlockPos composterPos = fox.getComposterPos();
        if (composterPos == null) return;

        fox.getNavigation().startMovingTo(
                composterPos.getX() + 0.5,
                composterPos.getY(),
                composterPos.getZ() + 0.5,
                speed
        );
    }

    @Override
    public void stop() {
        this.performingWork = false;
        this.workFinishTick = 0;
        fox.getNavigation().stop();
    }

    @Override
    public void tick() {
        BlockPos composterPos = fox.getComposterPos();
        if (composterPos == null) return;

        fox.getLookControl().lookAt(
                composterPos.getX() + 0.5,
                composterPos.getY() + 0.5,
                composterPos.getZ() + 0.5
        );

        if (this.performingWork) {
            fox.getNavigation().stop();

            if (fox.age >= this.workFinishTick) {
                performComposterWork(fox.getWorld(), composterPos);
                this.performingWork = false;
            }

            return;
        }

        double distanceSq = fox.squaredDistanceTo(
                composterPos.getX() + 0.5,
                composterPos.getY() + 0.5,
                composterPos.getZ() + 0.5
        );

        if (distanceSq > 3.0D) {
            fox.getNavigation().startMovingTo(
                    composterPos.getX() + 0.5,
                    composterPos.getY(),
                    composterPos.getZ() + 0.5,
                    speed
            );
            return;
        }

        fox.getNavigation().stop();
        fox.triggerFarmAnimation();
        this.performingWork = true;
        this.workFinishTick = fox.age + WORK_ANIMATION_DURATION;
    }

    private boolean shouldCollectBoneMeal() {
        BlockPos composterPos = fox.getComposterPos();
        if (composterPos == null) return false;

        BlockState state = fox.getWorld().getBlockState(composterPos);
        if (!state.isOf(Blocks.COMPOSTER)) return false;

        // LEVEL 8 = готовая костная мука
        return state.get(ComposterBlock.LEVEL) == 8;
    }

    private void performComposterWork(World world, BlockPos composterPos) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        BlockState state = world.getBlockState(composterPos);
        if (!state.isOf(Blocks.COMPOSTER)) {
            return;
        }

        // 1. Если компостер полный — сначала забираем костную муку
        if (state.get(ComposterBlock.LEVEL) == 8) {
            ComposterBlock.emptyFullComposter(fox, state, world, composterPos);
            fox.addToFarmerInventory(new ItemStack(Items.BONE_MEAL, 1));
            return;
        }

        // 2. Если есть излишки — кладём 1 предмет в компостер
        ItemStack compostable = fox.takeOneCompostableForComposter();
        if (compostable.isEmpty()) {
            return;
        }

        ItemStack oneItem = compostable.copy();
        oneItem.setCount(1);

        BlockState currentState = world.getBlockState(composterPos);
        ComposterBlock.compost(fox, currentState, serverWorld, oneItem, composterPos);

        // 3. Если после этого компостер стал полным — сразу забираем костную муку
        BlockState afterState = world.getBlockState(composterPos);
        if (afterState.isOf(Blocks.COMPOSTER) && afterState.get(ComposterBlock.LEVEL) == 8) {
            ComposterBlock.emptyFullComposter(fox, afterState, world, composterPos);
            fox.addToFarmerInventory(new ItemStack(Items.BONE_MEAL, 1));
        }
    }
}