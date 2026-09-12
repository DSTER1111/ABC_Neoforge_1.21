package com.a_c_e.bettercircuits.block.entity;

import com.a_c_e.bettercircuits.util.ItemFilterMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

//Filtered Hopper: a plain HopperBlockEntity plus one extra field, `filter` - the item "framed" inside the
//block's own top opening (see FilteredHopperBlock's own class comment for why this is one block, not a hopper
//paired with a real ItemFrame entity). Everything else about being a hopper - the 5-slot inventory, suck-in/
//push-out logic, cooldown, comparator output off the inventory's own fill level, minecart-hopper interaction -
//is entirely unmodified, inherited straight from HopperBlockEntity.
//
//The one behavioral hook: HopperBlockEntity itself has no virtual "can I pick this up" method (its actual
//suck-in/transfer logic - tryMoveInItem, suckInItems, addItem - is all private/public STATIC, no override
//point at all) - but every one of those paths funnels through Container's own canPlaceItem(slot, stack) before
//ever touching a slot (confirmed via decompiling HopperBlockEntity: tryMoveInItem calls
//canPlaceItemInContainer(to, ...), which calls to.canPlaceItem(slot, stack) first, unconditionally). That one
//already-virtual method is the entire filtering mechanism - no need to reimplement any of hopper's own
//substantial pickup/push code.
public class FilteredHopperBlockEntity extends HopperBlockEntity {
    private ItemStack filter = ItemStack.EMPTY;

    public FilteredHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    public ItemStack getFilter() {
        return filter;
    }

    //Always copies to a single-count stack - the filter is a type+identity marker, not a real stored item, so
    //its own count is meaningless (matches how right-clicking a real ItemFrame always inserts exactly 1
    //regardless of the held stack's size).
    public void setFilter(ItemStack stack) {
        this.filter = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            //Belt-and-suspenders alongside sendBlockUpdated, matching RedstoneCableBlockEntity's own identical
            //precedent (see its own comment: relying on sendBlockUpdated alone left later updates - anything
            //past the very first real blockstate transition - visually stale on some clients until something
            //else happened to force a chunk rebuild).
            requestModelDataUpdate();
        }
    }

    //An empty filter means "no filter installed" - the hopper behaves exactly like a normal, unrestricted one.
    //Once occupied, only items matching BOTH the exact item type AND the filter's own "identity" components
    //may be picked up.
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return super.canPlaceItem(slot, stack) && (filter.isEmpty() || matchesFilter(stack));
    }

    //"Certain NBT data" per the user's own spec: enchantments, potion effects, written-book text, custom
    //names, trim, etc. all count (a Fortune III book filters differently than a Fortune II book, or a plain
    //enchanted book) - see ItemFilterMatcher (shared with FilteredHopperMinecartEntity) for exactly which
    //components are excluded and why.
    private boolean matchesFilter(ItemStack stack) {
        return ItemFilterMatcher.matches(stack, filter);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        filter = tag.contains("Filter") ? ItemStack.parseOptional(registries, tag.getCompound("Filter")) : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!filter.isEmpty()) {
            tag.put("Filter", filter.save(registries));
        }
    }

    //Client sync for the renderer (see FilteredHopperBlockEntityRenderer) - vanilla's own BlockEntity default
    //for both of these sends nothing at all, so without this the framed item would never appear on any client
    //other than the one that set it. The receiving side needs no override of its own: BlockEntity's default
    //handleUpdateTag/onDataPacket already round-trip straight through loadAdditional, which is all that's
    //needed here since the renderer reads the live field directly (no ModelData indirection the way
    //RedstoneCableBlockEntity needs for ITS own per-face render data).
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
