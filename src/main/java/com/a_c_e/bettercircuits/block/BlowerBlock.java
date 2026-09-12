package com.a_c_e.bettercircuits.block;

import com.a_c_e.bettercircuits.BCParticleTypes;
import com.a_c_e.bettercircuits.block.entity.BlowerBlockEntity;
import com.a_c_e.bettercircuits.block.entity.BCBlockEntityTypes;
import com.a_c_e.bettercircuits.util.EntityForceUtil;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

//Pushes entities standing in front of it while powered by redstone, growing weaker the further away they are
//(up to RANGE blocks) - both the push (server) and the wind-stream particles (client) run off the same
//BlowerBlockEntity ticker, each side branching on level.isClientSide, so there's no cached POWERED blockstate
//property at all - both halves just call level.hasNeighborSignal(pos) live every tick (the only visual change
//"on" causes is the particle stream itself, so there's nothing else that would need a cached property to
//re-render off of).
//
//FACING placement mirrors DispenserBlock.getStateForPlacement exactly (decompiled to confirm: front faces the
//player, i.e. context.getNearestLookingDirection().getOpposite()) - the "standard rotation (like the dispenser)"
//the user asked for is this placement UX, not dispenser's own particular model-swapping technique (see
//BCBlockStateProvider#blower for how the model itself handles all 6 facings from one base model instead, closer
//to vanilla's own Piston).
public class BlowerBlock extends Block implements EntityBlock {
    public static final MapCodec<BlowerBlock> CODEC = simpleCodec(BlowerBlock::new);
    public static final DirectionProperty FACING = DirectionalBlock.FACING;

    private static final int RANGE = 8;
    private static final double MAX_PUSH_PER_TICK = 0.105;

    public BlowerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<BlowerBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlowerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != BCBlockEntityTypes.BLOWER.get()) {
            return null;
        }
        return level.isClientSide
                ? (lvl, pos, st, be) -> spawnWindParticles(lvl, pos, st)
                : (lvl, pos, st, be) -> pushEntities(lvl, pos, st);
    }

    //Server-side: pushes every non-spectator entity within RANGE blocks of the front face, strength tapering
    //linearly from MAX_PUSH_PER_TICK at the front face down to 0 at RANGE blocks out - see EntityForceUtil's own
    //comment for why a plain Entity.push isn't enough for mobs/minecarts/players.
    private static void pushEntities(Level level, BlockPos pos, BlockState state) {
        if (!level.hasNeighborSignal(pos)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
        AABB zone = new AABB(pos).expandTowards(dir.scale(RANGE));
        List<Entity> entities = level.getEntities((Entity) null, zone, EntitySelector.NO_SPECTATORS);
        for (Entity entity : entities) {
            double distance = distanceInFront(pos, facing, entity.position());
            double strength = MAX_PUSH_PER_TICK * Mth.clamp(1.0 - distance / RANGE, 0.0, 1.0);
            if (strength <= 0.0) {
                continue;
            }
            EntityForceUtil.applyForce(entity, new Vec3(dir.x * strength, dir.y * strength, dir.z * strength));
        }
    }

    //How far past the block's own front face a given world position sits, along the facing axis - 0 at the
    //face itself, RANGE at the edge of the push zone. Negative (behind/inside the block) is left uncapped here;
    //callers clamp the resulting strength ratio instead.
    private static double distanceInFront(BlockPos pos, Direction facing, Vec3 point) {
        return switch (facing) {
            case DOWN -> pos.getY() - point.y;
            case UP -> point.y - (pos.getY() + 1);
            case NORTH -> pos.getZ() - point.z;
            case SOUTH -> point.z - (pos.getZ() + 1);
            case WEST -> pos.getX() - point.x;
            case EAST -> point.x - (pos.getX() + 1);
        };
    }

    //Client-side: a continuous stream of wind particles blowing away from the front face - denser near the
    //block, sparser toward RANGE blocks out (linear spawn-chance falloff, evaluated every client tick rather
    //than via the usual animateTick hook, since animateTick only samples a random few block positions per tick -
    //fine for ambient smoke, too sparse for a stream meant to visibly indicate "on" at a glance).
    private static void spawnWindParticles(Level level, BlockPos pos, BlockState state) {
        if (!level.hasNeighborSignal(pos)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        RandomSource random = level.getRandom();
        Vec3 dir = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 origin = Vec3.atCenterOf(pos);
        Direction.Axis axis = facing.getAxis();

        for (int step = 0; step < RANGE; step++) {
            float spawnChance = 1.0F - (float) step / RANGE;
            if (random.nextFloat() > spawnChance) {
                continue;
            }
            double along = 0.5 + step + random.nextDouble();
            double lateral1 = (random.nextDouble() - 0.5) * 0.6;
            double lateral2 = (random.nextDouble() - 0.5) * 0.6;
            Vec3 particlePos = origin.add(dir.scale(along));
            particlePos = switch (axis) {
                case X -> particlePos.add(0, lateral1, lateral2);
                case Y -> particlePos.add(lateral1, 0, lateral2);
                case Z -> particlePos.add(lateral1, lateral2, 0);
            };
            double speed = 0.25;
            level.addParticle(BCParticleTypes.BLOWER_WIND, particlePos.x, particlePos.y, particlePos.z,
                    dir.x * speed, dir.y * speed, dir.z * speed);
        }
    }
}
