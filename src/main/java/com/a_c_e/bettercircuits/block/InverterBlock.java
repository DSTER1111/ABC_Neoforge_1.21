package com.a_c_e.bettercircuits.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

//Real simple - same placement/shape/single-front-input footprint as vanilla's own repeater (all inherited
//unchanged from DiodeBlock: getShape, getStateForPlacement's FACING part, canSurvive, getInputSignal, getSignal/
//getDirectSignal), just with shouldTurnOn flipped: outputs high exactly when NOT receiving an input signal, and
//vice versa. Matches a real redstone torch's own inversion behavior (unpowered = lit/active, powered =
//unlit/inactive) - see the inverter_on/inverter_off textures in BCBlockStateProvider for the matching lit/unlit
//torch rendering, keyed directly off POWERED (no LEFT_POWERED/RIGHT_POWERED the way AndGateBlock/XorGateBlock
//need - there's only ever the one input to show).
//
//LOCKED mirrors RepeaterBlock's own side-lock mechanic exactly: a diode (another repeater/comparator/gate)
//facing directly into one of my SIDES (perpendicular to my own FACING), while itself outputting a STRONG signal
//(getControlInputSignal's diodesOnly branch uses getDirectSignal, not weak getSignal), freezes my POWERED state
//until that signal goes away. DiodeBlock's own tick/checkTickOnNeighbor (both inherited unchanged) already skip
//updating POWERED whenever isLocked() is true - the only work here is keeping LOCKED itself correctly up to
//date, via the exact same isLocked/sideInputDiodesOnly/getStateForPlacement/updateShape overrides
//RepeaterBlock uses.
public class InverterBlock extends DiodeBlock implements ScrewdriverSyncable {
    public static final MapCodec<InverterBlock> CODEC = simpleCodec(InverterBlock::new);
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;

    public InverterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(LOCKED, false));
    }

    @Override
    public MapCodec<InverterBlock> codec() {
        return CODEC;
    }

    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        return getInputSignal(level, pos, state) == 0;
    }

    @Override
    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return this.getAlternateSignal(level, pos, state) > 0;
    }

    //Restricts what can trigger a lock to other diodes specifically (matching RepeaterBlock, not
    //ComparatorBlock, which also allows plain signal sources) - getControlInputSignal's diodesOnly branch is
    //also what makes the check use STRONG signal rather than weak power.
    @Override
    protected boolean sideInputDiodesOnly() {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(LOCKED, isLocked(context.getLevel(), context.getClickedPos(), state));
    }

    //Only recompute LOCKED for side (perpendicular-axis) neighbor changes, matching RepeaterBlock exactly -
    //front/back changes fall through to the normal shape-update path untouched.
    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        if (!level.isClientSide() && facing.getAxis() != state.getValue(FACING).getAxis()) {
            return state.setValue(LOCKED, isLocked(level, currentPos, state));
        }
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    //See ScrewdriverSyncable's own comment for why this needs to exist at all - the normal tick-gated POWERED
    //update never runs when a rotation makes the block locked in the same instant its input direction changes.
    @Override
    public BlockState resyncAfterRotation(Level level, BlockPos pos, BlockState state) {
        state = state.setValue(POWERED, shouldTurnOn(level, pos, state));
        return state.setValue(LOCKED, isLocked(level, pos, state));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LOCKED);
    }
}
