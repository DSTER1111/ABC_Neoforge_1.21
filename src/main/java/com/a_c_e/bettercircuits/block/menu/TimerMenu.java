package com.a_c_e.bettercircuits.block.menu;

import com.a_c_e.bettercircuits.block.BCBlocks;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

//Backs TimerBlock's own right-click GUI. No slots at all - just a synced ContainerData (progress, target, mode)
//and 2 button actions, all using vanilla's own menu-button plumbing (clickMenuButton below, dispatched
//client-side via Minecraft#gameMode#handleInventoryButtonClick - see TimerScreen) rather than a custom packet.
//MODE_BUTTON_ID toggles ticks/seconds display (unchanged from before); the old 12 relative-delta buttons plus
//Min/Max buttons are gone, replaced by VALUE_BUTTON_BASE+ticks setting target directly to an absolute tick
//count - both superseded by the new slider's own direct-drag positioning, matching RedstoneThresholdMenu's own
//VALUE_BUTTON_BASE convention (this mod's now-standard way to let a screen set an absolute value via button id
//without a custom packet).
//
//Two constructors, matching FurnaceMenu's own client/server split: the no-data constructor (registered as this
//menu's MenuType factory) is used client-side when the open-screen packet arrives with no block entity
//available, and gets a throwaway SimpleContainerData that vanilla's own container-sync packets fill in every
//tick the screen is open. The data-carrying constructor (TimerBlockEntity#createMenu) is instead handed a
//ContainerData whose get/set are wired directly to the real block entity's own fields, so clickMenuButton below
//(which only ever actually runs server-side) mutates the real block entity purely through this interface.
public class TimerMenu extends AbstractContainerMenu {
    public static final int MODE_BUTTON_ID = 0;
    public static final int VALUE_BUTTON_BASE = 1;

    private final ContainerData data;
    private final ContainerLevelAccess access;

    public TimerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainerData(3), ContainerLevelAccess.NULL);
    }

    public TimerMenu(int containerId, Inventory inventory, ContainerData data, ContainerLevelAccess access) {
        super(BCMenuTypes.TIMER.get(), containerId);
        this.data = data;
        this.access = access;
        addDataSlots(data);
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getTarget() {
        return data.get(1);
    }

    public boolean isSecondsMode() {
        return data.get(2) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == MODE_BUTTON_ID) {
            data.set(2, isSecondsMode() ? 0 : 1);
            return true;
        }
        if (id >= VALUE_BUTTON_BASE) {
            data.set(1, id - VALUE_BUTTON_BASE);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    //Generalized from the single-block stillValid(access, player, block) helper (same distance/block-match
    //logic reimplemented here) to accept either heavy Timer or Lightweight Timer - both share this same menu
    //(see LightweightTimerBlockEntity's own comment on why), so a single-block check would incorrectly close
    //this GUI immediately whenever it's opened from a Lightweight Timer.
    @Override
    public boolean stillValid(Player player) {
        return access.evaluate((level, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!state.is(BCBlocks.TIMER.get()) && !state.is(BCBlocks.LIGHTWEIGHT_TIMER.get())) {
                return false;
            }
            return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
        }, true);
    }
}
