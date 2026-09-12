package com.a_c_e.bettercircuits.block.lightweight;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import com.a_c_e.bettercircuits.block.ScrewdriverSyncable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

//Any-surface counterpart to AndGateBlock - identical 2-side-input AND logic (reads Left/Right instead of a
//single front input, locks from the back/FACING side, LEFT_POWERED/RIGHT_POWERED are purely visual and update
//immediately) built on LightweightDiodeBlock's SUPPORT+FACING orientation. See LightweightInverterBlock's own
//class comment for why this can't extend DiodeBlock/AndGateBlock directly, and readSignal/getControlInputSignal
//equivalents are reused straight from the base class instead of AndGateBlock's own getSideInputSignal (the same
//wire-aware weak-power check, already generalized there).
public class LightweightAndGateBlock extends LightweightDiodeBlock implements ScrewdriverSyncable {
    public static final MapCodec<LightweightAndGateBlock> CODEC = simpleCodec(LightweightAndGateBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LEFT_POWERED = BooleanProperty.create("left_powered");
    public static final BooleanProperty RIGHT_POWERED = BooleanProperty.create("right_powered");
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    private static final int DELAY_TICKS = 2;

    public LightweightAndGateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SUPPORT, Direction.DOWN).setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false).setValue(LEFT_POWERED, false).setValue(RIGHT_POWERED, false)
                .setValue(LOCKED, false));
    }

    @Override
    public MapCodec<LightweightAndGateBlock> codec() {
        return CODEC;
    }

    private boolean shouldTurnOn(LevelReader level, BlockPos pos, BlockState state) {
        return readSignal(level, pos, state, getLeft(state)) > 0 && readSignal(level, pos, state, getRight(state)) > 0;
    }

    //Back/FACING side locks (the one side this gate otherwise never reads at all, since both real sides are
    //spoken for as inputs) - matches AndGateBlock's own isLocked exactly, generalized to LightweightDiodeBlock's
    //"diode-like" (heavy DiodeBlock OR LightweightDiodeBlock) recognition instead of DiodeBlock.isDiode alone.
    private boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return getControlInputSignal(level, pos, state, state.getValue(FACING)) > 0;
    }

    //LEFT_POWERED/RIGHT_POWERED (purely visual per-side torches) and LOCKED are evaluated synchronously at
    //placement, matching LightweightInverterBlock's own POWERED override - a freshly placed gate should reflect
    //its surroundings immediately rather than waiting for a later tick. POWERED itself (the real AND result)
    //is deliberately left at its false default: unlike an Inverter, "no input on either side" naturally means
    //AND output should be off, so no synchronous override is needed for it.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean leftOn = readSignal(level, pos, state, getLeft(state)) > 0;
        boolean rightOn = readSignal(level, pos, state, getRight(state)) > 0;
        state = state.setValue(LEFT_POWERED, leftOn).setValue(RIGHT_POWERED, rightOn);
        return state.setValue(LOCKED, isLocked(level, pos, state));
    }

    //Only recompute LOCKED for changes directly behind the gate (facing == FACING, matching AndGateBlock's own
    //back-side-only updateShape) - Left/Right (the real inputs) and Output changes fall through untouched.
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!level.isClientSide() && facing == state.getValue(FACING)) {
            return state.setValue(LOCKED, isLocked((LevelReader) level, currentPos, state));
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!state.canSurvive(level, pos)) {
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            dropResources(state, level, pos, blockEntity);
            level.removeBlock(pos, false);
            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }
            return;
        }
        if (level.isClientSide) {
            return;
        }
        boolean leftOn = readSignal(level, pos, state, getLeft(state)) > 0;
        boolean rightOn = readSignal(level, pos, state, getRight(state)) > 0;
        boolean locked = isLocked(level, pos, state);
        if (leftOn != state.getValue(LEFT_POWERED) || rightOn != state.getValue(RIGHT_POWERED) || locked != state.getValue(LOCKED)) {
            state = state.setValue(LEFT_POWERED, leftOn).setValue(RIGHT_POWERED, rightOn).setValue(LOCKED, locked);
            level.setBlock(pos, state, 2);
        }
        if (!locked) {
            level.scheduleTick(pos, this, DELAY_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isLocked(level, pos, state)) {
            return;
        }
        boolean shouldBeOn = shouldTurnOn(level, pos, state);
        boolean isOn = state.getValue(POWERED);
        if (shouldBeOn != isOn) {
            level.setBlock(pos, state.setValue(POWERED, shouldBeOn), 2);
            notifyOutput(level, pos, getOutput(state));
        }
    }

    //See ScrewdriverSyncable's own comment for why this needs to exist at all - the normal tick-gated POWERED
    //update never runs when a rotation makes the block locked in the same instant its input directions change.
    @Override
    public BlockState resyncAfterRotation(Level level, BlockPos pos, BlockState state) {
        boolean leftOn = readSignal(level, pos, state, getLeft(state)) > 0;
        boolean rightOn = readSignal(level, pos, state, getRight(state)) > 0;
        state = state.setValue(LEFT_POWERED, leftOn).setValue(RIGHT_POWERED, rightOn)
                .setValue(LOCKED, isLocked(level, pos, state));
        return state.setValue(POWERED, shouldTurnOn(level, pos, state));
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) && side == state.getValue(FACING) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SUPPORT, FACING, POWERED, LEFT_POWERED, RIGHT_POWERED, LOCKED);
    }
}
