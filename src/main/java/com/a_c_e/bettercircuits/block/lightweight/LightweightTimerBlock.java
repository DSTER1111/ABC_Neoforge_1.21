package com.a_c_e.bettercircuits.block.lightweight;

import com.a_c_e.bettercircuits.block.TimerBlock;
import com.a_c_e.bettercircuits.block.entity.LightweightTimerBlockEntity;
import com.a_c_e.bettercircuits.block.entity.BCBlockEntityTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

//Any-surface counterpart to TimerBlock - sixth of the "lightweight" gate family. Counting/pulsing/resetting is
//entirely LightweightTimerBlockEntity's own responsibility (see its class comment, a direct port of
//TimerBlockEntity's own serverTick), same division of labor as heavy TimerBlock: this class only supplies the
//plumbing the ticker needs (readInputSignal) plus the Left/Right lock mechanic (generalized from FACING's
//clockwise/counter-clockwise sides, matching LightweightInverterBlock's own isLocked exactly - TimerBlock's own
//class comment confirms Timer uses the identical lock-side convention) and GUI-opening.
public class LightweightTimerBlock extends LightweightDiodeBlock implements EntityBlock {
    public static final MapCodec<LightweightTimerBlock> CODEC = simpleCodec(LightweightTimerBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    public static final BooleanProperty RUNNING = TimerBlock.RUNNING;

    public LightweightTimerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SUPPORT, Direction.DOWN).setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false).setValue(LOCKED, false).setValue(RUNNING, false));
    }

    @Override
    public MapCodec<LightweightTimerBlock> codec() {
        return CODEC;
    }

    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return getControlInputSignal(level, pos, state, getLeft(state)) > 0 || getControlInputSignal(level, pos, state, getRight(state)) > 0;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(LOCKED, isLocked(context.getLevel(), context.getClickedPos(), state));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!level.isClientSide() && (facing == getLeft(state) || facing == getRight(state))) {
            return state.setValue(LOCKED, isLocked((LevelReader) level, currentPos, state));
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    //Support-loss handling only - actual counting happens every tick via LightweightTimerBlockEntity's own
    //ticker regardless of neighbor events, same division of labor as heavy TimerBlock's own neighborChanged.
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!state.canSurvive(level, pos)) {
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            dropResources(state, level, pos, blockEntity);
            level.removeBlock(pos, false);
            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(pos.relative(direction), this);
            }
        }
    }

    //Exposes the base class's own touchesFace-gated input read to LightweightTimerBlockEntity's ticker, which
    //lives in a different package and isn't itself a LightweightDiodeBlock.
    public int readInputSignal(Level level, BlockPos pos, BlockState state) {
        return readSignal(level, pos, state, state.getValue(FACING));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof LightweightTimerBlockEntity timer)) {
            return 0;
        }
        int target = Math.max(1, timer.getTarget());
        return (15 * timer.getProgress()) / target;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWERED) && side == state.getValue(FACING) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LightweightTimerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != BCBlockEntityTypes.LIGHTWEIGHT_TIMER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> LightweightTimerBlockEntity.serverTick(lvl, pos, st, (LightweightTimerBlockEntity) be);
    }

    @Override
    //While LOCKED, the GUI simply doesn't open at all - matches TimerBlock's own restriction exactly.
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(LOCKED)) {
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof LightweightTimerBlockEntity timer) {
            player.openMenu(timer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SUPPORT, FACING, POWERED, LOCKED, RUNNING);
    }
}
