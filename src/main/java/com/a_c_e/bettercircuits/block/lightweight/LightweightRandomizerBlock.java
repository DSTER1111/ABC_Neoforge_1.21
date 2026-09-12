package com.a_c_e.bettercircuits.block.lightweight;

import com.a_c_e.bettercircuits.block.RandomizerBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.Random;

//Any-surface counterpart to RandomizerBlock - identical single-input/3-output edge-triggered random-pick logic
//(see RandomizerBlock's own class comment for the full spec: output duration matches input duration exactly,
//java.util.Random per the user's own explicit instruction, no lock mechanic). LEFT/RIGHT generalize
//FACING.getCounterClockWise/getClockWise to LightweightDiodeBlock's own Left/Right (already confirmed
//equivalent for the floor case - see LightweightDiodeBlock's own getRight comment); MIDDLE generalizes
//FACING.getOpposite() to the base class's own getOutput(). SELECTION/INPUT_ON reuse RandomizerBlock's own
//property instances directly, same as every lightweight gate already reuses shared property objects.
public class LightweightRandomizerBlock extends LightweightDiodeBlock {
    public static final MapCodec<LightweightRandomizerBlock> CODEC = simpleCodec(LightweightRandomizerBlock::new);
    public static final EnumProperty<RandomizerBlock.Selection> SELECTION = RandomizerBlock.SELECTION;
    public static final BooleanProperty INPUT_ON = RandomizerBlock.INPUT_ON;
    private static final RandomizerBlock.Selection[] OUTPUTS = {RandomizerBlock.Selection.LEFT, RandomizerBlock.Selection.MIDDLE, RandomizerBlock.Selection.RIGHT};
    private static final Random RANDOM = new Random();

    public LightweightRandomizerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SUPPORT, Direction.DOWN).setValue(FACING, Direction.NORTH)
                .setValue(SELECTION, RandomizerBlock.Selection.NONE).setValue(INPUT_ON, false));
    }

    @Override
    public MapCodec<LightweightRandomizerBlock> codec() {
        return CODEC;
    }

    private Direction outputDirection(BlockState state, RandomizerBlock.Selection selection) {
        return switch (selection) {
            case LEFT -> getLeft(state);
            case MIDDLE -> getOutput(state);
            case RIGHT -> getRight(state);
            case NONE -> null;
        };
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

        boolean inputOn = readSignal(level, pos, state, state.getValue(FACING)) > 0;
        boolean wasInputOn = state.getValue(INPUT_ON);
        if (inputOn == wasInputOn) {
            return;
        }

        if (inputOn) {
            RandomizerBlock.Selection chosen = OUTPUTS[RANDOM.nextInt(OUTPUTS.length)];
            BlockState newState = state.setValue(INPUT_ON, true).setValue(SELECTION, chosen);
            level.setBlock(pos, newState, 2);
            notifyOutput(level, pos, outputDirection(newState, chosen));
        } else {
            RandomizerBlock.Selection previous = state.getValue(SELECTION);
            BlockState newState = state.setValue(INPUT_ON, false).setValue(SELECTION, RandomizerBlock.Selection.NONE);
            level.setBlock(pos, newState, 2);
            if (previous != RandomizerBlock.Selection.NONE) {
                notifyOutput(level, pos, outputDirection(state, previous));
            }
        }
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        RandomizerBlock.Selection selection = state.getValue(SELECTION);
        if (selection == RandomizerBlock.Selection.NONE) {
            return 0;
        }
        Direction outputDir = outputDirection(state, selection);
        return side == outputDir.getOpposite() ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SUPPORT, FACING, SELECTION, INPUT_ON);
    }
}
