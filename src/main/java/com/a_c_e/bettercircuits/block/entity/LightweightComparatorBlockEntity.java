package com.a_c_e.bettercircuits.block.entity;

import com.a_c_e.bettercircuits.block.lightweight.LightweightComparatorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

//Any-surface counterpart to vanilla's own ComparatorBlockEntity - stores the comparator's own computed analog
//output signal (a real 0-15 VALUE, not just 0/15 like every other gate in this mod), same as vanilla's own
//field. Also drives the tick-every-frame refresh LightweightComparatorBlock relies on instead of vanilla's own
//neighborChanged-only approach - see that class's own comment for why a chest/hopper/lectern/item frame
//changing doesn't reliably notify its neighbors, so the comparator has to poll instead of wait to be told.
public class LightweightComparatorBlockEntity extends BlockEntity {
    private int outputSignal;

    public LightweightComparatorBlockEntity(BlockPos pos, BlockState state) {
        super(BCBlockEntityTypes.LIGHTWEIGHT_COMPARATOR.get(), pos, state);
    }

    public int getOutputSignal() {
        return outputSignal;
    }

    public void setOutputSignal(int outputSignal) {
        this.outputSignal = outputSignal;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LightweightComparatorBlockEntity be) {
        ((LightweightComparatorBlock) state.getBlock()).refreshOutputState(level, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("OutputSignal", outputSignal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        outputSignal = tag.getInt("OutputSignal");
    }
}
