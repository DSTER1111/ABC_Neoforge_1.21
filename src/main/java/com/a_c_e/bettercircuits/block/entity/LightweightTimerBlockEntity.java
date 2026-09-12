package com.a_c_e.bettercircuits.block.entity;

import com.a_c_e.bettercircuits.block.lightweight.LightweightTimerBlock;
import com.a_c_e.bettercircuits.block.menu.TimerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

//Any-surface counterpart to TimerBlockEntity - a direct port of its exact counting/pulsing/resetting/GUI logic
//(see that class's own comment), generalized only where LightweightTimerBlock's own SUPPORT+FACING orientation
//requires it (readInputSignal/isLocked delegate to LightweightTimerBlock instead of casting to TimerBlock;
//notifyOutput uses LightweightTimerBlock.FACING instead of TimerBlock.FACING). Reuses TimerMenu/TimerScreen
//directly rather than duplicating them - the GUI itself (progress/target/mode, delta buttons) has nothing
//orientation-specific about it; TimerMenu.stillValid was generalized to accept either block for this (see its
//own comment).
public class LightweightTimerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int TICKS_PER_SECOND = TimerBlockEntity.TICKS_PER_SECOND;
    public static final int MIN_TARGET = TimerBlockEntity.MIN_TARGET;
    public static final int MAX_TARGET = TimerBlockEntity.MAX_TARGET;
    public static final int DEFAULT_TARGET = TimerBlockEntity.DEFAULT_TARGET;
    public static final int MIN_TARGET_SECONDS_MODE = TimerBlockEntity.MIN_TARGET_SECONDS_MODE;
    public static final int MAX_TARGET_SECONDS_MODE = TimerBlockEntity.MAX_TARGET_SECONDS_MODE;

    private int progress;
    private int target = DEFAULT_TARGET;
    private int pulseCountdown;
    private boolean secondsMode;

    public LightweightTimerBlockEntity(BlockPos pos, BlockState state) {
        super(BCBlockEntityTypes.LIGHTWEIGHT_TIMER.get(), pos, state);
    }

    public int getProgress() {
        return progress;
    }

    public int getTarget() {
        return target;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LightweightTimerBlockEntity be) {
        LightweightTimerBlock block = (LightweightTimerBlock) state.getBlock();
        if (block.isLocked(level, pos, state)) {
            return;
        }

        if (be.pulseCountdown > 0) {
            be.pulseCountdown--;
            if (be.pulseCountdown == 0) {
                level.setBlock(pos, state.setValue(LightweightTimerBlock.POWERED, false), 2);
                be.notifyOutput(level, pos, state);
            }
            return;
        }

        boolean powered = block.readInputSignal(level, pos, state) > 0;
        if (state.getValue(LightweightTimerBlock.RUNNING) != powered) {
            state = state.setValue(LightweightTimerBlock.RUNNING, powered);
            level.setBlock(pos, state, 2);
        }

        if (!powered) {
            if (be.progress != 0) {
                be.progress = 0;
                be.syncToClient();
                level.updateNeighborsAt(pos, state.getBlock());
            }
            return;
        }

        be.progress++;
        if (be.progress >= be.target) {
            be.progress = 0;
            level.setBlock(pos, state.setValue(LightweightTimerBlock.POWERED, true), 2);
            be.pulseCountdown = 1;
        }
        be.syncToClient();
        //Notified every tick progress changes (not just when POWERED itself flips) - the analog output signal
        //(LightweightTimerBlock#getAnalogOutputSignal) tracks progress continuously, so a comparator reading it
        //needs a redstone update just as often, or it only ever sees the value from whenever it last happened
        //to be poked (the original bug: it worked once at placement, then stuck at that value forever).
        //
        //ALL 6 sides, not just the output-direction notifyOutput() every other change in this class uses:
        //hasAnalogOutputSignal/getAnalogOutputSignal (BlockState.getAnalogOutputSignal(Level, BlockPos)) take
        //no direction at all - a comparator can read this block's progress from ANY face it touches, same as
        //reading a chest's fullness from any side, not just a specific "output" side. An earlier version of
        //this fix only notified the single output-direction neighbor (mimicking a diode's own directional
        //signal push), which silently missed a comparator reading this block from any other face - exactly
        //the case the user's own test setup hit.
        level.updateNeighborsAt(pos, state.getBlock());
    }

    private void syncToClient() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private void notifyOutput(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(LightweightTimerBlock.FACING).getOpposite();
        BlockPos neighborPos = pos.relative(direction);
        if (net.neoforged.neoforge.event.EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), java.util.EnumSet.of(direction), false).isCanceled()) {
            return;
        }
        level.neighborChanged(neighborPos, state.getBlock(), pos);
        level.updateNeighborsAtExceptFromFacing(neighborPos, state.getBlock(), direction.getOpposite());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("Progress");
        target = Math.clamp(tag.getInt("Target"), MIN_TARGET, MAX_TARGET_SECONDS_MODE);
        pulseCountdown = tag.getInt("PulseCountdown");
        secondsMode = tag.getBoolean("SecondsMode");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Progress", progress);
        tag.putInt("Target", target);
        tag.putInt("PulseCountdown", pulseCountdown);
        tag.putBoolean("SecondsMode", secondsMode);
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
        return Component.translatable("block.better_circuits.lightweight_timer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> progress;
                    case 1 -> target;
                    default -> secondsMode ? 1 : 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index == 1) {
                    int min = secondsMode ? MIN_TARGET_SECONDS_MODE : MIN_TARGET;
                    int max = secondsMode ? MAX_TARGET_SECONDS_MODE : MAX_TARGET;
                    int clamped = Math.clamp(value, min, max);
                    if (clamped != target) {
                        target = clamped;
                        setChanged();
                    }
                } else if (index == 2) {
                    boolean newMode = value != 0;
                    if (newMode != secondsMode) {
                        if (newMode) {
                            target = ((target + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND) * TICKS_PER_SECOND;
                            target = Math.clamp(target, MIN_TARGET_SECONDS_MODE, MAX_TARGET_SECONDS_MODE);
                        }
                        secondsMode = newMode;
                        setChanged();
                    }
                }
            }

            @Override
            public int getCount() {
                return 3;
            }
        };
        return new TimerMenu(containerId, inventory, data, ContainerLevelAccess.create(level, worldPosition));
    }
}
