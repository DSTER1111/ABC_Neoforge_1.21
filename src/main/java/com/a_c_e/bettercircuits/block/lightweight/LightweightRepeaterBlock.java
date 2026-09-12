package com.a_c_e.bettercircuits.block.lightweight;

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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

//Any-surface counterpart to vanilla's own RepeaterBlock - can't extend DiodeBlock (see LightweightDiodeBlock's
//own class comment), so delay-cycling/getDelay/shouldTurnOn/isLocked are reimplemented directly here. Lock
//mechanic matches LightweightInverterBlock's own exactly (Left/Right, diode-only strong signal) - vanilla
//RepeaterBlock and this mod's own InverterBlock use the identical convention (confirmed via InverterBlock's
//own class comment). shouldTurnOn is a plain (non-inverting) single-input read, unlike Inverter's inverted one.
public class LightweightRepeaterBlock extends LightweightDiodeBlock {
    public static final MapCodec<LightweightRepeaterBlock> CODEC = simpleCodec(LightweightRepeaterBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    public static final IntegerProperty DELAY = BlockStateProperties.DELAY;

    public LightweightRepeaterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SUPPORT, Direction.DOWN).setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false).setValue(LOCKED, false).setValue(DELAY, 1));
    }

    @Override
    public MapCodec<LightweightRepeaterBlock> codec() {
        return CODEC;
    }

    private int getDelayTicks(BlockState state) {
        return state.getValue(DELAY) * 2;
    }

    private boolean shouldTurnOn(LevelReader level, BlockPos pos, BlockState state) {
        return readSignal(level, pos, state, state.getValue(FACING)) > 0;
    }

    private boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return getControlInputSignal(level, pos, state, getLeft(state)) > 0 || getControlInputSignal(level, pos, state, getRight(state)) > 0;
    }

    //Right-click (any/no item, matching vanilla's own useWithoutItem - RepeaterBlock has no useItemOn override
    //of its own, so Block's default useItemOn always passes through to this) cycles DELAY 1->2->3->4->1.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        }
        level.setBlock(pos, state.cycle(DELAY), 3);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        state = state.setValue(POWERED, shouldTurnOn(context.getLevel(), context.getClickedPos(), state));
        return state.setValue(LOCKED, isLocked(context.getLevel(), context.getClickedPos(), state));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!level.isClientSide() && (facing == getLeft(state) || facing == getRight(state))) {
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
        boolean locked = isLocked(level, pos, state);
        if (locked != state.getValue(LOCKED)) {
            level.setBlock(pos, state.setValue(LOCKED, locked), 2);
        }
        if (!locked) {
            level.scheduleTick(pos, this, getDelayTicks(state));
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
        builder.add(SUPPORT, FACING, POWERED, LOCKED, DELAY);
    }
}
