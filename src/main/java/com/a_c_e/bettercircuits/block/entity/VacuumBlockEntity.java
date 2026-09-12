package com.a_c_e.bettercircuits.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

//Trivial ticker anchor, matching BlowerBlockEntity/RainDetectorBlockEntity's own convention exactly - stores no
//state of its own, its only purpose is giving VacuumBlock's getTicker something to schedule the pull/particle
//logic against.
public class VacuumBlockEntity extends BlockEntity {
    public VacuumBlockEntity(BlockPos pos, BlockState state) {
        super(BCBlockEntityTypes.VACUUM.get(), pos, state);
    }
}
