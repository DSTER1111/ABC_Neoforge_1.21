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

//Any-surface counterpart to InverterBlock - identical redstone behavior (inverts the input, side-locks from a
//diode-like neighbor on Left or Right) built on LightweightDiodeBlock's SUPPORT+FACING orientation instead of a
//horizontal-only FACING. No DiodeBlock inheritance is possible (see LightweightDiodeBlock's own class comment),
//so the delayed POWERED flip DiodeBlock normally provides via its own tick/getDelay/shouldTurnOn machinery is
//reimplemented directly here using the same scheduleTick+tick() mechanism every Block (not just DiodeBlock
//subclasses) supports.
public class LightweightInverterBlock extends LightweightDiodeBlock implements ScrewdriverSyncable {
    public static final MapCodec<LightweightInverterBlock> CODEC = simpleCodec(LightweightInverterBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    private static final int DELAY_TICKS = 2;

    public LightweightInverterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SUPPORT, Direction.DOWN).setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false).setValue(LOCKED, false));
    }

    @Override
    public MapCodec<LightweightInverterBlock> codec() {
        return CODEC;
    }

    private boolean shouldTurnOn(LevelReader level, BlockPos pos, BlockState state) {
        return readSignal(level, pos, state, state.getValue(FACING)) == 0;
    }

    //Diode-only strong signal from Left or Right locks the block - matches InverterBlock's own
    //getAlternateSignal/sideInputDiodesOnly=true convention exactly, generalized to the new Left/Right.
    //"Diode-like" recognizes both vanilla DiodeBlock instances and other LightweightDiodeBlock instances, so
    //heavy and lightweight gates can lock each other.
    private boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return getControlInputSignal(level, pos, state, getLeft(state)) > 0 || getControlInputSignal(level, pos, state, getRight(state)) > 0;
    }

    //POWERED is evaluated synchronously here (same as LOCKED below) rather than left false and relying on a
    //later tick, so a freshly placed Inverter reflects its surroundings immediately - matching a real
    //redstone torch, which is lit the instant it's placed with nothing powering it, not just eventually.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        state = state.setValue(POWERED, shouldTurnOn(context.getLevel(), context.getClickedPos(), state));
        return state.setValue(LOCKED, isLocked(context.getLevel(), context.getClickedPos(), state));
    }

    //Only recompute LOCKED for Left/Right neighbor changes, matching InverterBlock's own perpendicular-axis-only
    //updateShape - generalized from "perpendicular axis" (only meaningful for a horizontal-only FACING) to an
    //exact Left/Right direction match, since Left/Right can now be on any axis depending on orientation.
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!level.isClientSide() && (facing == getLeft(state) || facing == getRight(state))) {
            return state.setValue(LOCKED, isLocked((LevelReader) level, currentPos, state));
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    //Fully replaces default neighborChanged - support-loss handling, then schedules the delayed POWERED flip
    //(DiodeBlock's own tick/getDelay/checkTickOnNeighbor machinery isn't available here, so this mirrors it
    //directly - always schedules when unlocked, matching RSLatchBlock/TimerBlock's own established "just always
    //call scheduleTick when needed" pattern in this mod rather than guarding against redundant scheduling).
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
        boolean locked = isLocked(level, pos, state);
        if (locked != state.getValue(LOCKED)) {
            level.setBlock(pos, state.setValue(LOCKED, locked), 2);
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

    //side == FACING (the input direction), not the output - confirmed against DiodeBlock's own default
    //getSignal convention (`FACING == side`), the same "backwards direction" redstone convention established
    //repeatedly this session.
    //See ScrewdriverSyncable's own comment for why this needs to exist at all - the normal tick-gated POWERED
    //update never runs when a rotation makes the block locked in the same instant its input direction changes.
    @Override
    public BlockState resyncAfterRotation(Level level, BlockPos pos, BlockState state) {
        state = state.setValue(POWERED, shouldTurnOn(level, pos, state));
        return state.setValue(LOCKED, isLocked(level, pos, state));
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
        builder.add(SUPPORT, FACING, POWERED, LOCKED);
    }
}
