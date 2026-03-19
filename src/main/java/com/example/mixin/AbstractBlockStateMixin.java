package com.example.mixin;

import com.example.registry.FurryVillageRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractBlock.class)
public abstract class AbstractBlockStateMixin {

    @Inject(method = "onStateReplaced", at = @At("HEAD"))
    private void examplemod$onStateReplaced(
            BlockState state,
            World world,
            BlockPos pos,
            BlockState newState,
            boolean moved,
            CallbackInfo ci
    ) {
        if (!state.isOf(newState.getBlock()) && state.isOf(Blocks.COMPOSTER)) {
            FurryVillageRegistry.unregisterComposter(world, pos, state);
        }
    }
}