package com.a_c_e.bettercircuits.item;

import com.a_c_e.bettercircuits.block.RSLatchBlock;
import com.a_c_e.bettercircuits.block.ScrewdriverSyncable;
import com.a_c_e.bettercircuits.block.lightweight.LightweightDiodeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.function.Predicate;

//Shift-right-click on any block with a recognized orientation property to cycle it, without needing to break
//and replace it. Detected purely by which property the target's own BlockState declares, not a hardcoded block
//list, so it automatically covers any future block using one of the same properties:
//
//- BlockStateProperties.FACING (Dispenser/Dropper/Observer/Piston/every lightweight gate in this mod) and
//  HORIZONTAL_FACING (Repeater/Comparator/every heavy gate in this mod) and FACING_HOPPER (Hopper) - a single
//  Direction value, cycled through this class's own UP->NORTH->EAST->DOWN->SOUTH->WEST order (NOT vanilla's own
//  Direction.values() order, per the user's own explicit spec), skipping any direction the property doesn't
//  declare as legal.
//- BlockStateProperties.RAIL_SHAPE (plain Rail) and RAIL_SHAPE_STRAIGHT (Powered/Detector/Activator Rail) - a
//  simple 2-way toggle between NORTH_SOUTH and EAST_WEST (per the user's own correction; curves/slopes/
//  junctions are left alone rather than force-cycled, since setting those independently of what's actually
//  touching the rail just produces a shape disconnected from the adjacent track).
//- BlockStateProperties.ORIENTATION (Crafter's own FrontAndTop: a front direction + a top direction, 12 valid
//  combinations) - only the FRONT half is cycled, through the same UP/NORTH/EAST/DOWN/SOUTH/WEST order every
//  other block uses; TOP is picked automatically (see nextOrientation's own comment for the exact policy),
//  since FrontAndTop only ever allows one specific top for a horizontal front anyway.
//
//Lightweight gates need one more constraint beyond "is this a legal property value at all": FACING must stay
//PERPENDICULAR to SUPPORT (the face it's physically mounted on) - a value equal to SUPPORT or its opposite
//would mean facing directly into/out of the wall it's stuck to, which LightweightDiodeBlock's own
//getStateForPlacement never produces and getRight/getLeft can't cross-product correctly either. Detected via
//state.hasProperty(LightweightDiodeBlock.SUPPORT) rather than a block-type check, so it also transparently
//covers any future lightweight gate added to that same family.
//
//After applying a new FACING/HORIZONTAL_FACING/FACING_HOPPER or ORIENTATION value, this self-notifies via
//level.neighborChanged(pos, block, pos) rather than hand-rolling a resync for every possible derived property
//(LOCKED/POWERED/RUNNING/INPUT_ON/etc. across a dozen different gate types) - every gate in this mod's own
//neighborChanged already recomputes its own derived state fully live from the CURRENT blockstate whenever
//called (that's exactly what happens on a real neighbor event), so calling it immediately after the rotation
//reuses each block's own existing reactive logic instead of duplicating it here. Timer/Comparator's own
//continuous-tick variants don't need this at all - their tickers pick up the rotated FACING on their very next
//tick regardless. Rails deliberately DON'T get this self-notify: a rail's own neighborChanged/updateState
//machinery recomputes its shape FROM its neighbors, which would just immediately overwrite the shape this item
//just set - the whole point here is to let the player override that.
//
//Once a valid shift-right-click is confirmed (player present, sneaking, has build permission, permitted by
//other mods/claims), this ALWAYS consumes the interaction, even when the target has no recognized orientation
//property at all - otherwise the click falls through to the block's own default use behavior (e.g. Jukebox's
//own record-eject interaction, or Crafter's own slot-toggle interaction), which looks like "the screwdriver
//did nothing" while actually doing something unintended.
public class ScrewdriverItem extends Item {
    private static final Direction[] CYCLE = {
            Direction.UP, Direction.NORTH, Direction.EAST, Direction.DOWN, Direction.SOUTH, Direction.WEST
    };

    public ScrewdriverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        BlockPos pos = context.getClickedPos();
        if (!player.getAbilities().mayBuild || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockState state = level.getBlockState(pos);
            Rotation rotation = computeRotation(state);
            if (rotation != null) {
                level.setBlock(pos, rotation.newState, rotation.setBlockFlags);
                if (rotation.resync) {
                    //ScrewdriverSyncable blocks bypass neighborChanged entirely here - see that interface's own
                    //comment for why the normal tick-gated resync can get permanently stuck when a rotation
                    //changes both the input direction and lock status in one atomic step, which only a
                    //synchronous full recompute (not neighborChanged) can correct.
                    if (rotation.newState.getBlock() instanceof ScrewdriverSyncable syncable) {
                        level.setBlock(pos, syncable.resyncAfterRotation(level, pos, rotation.newState), Block.UPDATE_ALL);
                    } else {
                        level.neighborChanged(pos, rotation.newState.getBlock(), pos);
                    }
                }
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, 0.7F);
                context.getItemInHand().hurtAndBreak(1, player,
                        context.getHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private record Rotation(BlockState newState, boolean resync, int setBlockFlags) {
    }

    @Nullable
    private static Rotation computeRotation(BlockState state) {
        if (isExcluded(state)) {
            return null;
        }

        DirectionProperty directionProperty = findDirectionProperty(state);
        if (directionProperty != null) {
            Direction next = nextDirection(state, directionProperty);
            if (next != null && next != state.getValue(directionProperty)) {
                //RS Latch (heavy and lightweight both reuse this exact property instance) deliberately does NOT
                //get the self-notify: FLIPPED is a memory bit representing which of its 2 states is active, not
                //something that should react to a purely cosmetic rotation. The self-notify calls into the
                //block's own neighborChanged, which for RS Latch runs tryFlip - if there happens to be live
                //signal on whatever the NEW orientation's active input side is, that immediately toggles
                //FLIPPED right after the rotation. Since FLIPPED's own rendering adds an extra 180 degrees on
                //top of FACING (by design - see the datagen's own rsLatch()/lightweightRSLatch() comments), an
                //unwanted flip right after rotating can make the block visually land on what looks exactly like
                //the OPPOSITE FACING value (NORTH+flipped renders identically to SOUTH+unflipped, and likewise
                //for EAST/WEST) even though the actually-stored FACING was set correctly the whole time - this
                //is exactly the bug the user found: not a misread, but an unwanted side effect that happens to
                //be visually indistinguishable from one.
                boolean resync = !state.hasProperty(RSLatchBlock.FLIPPED);
                return new Rotation(state.setValue(directionProperty, next), resync, Block.UPDATE_ALL);
            }
            return null;
        }

        Property<RailShape> railShapeProperty = findRailShapeProperty(state);
        if (railShapeProperty != null) {
            RailShape next = nextStraightRailShape(state.getValue(railShapeProperty));
            if (next != null && next != state.getValue(railShapeProperty)) {
                //UPDATE_CLIENTS only, deliberately WITHOUT UPDATE_NEIGHBORS: that flag alone is enough to
                //trigger the rail's own neighbor-driven shape recompute immediately, silently reverting this
                //exact change before it ever renders - the whole point here is to let the player override the
                //auto-connect logic, not just bounce straight back to it.
                return new Rotation(state.setValue(railShapeProperty, next), false, Block.UPDATE_CLIENTS);
            }
            return null;
        }

        if (state.hasProperty(BlockStateProperties.ORIENTATION)) {
            FrontAndTop current = state.getValue(BlockStateProperties.ORIENTATION);
            FrontAndTop next = nextOrientation(current);
            if (next != null && next != current) {
                return new Rotation(state.setValue(BlockStateProperties.ORIENTATION, next), true, Block.UPDATE_ALL);
            }
        }

        return null;
    }

    //These blocks all HAVE one of the recognized orientation properties, so they'd otherwise look rotatable to
    //the generic detection above - but changing that property alone doesn't correspond to any physically valid
    //configuration for them, unlike every other block this handles:
    //- Wall-mounted Lever/Button: HORIZONTAL_FACING there means "which direction is the wall I'm stuck to", not
    //  a cosmetic spin - changing it just claims to be attached to a DIFFERENT wall than the one actually
    //  supporting it, without moving anything. Floor/ceiling mounting has no such issue (HORIZONTAL_FACING is
    //  purely cosmetic there, always attached to the block above/below regardless of its value), so only WALL
    //  attachment is excluded.
    //- Tripwire Hook: exactly the same problem as a wall lever, but unconditionally - it's ALWAYS wall-mounted,
    //  there's no floor/ceiling case to allow.
    //- Non-SINGLE (double/large) Chest: rotating only the clicked half would desync it from its paired half
    //  (each half's own FACING has to agree for the double-chest geometry to render connected at all).
    //- A door whose BlockSetType says it can't be opened by hand (vanilla's own Iron Door, or any modded
    //  equivalent) - per the user's own spec.
    private static boolean isExcluded(BlockState state) {
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE) && state.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.WALL) {
            return true;
        }
        if (state.getBlock() instanceof TripWireHookBlock) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.CHEST_TYPE) && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
            return true;
        }
        return state.getBlock() instanceof DoorBlock door && !door.type().canOpenByHand();
    }

    @Nullable
    private static DirectionProperty findDirectionProperty(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return BlockStateProperties.FACING;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return BlockStateProperties.HORIZONTAL_FACING;
        }
        if (state.hasProperty(BlockStateProperties.FACING_HOPPER)) {
            return BlockStateProperties.FACING_HOPPER;
        }
        return null;
    }

    @Nullable
    private static Property<RailShape> findRailShapeProperty(BlockState state) {
        if (state.hasProperty(BlockStateProperties.RAIL_SHAPE)) {
            return BlockStateProperties.RAIL_SHAPE;
        }
        if (state.hasProperty(BlockStateProperties.RAIL_SHAPE_STRAIGHT)) {
            return BlockStateProperties.RAIL_SHAPE_STRAIGHT;
        }
        return null;
    }

    @Nullable
    private static Direction nextDirection(BlockState state, DirectionProperty property) {
        Collection<Direction> allowed = property.getPossibleValues();
        Direction support = state.hasProperty(LightweightDiodeBlock.SUPPORT) ? state.getValue(LightweightDiodeBlock.SUPPORT) : null;
        return nextInCycle(state.getValue(property), direction -> allowed.contains(direction)
                && (support == null || (direction != support && direction != support.getOpposite())));
    }

    //Front is cycled the same way every plain-Direction block is (all 6 directions are legal fronts across
    //FrontAndTop's own 12 combos, so nothing is ever skipped here) - top is then picked deterministically: a
    //horizontal front only ever has ONE legal top (UP, per FrontAndTop's own enum), so that's forced; a
    //vertical (UP/DOWN) front needs a horizontal top, so this reuses the CURRENT top if it's already horizontal
    //(preserving the twist when flipping between floor/ceiling mounting), or defaults to NORTH otherwise (e.g.
    //coming from a horizontal front, whose own top was UP and so isn't reusable).
    @Nullable
    private static FrontAndTop nextOrientation(FrontAndTop current) {
        Direction nextFront = nextInCycle(current.front(), direction -> true);
        if (nextFront == null) {
            return null;
        }
        Direction nextTop;
        if (nextFront.getAxis() == Direction.Axis.Y) {
            nextTop = current.top().getAxis() != Direction.Axis.Y ? current.top() : Direction.NORTH;
        } else {
            nextTop = Direction.UP;
        }
        return FrontAndTop.fromFrontAndTop(nextFront, nextTop);
    }

    @Nullable
    private static Direction nextInCycle(Direction current, Predicate<Direction> allowed) {
        int currentIndex = -1;
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex == -1) {
            return null;
        }
        for (int step = 1; step <= CYCLE.length; step++) {
            Direction candidate = CYCLE[(currentIndex + step) % CYCLE.length];
            if (allowed.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    //Simple 2-way toggle per the user's own correction (an earlier version cycled through every legal shape,
    //including curves/ascending/junctions - reverted since forcing those independently of what's actually
    //touching the rail produces a shape disconnected from the adjacent track). Any shape other than the two
    //straight ones (a curve, slope, or junction) normalizes to NORTH_SOUTH on the first click rather than
    //erroring or no-oping, so the toggle always has a well-defined starting point.
    private static RailShape nextStraightRailShape(RailShape current) {
        return current == RailShape.NORTH_SOUTH ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
    }
}
