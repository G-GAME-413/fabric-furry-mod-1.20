package com.example.registry;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

public final class FurryVillageRegistry {
    private static final boolean DEBUG = true;

    private FurryVillageRegistry() {}

    public static final int VILLAGE_RADIUS = FurryVillageState.VILLAGE_RADIUS;

    private static FurryVillageState state(World world) {
        if (!(world instanceof ServerWorld serverWorld)) {
            throw new IllegalStateException("Village registry can only be used on server side");
        }
        return FurryVillageState.get(serverWorld);
    }

    // ---------- CENTERS ----------

    public static void registerCenter(World world, BlockPos pos) {
        if (world.isClient) return;

        FurryVillageState state = state(world);
        state.addCenter(pos);
        state.recalculateBedsAfterCenterPlacement(pos);

        if (DEBUG) {
            System.out.println("[VillageRegistry] REGISTER CENTER " + pos + ", total centers=" + state.getCenters().size());
        }
    }

    public static void unregisterCenter(World world, BlockPos pos) {
        if (world.isClient) return;

        FurryVillageState state = state(world);
        state.removeCenter(pos);

        if (DEBUG) {
            System.out.println("[VillageRegistry] UNREGISTER CENTER " + pos + ", total centers=" + state.getCenters().size());
        }
    }

    public static boolean containsCenter(World world, BlockPos pos) {
        if (world.isClient) return false;
        return state(world).containsCenter(pos);
    }

    public static BlockPos findNearestCenter(World world, BlockPos origin, int radius) {
        if (world.isClient) return null;
        return state(world).findNearestCenter(origin, radius);
    }

    // ---------- BEDS ----------

    public static void registerBed(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;
        if (!(state.getBlock() instanceof BedBlock)) return;
        if (state.get(BedBlock.PART) != BedPart.HEAD) return;

        FurryVillageState villageState = state(world);
        villageState.addBed(pos);

        if (DEBUG) {
            System.out.println("[VillageRegistry] REGISTER BED " + pos
                    + ", owner=" + villageState.getVillageOwnerOfBed(pos)
                    + ", total beds=" + villageState.getBeds().size());
        }
    }

    public static void unregisterBed(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;
        if (!(state.getBlock() instanceof BedBlock)) return;
        if (state.get(BedBlock.PART) != BedPart.HEAD) return;

        FurryVillageState villageState = state(world);
        villageState.removeBed(pos);

        if (DEBUG) {
            System.out.println("[VillageRegistry] REGISTER BED " + pos
                    + ", owner=" + villageState.getVillageOwnerOfBed(pos)
                    + ", total beds=" + villageState.getBeds().size());
        }
    }

    public static BlockPos getVillageOwnerOfBed(World world, BlockPos bedPos) {
        if (world.isClient) return null;
        return state(world).getVillageOwnerOfBed(bedPos);
    }

    public static List<BlockPos> getBedsForVillage(World world, BlockPos villageCenter) {
        if (world.isClient) return List.of();
        return state(world).getBedsForVillage(villageCenter);
    }

    public static BlockPos findFreeBedForVillage(World world, BlockPos villageCenter) {
        if (world.isClient) return null;
        return state(world).findFreeBedForVillage(villageCenter);
    }

    public static boolean reserveBed(World world, BlockPos bedPos, UUID entityUuid) {
        if (world.isClient) return false;

        boolean result = state(world).reserveBed(bedPos, entityUuid);

        if (DEBUG) {
            System.out.println("[VillageRegistry] RESERVE BED " + bedPos
                    + " by " + entityUuid + " -> " + result);
        }

        return result;
    }

    public static void releaseBed(World world, BlockPos bedPos, UUID entityUuid) {
        if (world.isClient) return;
        state(world).releaseBed(bedPos, entityUuid);

        if (DEBUG) {
            System.out.println("[VillageRegistry] RELEASE BED " + bedPos
                    + " by " + entityUuid);
        }
    }

    public static void releaseAllBedsReservedBy(World world, UUID entityUuid) {
        if (world.isClient) return;
        state(world).releaseAllBedsReservedBy(entityUuid);
    }

    public static boolean isBedReservedBy(World world, BlockPos bedPos, UUID entityUuid) {
        if (world.isClient) return false;
        return state(world).isBedReservedBy(bedPos, entityUuid);
    }

    // ---------- COMPOSTERS ----------

    public static void registerComposter(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;
        if (!state.isOf(net.minecraft.block.Blocks.COMPOSTER)) return;

        FurryVillageState villageState = state(world);
        villageState.addComposter(pos);
    }

    public static void unregisterComposter(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;
        if (!state.isOf(net.minecraft.block.Blocks.COMPOSTER)) return;

        FurryVillageState villageState = state(world);
        villageState.removeComposter(pos);
    }

    public static BlockPos getVillageOwnerOfComposter(World world, BlockPos pos) {
        if (world.isClient) return null;
        return state(world).getVillageOwnerOfComposter(pos);
    }

    public static List<BlockPos> getCompostersForVillage(World world, BlockPos villageCenter) {
        if (world.isClient) return List.of();
        return state(world).getCompostersForVillage(villageCenter);
    }

    public static BlockPos findFreeComposterForVillage(World world, BlockPos villageCenter) {
        if (world.isClient) return null;
        return state(world).findFreeComposterForVillage(villageCenter);
    }

    public static boolean reserveComposter(World world, BlockPos pos, UUID entityUuid) {
        if (world.isClient) return false;
        return state(world).reserveComposter(pos, entityUuid);
    }

    public static void releaseComposter(World world, BlockPos pos, UUID entityUuid) {
        if (world.isClient) return;
        state(world).releaseComposter(pos, entityUuid);
    }

    public static boolean isComposterReservedBy(World world, BlockPos pos, UUID entityUuid) {
        if (world.isClient) return false;
        return state(world).isComposterReservedBy(pos, entityUuid);
    }
}