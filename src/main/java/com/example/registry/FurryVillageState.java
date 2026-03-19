package com.example.registry;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.*;

public class FurryVillageState extends PersistentState {

    public static final String STATE_ID = "examplemod_furry_village_state";

    public static final int VILLAGE_RADIUS = 24;

    private final LinkedHashMap<BlockPos, Long> centers = new LinkedHashMap<>();
    private final LinkedHashMap<BlockPos, BedEntry> beds = new LinkedHashMap<>();
    private long nextCenterOrder = 0L;

    public static class BedEntry {
        private BlockPos villageCenter; // null = ничейная кровать
        private UUID reservedBy;        // null = свободна

        public BedEntry(BlockPos villageCenter, UUID reservedBy) {
            this.villageCenter = villageCenter;
            this.reservedBy = reservedBy;
        }

        public BlockPos getVillageCenter() {
            return villageCenter;
        }

        public void setVillageCenter(BlockPos villageCenter) {
            this.villageCenter = villageCenter;
        }

        public UUID getReservedBy() {
            return reservedBy;
        }

        public void setReservedBy(UUID reservedBy) {
            this.reservedBy = reservedBy;
        }
    }

    public LinkedHashMap<BlockPos, Long> getCenters() {
        return centers;
    }

    public LinkedHashMap<BlockPos, BedEntry> getBeds() {
        return beds;
    }

    public void addCenter(BlockPos pos) {
        pos = pos.toImmutable();
        if (!centers.containsKey(pos)) {
            centers.put(pos, nextCenterOrder++);
            recalculateBedsAfterCenterPlacement(pos);
            recalculateCompostersAfterCenterPlacement(pos);
            markDirty();
        }
    }

    public void removeCenter(BlockPos centerPos) {
        centerPos = centerPos.toImmutable();
        if (centers.remove(centerPos) != null) {
            recalculateBedsAfterCenterRemoval(centerPos);
            recalculateCompostersAfterCenterRemoval(centerPos);
            markDirty();
        }
    }

    public boolean containsCenter(BlockPos pos) {
        return centers.containsKey(pos);
    }

    public void addBed(BlockPos bedHeadPos) {
        bedHeadPos = bedHeadPos.toImmutable();
        beds.putIfAbsent(bedHeadPos, new BedEntry(null, null));
        recalculateSingleBedOwner(bedHeadPos);
        markDirty();
    }

    public void removeBed(BlockPos bedHeadPos) {
        bedHeadPos = bedHeadPos.toImmutable();
        if (beds.remove(bedHeadPos) != null) {
            markDirty();
        }
    }

    public boolean containsBed(BlockPos bedHeadPos) {
        return beds.containsKey(bedHeadPos);
    }

    public BlockPos findNearestCenter(BlockPos origin, int radius) {
        BlockPos bestPos = null;
        long bestOrder = Long.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        double maxDistSq = radius * radius;

        for (Map.Entry<BlockPos, Long> entry : centers.entrySet()) {
            BlockPos centerPos = entry.getKey();
            double dist = origin.getSquaredDistance(centerPos);

            if (dist > maxDistSq) continue;

            long order = entry.getValue();

            if (bestPos == null || dist < bestDist || (dist == bestDist && order < bestOrder)) {
                bestPos = centerPos;
                bestDist = dist;
                bestOrder = order;
            }
        }

        return bestPos;
    }

    public List<BlockPos> getBedsForVillage(BlockPos villageCenter) {
        List<BlockPos> result = new ArrayList<>();

        for (Map.Entry<BlockPos, BedEntry> entry : beds.entrySet()) {
            BedEntry bed = entry.getValue();
            if (villageCenter.equals(bed.getVillageCenter())) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    public BlockPos findFreeBedForVillage(BlockPos villageCenter) {
        for (Map.Entry<BlockPos, BedEntry> entry : beds.entrySet()) {
            BedEntry bed = entry.getValue();
            if (villageCenter.equals(bed.getVillageCenter()) && bed.getReservedBy() == null) {
                return entry.getKey();
            }
        }

        return null;
    }

    public boolean reserveBed(BlockPos bedPos, UUID entityUuid) {
        BedEntry bed = beds.get(bedPos);
        if (bed == null) return false;

        if (bed.getReservedBy() == null || bed.getReservedBy().equals(entityUuid)) {
            bed.setReservedBy(entityUuid);
            markDirty();
            return true;
        }

        return false;
    }

    public void releaseBed(BlockPos bedPos, UUID entityUuid) {
        BedEntry bed = beds.get(bedPos);
        if (bed == null) return;

        if (entityUuid.equals(bed.getReservedBy())) {
            bed.setReservedBy(null);
            markDirty();
        }
    }

    public void releaseAllBedsReservedBy(UUID entityUuid) {
        boolean changed = false;

        for (BedEntry bed : beds.values()) {
            if (entityUuid.equals(bed.getReservedBy())) {
                bed.setReservedBy(null);
                changed = true;
            }
        }

        if (changed) {
            markDirty();
        }
    }

    public BlockPos getVillageOwnerOfBed(BlockPos bedPos) {
        BedEntry bed = beds.get(bedPos);
        return bed == null ? null : bed.getVillageCenter();
    }

    public boolean isBedReservedBy(BlockPos bedPos, UUID entityUuid) {
        BedEntry bed = beds.get(bedPos);
        return bed != null && entityUuid.equals(bed.getReservedBy());
    }

    public void recalculateAllBedOwnership() {
        for (BlockPos bedPos : new ArrayList<>(beds.keySet())) {
            recalculateSingleBedOwner(bedPos);
        }
        markDirty();
    }

    public void recalculateBedsAfterCenterPlacement(BlockPos newCenter) {
        for (BlockPos bedPos : new ArrayList<>(beds.keySet())) {
            if (isBedInsideVillageRadius(bedPos, newCenter)) {
                recalculateSingleBedOwner(bedPos);
            }
        }
        markDirty();
    }

    private void recalculateBedsAfterCenterRemoval(BlockPos removedCenter) {
        for (Map.Entry<BlockPos, BedEntry> entry : beds.entrySet()) {
            BedEntry bed = entry.getValue();
            if (removedCenter.equals(bed.getVillageCenter())) {
                bed.setVillageCenter(null);
                bed.setReservedBy(null);
                recalculateSingleBedOwner(entry.getKey());
            }
        }
    }

    public void recalculateSingleBedOwner(BlockPos bedPos) {
        BedEntry bed = beds.get(bedPos);
        if (bed == null) return;

        BlockPos owner = findOldestVillageContainingBed(bedPos);

        if (!Objects.equals(owner, bed.getVillageCenter())) {
            bed.setVillageCenter(owner);
            bed.setReservedBy(null); // при смене деревни бронь сбрасываем
            markDirty();
        }
    }

    private BlockPos findOldestVillageContainingBed(BlockPos bedPos) {
        BlockPos bestCenter = null;
        long bestOrder = Long.MAX_VALUE;

        for (Map.Entry<BlockPos, Long> entry : centers.entrySet()) {
            BlockPos centerPos = entry.getKey();
            long order = entry.getValue();

            if (!isBedInsideVillageRadius(bedPos, centerPos)) {
                continue;
            }

            if (bestCenter == null || order < bestOrder) {
                bestCenter = centerPos;
                bestOrder = order;
            }
        }

        return bestCenter;
    }

    private boolean isBedInsideVillageRadius(BlockPos bedPos, BlockPos centerPos) {
        return bedPos.getSquaredDistance(centerPos) <= (VILLAGE_RADIUS * VILLAGE_RADIUS);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        nbt.putLong("NextCenterOrder", nextCenterOrder);

        NbtList centersList = new NbtList();
        for (Map.Entry<BlockPos, Long> entry : centers.entrySet()) {
            NbtCompound centerNbt = new NbtCompound();
            centerNbt.putInt("X", entry.getKey().getX());
            centerNbt.putInt("Y", entry.getKey().getY());
            centerNbt.putInt("Z", entry.getKey().getZ());
            centerNbt.putLong("Order", entry.getValue());
            centersList.add(centerNbt);
        }
        nbt.put("Centers", centersList);

        NbtList bedsList = new NbtList();
        for (Map.Entry<BlockPos, BedEntry> entry : beds.entrySet()) {
            NbtCompound bedNbt = new NbtCompound();

            bedNbt.putInt("X", entry.getKey().getX());
            bedNbt.putInt("Y", entry.getKey().getY());
            bedNbt.putInt("Z", entry.getKey().getZ());

            BedEntry bed = entry.getValue();

            if (bed.getVillageCenter() != null) {
                bedNbt.putInt("VillageX", bed.getVillageCenter().getX());
                bedNbt.putInt("VillageY", bed.getVillageCenter().getY());
                bedNbt.putInt("VillageZ", bed.getVillageCenter().getZ());
            }

            if (bed.getReservedBy() != null) {
                bedNbt.putUuid("ReservedBy", bed.getReservedBy());
            }

            bedsList.add(bedNbt);
        }
        nbt.put("Beds", bedsList);

        NbtList compostersList = new NbtList();
        for (Map.Entry<BlockPos, ComposterEntry> entry : composters.entrySet()) {
            NbtCompound composterNbt = new NbtCompound();

            composterNbt.putInt("X", entry.getKey().getX());
            composterNbt.putInt("Y", entry.getKey().getY());
            composterNbt.putInt("Z", entry.getKey().getZ());

            ComposterEntry composter = entry.getValue();

            if (composter.getVillageCenter() != null) {
                composterNbt.putInt("VillageX", composter.getVillageCenter().getX());
                composterNbt.putInt("VillageY", composter.getVillageCenter().getY());
                composterNbt.putInt("VillageZ", composter.getVillageCenter().getZ());
            }

            if (composter.getReservedBy() != null) {
                composterNbt.putUuid("ReservedBy", composter.getReservedBy());
            }

            compostersList.add(composterNbt);
        }
        nbt.put("Composters", compostersList);

        return nbt;
    }

    public static FurryVillageState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        FurryVillageState state = new FurryVillageState();
        state.nextCenterOrder = nbt.getLong("NextCenterOrder");

        NbtList centersList = nbt.getList("Centers", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < centersList.size(); i++) {
            NbtCompound centerNbt = centersList.getCompound(i);
            BlockPos pos = new BlockPos(
                    centerNbt.getInt("X"),
                    centerNbt.getInt("Y"),
                    centerNbt.getInt("Z")
            );
            long order = centerNbt.getLong("Order");
            state.centers.put(pos, order);
        }

        NbtList bedsList = nbt.getList("Beds", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < bedsList.size(); i++) {
            NbtCompound bedNbt = bedsList.getCompound(i);

            BlockPos bedPos = new BlockPos(
                    bedNbt.getInt("X"),
                    bedNbt.getInt("Y"),
                    bedNbt.getInt("Z")
            );

            BlockPos villageCenter = null;
            if (bedNbt.contains("VillageX") && bedNbt.contains("VillageY") && bedNbt.contains("VillageZ")) {
                villageCenter = new BlockPos(
                        bedNbt.getInt("VillageX"),
                        bedNbt.getInt("VillageY"),
                        bedNbt.getInt("VillageZ")
                );
            }

            UUID reservedBy = bedNbt.containsUuid("ReservedBy") ? bedNbt.getUuid("ReservedBy") : null;

            state.beds.put(bedPos, new BedEntry(villageCenter, reservedBy));
        }

        NbtList compostersList = nbt.getList("Composters", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < compostersList.size(); i++) {
            NbtCompound composterNbt = compostersList.getCompound(i);

            BlockPos composterPos = new BlockPos(
                    composterNbt.getInt("X"),
                    composterNbt.getInt("Y"),
                    composterNbt.getInt("Z")
            );

            BlockPos villageCenter = null;
            if (composterNbt.contains("VillageX") && composterNbt.contains("VillageY") && composterNbt.contains("VillageZ")) {
                villageCenter = new BlockPos(
                        composterNbt.getInt("VillageX"),
                        composterNbt.getInt("VillageY"),
                        composterNbt.getInt("VillageZ")
                );
            }

            UUID reservedBy = composterNbt.containsUuid("ReservedBy") ? composterNbt.getUuid("ReservedBy") : null;

            state.composters.put(composterPos, new ComposterEntry(villageCenter, reservedBy));
        }

        return state;
    }

    public static FurryVillageState get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();

        return manager.getOrCreate(
                new PersistentState.Type<>(
                        FurryVillageState::new,
                        FurryVillageState::createFromNbt,
                        null
                ),
                STATE_ID
        );
    }


    // Фкрмкрство
    private final LinkedHashMap<BlockPos, ComposterEntry> composters = new LinkedHashMap<>();

    public static class ComposterEntry {
        private BlockPos villageCenter; // null = ничейный компостер
        private UUID reservedBy;        // null = свободен

        public ComposterEntry(BlockPos villageCenter, UUID reservedBy) {
            this.villageCenter = villageCenter;
            this.reservedBy = reservedBy;
        }

        public BlockPos getVillageCenter() {
            return villageCenter;
        }

        public void setVillageCenter(BlockPos villageCenter) {
            this.villageCenter = villageCenter;
        }

        public UUID getReservedBy() {
            return reservedBy;
        }

        public void setReservedBy(UUID reservedBy) {
            this.reservedBy = reservedBy;
        }
    }

    public LinkedHashMap<BlockPos, ComposterEntry> getComposters() {
        return composters;
    }

    public void addComposter(BlockPos pos) {
        pos = pos.toImmutable();
        composters.putIfAbsent(pos, new ComposterEntry(null, null));
        recalculateSingleComposterOwner(pos);
        markDirty();
    }

    public void removeComposter(BlockPos pos) {
        pos = pos.toImmutable();
        if (composters.remove(pos) != null) {
            markDirty();
        }
    }

    public boolean containsComposter(BlockPos pos) {
        return composters.containsKey(pos);
    }

    public List<BlockPos> getCompostersForVillage(BlockPos villageCenter) {
        List<BlockPos> result = new ArrayList<>();

        for (Map.Entry<BlockPos, ComposterEntry> entry : composters.entrySet()) {
            ComposterEntry composter = entry.getValue();
            if (villageCenter.equals(composter.getVillageCenter())) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    public BlockPos findFreeComposterForVillage(BlockPos villageCenter) {
        for (Map.Entry<BlockPos, ComposterEntry> entry : composters.entrySet()) {
            ComposterEntry composter = entry.getValue();
            if (villageCenter.equals(composter.getVillageCenter()) && composter.getReservedBy() == null) {
                return entry.getKey();
            }
        }

        return null;
    }

    public boolean reserveComposter(BlockPos pos, UUID entityUuid) {
        ComposterEntry composter = composters.get(pos);
        if (composter == null) return false;

        if (composter.getReservedBy() == null || composter.getReservedBy().equals(entityUuid)) {
            composter.setReservedBy(entityUuid);
            markDirty();
            return true;
        }

        return false;
    }

    public void releaseComposter(BlockPos pos, UUID entityUuid) {
        ComposterEntry composter = composters.get(pos);
        if (composter == null) return;

        if (entityUuid.equals(composter.getReservedBy())) {
            composter.setReservedBy(null);
            markDirty();
        }
    }

    public boolean isComposterReservedBy(BlockPos pos, UUID entityUuid) {
        ComposterEntry composter = composters.get(pos);
        return composter != null && entityUuid.equals(composter.getReservedBy());
    }

    public BlockPos getVillageOwnerOfComposter(BlockPos pos) {
        ComposterEntry composter = composters.get(pos);
        return composter == null ? null : composter.getVillageCenter();
    }

    public void recalculateCompostersAfterCenterPlacement(BlockPos newCenter) {
        for (BlockPos composterPos : new ArrayList<>(composters.keySet())) {
            if (isBlockInsideVillageRadius(composterPos, newCenter)) {
                recalculateSingleComposterOwner(composterPos);
            }
        }
        markDirty();
    }

    private void recalculateCompostersAfterCenterRemoval(BlockPos removedCenter) {
        for (Map.Entry<BlockPos, ComposterEntry> entry : composters.entrySet()) {
            ComposterEntry composter = entry.getValue();
            if (removedCenter.equals(composter.getVillageCenter())) {
                composter.setVillageCenter(null);
                composter.setReservedBy(null);
                recalculateSingleComposterOwner(entry.getKey());
            }
        }
    }

    public void recalculateSingleComposterOwner(BlockPos composterPos) {
        ComposterEntry composter = composters.get(composterPos);
        if (composter == null) return;

        BlockPos owner = findOldestVillageContainingBlock(composterPos);

        if (!Objects.equals(owner, composter.getVillageCenter())) {
            composter.setVillageCenter(owner);
            composter.setReservedBy(null);
            markDirty();
        }
    }

    private BlockPos findOldestVillageContainingBlock(BlockPos pos) {
        BlockPos bestCenter = null;
        long bestOrder = Long.MAX_VALUE;

        for (Map.Entry<BlockPos, Long> entry : centers.entrySet()) {
            BlockPos centerPos = entry.getKey();
            long order = entry.getValue();

            if (!isBlockInsideVillageRadius(pos, centerPos)) {
                continue;
            }

            if (bestCenter == null || order < bestOrder) {
                bestCenter = centerPos;
                bestOrder = order;
            }
        }

        return bestCenter;
    }

    private boolean isBlockInsideVillageRadius(BlockPos pos, BlockPos centerPos) {
        return pos.getSquaredDistance(centerPos) <= (VILLAGE_RADIUS * VILLAGE_RADIUS);
    }

}