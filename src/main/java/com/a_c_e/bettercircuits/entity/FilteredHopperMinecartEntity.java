package com.a_c_e.bettercircuits.entity;

import com.a_c_e.bettercircuits.block.BCBlocks;
import com.a_c_e.bettercircuits.item.BCItems;
import com.a_c_e.bettercircuits.util.ItemFilterMatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.vehicle.MinecartHopper;

//Minecart with Filtered Hopper - the same filtering concept as FilteredHopperBlock, on a hopper minecart instead
//of a placed hopper. Extends vanilla's own MinecartHopper (not final) rather than reimplementing a hopper
//minecart's substantial suck-in/push/rail-following behavior from scratch - the ONE override point that matters,
//canPlaceItem(int, ItemStack), is a plain virtual method inherited from Container, confirmed (by decompiling
//HopperBlockEntity's static suckInItems/addItem/tryMoveInItem chain, the same investigation FilteredHopperBlock
//itself relied on) to be the single gate every insertion path funnels through for a Hopper - automatic vacuum of
//loose ItemEntities AND pulling from a container sitting above, both included.
//
//Unlike HopperBlockEntity, MinecartHopper's own (EntityType<? extends MinecartHopper>, Level) constructor is NOT
//hardcoded to a fixed type (it takes the type as a parameter, satisfied here by our own bound subtype) - so
//there's no equivalent of BCHopperBlockEntityTypeFix needed here; getType() correctly reports our own
//BCEntityTypes.FILTERED_HOPPER_MINECART for every instance.
//
//The filter is a synced ItemStack (SynchedEntityData, matching vanilla's own ItemFrame - the closest existing
//precedent for "an entity displaying/carrying a single filter-like ItemStack"), not the block entity's own
//NBT-plus-manual-sendBlockUpdated approach, since entities have no equivalent of a block's chunk-update packet.
public class FilteredHopperMinecartEntity extends MinecartHopper {
    private static final EntityDataAccessor<ItemStack> DATA_FILTER =
            SynchedEntityData.defineId(FilteredHopperMinecartEntity.class, EntityDataSerializers.ITEM_STACK);

    public FilteredHopperMinecartEntity(EntityType<? extends FilteredHopperMinecartEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FILTER, ItemStack.EMPTY);
    }

    public ItemStack getFilter() {
        return getEntityData().get(DATA_FILTER);
    }

    //Always copies to a single-count stack - the filter is a type+identity marker, not a real stored item -
    //matching FilteredHopperBlockEntity's own setFilter exactly.
    public void setFilter(ItemStack stack) {
        getEntityData().set(DATA_FILTER, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    //An empty filter means unrestricted (behaves exactly like a normal hopper minecart). Once occupied, only
    //items matching both the exact item type and the filter's own "identity" components (see ItemFilterMatcher)
    //may be sucked up.
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        ItemStack filter = getFilter();
        return super.canPlaceItem(slot, stack) && (filter.isEmpty() || ItemFilterMatcher.matches(stack, filter));
    }

    //Item-in-hand click on an empty filter sets it (consuming 1, matching ItemFrame's own insert-one behavior);
    //on an occupied filter it's a no-op (a real ItemFrame doesn't swap on right-click either). Empty-handed
    //clicks fall straight through to MinecartHopper's own interaction (mount attempt, then the hopper GUI) -
    //completely unaffected, matching a plain vanilla hopper minecart.
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) {
            return super.interact(player, hand);
        }
        if (!getFilter().isEmpty()) {
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide) {
            setFilter(held);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            level().playSound(null, this, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    //Mirrors vanilla ItemFrame.hurt exactly (decompiled to confirm the shape): a non-explosion hit against an
    //occupied filter just pops the filter out and fully absorbs the hit - no damage accumulates, the cart is
    //never destroyed by this path - matching the block's own "player doesn't need shears, can simply hit the
    //item frame" rule. An empty filter, or an explosion, falls through to the normal minecart damage/destroy
    //behavior untouched.
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isAlive() && !getFilter().isEmpty() && !source.is(DamageTypeTags.IS_EXPLOSION)) {
            dropFilter();
            level().playSound(null, this, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
            return true;
        }
        return super.hurt(source, amount);
    }

    private void dropFilter() {
        ItemStack filter = getFilter();
        setFilter(ItemStack.EMPTY);
        Containers.dropItemStack(level(), getX(), getY(), getZ(), filter);
    }

    //Breaking the cart (explosion, fire, etc.) needs to also drop whatever's currently filtered - matching how
    //breaking a real ItemFrame, or a Filtered Hopper block, always drops its contents too.
    @Override
    public void destroy(DamageSource source) {
        ItemStack filter = getFilter();
        super.destroy(source);
        if (!filter.isEmpty()) {
            setFilter(ItemStack.EMPTY);
            Containers.dropItemStack(level(), getX(), getY(), getZ(), filter);
        }
    }

    //Bug fix (same category as MinecartHopper's own getPickResult() gap): AbstractMinecart.getPickResult()
    //switches on getMinecartType() - a fixed vanilla enum - so left unoverridden it would middle-click-pick
    //vanilla's own Hopper Minecart item instead of ours.
    @Override
    public ItemStack getPickResult() {
        return new ItemStack(BCItems.FILTERED_HOPPER_MINECART_ITEM.get());
    }

    @Override
    protected Item getDropItem() {
        return BCItems.FILTERED_HOPPER_MINECART_ITEM.get();
    }

    //Purely cosmetic: the "block inside the cart" vanilla's MinecartRenderer bakes as a static block model (see
    //FilteredHopperMinecartRenderer, which renders the frame+item on top of this in the same call) - reusing
    //BCBlocks.FILTERED_HOPPER's own model here is equivalent to vanilla's Blocks.HOPPER either way (Filtered
    //Hopper's own block model parents onto vanilla's unchanged), but this is the semantically correct one.
    @Override
    public BlockState getDefaultDisplayBlockState() {
        return BCBlocks.FILTERED_HOPPER.get().defaultBlockState();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ItemStack filter = getFilter();
        if (!filter.isEmpty()) {
            tag.put("Filter", filter.save(registryAccess()));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setFilter(tag.contains("Filter") ? ItemStack.parseOptional(registryAccess(), tag.getCompound("Filter")) : ItemStack.EMPTY);
    }
}
