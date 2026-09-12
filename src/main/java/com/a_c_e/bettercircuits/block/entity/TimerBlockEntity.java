package com.a_c_e.bettercircuits.block.entity;

import com.a_c_e.bettercircuits.block.TimerBlock;
import com.a_c_e.bettercircuits.block.menu.BCMenuTypes;
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

//Drives all of TimerBlock's own counting/pulsing/resetting (see TimerBlock's own class comment: DiodeBlock's
//inherited tick/getDelay machinery only fires on redstone events, not every tick, so it's bypassed entirely -
//serverTick below, called every tick via TimerBlock#getTicker, is the sole driver) and backs its right-click
//GUI (TimerMenu, via the MenuProvider implementation below).
public class TimerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int TICKS_PER_SECOND = 20;
    //Floor is 1 second (not 1 tick) in BOTH modes - below that the pointer's rotation-per-tick step gets big
    //enough to look visibly jittery rather than smoothly spinning, and observer-based clocks already cover
    //single-tick-and-up delays well, so there's no real need for this block to reach that low.
    public static final int MIN_TARGET = TICKS_PER_SECOND;
    //Matches MAX_TARGET_SECONDS_MODE exactly (1 hour) - per the user's own request, ticks and seconds mode now
    //reach the same effective ceiling, just expressed/selected in different units on the slider.
    public static final int MAX_TARGET = 3600 * TICKS_PER_SECOND;
    public static final int DEFAULT_TARGET = 20;
    //Floor matches MIN_TARGET exactly (both are 1 second) - kept as a separate constant since it's conceptually
    //seconds mode's own bound, not a coincidence that it equals the ticks-mode floor.
    public static final int MIN_TARGET_SECONDS_MODE = TICKS_PER_SECOND;
    public static final int MAX_TARGET_SECONDS_MODE = 3600 * TICKS_PER_SECOND;

    private int progress;
    private int target = DEFAULT_TARGET;
    private int pulseCountdown;
    //Display/input mode only - doesn't affect counting, locking, or the BER at all, purely how TimerMenu's
    //delta buttons interpret their own values and what TimerScreen labels them. Persisted so the block "remembers"
    //which mode it was last used in.
    private boolean secondsMode;

    public TimerBlockEntity(BlockPos pos, BlockState state) {
        super(BCBlockEntityTypes.TIMER.get(), pos, state);
    }

    public int getProgress() {
        return progress;
    }

    public int getTarget() {
        return target;
    }

    //Called every server tick regardless of redstone events. Locked freezes both counting and reset entirely
    //(matches the AND/XOR/Inverter gates' own lock behavior in this mod - input is simply ignored while locked,
    //not queued or remembered for later).
    public static void serverTick(Level level, BlockPos pos, BlockState state, TimerBlockEntity be) {
        TimerBlock block = (TimerBlock) state.getBlock();
        if (block.isLocked(level, pos, state)) {
            return;
        }

        if (be.pulseCountdown > 0) {
            be.pulseCountdown--;
            if (be.pulseCountdown == 0) {
                level.setBlock(pos, state.setValue(TimerBlock.POWERED, false), 2);
                be.notifyOutput(level, pos, state);
            }
            return;
        }

        boolean powered = block.readInputSignal(level, pos, state) > 0;
        if (state.getValue(TimerBlock.RUNNING) != powered) {
            state = state.setValue(TimerBlock.RUNNING, powered);
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
            level.setBlock(pos, state.setValue(TimerBlock.POWERED, true), 2);
            be.pulseCountdown = 1;
        }
        be.syncToClient();
        //Notified every tick progress changes (not just when POWERED itself flips) - the analog output signal
        //(TimerBlock#getAnalogOutputSignal) tracks progress continuously, so a comparator reading it needs a
        //redstone update just as often, or it only ever sees the value from whenever it last happened to be
        //poked. ALL 6 sides, not the single-direction notifyOutput() every other change in this class uses -
        //hasAnalogOutputSignal/getAnalogOutputSignal take no direction at all, so a comparator can read this
        //block's progress from any face it touches, same as reading a chest's fullness from any side, not
        //just a specific "output" side (found and fixed on LightweightTimerBlockEntity first, including an
        //intermediate version that only notified the output-direction neighbor and still missed this).
        level.updateNeighborsAt(pos, state.getBlock());
    }

    //setChanged() alone only marks this block entity dirty for chunk SAVING - it does NOT push anything to
    //clients. The BER needs to see fresh progress every tick to actually look like it's spinning (this was the
    //original bug: progress was only ever reaching connected clients whenever some unrelated blockstate change
    //happened to trigger a resync, e.g. the pulse itself, which is why it looked frozen the rest of the time),
    //so an explicit sendBlockUpdated - same old/new state, mirroring RedstoneCableBlockEntity's own changed()
    //helper - is needed on every tick progress actually changes.
    private void syncToClient() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    //Single-output analog of RSLatchBlock's own updateOutputNeighbor - flag 2 alone (used by both setBlock calls
    //above) only syncs to clients, so the actual neighbor notification has to happen explicitly here; forgetting
    //this exact step was a real bug caught and fixed for RS Latch earlier this session.
    private void notifyOutput(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(TimerBlock.FACING).getOpposite();
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
        //Clamped to the WIDER seconds-mode ceiling here (not MAX_TARGET) purely as a sanity guard against
        //corrupted NBT - a legitimately-saved seconds-mode target above 24000 ticks shouldn't get silently
        //truncated just because ticks mode's own tighter bound happens to be smaller.
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
        return Component.translatable("block.better_circuits.timer");
    }

    //ContainerData whose get/set are wired directly to this block entity's own fields, so TimerMenu's
    //clickMenuButton (server-side only) can mutate the real target/mode through the ContainerData interface
    //alone, without needing its own BlockPos/level reference back to this block entity. Index 2 (mode) is a
    //bit unusual for ContainerData - it's normally just plain numbers - but doubles as the toggle action itself:
    //writing a different value than what's already there is what performs the actual mode switch (see set()
    //below), same "set() as an action, not just a value write" idea TimerMenu's own clickMenuButton already
    //leans on for the delta buttons.
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
                            //Round UP to the nearest whole second (per the user's own example: 301 ticks -> 16s,
                            //not 15s) - ticks -> seconds is the only direction that can lose precision, since
                            //every whole-seconds value converts back to ticks exactly.
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
