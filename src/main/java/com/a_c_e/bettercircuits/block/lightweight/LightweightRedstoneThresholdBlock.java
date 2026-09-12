package com.a_c_e.bettercircuits.block.lightweight;

import com.a_c_e.bettercircuits.block.entity.LightweightRedstoneThresholdBlockEntity;
import com.a_c_e.bettercircuits.block.entity.BCBlockEntityTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

//Any-surface counterpart to RedstoneThresholdBlock - now also mirrors its LOCKED mechanic (see that class's own
//comment), generalized the same way LightweightInverterBlock/LightweightTimerBlock's own Left/Right lock already
//is. DiodeBlock's own tick/getDelay/shouldTurnOn machinery isn't available on LightweightDiodeBlock (see its own
//class comment - it extends plain Block, not DiodeBlock, since a 6-direction SUPPORT+FACING orientation doesn't
//fit DiodeBlock's horizontal-only FACING), so the delayed POWERED flip is reimplemented directly here via
//scheduleTick+tick(), matching LightweightInverterBlock's own approach exactly. The block entity (mode/threshold
//storage, GUI) is otherwise identical in spirit to the heavy version's own - see
//LightweightRedstoneThresholdBlockEntity.
public class LightweightRedstoneThresholdBlock extends LightweightDiodeBlock implements EntityBlock {
    public static final MapCodec<LightweightRedstoneThresholdBlock> CODEC = simpleCodec(LightweightRedstoneThresholdBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    //Matches ComparatorBlock's own fixed delay, same as heavy RedstoneThresholdBlock's own getDelay - there's no
    //configurable delay property for this block.
    private static final int DELAY_TICKS = 2;

    public LightweightRedstoneThresholdBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SUPPORT, Direction.DOWN).setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false).setValue(LOCKED, false));
    }

    @Override
    public MapCodec<LightweightRedstoneThresholdBlock> codec() {
        return CODEC;
    }

    //Exposes the base class's own touchesFace-gated input read for LightweightRedstoneThresholdBlockEntity's own
    //use, and for this block's own getSignal analog passthrough below.
    public int readInputSignal(Level level, BlockPos pos, BlockState state) {
        return readSignal(level, pos, state, state.getValue(FACING));
    }

    private boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof LightweightRedstoneThresholdBlockEntity threshold)) {
            return false;
        }
        return threshold.matches(readInputSignal(level, pos, state));
    }

    //POWERED is evaluated synchronously here (matching LightweightInverterBlock's own placement behavior) so a
    //freshly placed Threshold reflects its surroundings immediately rather than waiting for a later tick.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        state = state.setValue(POWERED, shouldTurnOn(context.getLevel(), context.getClickedPos(), state));
        return state.setValue(LOCKED, isLocked(context.getLevel(), context.getClickedPos(), state));
    }

    //Diode-only strong signal from Left or Right locks the block - matches LightweightInverterBlock/
    //LightweightTimerBlock's own isLocked exactly.
    private boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return getControlInputSignal(level, pos, state, getLeft(state)) > 0 || getControlInputSignal(level, pos, state, getRight(state)) > 0;
    }

    //Only recompute LOCKED for Left/Right neighbor changes, matching LightweightInverterBlock exactly.
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!level.isClientSide() && (facing == getLeft(state) || facing == getRight(state))) {
            return state.setValue(LOCKED, isLocked((LevelReader) level, currentPos, state));
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    //Support-loss handling, then recomputes LOCKED and only schedules a re-check while unlocked - matches
    //LightweightInverterBlock's own neighborChanged exactly (previously always scheduled unconditionally, back
    //when this block had no lock to gate it on).
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
        if (shouldBeOn != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, shouldBeOn), 2);
            notifyOutput(level, pos, getOutput(state));
        }
    }

    //Exposes the base class's own protected notifyOutput to LightweightRedstoneThresholdBlockEntity, which lives
    //in a different package - called after a GUI edit changes mode/threshold, since that isn't itself a
    //"neighbor changed" event this block's own scheduling would ever notice on its own. Reschedules a tick
    //rather than flipping POWERED directly, so the exact same shouldTurnOn/notifyOutput path runs either way.
    //A locked threshold never reaches the GUI in the first place (see useWithoutItem below), so there's nothing
    //to gate here - this is only ever called from an edit that couldn't have happened while locked.
    public void recheckOutput(Level level, BlockPos pos, BlockState state) {
        level.scheduleTick(pos, this, DELAY_TICKS);
    }

    //Analog passthrough, matching RedstoneThresholdBlock's own getOutputSignal override: while active, the
    //output matches whatever signal is currently coming in, not a fixed 15. While LOCKED, returns the frozen
    //snapshot (LightweightRedstoneThresholdBlockEntity#lastOutput) instead of re-reading the live input - see
    //RedstoneThresholdBlock#getOutputSignal's own comment for why POWERED alone freezing isn't enough for an
    //analog passthrough output.
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (!state.getValue(POWERED) || side != state.getValue(FACING)) {
            return 0;
        }
        if (!(level.getBlockEntity(pos) instanceof LightweightRedstoneThresholdBlockEntity threshold)) {
            return level instanceof Level realLevel ? readInputSignal(realLevel, pos, state) : 15;
        }
        if (state.getValue(LOCKED)) {
            return threshold.getLastOutput();
        }
        if (!(level instanceof Level realLevel)) {
            return 15;
        }
        int signal = readInputSignal(realLevel, pos, state);
        threshold.setLastOutput(signal);
        return signal;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LightweightRedstoneThresholdBlockEntity(pos, state);
    }

    //No ticker needed - the delayed POWERED flip runs off scheduleTick/tick() above, not a per-tick ticker; the
    //block entity here exists purely to store mode/threshold and back the GUI.
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    //While LOCKED, the GUI simply doesn't open at all - matches RedstoneThresholdBlock's own restriction exactly.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(LOCKED)) {
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LightweightRedstoneThresholdBlockEntity threshold) {
            player.openMenu(threshold);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SUPPORT, FACING, POWERED, LOCKED);
    }
}
