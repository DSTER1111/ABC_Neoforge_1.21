package com.a_c_e.bettercircuits.block;

import com.a_c_e.bettercircuits.block.entity.BCBlockEntityTypes;
import com.a_c_e.bettercircuits.block.entity.TimerBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
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

//Single front input/output, same footprint as InverterBlock (all inherited unchanged from DiodeBlock: getShape,
//getStateForPlacement's FACING part, canSurvive, getInputSignal, getSignal/getDirectSignal - POWERED here means
//"currently emitting the 1-tick pulse", read the same way an inverter/repeater's own POWERED drives its output).
//
//Unlike every other gate in this mod, actually counting ticks needs to happen every single game tick regardless
//of whether any neighbor changed - DiodeBlock's own tick/getDelay/checkTickOnNeighbor machinery only runs in
//response to a redstone update, so it's bypassed entirely here (neighborChanged below keeps only the inherited
//support-loss handling). TimerBlockEntity's own ticker (see its class comment) is the sole driver of counting/
//pulsing/resetting; this class only supplies the plumbing that ticker needs (readInputSignal below) plus the
//lock mechanic and GUI-opening.
//
//LOCKED mirrors InverterBlock's own side-lock mechanic exactly (confirmed via this session's own AND-gate lock
//bug fix: FACING is the back/input direction, so the lock-trigger sides are FACING's clockwise/counter-clockwise
//neighbors, i.e. getAlternateSignal's own inherited check) - a diode facing into either side, outputting a
//STRONG signal, freezes counting AND suppresses the reset-to-0-on-no-input behavior (see TimerBlockEntity's own
//serverTick: locked returns immediately, before either the pulse-countdown or the reset/increment branches).
public class TimerBlock extends DiodeBlock implements EntityBlock {
    public static final MapCodec<TimerBlock> CODEC = simpleCodec(TimerBlock::new);
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    //True whenever the input is currently held (actively counting toward the pulse) - drives the "on" top
    //texture, distinct from POWERED (the 1-tick pulse itself, "output" texture) and from neither (the "default"
    //idle texture). Purely a rendering/display flag - TimerBlockEntity#serverTick is the only thing that sets it.
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public TimerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(LOCKED, false).setValue(RUNNING, false));
    }

    @Override
    public MapCodec<TimerBlock> codec() {
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

    //Restricts lock-triggering to other diodes specifically, using STRONG signal - matches Inverter/AND/XOR gate
    //precedent in this mod exactly (confirmed with the user rather than assumed, since this is a deliberate,
    //non-obvious restriction rather than an obvious default).
    @Override
    protected boolean sideInputDiodesOnly() {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(LOCKED, isLocked(context.getLevel(), context.getClickedPos(), state));
    }

    //Only recompute LOCKED for side (perpendicular-axis) neighbor changes, matching Inverter/RepeaterBlock exactly.
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!level.isClientSide() && facing.getAxis() != state.getValue(FACING).getAxis()) {
            return state.setValue(LOCKED, isLocked(level, currentPos, state));
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    //Fully replaces DiodeBlock's own neighborChanged (support-loss handling copied from its default) rather than
    //calling super - see this class's own comment on why counting can't live here.
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

    //Exposes DiodeBlock's own (protected) input-reading logic to TimerBlockEntity's ticker, which lives in a
    //different package and isn't itself a DiodeBlock subclass.
    public int readInputSignal(Level level, BlockPos pos, BlockState state) {
        return getInputSignal(level, pos, state);
    }

    //Lets a comparator read the timer's own progress (not its FACING.getOpposite() output signal, which stays
    //POWERED-only per DiodeBlock's default getSignal/getDirectSignal) - a comparator measures whatever's BEHIND
    //it via these two hooks, same mechanism furnaces/hoppers/etc. use for their own fullness readouts.
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    //15 * progress/target, floored - 0 while idle/locked-at-zero, 15 only on the exact tick progress reaches
    //target (the same instant the pulse itself fires). While locked, this simply keeps reading whatever
    //progress was frozen at, same as everything else about a locked timer.
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TimerBlockEntity timer)) {
            return 0;
        }
        int target = Math.max(1, timer.getTarget());
        return (15 * timer.getProgress()) / target;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TimerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != BCBlockEntityTypes.TIMER.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> TimerBlockEntity.serverTick(lvl, pos, st, (TimerBlockEntity) be);
    }

    @Override
    //While LOCKED, the GUI simply doesn't open at all - matches the same restriction now applied to
    //RedstoneThresholdBlock (see its own comment), rather than letting the player peek at/edit a locked timer's
    //target while its counting stays frozen regardless.
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(LOCKED)) {
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TimerBlockEntity timer) {
            player.openMenu(timer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LOCKED, RUNNING);
    }
}
