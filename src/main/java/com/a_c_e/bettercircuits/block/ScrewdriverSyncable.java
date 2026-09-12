package com.a_c_e.bettercircuits.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

//Implemented by gates whose POWERED (and any other tick-gated derived property) can get permanently "stuck"
//when the screwdriver's rotation changes BOTH the input-reading direction AND the lock-trigger sides in one
//atomic step. Every one of these gates gates its OWN POWERED recompute behind "only if not currently locked" -
//DiodeBlock's own inherited tick/checkTickOnNeighbor for the heavy gates, a scheduled tick for the lightweight
//ones - matching a real redstone torch's own delayed-response feel. That's correct for NORMAL gameplay, where
//a single real neighbor-changed event can only ever affect ONE of "which way is my input" or "am I locked" at
//once (they're different physical positions) - but a screwdriver rotation changes the WHOLE orientation in one
//step, so a gate can go straight from "unlocked, stale POWERED" to "locked" without the tick that would have
//corrected POWERED ever getting a chance to run, freezing it at a value that was only ever correct for the OLD
//orientation.
//
//resyncAfterRotation lets the screwdriver ask the gate to synchronously recompute its own full derived state
//for a NEW orientation, the same way getStateForPlacement already does for a fresh placement, bypassing the
//tick-gated path entirely for this one specific case.
public interface ScrewdriverSyncable {
    BlockState resyncAfterRotation(Level level, BlockPos pos, BlockState state);
}
