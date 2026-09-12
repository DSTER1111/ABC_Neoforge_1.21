package com.a_c_e.bettercircuits.block.lightweight;

import com.a_c_e.bettercircuits.block.entity.LightweightComparatorBlockEntity;
import com.a_c_e.bettercircuits.block.entity.BCBlockEntityTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

//Any-surface counterpart to vanilla's own ComparatorBlock, last of the "lightweight" gate family - built from
//scratch the same way LightweightRepeaterBlock was (no heavy precedent exists in this mod; vanilla's own
//ComparatorBlock is used directly for the floor-only case), ported line-for-line from ComparatorBlock/
//DiodeBlock's own logic, generalized to Left/Right instead of FACING's clockwise/counter-clockwise sides.
//
//Unlike every diode-locking gate in this mod (Inverter/AndGate/XorGate/Timer), the side ("alternate") input is
//NOT diode-restricted - ComparatorBlock never overrides DiodeBlock's own sideInputDiodesOnly()=false default,
//so any redstone block/wire/signal source on either side counts, not just other diodes (see the base class's
//own getControlInputSignal(..., diodesOnly) overload, added for this).
//
//The front input ALSO isn't just a plain redstone read: a comparator reads a directly-adjacent block's own
//analog output signal (hasAnalogOutputSignal/getAnalogOutputSignal - the same mechanism LightweightTimerBlock
//exposes its own progress through), or reaches one block further through a solid block to read an item frame's
//facing-matched analog output or a second analog-capable block behind it - ported directly from
//ComparatorBlock#getInputSignal/getItemFrame.
//
//Architecture departs from vanilla's own neighborChanged-driven ComparatorBlock (and from every OTHER gate in
//this mod): a chest/hopper/lectern/item frame does NOT notify its neighbors when its contents change (that's
//real vanilla behavior, confirmed against the user's own in-game testing, not a bug on our end) - only actual
//redstone-signal changes reliably fire neighborChanged. A comparator that only re-evaluates on neighborChanged
//would therefore sit frozen at whatever value it read on placement (or whenever some UNRELATED redstone event
//happened to also poke it) for its entire container-reading lifetime. Instead this ticks every server tick via
//a BlockEntityTicker, same mechanism LightweightTimerBlock already uses for its own continuous counting -
//LightweightComparatorBlockEntity#serverTick calls refreshOutputState unconditionally every tick, so the
//output signal (and POWERED/downstream notification) tracks whatever it's reading in real time regardless of
//whether the source block ever notifies anyone.
public class LightweightComparatorBlock extends LightweightDiodeBlock implements EntityBlock {
    public static final MapCodec<LightweightComparatorBlock> CODEC = simpleCodec(LightweightComparatorBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final EnumProperty<ComparatorMode> MODE = BlockStateProperties.MODE_COMPARATOR;

    public LightweightComparatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(SUPPORT, Direction.DOWN).setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false).setValue(MODE, ComparatorMode.COMPARE));
    }

    @Override
    public MapCodec<LightweightComparatorBlock> codec() {
        return CODEC;
    }

    //Front input read, generalizing ComparatorBlock#getInputSignal: base wire-aware read (via the base class's
    //own touchesFace-gated readSignal) first, then upgraded to the neighbor's own analog output signal if it
    //has one, or (if still under 15 and the neighbor is a redstone-conductor) one more block further for an
    //item frame facing this direction or another analog-capable block.
    //
    //The analog/container portion (hasAnalogOutputSignal onward) is NOT touchesFace-gated, unlike every other
    //lightweight gate's own input read - touchesFace exists to stop a REDSTONE signal leaking across a
    //sub-block-height mismatch, which doesn't apply to a logical block-entity query like a chest's fullness;
    //vanilla's own ComparatorBlock reads it completely unconditionally regardless of the neighbor's actual
    //collision shape, and hoppers/lecterns in particular have small, irregular shapes that would otherwise
    //fail a geometry check outright.
    private int getInputSignal(Level level, BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING);
        int signal = readSignal(level, pos, state, direction);
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.hasAnalogOutputSignal()) {
            return neighborState.getAnalogOutputSignal(level, neighborPos);
        }
        if (signal < 15 && neighborState.isRedstoneConductor(level, neighborPos)) {
            BlockPos beyondPos = neighborPos.relative(direction);
            BlockState beyondState = level.getBlockState(beyondPos);
            ItemFrame itemFrame = getItemFrame(level, direction, beyondPos);
            int beyondSignal = Math.max(
                    itemFrame == null ? Integer.MIN_VALUE : itemFrame.getAnalogOutput(),
                    beyondState.hasAnalogOutputSignal() ? beyondState.getAnalogOutputSignal(level, beyondPos) : Integer.MIN_VALUE);
            if (beyondSignal != Integer.MIN_VALUE) {
                return beyondSignal;
            }
        }
        return signal;
    }

    @Nullable
    private ItemFrame getItemFrame(Level level, Direction facing, BlockPos pos) {
        List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class,
                new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1),
                itemFrame -> itemFrame != null && itemFrame.getDirection() == facing);
        return frames.size() == 1 ? frames.get(0) : null;
    }

    //Not diode-restricted - see this class's own comment.
    private int getAlternateSignal(Level level, BlockPos pos, BlockState state) {
        return Math.max(
                getControlInputSignal(level, pos, state, getLeft(state), false),
                getControlInputSignal(level, pos, state, getRight(state), false));
    }

    private int calculateOutputSignal(Level level, BlockPos pos, BlockState state) {
        int input = getInputSignal(level, pos, state);
        if (input == 0) {
            return 0;
        }
        int alternate = getAlternateSignal(level, pos, state);
        if (alternate > input) {
            return 0;
        }
        return state.getValue(MODE) == ComparatorMode.SUBTRACT ? input - alternate : input;
    }

    private boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
        int input = getInputSignal(level, pos, state);
        if (input == 0) {
            return false;
        }
        int alternate = getAlternateSignal(level, pos, state);
        return input > alternate || (input == alternate && state.getValue(MODE) == ComparatorMode.COMPARE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        }
        state = state.cycle(MODE);
        float pitch = state.getValue(MODE) == ComparatorMode.SUBTRACT ? 0.55F : 0.5F;
        level.playSound(player, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, pitch);
        level.setBlock(pos, state, 2);
        refreshOutputState(level, pos, state);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    //Called every server tick (see LightweightComparatorBlockEntity#serverTick) and, redundantly but harmlessly,
    //from useWithoutItem for immediate feedback on a mode-cycle click.
    public void refreshOutputState(Level level, BlockPos pos, BlockState state) {
        int newSignal = calculateOutputSignal(level, pos, state);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        int oldSignal = 0;
        if (blockEntity instanceof LightweightComparatorBlockEntity comparator) {
            oldSignal = comparator.getOutputSignal();
            comparator.setOutputSignal(newSignal);
        }
        if (oldSignal != newSignal || state.getValue(MODE) == ComparatorMode.COMPARE) {
            boolean shouldBeOn = shouldTurnOn(level, pos, state);
            boolean isOn = state.getValue(POWERED);
            if (isOn && !shouldBeOn) {
                level.setBlock(pos, state.setValue(POWERED, false), 2);
            } else if (!isOn && shouldBeOn) {
                level.setBlock(pos, state.setValue(POWERED, true), 2);
            }
            notifyOutput(level, pos, getOutput(state));
        }
    }

    //Support-loss handling only, matching every other lightweight gate - the actual output refresh no longer
    //happens here (see this class's own comment on why: it needs to run every tick regardless of whether
    //anything ever calls neighborChanged).
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

    //side == FACING (the output side, matching every other lightweight gate's own convention) - the actual
    //emitted STRENGTH is the block entity's own stored analog value, not a flat 15, matching ComparatorBlock's
    //own getOutputSignal.
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (!state.getValue(POWERED) || side != state.getValue(FACING)) {
            return 0;
        }
        return level.getBlockEntity(pos) instanceof LightweightComparatorBlockEntity comparator ? comparator.getOutputSignal() : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LightweightComparatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != BCBlockEntityTypes.LIGHTWEIGHT_COMPARATOR.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> LightweightComparatorBlockEntity.serverTick(lvl, pos, st, (LightweightComparatorBlockEntity) be);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SUPPORT, FACING, POWERED, MODE);
    }
}
