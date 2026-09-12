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

//A true 2-input AND gate, unlike vanilla's own DiodeBlock subclasses (repeater/comparator), which each read a
//single input from directly behind FACING - that's DiodeBlock's OWN convention (see getInputSignal: it reads
//pos.relative(FACING) directly), so FACING is the back/input side and OUTPUT actually flows the opposite way,
//FACING.getOpposite() (confirmed against BCBlockStateProvider.andGateModel's own geometry, which places the
//output torch there). This block ignores that back side's input entirely and instead reads its two inputs from
//the SIDES - the block's own left/right as seen by someone standing on the input side looking toward the output.
//LEFT is FACING's counter-clockwise side, RIGHT is FACING's clockwise side (see andGate() in
//BCBlockStateProvider for the matching torch placement in the model).
//
//LEFT_POWERED/RIGHT_POWERED are purely visual - which side torch is lit - and update immediately whenever either
//side's signal changes, independent of each other. POWERED (the real AND result: true only once both sides are
//high) still goes through DiodeBlock's own delayed-tick mechanism inherited unchanged, exactly like a real
//repeater/comparator, and is what actually drives the output signal (via DiodeBlock's default getSignal/
//getOutputSignal) and the middle torch/top texture.
//
//LOCKED (see isLocked below) freezes POWERED the same way a repeater's own lock does, but the trigger is the one
//side this block otherwise never uses at all - directly behind FACING - since both actual sides are already
//spoken for as real inputs.
public class AndGateBlock extends DiodeBlock implements ScrewdriverSyncable {
    public static final MapCodec<AndGateBlock> CODEC = simpleCodec(AndGateBlock::new);
    public static final BooleanProperty LEFT_POWERED = BooleanProperty.create("left_powered");
    public static final BooleanProperty RIGHT_POWERED = BooleanProperty.create("right_powered");
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;

    public AndGateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(POWERED, false)
                .setValue(LEFT_POWERED, false).setValue(RIGHT_POWERED, false).setValue(LOCKED, false));
    }

    @Override
    public MapCodec<AndGateBlock> codec() {
        return CODEC;
    }

    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        return getSideInputSignal(level, pos, state, leftDirection(state)) > 0
                && getSideInputSignal(level, pos, state, rightDirection(state)) > 0;
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

    //LOCKED here isn't triggered the way RepeaterBlock/InverterBlock trigger it (a diode facing into one of
    //their SIDES) - this block already uses both sides as real inputs, so locking instead comes from the one
    //side that's otherwise completely unused: directly behind the gate, on the OUTPUT's opposite side. FACING
    //itself IS that back/unused side, not its opposite - confirmed against DiodeBlock's own getInputSignal
    //(reads pos.relative(FACING) directly for its front input) and against this block's own datagen comment in
    //BCBlockStateProvider.andGateModel ("Output side... opposite FACING"): the OUTPUT is at FACING.getOpposite(),
    //so FACING alone is the correct direction to query here, not FACING.getOpposite() (an earlier version of
    //this got that backwards, which meant the lock check queried the OUTPUT side instead of the back and could
    //never actually trigger). A diode there outputting a strong signal freezes POWERED, same as everywhere else
    //- DiodeBlock's own inherited tick/checkTickOnNeighbor already skip updating POWERED whenever isLocked() is
    //true, so overriding isLocked correctly is the only thing actually needed for the freeze itself.
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

    //Only recompute LOCKED for changes directly behind the gate (facing == FACING, see isLocked's own comment
    //on why FACING - not its opposite - is the back) - side (left/right, the real inputs) and front (output)
    //changes fall through to the normal shape-update path untouched.
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

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (state.canSurvive(level, pos)) {
            boolean leftOn = getSideInputSignal(level, pos, state, leftDirection(state)) > 0;
            boolean rightOn = getSideInputSignal(level, pos, state, rightDirection(state)) > 0;
            if (state.getValue(LEFT_POWERED) != leftOn || state.getValue(RIGHT_POWERED) != rightOn) {
                state = state.setValue(LEFT_POWERED, leftOn).setValue(RIGHT_POWERED, rightOn);
                level.setBlock(pos, state, 2);
            }
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    //See ScrewdriverSyncable's own comment for why this needs to exist at all - the normal tick-gated POWERED
    //update never runs when a rotation makes the block locked in the same instant its input directions change.
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
