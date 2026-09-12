package com.a_c_e.bettercircuits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.FarmBlock;

//No vanilla equivalent to mirror: vanilla dispensers never plant seeds, only bone-meal existing crops.
//Modeled on ShulkerBoxDispenseBehavior (BlockItem.place via DirectionalPlaceContext), the closest vanilla
//"dispenser places a block in front of it" precedent, but the target cell is offset onto the checked
//farmland's top face rather than the checked cell itself.
public class BCFarmlandPlantBehavior extends DefaultDispenseItemBehavior {
    @Override
    protected ItemStack execute(BlockSource blockSource, ItemStack stack) {
        Level level = blockSource.level();
        Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
        BlockPos frontPos = blockSource.pos().relative(direction);

        //FarmBlock covers both minecraft:farmland and our own fertilized_farmland (which extends it)
        if (level.getBlockState(frontPos).getBlock() instanceof FarmBlock && stack.getItem() instanceof BlockItem blockItem) {
            BlockPos plantPos = frontPos.above();
            if (blockItem.place(new DirectionalPlaceContext(level, plantPos, direction, stack, Direction.UP)).consumesAction()) {
                return stack;
            }
        }

        //Not aimed at farmland, or the crop couldn't be placed there (e.g. already occupied) - dispense normally
        return super.execute(blockSource, stack);
    }
}
