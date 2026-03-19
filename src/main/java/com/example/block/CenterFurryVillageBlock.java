package com.example.block;

import com.example.entity.FurryFox;
import com.example.registry.FurryVillageRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.NavigationType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CenterFurryVillageBlock extends Block {

    private static final VoxelShape SHAPE = Block.createCuboidShape(
            4.0, 0.0, 4.0,
            12.0, 16.0, 12.0
    );

    private static final int REBIND_RADIUS = 48;

    public CenterFurryVillageBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getSidesShape(BlockState state, BlockView world, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCullingShape(BlockState state, BlockView world, BlockPos pos) {
        return VoxelShapes.empty();
    }

    @Override
    protected boolean canPathfindThrough(BlockState state, NavigationType type) {
        return false;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        FurryVillageRegistry.registerCenter(world, pos);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            FurryVillageRegistry.unregisterCenter(world, pos);

            if (!world.isClient) {
                Box box = Box.of(pos.toCenterPos(), REBIND_RADIUS * 2.0, 32.0, REBIND_RADIUS * 2.0);
                List<FurryFox> foxes = world.getEntitiesByClass(
                        FurryFox.class,
                        box,
                        fox -> pos.equals(fox.getVillageCenter())
                );

                for (FurryFox fox : foxes) {
                    fox.onVillageCenterDestroyed(pos);
                }
            }
        }

        super.onStateReplaced(state, world, pos, newState, moved);
    }
}