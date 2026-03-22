package com.example.entity.ai.pathing;

import com.example.registry.ModBlocks;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.ComposterBlock;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathContext;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;

public class FurryFoxPathNodeMaker extends LandPathNodeMaker {

    @Override
    public PathNodeType getDefaultNodeType(PathContext context, int x, int y, int z) {
        PathNodeType base = super.getDefaultNodeType(context, x, y, z);
        return adjustNodeType(context, x, y, z, base);
    }

    @Override
    public PathNodeType getNodeType(PathContext context, int x, int y, int z, MobEntity mob) {
        PathNodeType base = super.getNodeType(context, x, y, z, mob);
        return adjustNodeType(context, x, y, z, base);
    }

    private PathNodeType adjustNodeType(PathContext context, int x, int y, int z, PathNodeType base) {
        // Уже непроходимое оставляем непроходимым
        if (base == PathNodeType.BLOCKED) {
            return base;
        }

        BlockPos.Mutable currentPos = new BlockPos.Mutable(x, y, z);
        BlockPos.Mutable belowPos = new BlockPos.Mutable(x, y - 1, z);

        BlockState currentState = context.getBlockState(currentPos);
        BlockState belowState = context.getBlockState(belowPos);

        // Мягкое избегание
        if (isSoftAvoid(currentState) || isSoftAvoid(belowState) || isBed(currentState) || isBed(belowState)) {
            return PathNodeType.DANGER_OTHER;
        }

        return base;
    }

    private static boolean isBed(BlockState state) {
        return state.getBlock() instanceof BedBlock;
    }

    private static boolean isSoftAvoid(BlockState state) {
        if (state.getBlock() instanceof ComposterBlock) {
            return true;
        }

        if (state.isOf(Blocks.CRAFTING_TABLE)) {
            return true;
        }

        if (state.getBlock() instanceof AbstractFurnaceBlock) {
            return true; // furnace, smoker, blast furnace
        }

        if (state.getBlock() instanceof CampfireBlock) {
            return true; // обычный и soul campfire
        }

        return state.isOf(ModBlocks.CENTER_FURRY_VILLAGE);
    }
}