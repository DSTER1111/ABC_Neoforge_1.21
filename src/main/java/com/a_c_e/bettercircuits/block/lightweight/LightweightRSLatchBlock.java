package com.a_c_e.bettercircuits.block.lightweight;

import com.a_c_e.bettercircuits.block.RSLatchBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

//Any-surface counterpart to RSLatchBlock - same bistable SR latch logic (see RSLatchBlock's own class comment
//for the full FLIPPED/COOLDOWN mechanic), generalized from FACING's clockwise/counter-clockwise sides to
//LightweightDiodeBlock's own Left/Right. Unlike every other gate in this family, RS Latch has no locking
//mechanism at all - FLIPPED and COOLDOWN reuse RSLatchBlock's own property instances directly rather than
//declaring fresh ones, same as every lightweight gate already reuses BlockStateProperties.POWERED/LOCKED.
public class LightweightRSLatchBlock extends LightweightDiodeBlock {
    public static final MapCodec<LightweightRSLatchBlock> CODEC = simpleCodec(LightweightRSLatchBlock::new);
    public static final BooleanProperty FLIPPED = RSLatchBlock.FLIPPED;
    public static final BooleanProperty COOLDOWN = RSLatchBlock.COOLDOWN;
    private static final int COOLDOWN_TICKS = 20;

    public LightweightRSLatchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SUPPORT, Direction.DOWN).setValue(FACING, Direction.NORTH)
                .setValue(FLIPPED, false).setValue(COOLDOWN, false));
    }

    @Override
    public MapCodec<LightweightRSLatchBlock> codec() {
        return CODEC;
    }

    //!FLIPPED (state 1): active input = Right (matches RSLatchBlock's own FACING.getClockWise(), generalized -
    //see LightweightDiodeBlock's own getRight comment confirming Right IS getClockWise() for the floor case).
    //FLIPPED (state 2): active input = Left.
    private Direction activeInput(BlockState state) {
        return state.getValue(FLIPPED) ? getLeft(state) : getRight(state);
    }

    //Same touchesFace-gated read every other lightweight gate's input uses (see LightweightDiodeBlock.readSignal's
    //own comment) - RS Latch has the identical cross-blockface vulnerability a plain getSignal read would have.
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        Direction facing = state.getValue(FACING);
        Direction expected = state.getValue(FLIPPED) ? facing.getOpposite() : facing;
        return side == expected ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
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
        if (!level.isClientSide) {
            tryFlip(level, pos, state);
        }
    }

    private void tryFlip(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(COOLDOWN) || readSignal(level, pos, state, activeInput(state)) <= 0) {
            return;
        }
        Direction facing = state.getValue(FACING);
        level.setBlock(pos, state.setValue(FLIPPED, !state.getValue(FLIPPED)).setValue(COOLDOWN, true), 2);
        level.scheduleTick(pos, this, COOLDOWN_TICKS);
        notifyOutput(level, pos, facing);
        notifyOutput(level, pos, facing.getOpposite());
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(COOLDOWN)) {
            return;
        }
        BlockState cleared = state.setValue(COOLDOWN, false);
        level.setBlock(pos, cleared, 2);
        tryFlip(level, pos, cleared);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SUPPORT, FACING, FLIPPED, COOLDOWN);
    }
}
