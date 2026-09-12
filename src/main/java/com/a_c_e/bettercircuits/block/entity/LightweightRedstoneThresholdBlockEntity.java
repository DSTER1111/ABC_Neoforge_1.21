package com.a_c_e.bettercircuits.block.entity;

import com.a_c_e.bettercircuits.block.lightweight.LightweightRedstoneThresholdBlock;
import com.a_c_e.bettercircuits.block.menu.RedstoneThresholdMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

//Any-surface counterpart to RedstoneThresholdBlockEntity - a direct port of its exact mode/threshold storage and
//GUI logic (see that class's own comment), generalized only where LightweightRedstoneThresholdBlock's own
//SUPPORT+FACING orientation requires it (recheckOutput delegates to LightweightRedstoneThresholdBlock instead of
//casting to RedstoneThresholdBlock). Reuses RedstoneThresholdMenu/RedstoneThresholdScreen directly rather than
//duplicating them - the GUI itself (mode buttons folded into the slider, the 15-segment line) has nothing
//orientation-specific about it; RedstoneThresholdMenu.stillValid was generalized to accept either block for
//this, matching TimerMenu's own precedent exactly.
public class LightweightRedstoneThresholdBlockEntity extends BlockEntity implements MenuProvider {
    private int mode = RedstoneThresholdBlockEntity.MODE_EQUAL;
    private int threshold = RedstoneThresholdBlockEntity.DEFAULT_THRESHOLD;
    //See RedstoneThresholdBlockEntity#lastOutput's own comment - same frozen-snapshot need for
    //LightweightRedstoneThresholdBlock's own analog getSignal passthrough while LOCKED.
    private int lastOutput;

    public LightweightRedstoneThresholdBlockEntity(BlockPos pos, BlockState state) {
        super(BCBlockEntityTypes.LIGHTWEIGHT_REDSTONE_THRESHOLD.get(), pos, state);
    }

    public int getMode() {
        return mode;
    }

    public int getThreshold() {
        return threshold;
    }

    public int getLastOutput() {
        return lastOutput;
    }

    public void setLastOutput(int lastOutput) {
        if (this.lastOutput != lastOutput) {
            this.lastOutput = lastOutput;
            setChanged();
        }
    }

    //Shared with RedstoneThresholdBlockEntity's own instance matches() - see its own static overload's comment
    //for why "less than" also requires signal > 0.
    public boolean matches(int signal) {
        return RedstoneThresholdBlockEntity.matches(mode, threshold, signal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = Math.clamp(tag.getInt("Mode"), RedstoneThresholdBlockEntity.MODE_LESS, RedstoneThresholdBlockEntity.MODE_GREATER);
        threshold = Math.clamp(tag.getInt("Threshold"), RedstoneThresholdBlockEntity.MIN_THRESHOLD, RedstoneThresholdBlockEntity.MAX_THRESHOLD);
        lastOutput = tag.getInt("LastOutput");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Mode", mode);
        tag.putInt("Threshold", threshold);
        tag.putInt("LastOutput", lastOutput);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.better_circuits.lightweight_redstone_threshold");
    }

    private void recheckOutput() {
        if (level != null && !level.isClientSide && getBlockState().getBlock() instanceof LightweightRedstoneThresholdBlock block) {
            block.recheckOutput(level, worldPosition, getBlockState());
        }
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? mode : threshold;
            }

            @Override
            public void set(int index, int value) {
                if (index == 0) {
                    int clamped = Math.clamp(value, RedstoneThresholdBlockEntity.MODE_LESS, RedstoneThresholdBlockEntity.MODE_GREATER);
                    if (clamped != mode) {
                        mode = clamped;
                        setChanged();
                        recheckOutput();
                    }
                } else {
                    int clamped = Math.clamp(value, RedstoneThresholdBlockEntity.MIN_THRESHOLD, RedstoneThresholdBlockEntity.MAX_THRESHOLD);
                    if (clamped != threshold) {
                        threshold = clamped;
                        setChanged();
                        recheckOutput();
                    }
                }
            }

            @Override
            public int getCount() {
                return 2;
            }
        };
        return new RedstoneThresholdMenu(containerId, inventory, data, ContainerLevelAccess.create(level, worldPosition));
    }
}
