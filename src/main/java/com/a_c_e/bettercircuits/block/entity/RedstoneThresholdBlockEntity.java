package com.a_c_e.bettercircuits.block.entity;

import com.a_c_e.bettercircuits.block.RedstoneThresholdBlock;
import com.a_c_e.bettercircuits.block.menu.BCMenuTypes;
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

//Stores the two values RedstoneThresholdBlock's own shouldTurnOn compares the input signal against - mode
//(LESS/EQUAL/GREATER, matching the 3 GUI buttons) and the threshold power level itself (1-15, matching the 15
//redstone-tinted GUI segments, never 0 per the user's own spec) - and backs the right-click GUI
//(RedstoneThresholdMenu, via the MenuProvider implementation below), same overall shape as TimerBlockEntity's
//own ContainerData wiring.
//
//Unlike Timer, the actual redstone evaluation doesn't live here at all - RedstoneThresholdBlock uses vanilla's
//own standard DiodeBlock tick/checkTickOnNeighbor machinery (a plain shouldTurnOn override reading these two
//fields), since a threshold check needs no per-tick counting state, just a comparison re-run whenever the input
//changes - or, since a GUI edit to mode/threshold isn't a "neighbor changed" event DiodeBlock would ever notice
//on its own, ContainerData#set below also explicitly reschedules a re-check tick after any real change.
public class RedstoneThresholdBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MODE_LESS = 0;
    public static final int MODE_EQUAL = 1;
    public static final int MODE_GREATER = 2;
    public static final int MIN_THRESHOLD = 1;
    public static final int MAX_THRESHOLD = 15;
    public static final int DEFAULT_THRESHOLD = 1;

    private int mode = MODE_EQUAL;
    private int threshold = DEFAULT_THRESHOLD;
    //The real analog value last actually computed by RedstoneThresholdBlock's own getOutputSignal, cached so a
    //LOCKED threshold can keep returning it verbatim instead of recomputing from the (possibly since-changed)
    //live input signal every time something queries it - see that method's own comment for why the passthrough
    //output would otherwise keep drifting with the input even while POWERED itself stays frozen by the lock.
    private int lastOutput;

    public RedstoneThresholdBlockEntity(BlockPos pos, BlockState state) {
        super(BCBlockEntityTypes.REDSTONE_THRESHOLD.get(), pos, state);
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

    public boolean matches(int signal) {
        return matches(mode, threshold, signal);
    }

    //Static so LightweightRedstoneThresholdBlockEntity can share the exact same comparison instead of
    //duplicating it. "Less than" additionally requires signal > 0 - a completely unpowered input (0) is the
    //ABSENCE of a reading, not a real "less than" one, so it shouldn't satisfy the condition by itself (found
    //via the user's own testing: without this, "less than X" was true with nothing at all connected to the
    //input, and since the output is a straight analog passthrough of that same 0, it looked exactly like the
    //block wasn't working at all rather than correctly reporting "no signal").
    public static boolean matches(int mode, int threshold, int signal) {
        return switch (mode) {
            case MODE_LESS -> signal > 0 && signal < threshold;
            case MODE_GREATER -> signal > threshold;
            default -> signal == threshold;
        };
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = Math.clamp(tag.getInt("Mode"), MODE_LESS, MODE_GREATER);
        threshold = Math.clamp(tag.getInt("Threshold"), MIN_THRESHOLD, MAX_THRESHOLD);
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
        return Component.translatable("block.better_circuits.redstone_threshold");
    }

    //Forces a redstone re-check after a GUI edit - a mode/threshold change isn't a "neighbor changed" event
    //DiodeBlock's own tick scheduling would ever notice on its own, so without this the output would only catch
    //up to a new setting whenever something ELSE happened to poke this position.
    private void recheckOutput() {
        if (level != null && !level.isClientSide && getBlockState().getBlock() instanceof RedstoneThresholdBlock block) {
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
                    int clamped = Math.clamp(value, MODE_LESS, MODE_GREATER);
                    if (clamped != mode) {
                        mode = clamped;
                        setChanged();
                        recheckOutput();
                    }
                } else {
                    int clamped = Math.clamp(value, MIN_THRESHOLD, MAX_THRESHOLD);
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
