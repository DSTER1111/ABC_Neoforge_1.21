package com.a_c_e.bettercircuits;

import com.a_c_e.bettercircuits.block.BCBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

//Bug fix: FilteredHopperBlockEntity extends HopperBlockEntity, whose own 2-arg constructor hardcodes vanilla's
//real BlockEntityType.HOPPER internally (no subclass override point exists - see FilteredHopperBlock's own
//comment), so every instance's getType() reports THAT, not any separate type this mod could register. This was
//already accounted for in getTicker/the renderer registration, but LevelChunk.setBlockEntity ALSO calls
//blockEntity.getType().isValid(state) - decompiled directly - before (re-)registering a block entity into the
//chunk's own tracked maps (the SAME maps ticking and block-entity rendering both read from), and silently
//WARN-AND-BAILS if it returns false, without adding the block entity to those maps at all. BlockEntityType.
//HOPPER's own validBlocks set (an ImmutableSet fixed at vanilla's own bootstrap, containing only Blocks.HOPPER)
//obviously never included BCBlocks.FILTERED_HOPPER, so every later re-validation of this position (confirmed
//via boot-test diagnostics: the block was reachable via getBlockEntity/interactable at first, but never once
//appeared in the tick or render passes) failed this check and got silently dropped from tracking - explaining
//both "picks up once then never again" (the ticker that decrements cooldown never got attached) and "no visible
//item" (block entity rendering never got attached either) in one shot.
//
//validBlocks is a plain instance field (not static final), so - unlike genuinely inlined static-final constants
//- reflectively reassigning it on the ONE shared BlockEntityType.HOPPER object works reliably and is a
//well-established technique for exactly this "make my block count as a vanilla block-entity type" situation
//(the same category of trick mods use to make a custom furnace/chest interoperate with hopper suck-in logic
//that also gates on isValid). This only ADDS an entry - Blocks.HOPPER stays valid too - so every real vanilla
//hopper in the game is completely unaffected.
public class BCHopperBlockEntityTypeFix {
    private BCHopperBlockEntityTypeFix() {
    }

    public static void register() {
        try {
            Field validBlocksField = BlockEntityType.class.getDeclaredField("validBlocks");
            validBlocksField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<Block> current = (Set<Block>) validBlocksField.get(BlockEntityType.HOPPER);
            Set<Block> expanded = new HashSet<>(current);
            expanded.add(BCBlocks.FILTERED_HOPPER.get());
            validBlocksField.set(BlockEntityType.HOPPER, Set.copyOf(expanded));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to register Filtered Hopper as a valid block for BlockEntityType.HOPPER", e);
        }
    }
}
