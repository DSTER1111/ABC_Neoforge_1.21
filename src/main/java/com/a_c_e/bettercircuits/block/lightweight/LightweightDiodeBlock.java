package com.a_c_e.bettercircuits.block.lightweight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.EventHooks;

import java.util.EnumSet;

//Shared base for every "lightweight" gate - the any-surface counterpart to this mod's own floor-only
//DiodeBlock-based gates. DiodeBlock/HorizontalDirectionalBlock can only ever hold ONE of 4 horizontal
//directions, which is why none of these extend it or their own heavy counterpart - a block placeable on any
//solid surface needs a full 6-direction SUPPORT (which face it's stuck to) crossed with 4 possible FACING
//(input) directions perpendicular to that support - 24 total orientations (floor: 4, ceiling: 4, 4 walls x 4).
public abstract class LightweightDiodeBlock extends Block {
    //Two independent 6-value DirectionProperty instances - FACING reuses vanilla's own BlockStateProperties.FACING
    //(already the standard "any of the 6 directions" property, used by observers/droppers/etc.), SUPPORT is a
    //fresh property of the same shape, since the same property object can't be registered twice on one blockstate.
    public static final DirectionProperty SUPPORT = DirectionProperty.create("support");
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    protected LightweightDiodeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(SUPPORT, Direction.DOWN).setValue(FACING, Direction.NORTH));
    }

    //Output flows opposite FACING (the back/input direction) - same meaning FACING already has on every heavy
    //gate in this mod, just no longer restricted to a horizontal value.
    public static Direction getOutput(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    //Right = cross(SUPPORT, FACING), mapped back to a Direction. Verified by hand: for the existing floor case
    //(SUPPORT=DOWN, FACING=NORTH) this reduces EXACTLY to FACING.getClockWise() = EAST, matching AndGateBlock's
    //own established "RIGHT is FACING's clockwise side" convention - so this is a strict generalization of the
    //existing floor-only rotation, not a change to it. Left is simply the opposite.
    public static Direction getRight(BlockState state) {
        Direction support = state.getValue(SUPPORT);
        Direction facing = state.getValue(FACING);
        int x = support.getStepY() * facing.getStepZ() - support.getStepZ() * facing.getStepY();
        int y = support.getStepZ() * facing.getStepX() - support.getStepX() * facing.getStepZ();
        int z = support.getStepX() * facing.getStepY() - support.getStepY() * facing.getStepX();
        Direction right = Direction.fromDelta(x, y, z);
        if (right == null) {
            throw new IllegalStateException("SUPPORT and FACING must be perpendicular, got " + support + "/" + facing);
        }
        return right;
    }

    public static Direction getLeft(BlockState state) {
        return getRight(state).getOpposite();
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    //Same 2px-thick slab every heavy gate's own DiodeBlock-inherited getShape uses, generalized to sit flush
    //against whichever face is SUPPORT instead of always the floor.
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double thickness = 2.0;
        return switch (state.getValue(SUPPORT)) {
            case DOWN -> Block.box(0, 0, 0, 16, thickness, 16);
            case UP -> Block.box(0, 16 - thickness, 0, 16, 16, 16);
            case NORTH -> Block.box(0, 0, 0, 16, 16, thickness);
            case SOUTH -> Block.box(0, 0, 16 - thickness, 16, 16, 16);
            case WEST -> Block.box(0, 0, 0, thickness, 16, 16);
            case EAST -> Block.box(16 - thickness, 0, 0, 16, 16, 16);
        };
    }

    //Same wire-aware weak-power check every heavy gate's own getSideInputSignal/getInputSignal duplicates -
    //reads whatever's in the given direction, treating a plain redstone-wire neighbor's own POWER value as at
    //least as strong as its (often merely soft) getSignal reading.
    //
    //Bug fix: gated on touchesFace - mirrors RedstoneCableBlock's own fix for the identical gap, found first for
    //cables. level.getSignal has no concept of sub-block height: a floor-standing repeater's own thin slab
    //(y=0-2) was treated as a legitimate input just because it occupies the right ADJACENT block position, even
    //when this gate itself is mounted to a ceiling or wall - its own slab sitting nowhere near y=0-2 in that
    //same adjacent cell, with no physical overlap at all. state (not just direction) is needed now to know
    //which axis/extreme this gate's own slab actually sits at.
    public static int readSignal(SignalGetter level, BlockPos pos, BlockState state, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        if (!touchesFace(level, neighborPos, state.getValue(SUPPORT))) {
            return 0;
        }
        int signal = level.getSignal(neighborPos, direction);
        if (signal >= 15) {
            return signal;
        }
        BlockState neighborState = level.getBlockState(neighborPos);
        return Math.max(signal, neighborState.is(Blocks.REDSTONE_WIRE) ? neighborState.getValue(RedStoneWireBlock.POWER) : 0);
    }

    //Unlike RedstoneCableBlock's own version (whose collision pad is a small, position-specific box needing a
    //2-axis check), this block's own getShape() already spans the FULL footprint perpendicular to SUPPORT for
    //every orientation (see getShape's own comment) - so only the SUPPORT axis itself needs checking; the other
    //two axes are already fully covered by this gate's own geometry regardless of FACING. Threshold matches
    //this block's own 2px slab thickness.
    protected static boolean touchesFace(BlockGetter level, BlockPos pos, Direction support) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        Direction.Axis axis = support.getAxis();
        double threshold = 2.0 / 16.0;
        return support.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? shape.max(axis) >= 1.0 - threshold
                : shape.min(axis) <= threshold;
    }

    //Was duplicated identically across all 4 concrete subclasses (Inverter/AndGate/XorGate/Repeater) for their
    //own lock-trigger checks - moved here once shared, both to remove the duplication and because it had the
    //SAME touchesFace gap readSignal did (a repeater/diode-like neighbor was recognized as a valid lock trigger
    //purely by block position, regardless of whether its own geometry actually reaches this gate's slab).
    //"Diode-like" recognizes both vanilla DiodeBlock instances and other LightweightDiodeBlock instances, so
    //heavy and lightweight gates can lock each other.
    public static int getControlInputSignal(LevelReader level, BlockPos pos, BlockState state, Direction direction) {
        return getControlInputSignal(level, pos, state, direction, true);
    }

    //diodesOnly=false generalizes this to vanilla's own SignalGetter#getControlInputSignal(pos, direction,
    //diodesOnly) semantics (redstone block -> 15, redstone wire -> its own POWER value, otherwise any
    //isSignalSource() -> its signal, not just diode-like neighbors) - needed by LightweightComparatorBlock's
    //own alternate/side-input read, which (unlike every diode-locking gate in this mod) accepts ANY signal
    //source on its sides, not just other diodes (matches ComparatorBlock's own sideInputDiodesOnly()=false
    //default - it never overrides that method, unlike InverterBlock/AndGateBlock/etc).
    public static int getControlInputSignal(LevelReader level, BlockPos pos, BlockState state, Direction direction, boolean diodesOnly) {
        BlockPos neighborPos = pos.relative(direction);
        if (!touchesFace(level, neighborPos, state.getValue(SUPPORT))) {
            return 0;
        }
        BlockState neighborState = level.getBlockState(neighborPos);
        if (diodesOnly) {
            if (!(neighborState.getBlock() instanceof DiodeBlock) && !(neighborState.getBlock() instanceof LightweightDiodeBlock)) {
                return 0;
            }
            return level.getSignal(neighborPos, direction);
        }
        if (neighborState.is(Blocks.REDSTONE_BLOCK)) {
            return 15;
        }
        if (neighborState.is(Blocks.REDSTONE_WIRE)) {
            return neighborState.getValue(RedStoneWireBlock.POWER);
        }
        return neighborState.isSignalSource() ? level.getSignal(neighborPos, direction) : 0;
    }

    //SUPPORT = clicked face's opposite (matches RedstoneCableBlockItem's own existing "towardSupport" convention
    //in this mod). FACING (the INPUT side) = the opposite of the player's own ranked "nearest looking
    //direction" that's perpendicular to SUPPORT - getNearestLookingDirections()'s entries point the way the
    //player is LOOKING (same sense as getHorizontalDirection(), confirmed via BlockPlaceContext's own
    //Direction.orderedByNearest(player) - "nearest to the player's look vector", i.e. away from the player),
    //so FACING needs the .getOpposite() to point back toward the player, matching every heavy gate's own
    //"FACING = context.getHorizontalDirection().getOpposite()" convention (input faces the player).
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction support = context.getClickedFace().getOpposite();
        for (Direction candidate : context.getNearestLookingDirections()) {
            if (candidate.getAxis() != support.getAxis()) {
                return this.defaultBlockState().setValue(SUPPORT, support).setValue(FACING, candidate.getOpposite());
            }
        }
        //Unreachable in practice - getNearestLookingDirections() always includes at least 4 directions
        //perpendicular to any given axis - but every code path needs a value.
        Direction fallback = support.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        return this.defaultBlockState().setValue(SUPPORT, support).setValue(FACING, fallback);
    }

    //Generalizes FaceAttachedHorizontalDirectionalBlock's own canAttach (used by Lever/Button) to all 6
    //directions instead of just DOWN/UP/horizontal - same SupportType.RIGID check DiodeBlock's own
    //canSurviveOn uses for the floor-only heavy gates.
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction support = state.getValue(SUPPORT);
        BlockPos supportPos = pos.relative(support);
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, support.getOpposite(), SupportType.RIGID);
    }

    //Same single-direction neighbor-notification pattern RSLatchBlock/TimerBlockEntity/CapacitorBlock/
    //RandomizerBlock already use in this mod, just generalized to an arbitrary Direction instead of a
    //hardcoded FACING.getOpposite().
    protected void notifyOutput(Level level, BlockPos pos, Direction direction) {
        if (EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), EnumSet.of(direction), false).isCanceled()) {
            return;
        }
        BlockPos neighborPos = pos.relative(direction);
        level.neighborChanged(neighborPos, this, pos);
        level.updateNeighborsAtExceptFromFacing(neighborPos, this, direction.getOpposite());
    }
}
