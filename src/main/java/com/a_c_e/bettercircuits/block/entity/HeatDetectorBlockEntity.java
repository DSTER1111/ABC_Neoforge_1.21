package com.a_c_e.bettercircuits.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

//Trivial ticker anchor, mirroring RainDetectorBlockEntity/vanilla's own DaylightDetectorBlockEntity exactly -
//stores no state of its own, its only purpose is giving HeatDetectorBlock's getTicker something to schedule the
//every-20-ticks updateSignalStrength recompute against.
public class HeatDetectorBlockEntity extends BlockEntity {
    public HeatDetectorBlockEntity(BlockPos pos, BlockState state) {
        super(BCBlockEntityTypes.HEAT_DETECTOR.get(), pos, state);
    }
}
