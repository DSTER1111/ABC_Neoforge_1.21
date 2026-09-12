package com.a_c_e.bettercircuits.block;

import com.a_c_e.bettercircuits.BCTags;
import com.a_c_e.bettercircuits.block.entity.HeatSensorBlockEntity;
import com.a_c_e.bettercircuits.block.entity.BCBlockEntityTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nullable;

//Same "recompute every 20 ticks via a block entity ticker, expose the result as POWER" structure as
//RainDetectorBlock/vanilla's own DaylightDetectorBlock, but instead of reading an ambient environmental value,
//it scans outward along each of the 6 axis directions (see updateSignalStrength) looking for a "hot" block
//(BCTags.HOT_BLOCK - see BCBlockTagProvider for the exact list and why some LIT-gated blocks need an extra
//runtime check), stopping each ray early at the first solid obstruction. POWER only ever lands on one of 4
//discrete values (0/5/10/15 - see distanceToPower/updateSignalStrength), matching the 4 top textures supplied
//(heat_sensor, heat_sensor_5, heat_sensor_10, heat_sensor_15) - no separate on/off flag needed, the model is
//selected directly off POWER's own value (see BCBlockStateProvider#heatSensor).
public class HeatSensorBlock extends BaseEntityBlock {
    public static final MapCodec<HeatSensorBlock> CODEC = simpleCodec(HeatSensorBlock::new);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    //In the Nether (or any other ultrawarm dimension - see IceBlock's own melt()/playerDestroy() for this exact
    //same generalization), ambient heat alone is enough to always read at least NETHER_BASELINE_POWER, and the
    //detection range shrinks by one - a closer/hotter source can still push the reading up to 15 or 10.
    private static final int NETHER_BASELINE_POWER = 5;
    private static final int NORMAL_RADIUS = 3;
    private static final int NETHER_RADIUS = 2;

    public HeatSensorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    public MapCodec<HeatSensorBlock> codec() {
        return CODEC;
    }

    //Standard full block, unlike RainDetectorBlock/vanilla's own Daylight Detector - the user's own spec calls
    //for the plain "cube_all" model/shape, not a thin sensor-plate shape, so getShape/useShapeForLightOcclusion
    //are left at BaseEntityBlock's own full-cube defaults.

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return state.getValue(POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    //A HOT_BLOCK tag hit isn't automatically "on" - a Furnace/Smoker/Blast Furnace/Campfire/Soul Campfire only
    //counts while actually lit (a tag alone can't express that), everything else in the tag (lava, fire, torches,
    //magma block, ...) has no such state and counts whenever the tag matches.
    private static boolean isHotBlock(BlockState state) {
        if (!state.is(BCTags.HOT_BLOCK)) {
            return false;
        }
        return !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
    }

    //Walks outward from pos along a single direction, up to maxDistance blocks, returning the distance to the
    //first hot block found on that ray, or 0 if none (either nothing hot within range, or a solid block got in
    //the way first - "walls will interfere/block the detection", per the user's own spec).
    private static int scanDirection(Level level, BlockPos pos, Direction direction, int maxDistance) {
        for (int distance = 1; distance <= maxDistance; distance++) {
            BlockPos candidatePos = pos.relative(direction, distance);
            BlockState candidateState = level.getBlockState(candidatePos);
            if (isHotBlock(candidateState)) {
                return distance;
            }
            if (!candidateState.getCollisionShape(level, candidatePos).isEmpty()) {
                break;
            }
        }
        return 0;
    }

    private static int distanceToPower(int distance) {
        return switch (distance) {
            case 1 -> 15;
            case 2 -> 10;
            case 3 -> 5;
            default -> 0;
        };
    }

    private static void updateSignalStrength(BlockState state, Level level, BlockPos pos) {
        boolean nether = level.dimensionType().ultraWarm();
        int radius = nether ? NETHER_RADIUS : NORMAL_RADIUS;

        int closestDistance = 0;
        for (Direction direction : Direction.values()) {
            int distance = scanDirection(level, pos, direction, radius);
            if (distance != 0 && (closestDistance == 0 || distance < closestDistance)) {
                closestDistance = distance;
            }
        }

        int power = distanceToPower(closestDistance);
        if (nether) {
            power = Math.max(power, NETHER_BASELINE_POWER);
        }
        power = Mth.clamp(power, 0, 15);

        if (state.getValue(POWER) != power) {
            level.setBlock(pos, state.setValue(POWER, power), 3);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeatSensorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != BCBlockEntityTypes.HEAT_SENSOR.get()) {
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
        builder.add(POWER);
    }
}
