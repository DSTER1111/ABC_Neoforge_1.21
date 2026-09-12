package com.a_c_e.bettercircuits.block;

import com.a_c_e.bettercircuits.block.entity.BCBlockEntityTypes;
import com.a_c_e.bettercircuits.block.entity.RedstoneThresholdBlockEntity;
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
//getStateForPlacement's FACING part, canSurvive, getInputSignal, getSignal/getDirectSignal) - shouldTurnOn is
//overridden to defer the actual comparison to RedstoneThresholdBlockEntity#matches (mode + threshold value, both
//set via this block's own right-click GUI), and getOutputSignal to pass the input signal straight through while
//active rather than DiodeBlock's own default fixed 15. Everything else - scheduling the output flip, the
//resulting POWERED-driven torch texture - is vanilla DiodeBlock's own standard tick/checkTickOnNeighbor
//machinery, unlike TimerBlock (which needed a custom every-tick ticker since it counts continuously); a
//threshold is a pure, stateless-per-tick comparison, so the normal "only re-check on a redstone event" cadence
//is exactly right.
//
//LOCKED mirrors InverterBlock's own side-lock mechanic exactly - a diode facing into one of my SIDES, outputting
//a STRONG signal, freezes both POWERED (via DiodeBlock's own inherited checkTickOnNeighbor, which never
//schedules a recheck while isLocked() is true) and, additionally, the actual analog OUTPUT VALUE: unlike a
//boolean gate (whose "output" is just a fixed 15 once POWERED, so freezing POWERED alone freezes everything),
//this block's own getOutputSignal passthrough re-reads the LIVE input signal on every query - see that method's
//own comment for why it needs an explicit frozen snapshot (RedstoneThresholdBlockEntity#lastOutput) instead of
//just relying on POWERED staying put. Right-click also refuses to open the GUI while locked (see useWithoutItem).
public class RedstoneThresholdBlock extends DiodeBlock implements EntityBlock, ScrewdriverSyncable {
    public static final MapCodec<RedstoneThresholdBlock> CODEC = simpleCodec(RedstoneThresholdBlock::new);
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;

    public RedstoneThresholdBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(LOCKED, false));
    }

    @Override
    public MapCodec<RedstoneThresholdBlock> codec() {
        return CODEC;
    }

    //Matches ComparatorBlock's own fixed delay - there's no configurable delay property for this block.
    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof RedstoneThresholdBlockEntity threshold)) {
            return false;
        }
        return threshold.matches(getInputSignal(level, pos, state));
    }

    @Override
    public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
        return this.getAlternateSignal(level, pos, state) > 0;
    }

    //Restricts what can trigger a lock to other diodes specifically, matching InverterBlock/TimerBlock exactly.
    @Override
    protected boolean sideInputDiodesOnly() {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state.setValue(LOCKED, isLocked(context.getLevel(), context.getClickedPos(), state));
    }

    //Only recompute LOCKED for side (perpendicular-axis) neighbor changes, matching InverterBlock exactly.
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

    //Analog passthrough, per the user's own spec: while active, the output matches whatever signal is currently
    //coming in (e.g. receiving 6 emits 6), not DiodeBlock's own default fixed 15. getOutputSignal only ever gets
    //a BlockGetter, not the real Level getInputSignal needs - in practice this is always actually a Level during
    //normal gameplay redstone queries, hence the instanceof guard rather than a hard cast, with the inherited
    //fixed-15 default kept as a fallback for the (currently unencountered) case where it isn't.
    //
    //While LOCKED, this must NOT keep reading the live input - POWERED itself is already frozen by DiodeBlock's
    //own checkTickOnNeighbor (never reschedules while isLocked()), but getOutputSignal is invoked fresh on every
    //getSignal query regardless, so without this override the actual OUTPUT VALUE would keep drifting with
    //whatever the input currently is even though the block visually looks locked. Returns the last real value
    //computed before locking (RedstoneThresholdBlockEntity#lastOutput) instead, and keeps that value updated
    //(for whenever a future lock DOES engage) any time it's actually unlocked.
    @Override
    protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
        if (!(level.getBlockEntity(pos) instanceof RedstoneThresholdBlockEntity threshold)) {
            return level instanceof Level realLevel ? getInputSignal(realLevel, pos, state) : super.getOutputSignal(level, pos, state);
        }
        if (state.getValue(LOCKED)) {
            return threshold.getLastOutput();
        }
        if (!(level instanceof Level realLevel)) {
            return super.getOutputSignal(level, pos, state);
        }
        int signal = getInputSignal(realLevel, pos, state);
        threshold.setLastOutput(signal);
        return signal;
    }

    //Exposes DiodeBlock's own (protected) checkTickOnNeighbor to RedstoneThresholdBlockEntity, which lives in a
    //different package - called after a GUI edit changes mode/threshold, since that isn't itself a "neighbor
    //changed" redstone event DiodeBlock's own scheduling would ever notice on its own.
    public void recheckOutput(Level level, BlockPos pos, BlockState state) {
        checkTickOnNeighbor(level, pos, state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneThresholdBlockEntity(pos, state);
    }

    //No ticker needed at all - DiodeBlock's inherited tick()/checkTickOnNeighbor already drive POWERED entirely
    //off scheduled ticks, the block entity here exists purely to store mode/threshold and back the GUI.
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    //While LOCKED, the GUI simply doesn't open at all - per the user's own spec, a locked threshold's mode/
    //threshold become as inert as its output, not just read-only once inside the menu.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (state.getValue(LOCKED)) {
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RedstoneThresholdBlockEntity threshold) {
            player.openMenu(threshold);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LOCKED);
    }
}
