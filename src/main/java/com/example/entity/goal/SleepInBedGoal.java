package com.example.entity.goal;

import com.example.entity.FurryFox;
import com.example.registry.FurryVillageRegistry;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;

public class SleepInBedGoal extends Goal {

    private final FurryFox fox;
    private final double speed;

    private BlockPos targetBed;
    private Vec3d sleepPos;

    public SleepInBedGoal(FurryFox fox, double speed) {
        this.fox = fox;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }

    @Override
    public boolean canStart() {
        if (!fox.isVillageBound() || fox.getVillageCenter() == null) {
            return false;
        }

        if (!fox.isSleepTime()) {
            return false;
        }

        if (fox.getBedPos() == null) {
            fox.assignBedIfPossible();
        }

        BlockPos bedPos = fox.getBedPos();
        if (bedPos == null) {
            return false;
        }

        if (!isValidBedForFox(bedPos)) {
            fox.clearBed();
            fox.assignBedIfPossible();

            bedPos = fox.getBedPos();
            if (bedPos == null || !isValidBedForFox(bedPos)) {
                return false;
            }
        }

        this.targetBed = bedPos;
        this.sleepPos = calculateSleepPos(bedPos);

        return this.sleepPos != null;
    }

    @Override
    public boolean shouldContinue() {
        if (!fox.isSleepingInBed()) {
            return fox.canSleepNow() && this.targetBed != null && isValidBedForFox(this.targetBed);
        }

        return fox.isSleepTime() && this.targetBed != null && isValidBedForFox(this.targetBed);
    }

    @Override
    public void start() {
        fox.setSleepingInBed(false);

        if (this.sleepPos != null) {
            fox.getNavigation().startMovingTo(
                    this.sleepPos.x,
                    this.sleepPos.y,
                    this.sleepPos.z,
                    speed
            );
        }
    }

    @Override
    public void stop() {
        fox.getNavigation().stop();
        fox.wakeUp();
        this.targetBed = null;
        this.sleepPos = null;
    }

    @Override
    public void tick() {
        if (this.targetBed == null || this.sleepPos == null) {
            return;
        }

        if (!isValidBedForFox(this.targetBed)) {
            fox.wakeUp();
            fox.clearBed();
            fox.assignBedIfPossible();
            this.targetBed = null;
            this.sleepPos = null;
            fox.getNavigation().stop();
            return;
        }

        if (!fox.isSleepTime()) {
            fox.wakeUp();
            fox.getNavigation().stop();
            return;
        }

        double distanceSq = fox.squaredDistanceTo(
                this.sleepPos.x,
                this.sleepPos.y,
                this.sleepPos.z
        );

        if (distanceSq > 2.0D) {
            fox.setSleepingInBed(false);
            fox.getNavigation().startMovingTo(
                    this.sleepPos.x,
                    this.sleepPos.y,
                    this.sleepPos.z,
                    speed
            );
            return;
        }

        fox.getNavigation().stop();
        fox.startSleeping();

        Direction facing = getBedFacing(this.targetBed);
        if (facing != null) {
            fox.setYaw(facing.asRotation());
            fox.bodyYaw = facing.asRotation();
            fox.headYaw = facing.asRotation();
        }

        fox.setPosition(this.sleepPos.x, this.sleepPos.y, this.sleepPos.z);
        fox.setVelocity(0.0, 0.0, 0.0);
    }

    private boolean isValidBedForFox(BlockPos bedPos) {
        World world = fox.getWorld();
        BlockState state = world.getBlockState(bedPos);

        if (!(state.getBlock() instanceof BedBlock)) {
            return false;
        }

        if (state.get(BedBlock.PART) != BedPart.HEAD) {
            return false;
        }

        if (!FurryVillageRegistry.isBedReservedBy(world, bedPos, fox.getUuid())) {
            return false;
        }

        BlockPos ownerVillage = FurryVillageRegistry.getVillageOwnerOfBed(world, bedPos);
        return ownerVillage != null && ownerVillage.equals(fox.getVillageCenter());
    }

    private Vec3d calculateSleepPos(BlockPos bedPos) {
        World world = fox.getWorld();
        BlockState state = world.getBlockState(bedPos);

        if (!(state.getBlock() instanceof BedBlock)) {
            return null;
        }

        Direction facing = state.get(BedBlock.FACING);

        double x = bedPos.getX() + 0.5;
        double y = bedPos.getY() + 0.5625;
        double z = bedPos.getZ() + 0.5;

        double offset = 0.15;

        // Небольшое смещение к центру кровати
        x -= facing.getOffsetX() * offset;
        z -= facing.getOffsetZ() * offset;

        return new Vec3d(x, y, z);
    }

    private Direction getBedFacing(BlockPos bedPos) {
        BlockState state = fox.getWorld().getBlockState(bedPos);
        if (state.getBlock() instanceof BedBlock) {
            return state.get(BedBlock.FACING);
        }
        return null;
    }
}