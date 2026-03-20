package com.example.mixin;

import com.example.registry.FurryVillageRegistry;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedBlock.class)
public abstract class BedBlockMixin {

    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void examplemod$onPlaced(
            World world,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack itemStack,
            CallbackInfo ci
    ) {
        if (world.isClient) return;

        BlockPos headPos = getHeadPos(pos, state);
        BlockState headState = world.getBlockState(headPos);

        FurryVillageRegistry.registerBed(world, headPos, headState);
    }

    @Inject(
            method = "onBreak(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/block/BlockState;",
            at = @At("HEAD")
    )
    private void examplemod$onBreak(
            World world,
            BlockPos pos,
            BlockState state,
            PlayerEntity player,
            CallbackInfoReturnable<BlockState> cir
    ) {
        if (world.isClient) return;

        BlockPos headPos = getHeadPos(pos, state);

        // важно: удаляем по вычисленной позиции HEAD,
        // даже если ломают FOOT
        FurryVillageRegistry.unregisterBedByHeadPos(world, headPos);
    }

    @Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"))
    private void examplemod$getStateForNeighborUpdate(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            WorldAccess world,
            BlockPos pos,
            BlockPos neighborPos,
            CallbackInfoReturnable<BlockState> cir
    ) {
        BlockState newState = cir.getReturnValue();

        if (!(world instanceof World realWorld) || realWorld.isClient) return;

        if (newState.isAir()) {
            BlockPos headPos = getHeadPos(pos, state);
            FurryVillageRegistry.unregisterBedByHeadPos(realWorld, headPos);
        }
    }

    private static BlockPos getHeadPos(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof BedBlock)) {
            return pos;
        }

        return state.get(BedBlock.PART) == BedPart.HEAD
                ? pos
                : pos.offset(state.get(BedBlock.FACING));
    }
}