package com.a_c_e.bettercircuits.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.Locale;
import java.util.Random;

//Single input, 3 outputs (LEFT/MIDDLE/RIGHT, same FACING.getCounterClockWise/getOpposite/getClockWise
//convention AndGateBlock's own LEFT/RIGHT uses - MIDDLE is the classic single-output direction every other
//gate in this mod uses). On a rising edge (INPUT_ON tracks this exactly like CapacitorBlock's own edge
//detector), rolls a uniform pick among the 3 outputs using java.util.Random (per the user's own explicit
//instruction, not Minecraft's own RandomSource) and asserts a signal on ONLY that one direction for as long as
//the input stays held - output duration matches input duration exactly, per the user's own spec ("if the input
//is 10 ticks, the output is 10 ticks"), not a fixed-length pulse. The center torch lights up for that same
//window (on for any non-NONE SELECTION, off for NONE) and the base texture shows which side was picked.
//
//No lock mechanic - unlike every other gate in this mod, the user never asked for one here.
//
//No scheduled-tick machinery needed at all - unlike a fixed-duration pulse (this block's own earlier design),
//"output tracks input exactly" only ever needs to react to the SAME 2 events DiodeBlock's own neighborChanged
//already reacts to (input rising, input falling), so it's pure event-driven blockstate, no ticker/timer of any kind.
public class RandomizerBlock extends DiodeBlock {
    public static final MapCodec<RandomizerBlock> CODEC = simpleCodec(RandomizerBlock::new);
    public static final EnumProperty<Selection> SELECTION = EnumProperty.create("selection", Selection.class);
    public static final BooleanProperty INPUT_ON = BooleanProperty.create("input_on");
    private static final Selection[] OUTPUTS = {Selection.LEFT, Selection.MIDDLE, Selection.RIGHT};
    private static final Random RANDOM = new Random();

    public enum Selection implements StringRepresentable {
        NONE, LEFT, MIDDLE, RIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public RandomizerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(SELECTION, Selection.NONE).setValue(INPUT_ON, false));
    }

    @Override
    public MapCodec<RandomizerBlock> codec() {
        return CODEC;
    }

    //Never actually invoked - DiodeBlock's own shouldTurnOn/checkTickOnNeighbor path is bypassed entirely here
    //(neighborChanged below never calls it) - but DiodeBlock declares this abstract, so every subclass needs a
    //value; matches every other gate's own placeholder.
    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    private Direction outputDirection(BlockState state, Selection selection) {
        Direction facing = state.getValue(FACING);
        return switch (selection) {
            case LEFT -> facing.getCounterClockWise();
            case MIDDLE -> facing.getOpposite();
            case RIGHT -> facing.getClockWise();
            case NONE -> null;
        };
    }

    //Fully replaces DiodeBlock's own neighborChanged (support-loss handling copied from its default). A rising
    //edge picks a fresh random direction and starts outputting on it; a falling edge stops outputting on
    //whichever direction was active. A held-high or held-low input (no edge) does nothing further.
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

        boolean inputOn = getInputSignal(level, pos, state) > 0;
        boolean wasInputOn = state.getValue(INPUT_ON);
        if (inputOn == wasInputOn) {
            return;
        }

        if (inputOn) {
            Selection chosen = OUTPUTS[RANDOM.nextInt(OUTPUTS.length)];
            BlockState newState = state.setValue(INPUT_ON, true).setValue(SELECTION, chosen);
            level.setBlock(pos, newState, 2);
            notifyOutput(level, pos, outputDirection(newState, chosen));
        } else {
            Selection previous = state.getValue(SELECTION);
            BlockState newState = state.setValue(INPUT_ON, false).setValue(SELECTION, Selection.NONE);
            level.setBlock(pos, newState, 2);
            if (previous != Selection.NONE) {
                notifyOutput(level, pos, outputDirection(state, previous));
            }
        }
    }

    //Same neighbor-notification pattern RSLatchBlock/TimerBlockEntity/CapacitorBlock already use in this mod -
    //flag 2 alone (used by both setBlock calls above) only syncs to clients, so the actual neighbor
    //notification needs this explicit step whenever the asserted output direction changes.
    private void notifyOutput(Level level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        if (net.neoforged.neoforge.event.EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), java.util.EnumSet.of(direction), false).isCanceled()) {
            return;
        }
        level.neighborChanged(neighborPos, this, pos);
        level.updateNeighborsAtExceptFromFacing(neighborPos, this, direction.getOpposite());
    }

    //side == outputDirection.getOpposite() - the general form of the "backwards direction" redstone convention
    //(side is the direction FROM the querying neighbor TO me) confirmed against DiodeBlock's own default
    //getSignal for the single-output case (side==FACING, which reduces to exactly this formula when
    //outputDirection==FACING.getOpposite()) - same convention that caused CapacitorBlock's own getSignal bug
    //earlier this session, generalized here to 3 possible output directions instead of 1. Confirmed correct via
    //a scripted, fully deterministic server-side test (edge detection, the random pick, and the per-direction
    //output signal all verified to fire correctly the instant power arrives).
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        Selection selection = state.getValue(SELECTION);
        if (selection == Selection.NONE) {
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
        builder.add(FACING, SELECTION, INPUT_ON);
    }
}
