package com.a_c_e.bettercircuits.block;

import com.a_c_e.bettercircuits.BCTags;
import com.a_c_e.bettercircuits.block.entity.HeatDetectorBlockEntity;
import com.a_c_e.bettercircuits.block.entity.BCBlockEntityTypes;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

//Same "recompute every 20 ticks via a block entity ticker, expose the result as POWER" structure as
//RainDetectorBlock/vanilla's own DaylightDetectorBlock, and now shares that same thin sensor-plate shape/model
//and right-click-to-invert INVERTED property too. Not inverted, it scans outward along each of the 6 axis
//directions (see updateSignalStrength) looking for a "hot" block (BCTags.HOT_BLOCK); inverted, it scans the same
//way for a "cold" block instead (BCTags.COLD_BLOCK - ice and snow, see BCBlockTagProvider). POWER lands on one of
//4 discrete values (0/5/10/15 - see distanceToPower/updateSignalStrength) based on how close the nearest
//qualifying block is, matching the 2 top textures supplied (heat_detector, heat_detector_inverted - see
//BCBlockStateProvider#heatDetector). Ambient dimension/biome climate can also raise the reading on its own,
//with no nearby block needed - see climateBaseline.
public class HeatDetectorBlock extends BaseEntityBlock {
    public static final MapCodec<HeatDetectorBlock> CODEC = simpleCodec(HeatDetectorBlock::new);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;
    protected static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 6.0, 16.0);

    //In the Nether (or any other ultrawarm dimension - see IceBlock's own melt()/playerDestroy() for this exact
    //same generalization), ambient heat alone is enough to always read at least NETHER_BASELINE_POWER while
    //detecting heat - a closer/hotter source can still push the reading up to 15 or 10. Doesn't apply while
    //INVERTED (detecting cold) - the Nether being hot has no bearing on a "how cold is it here" reading.
    //END_BASELINE_POWER is the End's own mirror of this, applying instead while INVERTED (the End reads as
    //ambiently cold). Both baselines are also granted early by a sufficiently hot/cold Overworld biome - see
    //climateBaseline.
    private static final int NETHER_BASELINE_POWER = 5;
    private static final int END_BASELINE_POWER = 5;
    private static final int NORMAL_RADIUS = 3;
    private static final int NETHER_RADIUS = 2;

    public HeatDetectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0).setValue(INVERTED, false));
    }

    @Override
    public MapCodec<HeatDetectorBlock> codec() {
        return CODEC;
    }

    //Thin sensor-plate shape, matching Rain Detector/vanilla's own Daylight Detector - the model now reuses
    //vanilla's template_daylight_detector directly (see BCBlockStateProvider#heatDetector), so the collision/
    //selection shape has to match it, unlike the old full-cube shape.
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

    //A HOT_BLOCK tag hit isn't automatically "on" - a Furnace/Smoker/Blast Furnace/Campfire/Soul Campfire only
    //counts while actually lit (a tag alone can't express that), everything else in the tag (lava, fire, torches,
    //magma block, ...) has no such state and counts whenever the tag matches.
    private static boolean isHotBlock(BlockState state) {
        if (!state.is(BCTags.HOT_BLOCK)) {
            return false;
        }
        return !state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT);
    }

    //COLD_BLOCK (ice and snow, see BCTags/BCBlockTagProvider) has no LIT-style gate to worry about - every
    //member always counts.
    private static boolean isColdBlock(BlockState state) {
        return state.is(BCTags.COLD_BLOCK);
    }

    //Walks outward from pos along a single direction, up to maxDistance blocks, returning the distance to the
    //first qualifying block found on that ray, or 0 if none (either nothing qualifying within range, or a solid
    //block got in the way first - "walls will interfere/block the detection", per the source mod's own spec).
    //Which tag qualifies depends on inverted: hot while normal, cold while inverted.
    private static int scanDirection(Level level, BlockPos pos, Direction direction, int maxDistance, boolean inverted) {
        for (int distance = 1; distance <= maxDistance; distance++) {
            BlockPos candidatePos = pos.relative(direction, distance);
            BlockState candidateState = level.getBlockState(candidatePos);
            boolean qualifies = inverted ? isColdBlock(candidateState) : isHotBlock(candidateState);
            if (qualifies) {
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

    //Ambient climate baseline, independent of any actual hot/cold block nearby: the Nether (or any other
    //ultrawarm dimension) always reads as at least somewhat hot, the End always reads as at least somewhat
    //cold, and - short of being in either of those - a sufficiently hot or cold Overworld biome grants the same
    //baseline early, using the exact same "hot/no precipitation, cold/snow precipitation" read vanilla itself
    //uses to decide whether a biome gets rain or snow (Biome#getPrecipitationAt - NONE/RAIN/SNOW).
    private static int climateBaseline(Level level, BlockPos pos, boolean inverted) {
        if (level.dimensionType().ultraWarm()) {
            return inverted ? 0 : NETHER_BASELINE_POWER;
        }
        if (level.dimension() == Level.END) {
            return inverted ? END_BASELINE_POWER : 0;
        }
        Biome.Precipitation precipitation = level.getBiome(pos).value().getPrecipitationAt(pos);
        if (precipitation == Biome.Precipitation.NONE) {
            return inverted ? 0 : NETHER_BASELINE_POWER;
        }
        if (precipitation == Biome.Precipitation.SNOW) {
            return inverted ? END_BASELINE_POWER : 0;
        }
        return 0;
    }

    private static void updateSignalStrength(BlockState state, Level level, BlockPos pos) {
        boolean inverted = state.getValue(INVERTED);
        int radius = level.dimensionType().ultraWarm() ? NETHER_RADIUS : NORMAL_RADIUS;

        int closestDistance = 0;
        for (Direction direction : Direction.values()) {
            int distance = scanDirection(level, pos, direction, radius, inverted);
            if (distance != 0 && (closestDistance == 0 || distance < closestDistance)) {
                closestDistance = distance;
            }
        }

        int power = distanceToPower(closestDistance);
        power = Math.max(power, climateBaseline(level, pos, inverted));
        power = Mth.clamp(power, 0, 15);

        if (state.getValue(POWER) != power) {
            level.setBlock(pos, state.setValue(POWER, power), 3);
        }
    }

    //Right-click cycles INVERTED, mirroring RainDetectorBlock/vanilla's own Daylight Detector exactly, then
    //immediately recomputes POWER against the new mode instead of waiting up to 20 ticks for the next tick.
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
        return new HeatDetectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != BCBlockEntityTypes.HEAT_DETECTOR.get()) {
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
