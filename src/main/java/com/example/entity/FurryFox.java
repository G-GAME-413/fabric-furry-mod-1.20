package com.example.entity;

import com.example.entity.goal.ComposterWorkGoal;
import com.example.entity.goal.FarmerWorkGoal;
import com.example.entity.goal.ReturnToVillageCenterGoal;
import com.example.entity.goal.SleepInBedGoal;
import com.example.registry.FurryVillageRegistry;
import com.example.trading.FurryFoxTrades;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Merchant;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.world.World;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.player.PlayerEntity;

public class FurryFox extends PathAwareEntity implements GeoEntity, Merchant {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public FurryFox(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }


    // Village
    private BlockPos villageCenter;
    private BlockPos bedPos;
    private boolean villageBound = false;

    private boolean needsVillageSearch = false;
    private int nextVillageSearchAge = -1;
    private int villageSearchDeadlineAge = -1;

    private static final int VILLAGE_BIND_RADIUS = 32;
    private static final int VILLAGE_REBIND_RADIUS = 48;
    private static final int VILLAGE_WANDER_RADIUS = 24;

    private static final int REBIND_TOTAL_TIME = 20 * 60 * 3; // 3 минуты
    private static final int REBIND_FIRST_MIN = 20 * 5;       // 5 сек
    private static final int REBIND_FIRST_MAX = 20 * 15;      // 15 сек
    private static final int REBIND_REPEAT_MIN = 20 * 30;     // 30 сек
    private static final int REBIND_REPEAT_MAX = 20 * 45;     // 45 сек

    public boolean isVillageBound() {
        return this.villageBound;
    }

    public BlockPos getVillageCenter() {
        return this.villageCenter;
    }

    public void setVillageCenter(BlockPos pos) {
        this.villageCenter = pos.toImmutable();
        this.villageBound = true;
        this.needsVillageSearch = false;
        this.nextVillageSearchAge = -1;
        this.villageSearchDeadlineAge = -1;
        this.setPositionTarget(this.villageCenter, VILLAGE_WANDER_RADIUS);

        this.assignBedIfPossible();

        if (this.isFarmer()) {
            this.assignComposterIfPossible();
        }
    }

    public void clearVillageCenter() {
        this.clearBed();
        this.clearComposter();
        this.villageCenter = null;
        this.villageBound = false;
    }

    public boolean isTooFarFromVillage() {
        if (this.villageCenter == null) return false;

        return this.squaredDistanceTo(
                this.villageCenter.getX() + 0.5,
                this.villageCenter.getY() + 0.5,
                this.villageCenter.getZ() + 0.5
        ) > (VILLAGE_WANDER_RADIUS * VILLAGE_WANDER_RADIUS);
    }

    public boolean bindToNearestVillageCenter(int radius) {
        BlockPos nearest = FurryVillageRegistry.findNearestCenter(this.getWorld(), this.getBlockPos(), radius);
        if (nearest != null) {
            this.setVillageCenter(nearest);
            return true;
        }
        return false;
    }

    public void onVillageCenterDestroyed(BlockPos destroyedCenter) {
        if (this.villageCenter == null || !this.villageCenter.equals(destroyedCenter)) {
            return;
        }

        this.clearBed();
        this.clearComposter();
        this.villageCenter = null;
        this.villageBound = false;

        this.needsVillageSearch = true;
        this.nextVillageSearchAge = this.age + REBIND_FIRST_MIN + this.random.nextInt(REBIND_FIRST_MAX - REBIND_FIRST_MIN + 1);
        this.villageSearchDeadlineAge = this.age + REBIND_TOTAL_TIME;
    }

    @Override
    public boolean cannotDespawn() {
        return this.villageBound;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return !this.villageBound;
    }

    @Override
    public net.minecraft.entity.EntityData initialize(
            net.minecraft.world.ServerWorldAccess world,
            net.minecraft.world.LocalDifficulty difficulty,
            net.minecraft.entity.SpawnReason spawnReason,
            net.minecraft.entity.EntityData entityData
    ) {
        net.minecraft.entity.EntityData data = super.initialize(world, difficulty, spawnReason, entityData);

        if (!this.bindToNearestVillageCenter(VILLAGE_BIND_RADIUS)) {
            this.clearVillageCenter();
        }

        return data;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) {
            return;
        }

        this.tickVillageRebindSearch();
        this.tickVillageCenterValidation();
        this.tickBedValidation();
        this.tickComposterValidation();
    }

    private void tickVillageRebindSearch() {
        if (!this.needsVillageSearch) {
            return;
        }

        if (this.age < this.nextVillageSearchAge) {
            return;
        }

        boolean found = this.bindToNearestVillageCenter(VILLAGE_REBIND_RADIUS);

        if (found) {
            return;
        }

        if (this.age >= this.villageSearchDeadlineAge) {
            this.needsVillageSearch = false;
            this.clearVillageCenter();
            return;
        }

        this.nextVillageSearchAge = this.age + REBIND_REPEAT_MIN
                + this.random.nextInt(REBIND_REPEAT_MAX - REBIND_REPEAT_MIN + 1);
    }

    private void tickVillageCenterValidation() {
        // Страховочная проверка раз в 10 секунд
        if (this.age % 200 != 0) {
            return;
        }

        // Если моб уже потерял центр и ищет новый, проверять нечего
        if (!this.villageBound || this.villageCenter == null || this.needsVillageSearch) {
            return;
        }

        if (!FurryVillageRegistry.containsCenter(this.getWorld(), this.villageCenter)) {
            this.onVillageCenterDestroyed(this.villageCenter);
        }
    }

    @Override
    public void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Specialization", getCurrentSpecialization().name());

        nbt.putBoolean("SleepingInBed", this.isSleepingInBed());
        nbt.putBoolean("VillageBound", this.villageBound);
        nbt.putBoolean("NeedsVillageSearch", this.needsVillageSearch);
        nbt.putInt("NextVillageSearchAge", this.nextVillageSearchAge);
        nbt.putInt("VillageSearchDeadlineAge", this.villageSearchDeadlineAge);

        if (this.villageCenter != null) {
            nbt.putInt("VillageCenterX", this.villageCenter.getX());
            nbt.putInt("VillageCenterY", this.villageCenter.getY());
            nbt.putInt("VillageCenterZ", this.villageCenter.getZ());
        }

        if (this.bedPos != null) {
            nbt.putInt("BedX", this.bedPos.getX());
            nbt.putInt("BedY", this.bedPos.getY());
            nbt.putInt("BedZ", this.bedPos.getZ());
        }

        if (this.composterPos != null) {
            nbt.putInt("ComposterX", this.composterPos.getX());
            nbt.putInt("ComposterY", this.composterPos.getY());
            nbt.putInt("ComposterZ", this.composterPos.getZ());
        }

        NbtList farmerInventoryList = new NbtList();
        for (int i = 0; i < this.farmerInventory.size(); i++) {
            ItemStack stack = this.farmerInventory.getStack(i);
            if (!stack.isEmpty()) {
                NbtCompound stackNbt = (NbtCompound) stack.encode(this.getRegistryManager());
                stackNbt.putByte("Slot", (byte) i);
                farmerInventoryList.add(stackNbt);
            }
        }
        nbt.put("FarmerInventory", farmerInventoryList);
    }

    @Override
    public void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.contains("Specialization")) {
            try {
                Specialization spec = Specialization.valueOf(nbt.getString("Specialization"));
                this.dataTracker.set(SPECIALIZATION, spec.ordinal());
            } catch (IllegalArgumentException e) {
                this.dataTracker.set(SPECIALIZATION, Specialization.NONE.ordinal());
            }
        }

        this.setSleepingInBed(nbt.getBoolean("SleepingInBed"));
        this.villageBound = nbt.getBoolean("VillageBound");
        this.needsVillageSearch = nbt.getBoolean("NeedsVillageSearch");
        this.nextVillageSearchAge = nbt.getInt("NextVillageSearchAge");
        this.villageSearchDeadlineAge = nbt.getInt("VillageSearchDeadlineAge");

        if (nbt.contains("VillageCenterX") && nbt.contains("VillageCenterY") && nbt.contains("VillageCenterZ")) {
            this.villageCenter = new BlockPos(
                    nbt.getInt("VillageCenterX"),
                    nbt.getInt("VillageCenterY"),
                    nbt.getInt("VillageCenterZ")
            );

            if (this.villageBound) {
                this.setPositionTarget(this.villageCenter, VILLAGE_WANDER_RADIUS);
            }
        } else {
            this.villageCenter = null;
        }

        if (nbt.contains("BedX") && nbt.contains("BedY") && nbt.contains("BedZ")) {
            this.bedPos = new BlockPos(
                    nbt.getInt("BedX"),
                    nbt.getInt("BedY"),
                    nbt.getInt("BedZ")
            );
        } else {
            this.bedPos = null;
        }

        if (nbt.contains("ComposterX") && nbt.contains("ComposterY") && nbt.contains("ComposterZ")) {
            this.composterPos = new BlockPos(
                    nbt.getInt("ComposterX"),
                    nbt.getInt("ComposterY"),
                    nbt.getInt("ComposterZ")
            );
        } else {
            this.composterPos = null;
        }

        for (int i = 0; i < this.farmerInventory.size(); i++) {
            this.farmerInventory.setStack(i, ItemStack.EMPTY);
        }

        if (nbt.contains("FarmerInventory")) {
            NbtList farmerInventoryList = nbt.getList("FarmerInventory", 10);
            for (int i = 0; i < farmerInventoryList.size(); i++) {
                NbtCompound stackNbt = farmerInventoryList.getCompound(i);
                int slot = stackNbt.getByte("Slot") & 255;

                ItemStack decoded = ItemStack.fromNbt(this.getRegistryManager(), stackNbt).orElse(ItemStack.EMPTY);
                if (slot >= 0 && slot < this.farmerInventory.size()) {
                    this.farmerInventory.setStack(slot, decoded);
                }
            }
        }

        this.offers = null;
    }

    private void tickBedValidation() {
        if (this.age % 200 != 0) {
            return;
        }

        if (!this.villageBound || this.villageCenter == null) {
            return;
        }

        this.validateAssignedBed();

        if (this.bedPos == null) {
            this.assignBedIfPossible();
        }
    }

    private void tickComposterValidation() {
        if (this.age % 200 != 0) {
            return;
        }

        if (!this.villageBound || this.villageCenter == null || !this.isFarmer()) {
            return;
        }

        this.validateAssignedComposter();

        if (this.composterPos == null) {
            this.assignComposterIfPossible();
        }
    }

    private void releaseReservedBedIfNeeded() {
        if (!this.getWorld().isClient && this.bedPos != null) {
            FurryVillageRegistry.releaseBed(this.getWorld(), this.bedPos, this.getUuid());
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED) {
            releaseReservedBedIfNeeded();
            releaseReservedComposterIfNeeded();
        }

        super.remove(reason);
    }


    // Sleep
    public BlockPos getBedPos() {
        return this.bedPos;
    }

    public boolean hasBed() {
        return this.bedPos != null;
    }

    public void setBedPos(BlockPos bedPos) {
        this.bedPos = bedPos == null ? null : bedPos.toImmutable();
    }

    public void clearBed() {
        if (!this.getWorld().isClient && this.bedPos != null) {
            if (FurryVillageRegistry.isBedReservedBy(this.getWorld(), this.bedPos, this.getUuid())) {
                FurryVillageRegistry.releaseBed(this.getWorld(), this.bedPos, this.getUuid());
            }
        }
        this.bedPos = null;
    }

    public boolean assignBedIfPossible() {
        if (this.getWorld().isClient) return false;
        if (!this.villageBound || this.villageCenter == null) return false;
        if (this.bedPos != null) return true;

        System.out.println("[FurryFox] " + this.getUuid() + " trying to assign bed for village " + this.villageCenter);

        BlockPos freeBed = FurryVillageRegistry.findFreeBedForVillage(this.getWorld(), this.villageCenter);
        System.out.println("[FurryFox] free bed found: " + freeBed);

        if (freeBed == null) return false;

        boolean reserved = FurryVillageRegistry.reserveBed(this.getWorld(), freeBed, this.getUuid());
        if (reserved) {
            this.bedPos = freeBed;
            System.out.println("[FurryFox] assigned bed: " + freeBed);
            return true;
        }

        return false;
    }

    public void validateAssignedBed() {
        if (this.getWorld().isClient || this.bedPos == null) return;

        BlockPos ownerVillage = FurryVillageRegistry.getVillageOwnerOfBed(this.getWorld(), this.bedPos);

        if (ownerVillage == null || !ownerVillage.equals(this.villageCenter)) {
            System.out.println("[FurryFox] bed invalid (wrong village), clearing");
            this.clearBed();
            return;
        }

        if (!FurryVillageRegistry.isBedReservedBy(this.getWorld(), this.bedPos, this.getUuid())) {
            System.out.println("[FurryFox] bed not reserved by me, clearing");
            this.clearBed();
        }
    }

    public void validateAssignedComposter() {
        if (this.getWorld().isClient || this.composterPos == null) return;

        BlockPos ownerVillage = FurryVillageRegistry.getVillageOwnerOfComposter(this.getWorld(), this.composterPos);

        if (ownerVillage == null || !ownerVillage.equals(this.villageCenter)) {
            this.clearComposter();
            return;
        }

        if (!FurryVillageRegistry.isComposterReservedBy(this.getWorld(), this.composterPos, this.getUuid())) {
            this.clearComposter();
        }
    }

    public boolean isSleepTime() {
        long time = this.getWorld().getTimeOfDay() % 24000L;
        return time >= 13000L && time <= 23000L;
    }

    public boolean isSleepingInBed() {
        return this.dataTracker.get(SLEEPING);
    }

    public void setSleepingInBed(boolean sleeping) {
        this.dataTracker.set(SLEEPING, sleeping);
    }

    public void startSleeping() {
        this.dataTracker.set(SLEEPING, true);
    }

    public void wakeUp() {
        this.dataTracker.set(SLEEPING, false);
    }

    public boolean canSleepNow() {
        return this.isVillageBound()
                && this.getVillageCenter() != null
                && this.isSleepTime();
    }

    private PlayState sleepPredicate(AnimationState<FurryFox> state) {
        if (this.isSleepingInBed()) {
            state.getController().setAnimation(
                    RawAnimation.begin().thenLoop("sleep")
            );
            return PlayState.CONTINUE;
        }

        return PlayState.STOP;
    }


    // Farmer logic
    private final SimpleInventory farmerInventory = new SimpleInventory(18);

    public SimpleInventory getFarmerInventory() {
        return this.farmerInventory;
    }

    public boolean isFarmerRelevantItem(Item item) {
        return item == Items.WHEAT_SEEDS
                || item == Items.BEETROOT_SEEDS
                || item == Items.CARROT
                || item == Items.POTATO
                || item == Items.WHEAT
                || item == Items.BEETROOT
                || item == Items.BONE_MEAL;
    }

    public boolean isCompostableFarmerItem(Item item) {
        return item == Items.WHEAT_SEEDS
                || item == Items.BEETROOT_SEEDS
                || item == Items.WHEAT
                || item == Items.BEETROOT
                || item == Items.CARROT
                || item == Items.POTATO;
    }

    public boolean addToFarmerInventory(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!isFarmerRelevantItem(stack.getItem())) return false;

        for (int i = 0; i < this.farmerInventory.size(); i++) {
            ItemStack slot = this.farmerInventory.getStack(i);

            if (slot.isEmpty()) {
                this.farmerInventory.setStack(i, stack.copy());
                stack.setCount(0);
                return true;
            }

            if (ItemStack.areItemsAndComponentsEqual(slot, stack) && slot.getCount() < slot.getMaxCount()) {
                int move = Math.min(stack.getCount(), slot.getMaxCount() - slot.getCount());
                slot.increment(move);
                stack.decrement(move);

                if (stack.isEmpty()) {
                    return true;
                }
            }
        }

        return stack.isEmpty();
    }

    public boolean isFarmerInventoryFull() {
        for (int i = 0; i < this.farmerInventory.size(); i++) {
            if (this.farmerInventory.getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int countItemInFarmerInventory(Item item) {
        int total = 0;

        for (int i = 0; i < this.farmerInventory.size(); i++) {
            ItemStack stack = this.farmerInventory.getStack(i);
            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }

        return total;
    }

    public boolean removeItemsFromFarmerInventory(Item item, int count) {
        int remaining = count;

        for (int i = 0; i < this.farmerInventory.size(); i++) {
            ItemStack stack = this.farmerInventory.getStack(i);
            if (!stack.isOf(item)) continue;

            int take = Math.min(stack.getCount(), remaining);
            stack.decrement(take);
            remaining -= take;

            if (stack.isEmpty()) {
                this.farmerInventory.setStack(i, ItemStack.EMPTY);
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return remaining <= 0;
    }

    public boolean hasPlantingItem() {
        return countItemInFarmerInventory(Items.WHEAT_SEEDS) > 0
                || countItemInFarmerInventory(Items.BEETROOT_SEEDS) > 0
                || countItemInFarmerInventory(Items.CARROT) > 0
                || countItemInFarmerInventory(Items.POTATO) > 0;
    }

    public Item getPlantingItemForCropBlock(Block block) {
        if (block == Blocks.WHEAT && countItemInFarmerInventory(Items.WHEAT_SEEDS) > 0) {
            return Items.WHEAT_SEEDS;
        }
        if (block == Blocks.BEETROOTS && countItemInFarmerInventory(Items.BEETROOT_SEEDS) > 0) {
            return Items.BEETROOT_SEEDS;
        }
        if (block == Blocks.CARROTS && countItemInFarmerInventory(Items.CARROT) > 0) {
            return Items.CARROT;
        }
        if (block == Blocks.POTATOES && countItemInFarmerInventory(Items.POTATO) > 0) {
            return Items.POTATO;
        }
        return null;
    }

    public boolean shouldKeepForPlanting(Item item) {
        int count = countItemInFarmerInventory(item);

        if (item == Items.WHEAT_SEEDS) return count <= 16;
        if (item == Items.BEETROOT_SEEDS) return count <= 16;
        if (item == Items.CARROT) return count <= 16;
        if (item == Items.POTATO) return count <= 16;

        return false;
    }

    public boolean hasBoneMeal() {
        return countItemInFarmerInventory(Items.BONE_MEAL) > 0;
    }

    public boolean consumeOneBoneMeal() {
        return removeItemsFromFarmerInventory(Items.BONE_MEAL, 1);
    }



    public boolean isFarmerWorkTime() {
        long time = this.getWorld().getTimeOfDay() % 24000L;
        return time >= 1000L && time <= 11000L;
    }

    public boolean isFarmer() {
        return this.getCurrentSpecialization() == Specialization.FARMER;
    }

    public void triggerFarmAnimation() {
        triggerAnim("actionController", "farm");
    }

    private BlockPos composterPos;

    public BlockPos getComposterPos() {
        return this.composterPos;
    }

    public boolean hasComposter() {
        return this.composterPos != null;
    }

    public void clearComposter() {
        if (!this.getWorld().isClient && this.composterPos != null) {
            if (FurryVillageRegistry.isComposterReservedBy(this.getWorld(), this.composterPos, this.getUuid())) {
                FurryVillageRegistry.releaseComposter(this.getWorld(), this.composterPos, this.getUuid());
            }
        }
        this.composterPos = null;
    }

    public boolean assignComposterIfPossible() {
        if (this.getWorld().isClient) return false;
        if (!this.villageBound || this.villageCenter == null) return false;
        if (!this.isFarmer()) return false;
        if (this.composterPos != null) return true;

        BlockPos freeComposter = FurryVillageRegistry.findFreeComposterForVillage(this.getWorld(), this.villageCenter);
        if (freeComposter == null) return false;

        boolean reserved = FurryVillageRegistry.reserveComposter(this.getWorld(), freeComposter, this.getUuid());
        if (reserved) {
            this.composterPos = freeComposter;
            return true;
        }

        return false;
    }

    private void releaseReservedComposterIfNeeded() {
        if (!this.getWorld().isClient && this.composterPos != null) {
            FurryVillageRegistry.releaseComposter(this.getWorld(), this.composterPos, this.getUuid());
        }
    }

    public boolean hasExcessCompostables() {
        for (int i = 0; i < this.farmerInventory.size(); i++) {
            ItemStack stack = this.farmerInventory.getStack(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            if (!isCompostableFarmerItem(item)) continue;
            if (shouldKeepForPlanting(item)) continue;

            return true;
        }

        return false;
    }

    public ItemStack takeOneCompostableForComposter() {
        for (int i = 0; i < this.farmerInventory.size(); i++) {
            ItemStack stack = this.farmerInventory.getStack(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            if (!isCompostableFarmerItem(item)) continue;
            if (shouldKeepForPlanting(item)) continue;

            ItemStack result = stack.split(1);
            if (stack.isEmpty()) {
                this.farmerInventory.setStack(i, ItemStack.EMPTY);
            }
            return result;
        }

        return ItemStack.EMPTY;
    }

    public int countExcessCompostables() {
        int total = 0;

        for (int i = 0; i < this.farmerInventory.size(); i++) {
            ItemStack stack = this.farmerInventory.getStack(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            if (!isCompostableFarmerItem(item)) continue;
            if (shouldKeepForPlanting(item)) continue;

            total += stack.getCount();
        }

        return total;
    }


    // Trading
    private PlayerEntity currentCustomer;
    private TradeOfferList offers;

    @Override
    public boolean isClient() {
        return this.getEntityWorld().isClient;
    }

    @Override
    public void setCustomer(PlayerEntity customer) {
        this.currentCustomer = customer;
    }

    @Override
    public PlayerEntity getCustomer() {
        return this.currentCustomer;
    }

    @Override
    public TradeOfferList getOffers() {
        if (offers == null) {
            if (getCurrentSpecialization() == Specialization.BLACKSMITH) {
                offers = FurryFoxTrades.createBlacksmithTrades();
                System.out.println("BLACKSMITH offers size = " + offers.size());
            } else {
                offers = new TradeOfferList();
                System.out.println("NON-BLACKSMITH offers size = 0");
            }
        }
        return offers;
    }

    @Override
    public void setOffersFromServer(TradeOfferList offers) {
        this.offers = offers;
    }

    @Override
    public void trade(TradeOffer offer) {
        offer.use();
    }

    @Override
    public void onSellingItem(ItemStack stack) {}

    @Override
    public int getExperience() {
        return 0;
    }

    @Override
    public void setExperienceFromServer(int experience) {}

    @Override
    public boolean isLeveledMerchant() {
        return false;
    }

    @Override
    public SoundEvent getYesSound() {
        return net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_YES;
    }

    // Specialization
    public enum Specialization {
        NONE,
        BLACKSMITH,
        FARMER
    }

    public Specialization getCurrentSpecialization() {
        int index = this.dataTracker.get(SPECIALIZATION);
        Specialization[] values = Specialization.values();
        if (index < 0 || index >= values.length) {
            return Specialization.NONE;
        }
        return values[index];
    }

    private static final TrackedData<Integer> SPECIALIZATION =
            DataTracker.registerData(FurryFox.class, TrackedDataHandlerRegistry.INTEGER);

    private static final TrackedData<Boolean> SLEEPING =
            DataTracker.registerData(FurryFox.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SPECIALIZATION, Specialization.NONE.ordinal());
        builder.add(SLEEPING, false);
    }

    public void changeSpecialization(Specialization specialization) {
        if (this.getCurrentSpecialization() == Specialization.FARMER && specialization != Specialization.FARMER) {
            this.clearComposter();
        }

        this.dataTracker.set(SPECIALIZATION, specialization.ordinal());
        this.offers = null;
        updateSkinBasedOnSpecialization();

        if (specialization == Specialization.FARMER) {
            this.assignComposterIfPossible();
        }
    }

    private void updateSkinBasedOnSpecialization() {
        if (this.getCurrentSpecialization() == Specialization.BLACKSMITH) {
            this.setCustomName(Text.literal("Blacksmith"));
        } else if (this.getCurrentSpecialization() == Specialization.FARMER) {
            this.setCustomName(Text.literal("Farmer"));
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (stack.getItem() == Items.IRON_PICKAXE) {
            if (!this.getWorld().isClient) {
                changeSpecialization(Specialization.BLACKSMITH);
                player.sendMessage(Text.literal("You've become a blacksmith!"), false);
            }
            return ActionResult.SUCCESS;
        }
        if (stack.getItem() == Items.IRON_HOE) {
            if (!this.getWorld().isClient) {
                changeSpecialization(Specialization.FARMER);
                player.sendMessage(Text.literal("You've become a farmer!"), false);
            }
            return ActionResult.SUCCESS;
        }

        if (getCurrentSpecialization() == Specialization.BLACKSMITH) {
            if (!this.getWorld().isClient) {
                this.setCustomer(player);

                var optional = player.openHandledScreen(
                        new SimpleNamedScreenHandlerFactory(
                                (syncId, inventory, p) ->
                                        new MerchantScreenHandler(syncId, inventory, this),
                                Text.literal("Blacksmith")
                        )
                );

                if (optional.isPresent()) {
                    int syncId = optional.getAsInt();
                    player.sendTradeOffers(syncId, this.getOffers(), 0, 0, false, false);
                }
            }

            return ActionResult.SUCCESS;
        }

        return super.interactMob(player, hand);
    }


    // Logic and animation
    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new SleepInBedGoal(this, 1.0D));
        this.goalSelector.add(2, new ReturnToVillageCenterGoal(this, 1.1D));
        this.goalSelector.add(3, new ComposterWorkGoal(this, 1.0D));
        this.goalSelector.add(4, new FarmerWorkGoal(this, 1.0D));
        this.goalSelector.add(5, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 1.0));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));

        this.targetSelector.add(1, new RevengeGoal(this));
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 1)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16); // Добавляем дальность следования
    }

    // Контроллер движения
    private PlayState movementPredicate(AnimationState<FurryFox> state) {
        if (this.isSleepingInBed()) {
            return PlayState.STOP;
        }

        if (state.isMoving()) {
            state.getController().setAnimation(
                    RawAnimation.begin().thenLoop("walk")
            );
        } else {
            state.getController().setAnimation(
                    RawAnimation.begin().thenLoop("idle")
            );
        }

        return PlayState.CONTINUE;
    }

    // Контроллер атаки
    @Override
    public boolean tryAttack(Entity target) {

        triggerAnim("actionController", "attack");

        return super.tryAttack(target);
    }

    private PlayState attackPredicate(AnimationState<FurryFox> state) {
        return PlayState.STOP;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

        controllers.add(
                new AnimationController<>(
                        this,
                        "movementController",
                        2,
                        this::movementPredicate
                )
        );

        controllers.add(
                new AnimationController<>(
                        this,
                        "sleepController",
                        0,
                        this::sleepPredicate
                )
        );

        controllers.add(
                new AnimationController<>(
                        this,
                        "actionController",
                        0,
                        this::attackPredicate
                ).triggerableAnim(
                        "attack",
                        RawAnimation.begin().thenPlay("attack")
                )
                .triggerableAnim(
                        "farm",
                        RawAnimation.begin().thenPlay("farm")
                )
        );
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

}