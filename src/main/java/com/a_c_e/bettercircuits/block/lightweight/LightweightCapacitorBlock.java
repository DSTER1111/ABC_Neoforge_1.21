package com.a_c_e.bettercircuits.block.lightweight;

import com.a_c_e.bettercircuits.block.CapacitorBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

//Any-surface counterpart to CapacitorBlock - identical edge-triggered pulse-counting logic (see CapacitorBlock's
//own class comment for the full STRENGTH/INPUT_ON/LOCKED spec), generalized from FACING's clockwise/
//counter-clockwise sides to LightweightDiodeBlock's own Left/Right. STRENGTH/INPUT_ON reuse CapacitorBlock's
//own property instances directly rather than declaring fresh ones, same as every lightweight gate already
//reuses BlockStateProperties.POWERED/LOCKED. No block entity needed here either - counting is purely
//event-driven off neighborChanged (a pulse IS a redstone update), matching the heavy block exactly.
public class LightweightCapacitorBlock extends LightweightDiodeBlock {
    public static final MapCodec<LightweightCapacitorBlock> CODEC = simpleCodec(LightweightCapacitorBlock::new);
    public static final IntegerProperty STRENGTH = CapacitorBlock.STRENGTH;
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    public static final BooleanProperty INPUT_ON = CapacitorBlock.INPUT_ON;

    public LightweightCapacitorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SUPPORT, Direction.DOWN).setValue(FACING, Direction.NORTH)
                .setValue(STRENGTH, 0).setValue(LOCKED, false).setValue(INPUT_ON, false));
    }

    @Override
    public MapCodec<LightweightCapacitorBlock> codec() {
        return CODEC;
    }

    private boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return getControlInputSignal(level, pos, state, getLeft(state)) > 0 || getControlInputSignal(level, pos, state, getRight(state)) > 0;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(LOCKED, isLocked(context.getLevel(), context.getClickedPos(), state));
    }

    //Bug fix: this and neighborChanged below both recompute LOCKED for a Left/Right change (this one catches
    //perpendicular-neighbor changes that affect isLocked without necessarily firing a proper redstone-type
    //neighborChanged event, e.g. a block simply being placed/broken there). Originally this only touched
    //LOCKED, leaving STRENGTH alone - but since Minecraft can call this BEFORE neighborChanged for the exact
    //same underlying change, updateShape's own write would already overwrite LOCKED to its new (unlocked)
    //value before neighborChanged ever read it as "wasLocked" - so neighborChanged's own wasLocked&&!locked
    //reset check always saw wasLocked=false too and never fired. Applying the SAME reset-on-unlock logic here
    //(and notifying downstream, since STRENGTH is this block's own output value, unlike LOCKED) closes that gap
    //regardless of which handler happens to run first.
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!level.isClientSide() && (facing == getLeft(state) || facing == getRight(state))) {
            boolean locked = isLocked((LevelReader) level, currentPos, state);
            boolean wasLocked = state.getValue(LOCKED);
            int strength = wasLocked && !locked ? 0 : state.getValue(STRENGTH);
            BlockState newState = state.setValue(LOCKED, locked).setValue(STRENGTH, strength);
            if (strength != state.getValue(STRENGTH)) {
                notifyOutput((Level) level, currentPos, getOutput(newState));
            }
            return newState;
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

        boolean locked = isLocked(level, pos, state);
        boolean wasLocked = state.getValue(LOCKED);
        boolean inputOn = readSignal(level, pos, state, state.getValue(FACING)) > 0;
        boolean wasInputOn = state.getValue(INPUT_ON);
        int strength = state.getValue(STRENGTH);

        if (wasLocked && !locked) {
            strength = 0;
        } else if (!locked && inputOn && !wasInputOn) {
            strength = strength >= 15 ? 0 : strength + 1;
        }

        BlockState newState = state.setValue(LOCKED, locked).setValue(INPUT_ON, inputOn).setValue(STRENGTH, strength);
        if (newState.equals(state)) {
            return;
        }
        level.setBlock(pos, newState, 2);
        if (newState.getValue(STRENGTH) != state.getValue(STRENGTH)) {
            notifyOutput(level, pos, getOutput(newState));
        }
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(FACING) == side ? state.getValue(STRENGTH) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SUPPORT, FACING, STRENGTH, LOCKED, INPUT_ON);
    }
}
