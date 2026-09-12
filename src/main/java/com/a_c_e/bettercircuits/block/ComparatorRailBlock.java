package com.a_c_e.bettercircuits.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RailState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.AABB;

import java.util.List;

//Reads the inventory of a chest/hopper minecart passing over it and outputs exactly what a comparator would if
//that same inventory were in a regular hopper/chest placed in the world - AbstractContainerMenu.
//getRedstoneSignalFromContainer is the SAME helper vanilla's own comparator uses for any container block, so
//"as if the contents were in a regular hopper and you attached a comparator to it" falls straight out of reusing
//it here unmodified.
//
//Extends BaseRailBlock directly rather than vanilla's own DetectorRailBlock (the closest existing equivalent -
//it already has near-identical "is a minecart here" plumbing) because DetectorRailBlock is a CONCRETE class
//whose own codec() is fixed to MapCodec<DetectorRailBlock> - Java's invariant generics make that impossible to
//re-narrow to MapCodec<ComparatorRailBlock> in a subclass, and every other block in this mod gets its own proper
//codec (see this mod's own established convention). The only piece that would have been reused from
//DetectorRailBlock instead of BaseRailBlock is updatePowerToConnected, which is trivial to reproduce directly
//against RailState's own public API (see that method below).
//
//This block replaces DetectorRailBlock's own boolean "is any minecart present" POWERED state entirely with
//SIGNAL (an IntegerProperty 0-15, matching vanilla's own ComparatorBlock.OUTPUT_SIGNAL convention) - a
//comparator has no separate "digital presence" signal distinct from its own computed value, so neither should
//this. The user's own explicit choice: a minecart with nothing readable (empty rail, a plain/furnace/TNT
//minecart, ...) outputs 0, not vanilla Detector Rail's own "15 for mere presence" fallback - matching how a real
//comparator reads 0 facing empty air, not a flat "something is there" signal.
public class ComparatorRailBlock extends BaseRailBlock {
    public static final MapCodec<ComparatorRailBlock> CODEC = simpleCodec(ComparatorRailBlock::new);
    public static final EnumProperty<RailShape> SHAPE = EnumProperty.create("shape", RailShape.class,
            RailShape.NORTH_SOUTH, RailShape.EAST_WEST, RailShape.ASCENDING_EAST, RailShape.ASCENDING_WEST,
            RailShape.ASCENDING_NORTH, RailShape.ASCENDING_SOUTH);
    public static final IntegerProperty SIGNAL = IntegerProperty.create("signal", 0, 15);
    //Same 20-tick cadence vanilla Detector Rail polls at while a minecart remains, to notice a container's
    //contents changing (a hopper feeding it, items being pulled out, ...) or the minecart finally leaving,
    //neither of which fires entityInside on its own.
    private static final int CHECK_PERIOD = 20;

    public ComparatorRailBlock(BlockBehaviour.Properties properties) {
        //isStraight=true, matching Detector/Powered/Activator Rail - only the 6 straight/ascending shapes above,
        //never a curve, matching this mod's own SHAPE property declaration.
        super(true, properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(SHAPE, RailShape.NORTH_SOUTH)
                .setValue(SIGNAL, 0)
                .setValue(WATERLOGGED, false));
    }

    @Override
    public MapCodec<ComparatorRailBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE, SIGNAL, WATERLOGGED);
    }

    @Override
    public Property<RailShape> getShapeProperty() {
        return SHAPE;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide) {
            checkSignal(level, pos, state);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        checkSignal(level, pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock())) {
            BlockState updated = this.updateState(state, level, pos, isMoving);
            checkSignal(level, pos, updated);
        }
    }

    //Mirrors DetectorRailBlock's own checkPressed, but keyed on the computed SIGNAL value changing rather than a
    //boolean toggling, and reschedules polling based on whether a minecart is still physically present (a
    //container minecart that empties out mid-visit needs to drop back to 0 without leaving, which a
    //presence-only reschedule condition still correctly catches on the next 20-tick pass).
    private void checkSignal(Level level, BlockPos pos, BlockState state) {
        if (!canSurvive(state, level, pos)) {
            return;
        }
        int oldSignal = state.getValue(SIGNAL);
        int newSignal = computeSignal(level, pos);
        if (newSignal != oldSignal) {
            BlockState newState = state.setValue(SIGNAL, newSignal);
            level.setBlock(pos, newState, 3);
            updatePowerToConnected(level, pos, newState);
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(pos.below(), this);
            level.setBlocksDirty(pos, state, newState);
        }
        if (!getInteractingMinecarts(level, pos).isEmpty()) {
            level.scheduleTick(pos, this, CHECK_PERIOD);
        }
        level.updateNeighbourForOutputSignal(pos, this);
    }

    //Reproduces DetectorRailBlock's own (protected, unreachable from here - see class comment) notification of
    //every rail this one is physically connected to, so a Powered Rail or another Comparator Rail chained onto
    //this one gets told to recheck itself immediately rather than waiting for its own unrelated trigger.
    private void updatePowerToConnected(Level level, BlockPos pos, BlockState state) {
        RailState railState = new RailState(level, pos, state);
        for (BlockPos connectedPos : railState.getConnections()) {
            BlockState connectedState = level.getBlockState(connectedPos);
            level.neighborChanged(connectedState, connectedPos, connectedState.getBlock(), pos, false);
        }
    }

    //The actual "read it like a comparator would" step: the first Container-implementing minecart found (chest
    //or hopper minecart - vanilla's own AbstractMinecartContainer implements Container, matching exactly the two
    //cart types the user described) has its contents run through the SAME helper a comparator uses on a real
    //container block. Anything else on the rail (a plain minecart, furnace minecart, TNT minecart, ... - none of
    //which implement Container) contributes nothing, so a rail with only those present reads 0.
    private int computeSignal(Level level, BlockPos pos) {
        for (AbstractMinecart minecart : getInteractingMinecarts(level, pos)) {
            if (minecart instanceof Container container) {
                return AbstractContainerMenu.getRedstoneSignalFromContainer(container);
            }
        }
        return 0;
    }

    private List<AbstractMinecart> getInteractingMinecarts(Level level, BlockPos pos) {
        return level.getEntitiesOfClass(AbstractMinecart.class, getSearchBB(pos));
    }

    //Identical bounding box to DetectorRailBlock's own private getSearchBB (not reachable from here) - a 0.2
    //margin inset from the full block on every horizontal side, full height minus 0.2 off the top, so a cart
    //passing near the very edge of the block still counts while one on a barely-touching neighboring rail does
    //not.
    private static AABB getSearchBB(BlockPos pos) {
        double inset = 0.2;
        return new AABB(pos.getX() + inset, pos.getY(), pos.getZ() + inset,
                pos.getX() + 1 - inset, pos.getY() + 1 - inset, pos.getZ() + 1 - inset);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(SIGNAL);
    }

    //Direct (strong) power only flows upward, matching every other single-tile ground-level redstone component's
    //own convention (a block sitting directly on top is the only thing that can be strongly powered - sideways/
    //downward through a solid block instead relies on getSignal's weak-power path).
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return direction == Direction.UP ? state.getValue(SIGNAL) : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(SIGNAL);
    }
}
