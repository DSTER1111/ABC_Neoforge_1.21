package com.a_c_e.bettercircuits.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

//Single front input/output like Inverter, but with memory: STRENGTH (0-15) counts how many separate pulses
//it's received, output = STRENGTH directly (unlike every other gate here, which is a pure function of the
//CURRENT input - this one remembers). Counting is edge-triggered, not level-triggered: INPUT_ON tracks whether
//the input was already high as of the last check, so a signal HELD high only ever counts once (matches the
//user's own "stays at 1 even if the input goes away" spec) - the next count only happens on a fresh off->on
//transition. Reaching 15 and receiving one more pulse wraps back to 0, not clamps.
//
//LOCKED mirrors Inverter/AND/XOR/Timer's own side-lock mechanic exactly (strong signal from a diode on either
//perpendicular side, via DiodeBlock's inherited getAlternateSignal/sideInputDiodesOnly) - while locked, pulses
//are ignored entirely (STRENGTH neither increments nor resets). Going from locked back to unlocked is itself a
//trigger, though - per the user's own spec it resets STRENGTH to 0 the instant the lock signal goes away, unlike
//every other lockable gate in this mod (which just resume whatever they were already doing).
//
//No block entity needed, unlike Timer - counting only needs to react to actual neighbor-changed events
//(a pulse is exactly a redstone update), not tick continuously.
public class CapacitorBlock extends DiodeBlock {
    public static final MapCodec<CapacitorBlock> CODEC = simpleCodec(CapacitorBlock::new);
    public static final IntegerProperty STRENGTH = IntegerProperty.create("strength", 0, 15);
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    public static final BooleanProperty INPUT_ON = BooleanProperty.create("input_on");

    public CapacitorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(STRENGTH, 0).setValue(LOCKED, false).setValue(INPUT_ON, false));
    }

    @Override
    public MapCodec<CapacitorBlock> codec() {
        return CODEC;
    }

    //Never actually invoked - neighborChanged below never calls checkTickOnNeighbor - but DiodeBlock declares
    //this abstract, so every subclass needs a value; matches every other gate's own placeholder.
    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return this.getAlternateSignal(level, pos, state) > 0;
    }

    //Restricts lock-triggering to other diodes specifically, using STRONG signal - matches Inverter/AND/XOR/
    //Timer precedent in this mod exactly.
    @Override
    protected boolean sideInputDiodesOnly() {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(LOCKED, isLocked(context.getLevel(), context.getClickedPos(), state));
    }

    //Only recompute LOCKED for side (perpendicular-axis) neighbor changes, matching Inverter/Timer exactly -
    //purely a cached/robustness copy (neighborChanged below recomputes isLocked live on every call anyway, so
    //this doesn't gate the actual reset-on-unlock logic), catching perpendicular-neighbor changes that affect
    //isLocked without necessarily firing a redstone-type neighborChanged event.
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!level.isClientSide() && facing.getAxis() != state.getValue(FACING).getAxis()) {
            return state.setValue(LOCKED, isLocked(level, currentPos, state));
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    //Fully replaces DiodeBlock's own neighborChanged (support-loss handling copied from its default) - counting
    //is entirely event-driven from here rather than DiodeBlock's own tick/getDelay machinery, similar to how
    //RSLatchBlock/TimerBlock in this mod bypass it too.
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
        boolean inputOn = getInputSignal(level, pos, state) > 0;
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
            notifyOutput(level, pos, newState);
        }
    }

    //Same single-output neighbor-notification pattern RSLatchBlock/TimerBlockEntity already use in this mod -
    //flag 2 alone (used by setBlock above) only syncs to clients, so the actual neighbor notification needs
    //this explicit step whenever STRENGTH (the block's own output signal) changes.
    private void notifyOutput(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING).getOpposite();
        BlockPos neighborPos = pos.relative(direction);
        if (net.neoforged.neoforge.event.EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), java.util.EnumSet.of(direction), false).isCanceled()) {
            return;
        }
        level.neighborChanged(neighborPos, this, pos);
        level.updateNeighborsAtExceptFromFacing(neighborPos, this, direction.getOpposite());
    }

    //side == FACING, not FACING.getOpposite() - confirmed against DiodeBlock's own default getSignal
    //(`blockState.getValue(FACING) == side`), the same "backwards direction" redstone convention that caused
    //the AND gate's own lock-direction bug earlier this session. Output still physically flows toward
    //FACING.getOpposite() - side here is "the direction FROM the querying neighbor TO me", not the reverse.
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
        builder.add(FACING, STRENGTH, LOCKED, INPUT_ON);
    }
}
