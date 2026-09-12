package com.a_c_e.bettercircuits;

import com.a_c_e.bettercircuits.block.entity.FilteredHopperBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;

//A dispenser facing a Filtered Hopper puts whatever it dispenses into the FILTER, not the hopper's own 5-slot
//inventory - a normal item fills an empty filter (consumed 1) and does nothing to an occupied one (matches
//vanilla's own "dispense action that can't apply leaves the stack untouched" convention, e.g. a bucket
//dispensed at a full water source); shears are the one special case - they empty an occupied filter (popping
//the item out, shears themselves stay in the dispenser) or, same as any other item, fill an empty one.
//
//This has to apply to literally every item (any item can become a filter), unlike BCCauldronDispenseBehaviors'
//own handful of specific items - so it extends that same file's own wrap-and-fallback-to-original pattern
//(capture DispenserBlock.DISPENSER_REGISTRY's existing entry, register a replacement that only special-cases
//targeting a Filtered Hopper and otherwise defers to the original) across BuiltInRegistries.ITEM as a whole.
//Every other target block for every item is completely unaffected - the replacement immediately falls through
//to the exact behavior that was already registered the instant the target isn't a FilteredHopperBlockEntity.
public class BCFilteredHopperDispenseBehaviors {
    private BCFilteredHopperDispenseBehaviors() {
    }

    public static void register() {
        for (Item item : BuiltInRegistries.ITEM) {
            DispenseItemBehavior original = DispenserBlock.DISPENSER_REGISTRY.get(item);
            if (original == null) {
                continue;
            }
            DispenserBlock.registerBehavior(item, wrap(item, original));
        }
    }

    private static DispenseItemBehavior wrap(Item item, DispenseItemBehavior original) {
        return new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                BlockPos targetPos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                if (!(source.level().getBlockEntity(targetPos) instanceof FilteredHopperBlockEntity hopper)) {
                    return original.dispense(source, stack);
                }
                if (item == Items.SHEARS) {
                    return dispenseShears(source, hopper, stack);
                }
                return dispenseIntoFilter(hopper, stack);
            }
        };
    }

    private static ItemStack dispenseShears(BlockSource source, FilteredHopperBlockEntity hopper, ItemStack stack) {
        if (!hopper.getFilter().isEmpty()) {
            popOutFilter(source, hopper);
            return stack;
        }
        return dispenseIntoFilter(hopper, stack);
    }

    private static ItemStack dispenseIntoFilter(FilteredHopperBlockEntity hopper, ItemStack stack) {
        if (!hopper.getFilter().isEmpty()) {
            return stack;
        }
        hopper.setFilter(stack);
        stack.shrink(1);
        return stack;
    }

    private static void popOutFilter(BlockSource source, FilteredHopperBlockEntity hopper) {
        ItemStack filter = hopper.getFilter();
        hopper.setFilter(ItemStack.EMPTY);
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        Position dispensePos = DispenserBlock.getDispensePosition(source);
        DefaultDispenseItemBehavior.spawnItem(source.level(), filter, 6, facing, dispensePos);
    }
}
