package com.a_c_e.bettercircuits.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

//Same 2-side-input shape as AndGateBlock (see its own class comment for the LEFT/RIGHT convention - LEFT is
//FACING's counter-clockwise side, RIGHT is clockwise; FACING itself is the back/input side DiodeBlock's own
//getInputSignal reads, output actually flows FACING.getOpposite()), but the gate logic is XOR: output only
//goes high when exactly one side is powered, not when both are (or neither is).
//
//Unlike AndGateBlock, there's no separate torch-lit visual for the delayed POWERED tick - the 4 provided
//textures (xor_gate_off_off/off_on/on_left/on_right) already encode output state directly via their own
//off_/on_ prefix, keyed straight off the instantaneous LEFT_POWERED/RIGHT_POWERED reads: both off -> off_off,
//both on -> off_on (output off either way, distinct from "nothing powered"), left only -> on_left, right only
//-> on_right. POWERED (the actual, delayed output signal - still needed for DiodeBlock's own
//getSignal/getOutputSignal mechanics, same propagation-delay behavior as a real repeater/comparator) doesn't
//drive any texture choice at all here.
//
//LOCKED mirrors AndGateBlock's own version exactly: the trigger is the one side this block never uses at all
//(directly behind FACING, since both actual sides are already real inputs), not the perpendicular-side trigger
//RepeaterBlock/InverterBlock use. Unlike AndGateBlock, LEFT_POWERED/RIGHT_POWERED freeze while locked too (see
//neighborChanged) - the locked visual here still needs an actual left/right texture to show (with the bar
//overlaid), so it has to freeze at whatever it looked like right before locking, not just hide input state
//behind a generic bar the way AndGateBlock's own locked visual does.
public class XorGateBlock extends DiodeBlock implements ScrewdriverSyncable {
    public static final MapCodec<XorGateBlock> CODEC = simpleCodec(XorGateBlock::new);
    public static final BooleanProperty LEFT_POWERED = BooleanProperty.create("left_powered");
    public static final BooleanProperty RIGHT_POWERED = BooleanProperty.create("right_powered");
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;

    public XorGateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(POWERED, false)
                .setValue(LEFT_POWERED, false).setValue(RIGHT_POWERED, false).setValue(LOCKED, false));
    }

    @Override
    public MapCodec<XorGateBlock> codec() {
        return CODEC;
    }

    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        boolean leftOn = getSideInputSignal(level, pos, state, leftDirection(state)) > 0;
        boolean rightOn = getSideInputSignal(level, pos, state, rightDirection(state)) > 0;
        return leftOn != rightOn;
    }

    //Mirrors DiodeBlock's own getInputSignal (the front-input read) rather than getAlternateSignal/
    //getControlInputSignal, which only ever look for it.isSignalSource() (a direct signal source - levers, wire,
    //other diodes) and deliberately ignore soft/indirect power. getSignal instead checks shouldCheckWeakPower,
    //so a plain block sitting on either input side that's merely soft-powered by an adjacent lever/wire still
    //counts as an input here, matching how a repeater/comparator's own front input behaves.
    private int getSideInputSignal(SignalGetter level, BlockPos pos, BlockState state, Direction side) {
        BlockPos neighborPos = pos.relative(side);
        int signal = level.getSignal(neighborPos, side);
        if (signal >= 15) {
            return signal;
        }
        BlockState neighborState = level.getBlockState(neighborPos);
        return Math.max(signal, neighborState.is(Blocks.REDSTONE_WIRE) ? neighborState.getValue(RedStoneWireBlock.POWER) : 0);
    }

    private Direction leftDirection(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    private Direction rightDirection(BlockState state) {
        return state.getValue(FACING).getClockWise();
    }

    //See AndGateBlock's own isLocked for the full reasoning - FACING is the back/unused side here (not its
    //opposite, which is the output), so it's queried directly.
    @Override
    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        Direction back = state.getValue(FACING);
        return level.getControlInputSignal(pos.relative(back), back, true) > 0;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(LOCKED, isLocked(context.getLevel(), context.getClickedPos(), state));
    }

    //Only recompute LOCKED for changes directly behind the gate (facing == FACING) - side (left/right, the real
    //inputs) and front (output) changes fall through to the normal shape-update path untouched.
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!level.isClientSide() && facing == state.getValue(FACING)) {
            return state.setValue(LOCKED, isLocked(level, currentPos, state));
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        boolean leftOn = getSideInputSignal(level, pos, state, leftDirection(state)) > 0;
        boolean rightOn = getSideInputSignal(level, pos, state, rightDirection(state)) > 0;
        if (leftOn || rightOn) {
            state = state.setValue(LEFT_POWERED, leftOn).setValue(RIGHT_POWERED, rightOn);
            level.setBlock(pos, state, 2);
        }
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    //While LOCKED, LEFT_POWERED/RIGHT_POWERED freeze too, not just POWERED - unlike AndGateBlock, this block's
    //locked visual (see xorGate() in BCBlockStateProvider) still shows a left/right-specific texture with the
    //bar overlaid, so it needs an actual frozen snapshot to display, not just live values that happen to be
    //hidden. Without this, the texture would keep changing with the (still-live) inputs even while locked,
    //defeating the point of a "locked" state showing what it looked like right before locking.
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (state.canSurvive(level, pos)) {
            if (!isLocked(level, pos, state)) {
                boolean leftOn = getSideInputSignal(level, pos, state, leftDirection(state)) > 0;
                boolean rightOn = getSideInputSignal(level, pos, state, rightDirection(state)) > 0;
                if (state.getValue(LEFT_POWERED) != leftOn || state.getValue(RIGHT_POWERED) != rightOn) {
                    state = state.setValue(LEFT_POWERED, leftOn).setValue(RIGHT_POWERED, rightOn);
                    level.setBlock(pos, state, 2);
                }
            }
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    //See ScrewdriverSyncable's own comment for why this needs to exist at all - the normal tick-gated POWERED
    //update never runs when a rotation makes the block locked in the same instant its input directions change.
    //Always computes LEFT_POWERED/RIGHT_POWERED fresh here (unlike neighborChanged's own locked-freeze
    //ternary) - this is a one-time "as if freshly placed in this orientation" snapshot, not the ongoing
    //while-locked freeze that ternary exists for.
    @Override
    public BlockState resyncAfterRotation(Level level, BlockPos pos, BlockState state) {
        boolean leftOn = getSideInputSignal(level, pos, state, leftDirection(state)) > 0;
        boolean rightOn = getSideInputSignal(level, pos, state, rightDirection(state)) > 0;
        state = state.setValue(LEFT_POWERED, leftOn).setValue(RIGHT_POWERED, rightOn)
                .setValue(LOCKED, isLocked(level, pos, state));
        return state.setValue(POWERED, shouldTurnOn(level, pos, state));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LEFT_POWERED, RIGHT_POWERED, LOCKED);
    }
}
