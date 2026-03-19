package com.example.mixin;

import com.example.registry.FurryVillageRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.block.Block;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockPlacementMixin {

    @Inject(method = "onPlaced", at = @At("TAIL"))
    private void examplemod$onPlaced(
            World world,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack itemStack,
            CallbackInfo ci
    ) {
        if (state.isOf(Blocks.COMPOSTER)) {
            FurryVillageRegistry.registerComposter(world, pos, state);
        }
    }
}