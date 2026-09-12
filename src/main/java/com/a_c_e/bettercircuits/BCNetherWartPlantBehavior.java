package com.a_c_e.bettercircuits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;

//Same idea as BCFarmlandPlantBehavior (dispenser plants onto the checked block's top face instead of just
//ejecting the item), but Nether Wart isn't a CropBlock/FarmBlock crop - it grows on Soul Sand specifically
//(see NetherWartBlock#mayPlaceOn) - so it needs its own support-block check rather than sharing that class.
public class BCNetherWartPlantBehavior extends DefaultDispenseItemBehavior {
    @Override
    protected ItemStack execute(BlockSource blockSource, ItemStack stack) {
        Level level = blockSource.level();
        Direction direction = blockSource.state().getValue(DispenserBlock.FACING);
        BlockPos frontPos = blockSource.pos().relative(direction);

        if (level.getBlockState(frontPos).is(Blocks.SOUL_SAND) && stack.getItem() instanceof BlockItem blockItem) {
            BlockPos plantPos = frontPos.above();
            if (blockItem.place(new DirectionalPlaceContext(level, plantPos, direction, stack, Direction.UP)).consumesAction()) {
                return stack;
            }
        }

        //Not aimed at soul sand, or the wart couldn't be placed there (e.g. already occupied) - dispense normally
        return super.execute(blockSource, stack);
    }
}
