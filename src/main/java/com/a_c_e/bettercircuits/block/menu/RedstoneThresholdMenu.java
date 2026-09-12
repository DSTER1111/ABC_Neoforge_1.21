package com.a_c_e.bettercircuits.block.menu;

import com.a_c_e.bettercircuits.block.BCBlocks;
import com.a_c_e.bettercircuits.block.entity.RedstoneThresholdBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

//Backs RedstoneThresholdBlock's own right-click GUI. No slots, same overall shape as TimerMenu - a synced
//ContainerData (mode, threshold) plus button clicks dispatched through vanilla's own menu-button plumbing
//(clickMenuButton below, see RedstoneThresholdScreen for how it's actually triggered - real Button widgets for
//the 3 mode buttons, manual mouseClicked hit-testing for the 15 threshold-value segments, since those need a
//fully custom tinted appearance a vanilla Button's own texture would fight with).
public class RedstoneThresholdMenu extends AbstractContainerMenu {
    public static final int MODE_BUTTON_BASE = 0;
    public static final int VALUE_BUTTON_BASE = 3;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    public RedstoneThresholdMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainerData(2), ContainerLevelAccess.NULL);
    }

    public RedstoneThresholdMenu(int containerId, Inventory inventory, ContainerData data, ContainerLevelAccess access) {
        super(BCMenuTypes.REDSTONE_THRESHOLD.get(), containerId);
        this.data = data;
        this.access = access;
        addDataSlots(data);
    }

    public int getMode() {
        return data.get(0);
    }

    public int getThreshold() {
        return data.get(1);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= MODE_BUTTON_BASE && id < MODE_BUTTON_BASE + 3) {
            data.set(0, id - MODE_BUTTON_BASE);
            return true;
        }
        int value = id - VALUE_BUTTON_BASE + RedstoneThresholdBlockEntity.MIN_THRESHOLD;
        if (value >= RedstoneThresholdBlockEntity.MIN_THRESHOLD && value <= RedstoneThresholdBlockEntity.MAX_THRESHOLD) {
            data.set(1, value);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    //Generalized to accept either heavy Redstone Threshold or Lightweight Redstone Threshold - both share this
    //same menu (see LightweightRedstoneThresholdBlockEntity's own comment), matching TimerMenu's own identical
    //precedent exactly.
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!state.is(BCBlocks.REDSTONE_THRESHOLD.get()) && !state.is(BCBlocks.LIGHTWEIGHT_REDSTONE_THRESHOLD.get())) {
                return false;
            }
            return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
        }, true);
    }
}
