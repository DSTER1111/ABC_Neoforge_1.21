package com.a_c_e.bettercircuits.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

//Trivial ticker anchor, matching RainDetectorBlockEntity's own convention exactly - stores no state of its own,
//its only purpose is giving BlowerBlock's getTicker something to schedule the push/particle logic against.
public class BlowerBlockEntity extends BlockEntity {
    public BlowerBlockEntity(BlockPos pos, BlockState state) {
        super(BCBlockEntityTypes.BLOWER.get(), pos, state);
    }
}
