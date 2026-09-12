package com.a_c_e.bettercircuits.block;

import com.a_c_e.bettercircuits.block.entity.BCBlockEntityTypes;
import com.a_c_e.bettercircuits.block.entity.RainDetectorBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

//A line-for-line functional mirror of vanilla's own DaylightDetectorBlock (same POWER/INVERTED properties, same
//6px-tall shape, same "recompute every 20 ticks via a block entity ticker" structure, same right-click-to-
//invert behavior) with light level swapped for rain exposure: POWER scales with how hard it's currently
//raining (Level#getRainLevel), but only counts at all when this exact position is actually getting rained on
//(Level#isRainingAt - checks global rain state, a clear sky above, and the local biome allowing rain rather
//than snow), matching the user's own "must be exposed to the rain, having rain hit the detector directly" spec.
public class RainDetectorBlock extends BaseEntityBlock {
    public static final MapCodec<RainDetectorBlock> CODEC = simpleCodec(RainDetectorBlock::new);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;
    protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0);

    public RainDetectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0).setValue(INVERTED, false));
    }

    @Override
    public MapCodec<RainDetectorBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    //Same "not exposed -> 0, otherwise scale 0-15 by intensity, then flip if INVERTED" shape as
    //DaylightDetectorBlock's own updateSignalStrength - just isRainingAt+getRainLevel in place of sky
    //brightness+sun angle. No smoothing curve is needed here the way daylight's moon-phase cosine is, since
    //getRainLevel already ramps smoothly on its own as rain starts/stops.
    //
    //isRainingAt checks pos.above(), NOT pos itself - its own heightmap check (getHeightmapPos(...).getY() >
    //pos.getY()) is written for querying the OPEN AIR space directly above a solid column, which for a
    //topmost, motion-blocking block like this one is always one Y above the block's own position (confirmed
    //against FarmBlock/LeavesBlock, the two vanilla blocks in the same "am I, a topmost solid block, exposed to
    //rain" situation - both call isRainingAt(pos.above()), never isRainingAt(pos) on their own position).
    private static void updateSignalStrength(BlockState state, Level level, BlockPos pos) {
        int power = level.isRainingAt(pos.above()) ? Math.round(15.0F * level.getRainLevel(1.0F)) : 0;
        if (state.getValue(INVERTED)) {
            power = 15 - power;
        }
        power = Mth.clamp(power, 0, 15);
        if (state.getValue(POWER) != power) {
            level.setBlock(pos, state.setValue(POWER, power), 3);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.mayBuild()) {
            return super.useWithoutItem(state, level, pos, player, hitResult);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockState newState = state.cycle(INVERTED);
        level.setBlock(pos, newState, 2);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, newState));
        updateSignalStrength(newState, level, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RainDetectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !level.dimensionType().hasSkyLight() || type != BCBlockEntityTypes.RAIN_DETECTOR.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (lvl.getGameTime() % 20L == 0L) {
                updateSignalStrength(st, lvl, pos);
            }
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER, INVERTED);
    }
}
