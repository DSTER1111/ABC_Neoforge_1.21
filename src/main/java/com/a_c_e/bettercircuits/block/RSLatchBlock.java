package com.a_c_e.bettercircuits.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

//A bistable SR latch, unlike every other DiodeBlock subclass in this mod (AND/XOR/Inverter), which are all
//combinational - their output is a pure function of the CURRENT input, recomputed fresh every time. This one has
//actual memory: 2 states, each with its own active input/output pair, and it only moves between them when the
//currently-active input receives a signal - the block otherwise doesn't care what either input is doing.
//
//FACING is (as with every other gate in this mod - see AndGateBlock's own comment) the back/input-reading
//direction DiodeBlock's own getInputSignal uses, so OUTPUT actually flows FACING.getOpposite(). This block
//doesn't use that front/back axis for a single input the way a plain repeater does, though - it uses BOTH ends
//of BOTH axes: FACING and FACING.getOpposite() are the two OUTPUTS, and FACING's clockwise/counter-clockwise
//sides (the same axis AndGateBlock/XorGateBlock use for their 2 inputs) are the two INPUTS.
//
//FLIPPED tracks which of the 2 states is active. !FLIPPED (state 1, the default): input = FACING's clockwise
//side, output = FACING.getOpposite(). FLIPPED (state 2): input = FACING's counter-clockwise side, output =
//FACING. Flipping swaps BOTH pairs simultaneously - exactly what a 180-degree rotation around the vertical axis
//does to a symmetric layout, which is why one baked model works for both states (see rsLatch() in
//BCBlockStateProvider): the same geometry, rotated an extra 180 degrees when FLIPPED, naturally moves whichever
//torches were lit onto the opposite pair of sides.
//
//Flipping immediately changes WHICH input is being watched, so the very next check (whether triggered by the
//same neighborChanged call chain or a later one) reads the OTHER input, not the one that just fired - a signal
//held high on the side that just triggered a flip is simply never looked at again until (if ever) it becomes
//the active input once more. That alone isn't enough to stay cheap under a PERSISTENT signal on both inputs at
//once, though: every neighbor update on the currently-active side would flip it again immediately, and since
//each flip itself fires neighbor updates (both outputs changing), a permanently-high pair of inputs turns into
//a same-tick flip storm. COOLDOWN guards against that: right after a flip, it's set and a tick is scheduled
//COOLDOWN_TICKS later to clear it (DiodeBlock's own scheduled-tick plumbing, just without the POWERED/getDelay
//machinery it's normally paired with) - while COOLDOWN is set, neighborChanged ignores triggers entirely, same
//idea as a repeater's own tick delay but applied to "how often this can flip" rather than "how long output
//takes to update."
public class RSLatchBlock extends DiodeBlock {
    public static final MapCodec<RSLatchBlock> CODEC = simpleCodec(RSLatchBlock::new);
    public static final BooleanProperty FLIPPED = BooleanProperty.create("flipped");
    public static final BooleanProperty COOLDOWN = BooleanProperty.create("cooldown");
    private static final int COOLDOWN_TICKS = 20;

    public RSLatchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FLIPPED, false).setValue(COOLDOWN, false));
    }

    @Override
    public MapCodec<RSLatchBlock> codec() {
        return CODEC;
    }

    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    private Direction activeInput(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(FLIPPED) ? facing.getCounterClockWise() : facing.getClockWise();
    }

    //Same direct-signal read AndGateBlock/XorGateBlock use for their own inputs (see their own comment on why -
    //shouldCheckWeakPower means a plain block merely soft-powered by an adjacent lever/wire still counts).
    private int getSideInputSignal(SignalGetter level, BlockPos pos, Direction side) {
        BlockPos neighborPos = pos.relative(side);
        int signal = level.getSignal(neighborPos, side);
        if (signal >= 15) {
            return signal;
        }
        BlockState neighborState = level.getBlockState(neighborPos);
        return Math.max(signal, neighborState.is(Blocks.REDSTONE_WIRE) ? neighborState.getValue(RedStoneWireBlock.POWER) : 0);
    }

    //Full strength on whichever side is the CURRENT active output, nothing on every other side - no delay, no
    //POWERED involved, this is a pure function of FACING+FLIPPED alone. side==FACING matches DiodeBlock's own
    //default getSignal check exactly for the !FLIPPED case (output = FACING.getOpposite()), confirming the
    //direction convention lines up with the rest of DiodeBlock's own signal-direction handling.
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

    //Fully replaces DiodeBlock's own neighborChanged (support-loss handling copied from its default) rather than
    //calling super - this block doesn't use checkTickOnNeighbor/tick/POWERED at all, so there's nothing in the
    //inherited version worth keeping beyond the support check.
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

    //Shared by neighborChanged and by tick() (once COOLDOWN expires) so a still-held input picks back up right
    //where it left off: rather than going silent until some unrelated neighbor event happens to fire again, a
    //persistently-signaled input keeps flipping at the capped rate of once per COOLDOWN_TICKS instead of the
    //uncapped flip-storm this guards against.
    private void tryFlip(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(COOLDOWN) || getSideInputSignal(level, pos, activeInput(state)) <= 0) {
            return;
        }
        Direction facing = state.getValue(FACING);
        level.setBlock(pos, state.setValue(FLIPPED, !state.getValue(FLIPPED)).setValue(COOLDOWN, true), 2);
        level.scheduleTick(pos, this, COOLDOWN_TICKS);
        //Flipping swaps BOTH outputs at once (one turns off, the other turns on), so unlike a plain
        //DiodeBlock's single-sided updateNeighborsInFront, both FACING and FACING.getOpposite() need
        //telling - same per-side logic vanilla uses, just applied to both output sides.
        updateOutputNeighbor(level, pos, facing);
        updateOutputNeighbor(level, pos, facing.getOpposite());
    }

    //Clears COOLDOWN once it expires, then immediately re-checks: COOLDOWN itself doesn't affect either
    //output's signal (see getSignal above, which only ever looks at FACING+FLIPPED), so clearing it needs no
    //neighbor notification on its own, but a held-high input needs this recheck to keep the latch moving at
    //its capped rate instead of getting stuck waiting for a fresh neighborChanged that may never come.
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(COOLDOWN)) {
            return;
        }
        BlockState cleared = state.setValue(COOLDOWN, false);
        level.setBlock(pos, cleared, 2);
        tryFlip(level, pos, cleared);
    }

    private void updateOutputNeighbor(Level level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        if (net.neoforged.neoforge.event.EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), java.util.EnumSet.of(direction), false).isCanceled()) {
            return;
        }
        level.neighborChanged(neighborPos, this, pos);
        level.updateNeighborsAtExceptFromFacing(neighborPos, this, direction.getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FLIPPED, COOLDOWN);
    }
}
