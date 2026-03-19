package com.example.entity.goal;

import com.example.entity.FurryFox;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class FarmerWorkGoal extends Goal {

    private final FurryFox fox;
    private final double speed;

    private BlockPos targetPos;
    private TargetType targetType = TargetType.NONE;

    private int nextSearchTick = 0;

    private static final int FARM_RADIUS = 8;
    private static final int SEARCH_INTERVAL_MIN = 40; // 2 сек
    private static final int SEARCH_INTERVAL_MAX = 60; // 3 сек

    private boolean performingWork = false;
    private int workFinishTick = 0;

    private int nextBoneMealTick = 0;

    private static final int WORK_ANIMATION_DURATION = 80; // 4 секунды

    private enum TargetType {
        NONE,
        HARVEST,
        PLANT,
        BONE_MEAL
    }

    private static final Map<BlockPos, Reservation> RESERVED_TARGETS = new HashMap<>();

    private static final int RESERVATION_TTL = 20 * 15; // 15 секунд

    private static class Reservation {
        private final UUID owner;
        private final int expireTick;

        private Reservation(UUID owner, int expireTick) {
            this.owner = owner;
            this.expireTick = expireTick;
        }
    }

    private void cleanupExpiredReservations() {
        RESERVED_TARGETS.entrySet().removeIf(entry -> fox.age >= entry.getValue().expireTick);
    }

    private boolean isReservedByOther(BlockPos pos) {
        Reservation reservation = RESERVED_TARGETS.get(pos);
        if (reservation == null) {
            return false;
        }

        if (fox.age >= reservation.expireTick) {
            RESERVED_TARGETS.remove(pos);
            return false;
        }

        return !reservation.owner.equals(fox.getUuid());
    }

    private void reserveTarget(BlockPos pos) {
        RESERVED_TARGETS.put(pos.toImmutable(), new Reservation(fox.getUuid(), fox.age + RESERVATION_TTL));
    }

    private void releaseTarget(BlockPos pos) {
        if (pos == null) return;

        Reservation reservation = RESERVED_TARGETS.get(pos);
        if (reservation != null && reservation.owner.equals(fox.getUuid())) {
            RESERVED_TARGETS.remove(pos);
        }
    }

    private void collectNearbyDrops(World world, BlockPos pos) {
        List<ItemEntity> items = world.getEntitiesByClass(
                ItemEntity.class,
                new net.minecraft.util.math.Box(pos).expand(1.5),
                entity -> entity.isAlive() && !entity.getStack().isEmpty()
        );

        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getStack();
            if (!fox.isFarmerRelevantItem(stack.getItem())) {
                continue;
            }

            ItemStack copy = stack.copy();
            boolean inserted = fox.addToFarmerInventory(copy);

            if (inserted) {
                itemEntity.discard();
            } else {
                int moved = stack.getCount() - copy.getCount();
                if (moved > 0) {
                    stack.setCount(copy.getCount());
                }
            }
        }
    }

    public FarmerWorkGoal(FurryFox fox, double speed) {
        this.fox = fox;
        this.speed = speed;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!fox.isFarmer()) {
            return false;
        }

        if (!fox.isVillageBound() || fox.getVillageCenter() == null) {
            return false;
        }

        if (!fox.hasComposter() || fox.getComposterPos() == null) {
            return false;
        }

        if (!fox.isFarmerWorkTime()) {
            return false;
        }

        if (!fox.hasPlantingItem() && !fox.hasBoneMeal()) {
            // Разрешаем работать только если есть зрелые культуры для сбора
            BlockPos harvestTarget = findNearestMatureCrop();
            if (harvestTarget == null) {
                return false;
            }
        }

        if (fox.age < nextSearchTick) {
            return false;
        }

        cleanupExpiredReservations();

        this.findWorkTarget();
        if (this.targetPos != null) {
            reserveTarget(this.targetPos);
            return true;
        }

        return false;
    }

    @Override
    public boolean shouldContinue() {
        if (!fox.isFarmer()) {
            return false;
        }

        if (!fox.isVillageBound() || fox.getVillageCenter() == null) {
            return false;
        }

        if (!fox.hasComposter() || fox.getComposterPos() == null) {
            return false;
        }

        if (!fox.isFarmerWorkTime()) {
            return false;
        }

        return this.targetPos != null;
    }

    @Override
    public void start() {
        if (this.targetPos != null) {
            fox.getNavigation().startMovingTo(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    speed
            );
        }
    }

    @Override
    public void stop() {
        releaseTarget(this.targetPos);

        this.targetPos = null;
        this.targetType = TargetType.NONE;
        this.performingWork = false;
        this.workFinishTick = 0;
        fox.getNavigation().stop();

        this.nextSearchTick = fox.age + SEARCH_INTERVAL_MIN
                + fox.getRandom().nextInt(SEARCH_INTERVAL_MAX - SEARCH_INTERVAL_MIN + 1);
    }

    private boolean canStillHarvest(World world, BlockPos cropPos) {
        BlockState state = world.getBlockState(cropPos);

        if (!(state.getBlock() instanceof CropBlock crop)) {
            return false;
        }

        return crop.isMature(state);
    }

    private boolean canStillPlant(World world, BlockPos plantPos) {
        BlockPos farmlandPos = plantPos.down();

        BlockState farmlandState = world.getBlockState(farmlandPos);
        BlockState plantState = world.getBlockState(plantPos);

        return farmlandState.isOf(Blocks.FARMLAND) && plantState.isAir();
    }

    private boolean canPerformCurrentTarget(World world) {
        if (this.targetPos == null) {
            return false;
        }

        return switch (this.targetType) {
            case HARVEST -> canStillHarvest(world, this.targetPos);
            case PLANT -> canActuallyPlantAt(world, this.targetPos);
            case BONE_MEAL -> canActuallyUseBoneMealAt(world, this.targetPos);
            default -> false;
        };
    }

    private boolean canActuallyPlantAt(World world, BlockPos plantPos) {
        if (!canStillPlant(world, plantPos)) {
            return false;
        }

        BlockState cropState = chooseCropStateForPlanting(world, plantPos, null);
        if (cropState == null) {
            return false;
        }

        Item plantingItem = plantingItemForState(cropState);
        return plantingItem != null && fox.countItemInFarmerInventory(plantingItem) > 0;
    }

    private boolean canActuallyUseBoneMealAt(World world, BlockPos cropPos) {
        if (!fox.hasBoneMeal()) {
            return false;
        }

        BlockState state = world.getBlockState(cropPos);
        if (!(state.getBlock() instanceof CropBlock crop)) {
            return false;
        }

        return !crop.isMature(state);
    }

    @Override
    public void tick() {
        if (this.targetPos == null) {
            return;
        }

        fox.getLookControl().lookAt(
                this.targetPos.getX() + 0.5,
                this.targetPos.getY() + 0.5,
                this.targetPos.getZ() + 0.5
        );

        if (this.performingWork) {
            fox.getNavigation().stop();

            if (fox.age >= this.workFinishTick) {
                World world = fox.getWorld();

                if (this.targetType == TargetType.HARVEST) {
                    if (canStillHarvest(world, this.targetPos)) {
                        harvestAndReplant(world, this.targetPos);
                    }
                } else if (this.targetType == TargetType.PLANT) {
                    if (canStillPlant(world, this.targetPos)) {
                        plantAt(world, this.targetPos);
                    }
                } else if (this.targetType == TargetType.BONE_MEAL) {
                    if (tryUseBoneMealOn(world, this.targetPos)) {
                        nextBoneMealTick = fox.age + 20 * 5; // 5 секунд
                    }
                }

                releaseTarget(this.targetPos);

                this.performingWork = false;
                this.targetPos = null;
                this.targetType = TargetType.NONE;

                this.nextSearchTick = fox.age + SEARCH_INTERVAL_MIN
                        + fox.getRandom().nextInt(SEARCH_INTERVAL_MAX - SEARCH_INTERVAL_MIN + 1);
            }

            return;
        }

        double distanceSq = fox.squaredDistanceTo(
                this.targetPos.getX() + 0.5,
                this.targetPos.getY() + 0.5,
                this.targetPos.getZ() + 0.5
        );

        if (distanceSq > 2.5D) {
            fox.getNavigation().startMovingTo(
                    this.targetPos.getX() + 0.5,
                    this.targetPos.getY(),
                    this.targetPos.getZ() + 0.5,
                    speed
            );
            return;
        }

        if (!canPerformCurrentTarget(fox.getWorld())) {
            releaseTarget(this.targetPos);
            this.targetPos = null;
            this.targetType = TargetType.NONE;
            this.performingWork = false;
            this.nextSearchTick = fox.age + SEARCH_INTERVAL_MIN
                    + fox.getRandom().nextInt(SEARCH_INTERVAL_MAX - SEARCH_INTERVAL_MIN + 1);
            fox.getNavigation().stop();
            return;
        }

        fox.getNavigation().stop();
        fox.triggerFarmAnimation();
        this.performingWork = true;
        this.workFinishTick = fox.age + WORK_ANIMATION_DURATION;
    }

    private void findWorkTarget() {
        this.targetPos = findNearestMatureCrop();
        if (this.targetPos != null) {
            this.targetType = TargetType.HARVEST;
            return;
        }

        this.targetPos = findNearestPlantableFarmland();
        if (this.targetPos != null && canActuallyPlantAt(fox.getWorld(), this.targetPos)) {
            this.targetType = TargetType.PLANT;
            return;
        }

        if (fox.age >= nextBoneMealTick && fox.countItemInFarmerInventory(Items.BONE_MEAL) >= 2) {
            this.targetPos = findNearestBonemealableCrop();
            if (this.targetPos != null) {
                this.targetType = TargetType.BONE_MEAL;
                return;
            }
        }

        this.targetType = TargetType.NONE;
    }

    private BlockPos findNearestMatureCrop() {
        World world = fox.getWorld();
        BlockPos center = fox.getComposterPos();
        if (center == null) return null;

        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -FARM_RADIUS; x <= FARM_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -FARM_RADIUS; z <= FARM_RADIUS; z++) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (state.getBlock() instanceof CropBlock crop && crop.isMature(state)) {
                        if (isReservedByOther(pos)) {
                            continue;
                        }

                        double dist = fox.squaredDistanceTo(
                                pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5
                        );

                        if (dist < bestDist) {
                            bestDist = dist;
                            bestPos = pos.toImmutable();
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    private BlockPos findNearestPlantableFarmland() {
        World world = fox.getWorld();
        BlockPos center = fox.getComposterPos();
        if (center == null) return null;

        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -FARM_RADIUS; x <= FARM_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -FARM_RADIUS; z <= FARM_RADIUS; z++) {
                    BlockPos farmlandPos = center.add(x, y, z);
                    BlockPos plantPos = farmlandPos.up();

                    if (!canPlantAt(world, farmlandPos, plantPos)) {
                        continue;
                    }

                    if (!canActuallyPlantAt(world, plantPos)) {
                        continue;
                    }

                    if (isReservedByOther(plantPos)) {
                        continue;
                    }

                    double dist = fox.squaredDistanceTo(
                            plantPos.getX() + 0.5,
                            plantPos.getY() + 0.5,
                            plantPos.getZ() + 0.5
                    );

                    if (dist < bestDist) {
                        bestDist = dist;
                        bestPos = plantPos.toImmutable();
                    }
                }
            }
        }

        return bestPos;
    }

    private boolean canPlantAt(World world, BlockPos farmlandPos, BlockPos plantPos) {
        BlockState farmlandState = world.getBlockState(farmlandPos);
        BlockState plantState = world.getBlockState(plantPos);

        return farmlandState.isOf(Blocks.FARMLAND) && plantState.isAir();
    }

    private void harvestAndReplant(World world, BlockPos cropPos) {
        if (!canStillHarvest(world, cropPos)) {
            return;
        }

        BlockState state = world.getBlockState(cropPos);
        if (!(state.getBlock() instanceof CropBlock)) {
            return;
        }

        Block cropBlock = state.getBlock();

        world.breakBlock(cropPos, true, fox);
        collectNearbyDrops(world, cropPos);

        if (canStillPlant(world, cropPos)) {
            BlockState replanted = chooseCropStateForPlanting(world, cropPos, cropBlock);
            if (replanted != null) {
                Item plantingItem = plantingItemForState(replanted);

                if (plantingItem != null && fox.countItemInFarmerInventory(plantingItem) > 0) {
                    if (fox.removeItemsFromFarmerInventory(plantingItem, 1)) {
                        world.setBlockState(cropPos, replanted);
                    }
                }
            }
        }
    }

    private Item plantingItemForState(BlockState state) {
        if (state.isOf(Blocks.WHEAT)) return Items.WHEAT_SEEDS;
        if (state.isOf(Blocks.BEETROOTS)) return Items.BEETROOT_SEEDS;
        if (state.isOf(Blocks.CARROTS)) return Items.CARROT;
        if (state.isOf(Blocks.POTATOES)) return Items.POTATO;
        return null;
    }

    private void plantAt(World world, BlockPos plantPos) {
        if (!canStillPlant(world, plantPos)) {
            return;
        }

        BlockState cropState = chooseCropStateForPlanting(world, plantPos, null);
        if (cropState != null) {
            Item plantingItem = plantingItemForState(cropState);

            if (plantingItem != null && fox.countItemInFarmerInventory(plantingItem) > 0) {
                if (fox.removeItemsFromFarmerInventory(plantingItem, 1)) {
                    world.setBlockState(plantPos, cropState);
                }
            }
        }
    }

    private boolean tryUseBoneMealOn(World world, BlockPos cropPos) {
        if (!fox.hasBoneMeal()) return false;

        BlockState state = world.getBlockState(cropPos);
        if (!(state.getBlock() instanceof CropBlock crop)) return false;
        if (crop.isMature(state)) return false;

        if (!fox.consumeOneBoneMeal()) return false;

        int currentAge = crop.getAge(state);
        int newAge = Math.min(currentAge + 1, crop.getMaxAge());
        world.setBlockState(cropPos, crop.withAge(newAge));
        return true;
    }

    private BlockState chooseCropStateForPlanting(World world, BlockPos plantPos, Block previousCropBlock) {
        BlockState preferredNeighbor = findNeighborCropState(world, plantPos);
        if (preferredNeighbor != null) {
            Item plantingItem = plantingItemForState(preferredNeighbor);
            if (plantingItem != null && fox.countItemInFarmerInventory(plantingItem) > 0) {
                return preferredNeighbor;
            }
        }

        if (previousCropBlock != null) {
            BlockState previousDefault = previousCropBlock.getDefaultState();
            Item plantingItem = plantingItemForState(previousDefault);
            if (plantingItem != null && fox.countItemInFarmerInventory(plantingItem) > 0) {
                return previousDefault;
            }
        }

        List<BlockState> candidates = new ArrayList<>();

        if (fox.countItemInFarmerInventory(Items.WHEAT_SEEDS) > 0) {
            candidates.add(Blocks.WHEAT.getDefaultState());
        }
        if (fox.countItemInFarmerInventory(Items.CARROT) > 0) {
            candidates.add(Blocks.CARROTS.getDefaultState());
        }
        if (fox.countItemInFarmerInventory(Items.POTATO) > 0) {
            candidates.add(Blocks.POTATOES.getDefaultState());
        }
        if (fox.countItemInFarmerInventory(Items.BEETROOT_SEEDS) > 0) {
            candidates.add(Blocks.BEETROOTS.getDefaultState());
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(fox.getRandom().nextInt(candidates.size()));
    }

    private BlockPos findNearestBonemealableCrop() {
        World world = fox.getWorld();
        BlockPos center = fox.getComposterPos();
        if (center == null) return null;

        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -FARM_RADIUS; x <= FARM_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -FARM_RADIUS; z <= FARM_RADIUS; z++) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (isReservedByOther(pos)) {
                        continue;
                    }

                    if (state.getBlock() instanceof CropBlock crop && !crop.isMature(state)) {
                        double dist = fox.squaredDistanceTo(
                                pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5
                        );

                        if (dist < bestDist) {
                            bestDist = dist;
                            bestPos = pos.toImmutable();
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    private BlockState findNeighborCropState(World world, BlockPos plantPos) {
        BlockPos[] neighbors = new BlockPos[] {
                plantPos.north(),
                plantPos.south(),
                plantPos.west(),
                plantPos.east()
        };

        List<BlockState> cropNeighbors = new ArrayList<>();

        for (BlockPos neighborPos : neighbors) {
            BlockState neighborState = world.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof CropBlock) {
                cropNeighbors.add(neighborState.getBlock().getDefaultState());
            }
        }

        if (cropNeighbors.isEmpty()) {
            return null;
        }

        return cropNeighbors.get(fox.getRandom().nextInt(cropNeighbors.size()));
    }
}