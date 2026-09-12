package com.a_c_e.bettercircuits.block;

import com.a_c_e.bettercircuits.block.entity.RedstoneCableBlockEntity;
import com.a_c_e.bettercircuits.block.entity.RedstoneCableBlockEntity.FaceState;
import com.a_c_e.bettercircuits.item.AluminumFrameBlockItem;
import com.a_c_e.bettercircuits.item.BCItems;
import com.a_c_e.bettercircuits.item.RedstoneCableBlockItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

//Phase 3: one block position can now host up to 6 independent per-face cables (one per possible mounting
//direction) instead of Phase 2's single cable-per-position with a "climbing" algorithm that kept needing new
//edge-case handling every time a new placement geometry came up (floor-to-wall corners, wall-to-wall corners,
//diagonal update propagation, floating-gap power leaks - five separate rounds of fixes this session). This
//redesign (the user's own proposal) replaces all of that with 3 uniform rules applied identically to every face
//regardless of which direction it's on, none of them needing chained neighbor-of-neighbor reasoning:
//
//For an active face F at this block's position Q, with `towardSupport` = the direction from Q to whatever solid
//block F is mounted against (support S = Q + towardSupport), and F's 4 "in-plane" directions being the ones
//perpendicular to towardSupport's axis:
//  1. SIBLING FACE - one of Q's own *other* 4 faces (not F, not F's opposite) is also active. Pure lookup into
//     this same block's own block entity, no neighbor position involved.
//  2. SAME-FACE NEIGHBOR - for in-plane direction d, does Q+d have an active face with the SAME towardSupport as
//     F? (Direct analog of plain same-plane connections.)
//  3. SHARED-ANCHOR, DIFFERENT FACE - for in-plane direction d, does S+d (one step from *my own support*, not
//     from Q) have an active face whose towardSupport is d.getOpposite() (mounted facing back at S)? This is what
//     replaces Phase 2's whole "is my neighbor tall enough, is its face sturdy, is there something beyond it"
//     chain with one direct position lookup.
//
//Since blockstate permutations can't represent this much combinatorial state (up to 6 independent copies of a
//per-face connection+power space), all of it lives in RedstoneCableBlockEntity instead, and this block has no
//blockstate properties at all - not even for rendering, which is driven by the block entity via ModelData
//(Phase 3b; this pass uses a placeholder shape to prove the placement/connection/power logic first).
//
//Insulated Redstone Cable: each face additionally carries a nullable DyeColor (FaceState.color()), set once at
//placement and never reassigned. A plain (null) face connects to anything; two DIFFERENT colors never connect
//to each other under any of the 3 rules above - see colorsCompatible and its callers in computeFace. This is
//deliberately NOT a separate Block per color: Minecraft only ever allows one Block/BlockState per position, but
//the user's own spec requires several different colors (plus plain cable) to coexist on the SAME position's
//different faces at once (a green floor cable, a red ceiling cable, ...) - so every color places this exact
//same Block/BlockEntity (see RedstoneCableBlockItem's own color field), and only the per-face data differs.
//Insulated faces also render thicker (4x4 instead of the plain 2x2 - see insulatedFaceBounds/insulatedArmBounds
//and RedstoneCableBakedModel) and sample their own solid-colored sprite instead of the plain wire's tintable one.
//
//Bundled Redstone Cable: a THIRD kind of face (FaceState.bundled()), reusing the exact same Block/BlockEntity for
//the same reason Insulated does - see BCItems.BUNDLED_CABLE_ITEM. Always uncolored (there's only one variant, not
//one per DyeColor). Connects to itself and to Insulated Redstone Cable of any color only - never to plain cable
//or a generic redstone component (see cablesCompatible). Renders bigger still (6x6x4 visual, reusing Insulated's
//own COLLISION pad - see bundledFaceBounds) and samples its own single sprite. This pass is visual setup ONLY:
//power is hardcoded to 0 for every bundled face (see computeFace's own comment) - the real 16-channel signal
//transmission this block is meant to eventually carry is separate, later work.
//Aluminum Frame (see FrameBlock's own original standalone-block comment, now superseded): the user's own spec
//requires Frame to "occupy the same block as cables" - genuine coexistence at one BlockPos, which Minecraft's
//one-Block-per-position rule makes impossible for two separate Block classes. Folded in the exact same way
//Insulated/Bundled Cable already share this one Block/BlockEntity instead of getting their own: a frame is a
//third kind of thing RedstoneCableBlockEntity can hold at a position (RedstoneCableBlockEntity#hasFrame/
//frameArms), alongside whatever cable faces (if any) already live there. See addFrame/removeFrame,
//refreshAndNotify's own frame-arm recompute, getShape's frame-shape union, and RedstoneCableBakedModel's own
//frame-quad baking. WATERLOGGED (this block's first-ever blockstate property - everything else lives in the
//block entity, see the class comment above) exists purely for Frame's own "no support needed, waterloggable"
//requirement; a frame-less cable can still technically end up waterlogged if placed directly into water, which
//was never asked for but is harmless (matches vanilla's own general tolerance for a WATERLOGGED flag with no
//visible effect beyond correct fluid behavior).
public class RedstoneCableBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<RedstoneCableBlock> CODEC = simpleCodec(RedstoneCableBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    //---- Aluminum Frame shape data: collision/hit-testing ONLY - the frame's own RENDERED geometry (baked in
    //RedstoneCableBakedModel, a literal transcription of the uploaded reference model) stays the real hollow
    //lattice; this is a simplified QoL bounding volume around it. The lattice's actual outline was originally
    //used here too (hugging every individual strut), but a hollow frame with no cable in it let the player's
    //own reach ray pass clean through the gaps, making it awkward to target - the user's own fix: a single
    //solid 8x8x8 cube around the whole core (the lattice's own real footprint already sits entirely inside the
    //4-12 range on every axis - see isFrameHit's own comment), plus one solid 8x8x4 cube per active arm,
    //reaching from that same cube's own edge out to the block boundary in that direction (same "core edge to
    //block boundary" relationship extendToEdge already expresses for the center cable's own bounds below).
    //CORE_SHAPE is always unioned in when a frame is present; each ARM_SHAPES entry only when that direction's
    //arm is active (RedstoneCableBlockEntity#frameArms). Also reused directly (as raw bounds, not VoxelShape)
    //by isFrameHit below for precise break/pick-block hit-testing against frame geometry, the same way it's
    //already used for cable faces. ----

    private static final double[] FRAME_CORE_BOX = {4, 4, 4, 12, 12, 12};
    private static final Map<Direction, double[]> FRAME_ARM_BOXES = new EnumMap<>(Direction.class);

    static {
        for (Direction direction : Direction.values()) {
            FRAME_ARM_BOXES.put(direction, extendToEdge(FRAME_CORE_BOX, direction));
        }
    }

    private static final VoxelShape FRAME_CORE_SHAPE = box(FRAME_CORE_BOX);
    private static final Map<Direction, VoxelShape> FRAME_ARM_SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction direction : Direction.values()) {
            FRAME_ARM_SHAPES.put(direction, box(FRAME_ARM_BOXES.get(direction)));
        }
    }

    //Reverted back to a global flag (was briefly scoped to a single BlockPos - see git history/session notes):
    //scoping suppression to only the one position being recomputed fixed a narrow placement-ordering bug (a
    //freshly placed cable occasionally failing to pick up another cable's already-settled soft power), but it
    //also meant two-plus cables sharing a support block could freely read each other's live, in-progress output
    //instead of being blocked by the same global suppression window - which is exactly what let cable/cable and
    //cable/dust reflective loops form so easily. The user's own call: an occasional missed initial soft-power
    //read is a much smaller cost than cables loop-locking any time multiple of them touch the same block, which
    //happened constantly. Global suppression doesn't eliminate reflective loops entirely (matches vanilla's own
    //acceptance of self-sustaining redstone loops, e.g. a repeater feeding itself, as something the player needs
    //to avoid rather than something the engine prevents), it just makes them meaningfully rarer.
    private static boolean shouldSignal = true;

    public RedstoneCableBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
    }

    @Override
    public MapCodec<RedstoneCableBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneCableBlockEntity(pos, state);
    }

    //Same WaterloggedTransparentBlock pattern used elsewhere in this codebase (Aluminum Grate) - sets
    //WATERLOGGED from whatever fluid is at the target position at the moment of the VERY FIRST thing placed
    //here (a face or a frame), whichever happens first; it's never revisited afterward, matching how vanilla's
    //own waterloggable blocks never re-check once placed either. placeBaseBlock (used by both addFace's and
    //addFrame's own air-branch) applies this same fluid check directly rather than going through this method,
    //since those two static helpers place the block themselves rather than through BlockItem's normal flow.
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean waterlogged = context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER);
        return this.defaultBlockState().setValue(WATERLOGGED, waterlogged);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(true) : super.getFluidState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        //Real geometry comes from RedstoneCableBakedModel via ModelData (see BetterCircuits.ClientModEvents'
        //ModifyBakingResult handler, which wraps this block's baked model to supply it).
        return RenderShape.MODEL;
    }

    //Bug fix: never overridden before, so this defaulted to Block's own false - meaning vanilla redstone dust's
    //own RedStoneWireBlock#shouldConnectTo (`state.is(REDSTONE_WIRE) || repeater-facing-check ||
    //(state.isSignalSource() && direction != null)`) had no reason to visually connect to a cable at all: it's
    //not literally REDSTONE_WIRE, not a vanilla repeater, and isSignalSource() was false. Matches vanilla dust's
    //own isSignalSource()=true declaration (and every LightweightDiodeBlock subclass's own override) - the
    //cable IS meant to act as a redstone conductor other components should recognize as one.
    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    private static List<Direction> inPlaneDirections(Direction towardSupport) {
        return Direction.stream().filter(d -> d.getAxis() != towardSupport.getAxis()).toList();
    }

    @Nullable
    private static RedstoneCableBlockEntity getCableAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof RedstoneCableBlockEntity cable ? cable : null;
    }

    //---- Placement ----

    //Adding the FIRST face at a position places this block (via the item - see RedstoneCableBlockItem); every
    //face after that is added here directly, without placing a new block. Returns false if the face couldn't be
    //added (already present, or no valid support there).
    //Validates everything (target type, no duplicate face, sturdy support) BEFORE placing the block in the
    //air branch, not after - placing first and checking sturdiness afterward (the original order here) left an
    //empty, faceless, invisible, hitbox-less cable behind on a failed check, since nothing ever undid the
    //placement once canSurviveOn came back false (see RedstoneCableBlockItem.place() for the reported bug and
    //its own pre-check, which stops super.place() from ever getting this far on a bad target in the first
    //place - this reordering just makes addFace() itself safe on its own terms too, for any other caller).
    //
    //color is null for a plain Redstone Cable face, or one of the 16 DyeColor values for an Insulated Redstone
    //Cable face (see RedstoneCableBlockItem's own color field) - both place the exact same Block/BlockEntity,
    //since a single block position needs to be able to host DIFFERENT colors on its different faces at once (a
    //green floor cable, a red ceiling cable, ... - see the class comment) and Minecraft only ever allows one
    //Block/BlockState per position. There's still only ever ONE face per direction regardless of color - the
    //hasFace check above doesn't look at color at all, so a second cable (any color) can never be added on top
    //of an existing face in the same direction.
    //
    //bundled is true for a Bundled Redstone Cable face - always paired with color == null (there's only one
    //Bundled Cable item, not one per DyeColor - see FaceState's own comment), passed through the same way color
    //is (see RedstoneCableBlockItem's own bundled field).
    public static boolean addFace(Level level, BlockPos pos, Direction towardSupport, @Nullable DyeColor color, boolean bundled) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !(state.getBlock() instanceof RedstoneCableBlock)) {
            return false;
        }
        RedstoneCableBlockEntity existingCable = getCableAt(level, pos);
        if (existingCable != null && existingCable.hasFace(towardSupport)) {
            return false;
        }
        BlockPos support = pos.relative(towardSupport);
        if (!canSurviveOn(level, support, level.getBlockState(support), towardSupport)) {
            return false;
        }
        if (state.isAir()) {
            placeBaseBlock(level, pos);
        }
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return false;
        }
        cable.setFace(towardSupport, FaceState.empty(color, bundled));
        refreshAndNotify(level, pos);
        return true;
    }

    //Shared by addFace's and addFrame's own air-branch: places a fresh RedstoneCableBlock, setting WATERLOGGED
    //from whatever fluid is actually at pos right now (same check getStateForPlacement itself uses for the
    //normal BlockItem placement flow - this is the OTHER path, for the two static helpers that place the block
    //directly rather than going through that flow).
    private static void placeBaseBlock(Level level, BlockPos pos) {
        boolean waterlogged = level.getFluidState(pos).is(Fluids.WATER);
        level.setBlock(pos, BCBlocks.REDSTONE_CABLE.get().defaultBlockState().setValue(WATERLOGGED, waterlogged), 3);
    }

    //Bug fix: Rule 3's own shared-anchor wrap connection (see computeFace's own comment) links two cables that
    //are NEVER directly adjacent to each other - they only share a diagonal relationship through a common
    //support block. Vanilla's own neighbor-update propagation (both a plain removeBlock/destroyBlock AND
    //updateNeighborsAt) only ever reaches a position's own 6 direct neighbors, which can never include a
    //diagonal wrap partner - so a wrap-connected cable that's still standing never got told anything changed
    //when the OTHER side of that connection was removed, and stayed visually connected (a padded, or even
    //power-carrying, arm toward nothing) until some unrelated update happened to jar it loose. refreshAndNotify
    //below already issues this exact same explicit neighborChanged call for every wrap target of every
    //SURVIVING face in its own closing loop - this is that same call, factored out so it can also be issued for
    //a face that's about to stop existing entirely (this position's own block being removed/destroyed, or just
    //this one face losing support), since once that happens there's no "surviving face" left for that loop to
    //find and iterate over.
    private static void notifyWrapNeighbors(Level level, BlockPos pos, Direction towardSupport) {
        Block self = BCBlocks.REDSTONE_CABLE.get();
        BlockPos support = pos.relative(towardSupport);
        for (Direction d : inPlaneDirections(towardSupport)) {
            BlockPos wrapTarget = support.relative(d);
            if (getCableAt(level, wrapTarget) != null) {
                level.neighborChanged(wrapTarget, self, pos);
            }
        }
    }

    //Removes one face; if nothing (no other faces, no frame) is left, removes the whole block (matching how the
    //block never exists with zero active faces and no frame).
    private static void removeFace(Level level, BlockPos pos, Direction towardSupport) {
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return;
        }
        cable.removeFace(towardSupport);
        if (cable.hasAnyFace() || cable.hasFrame()) {
            refreshAndNotify(level, pos);
        } else {
            notifyWrapNeighbors(level, pos, towardSupport);
            level.removeBlock(pos, false);
        }
    }

    //---- Aluminum Frame placement/removal (see the class comment for why this shares RedstoneCableBlock/
    //RedstoneCableBlockEntity with cable faces rather than being its own Block) ----

    //Mirrors addFace's own shape exactly, just without any support/color/bundled concerns - a frame never needs
    //support (the user's own spec: "can be placed anywhere"), and there's only ever one kind of frame, so the
    //only failure case is "a frame is already here". Frame CONNECTIVITY (frameArms) isn't computed here at all
    //- refreshAndNotify recomputes it for every active frame the same way it already recomputes cable face
    //connections, so this only ever needs to flip the presence flag and let that shared recompute take it from
    //there (same as addFace itself never computing its own face's connections directly).
    public static boolean addFrame(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !(state.getBlock() instanceof RedstoneCableBlock)) {
            return false;
        }
        RedstoneCableBlockEntity existingCable = getCableAt(level, pos);
        if (existingCable != null && existingCable.hasFrame()) {
            return false;
        }
        if (state.isAir()) {
            placeBaseBlock(level, pos);
        }
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return false;
        }
        cable.setHasFrame(true);
        refreshAndNotify(level, pos);
        return true;
    }

    //Mirrors breakFace's own pop/sound/stat pattern (see that method's own comment for why null, not player, is
    //passed to levelEvent/playSound) but far simpler - a frame is a single flag, not one-of-several faces, so
    //there's no per-direction resolution needed here (the caller - breakFace's own dispatch below, or
    //BCEventHandlers#onBreakCable for the real-mining-time path - already resolved that the hit landed on
    //frame geometry specifically before calling this).
    //
    //Drop requires a pickaxe (matches vanilla's own "wrong tool still breaks it, just doesn't drop" convention
    //for requiresCorrectToolForDrops blocks - see getDestroyProgress's own comment for why that's a static
    //property elsewhere but a live instanceof check here) - this only gates the ITEM, never the removal itself,
    //so a bare-handed break (survival, no pickaxe) still empties the frame, it just pops nothing.
    public static void removeFrame(Level level, BlockPos pos, Player player) {
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null || !cable.hasFrame()) {
            return;
        }
        if (!player.getAbilities().instabuild && player.getMainHandItem().getItem() instanceof PickaxeItem) {
            popResource(level, pos, new ItemStack(BCItems.ALUMINUM_FRAME_ITEM.get()));
        }
        //A center cable only ever exists because the frame supports it (see addCenter's own requirement, and
        //the class comment) - it can't survive the frame disappearing, the same way a face can't survive its
        //own support block disappearing. Popped unconditionally alongside the frame item (not tool-gated the
        //way the frame's own drop is - a center cable is an ordinary Insulated/Bundled Cable item, not
        //something that ever required a pickaxe to drop).
        if (cable.hasCenter()) {
            FaceState center = cable.getCenter();
            popResource(level, pos, new ItemStack(itemFor(center.color(), center.bundled())));
            cable.removeCenter();
        }
        level.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(level.getBlockState(pos)));
        player.awardStat(Stats.BLOCK_MINED.get(BCBlocks.REDSTONE_CABLE.get()));
        cable.setHasFrame(false);
        //Bug fix: this used to also clear frameArms directly, right here - but recomputeFrameArms (called
        //below via refreshAndNotify) detects a frame-arm CHANGE by comparing its freshly computed result
        //against whatever's currently stored, and reports "changed" only when they differ. Clearing frameArms
        //here first meant that by the time recomputeFrameArms ran, both sides of that comparison were already
        //the same empty set - a REAL change (connected arms -> none) had just happened, but nothing was left
        //to detect it as one. That silently dropped this position out of refreshAndNotify's own
        //changedPositions set, which is the ONLY thing that triggers its closing neighbor-notification loop -
        //so a frame connected to this one (frame-to-frame is a plain direct-adjacency check, not the diagonal
        //Rule 3 wrap cable faces use, so no special wrap-notify is needed here) never got told anything
        //changed at all, and sat there with its own arm still extended toward a frame that no longer existed.
        //Leaving frameArms untouched here lets recomputeFrameArms see the real before/after difference itself.
        if (cable.hasAnyFace() || cable.hasFrame()) {
            refreshAndNotify(level, pos);
        } else {
            level.removeBlock(pos, false);
        }
    }

    //---- Center-mounted cable placement/removal (see the class comment for the full feature) ----

    //Mirrors addFace's own duplicate-slot rejection (not an overwrite) - requires a frame already present (a
    //center cable is "supported" by the frame, see removeFrame's own cascading removal above) and an empty
    //center. The plain-cable restriction is defensive, not load-bearing - BCEventHandlers/useItemOn's own
    //caller only ever invokes this for a RedstoneCableBlockItem whose color is non-null or whose bundled flag
    //is true, but this makes that invariant self-enforcing rather than trusting every future caller to get it
    //right.
    public static boolean addCenter(Level level, BlockPos pos, @Nullable DyeColor color, boolean bundled) {
        if (color == null && !bundled) {
            return false;
        }
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null || !cable.hasFrame() || cable.hasCenter()) {
            return false;
        }
        cable.setCenter(FaceState.empty(color, bundled));
        refreshAndNotify(level, pos);
        return true;
    }

    //Mirrors breakFace's own pop/sound/stat pattern - the caller (breakFace's own dispatch, or
    //useWithoutItem/getCloneItemStack's own center-hit branches) already resolved that the hit landed on the
    //center cable specifically. Never removes the whole block itself - a center cable can't be the last thing
    //keeping the block alive, since it can only ever exist alongside a frame (see addCenter's own
    //requirement), and the frame's own presence already keeps the block alive regardless.
    public static void removeCenterCable(Level level, BlockPos pos, Player player) {
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null || !cable.hasCenter()) {
            return;
        }
        FaceState center = cable.getCenter();
        if (!player.getAbilities().instabuild) {
            popResource(level, pos, new ItemStack(itemFor(center.color(), center.bundled())));
        }
        level.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(level.getBlockState(pos)));
        player.awardStat(Stats.BLOCK_MINED.get(BCBlocks.REDSTONE_CABLE.get()));
        cable.removeCenter();
        refreshAndNotify(level, pos);
    }

    //Shared by breakFace and isFrameTargeted below: the same server-side raycast technique Player#pick itself
    //uses (eye position/look vector/reach), needed because neither the LeftClickBlock event's own hit info nor
    //a mid-mining getDestroyProgress call carries a precise enough hit point to resolve WHICH box (a specific
    //face, or the frame) is actually under the crosshair.
    private static BlockHitResult raycastAt(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 reachEnd = eye.add(look.scale(player.blockInteractionRange()));
        return level.clip(new ClipContext(eye, reachEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }

    //Aluminum Frame: whether the player is CURRENTLY aiming at the frame specifically, not a face - the single
    //shared routing decision used both by BCEventHandlers#onLeftClickCable (should this click even be
    //intercepted, or left alone to fall through to vanilla's own timed mining?) and by getDestroyProgress below
    //(which hardness/tool formula applies this tick). Same "face checked first, frame as fallback" resolution
    //breakFace/useWithoutItem already use.
    public static boolean isFrameTargeted(Level level, BlockPos pos, Player player) {
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null || !cable.hasFrame()) {
            return false;
        }
        BlockHitResult hit = raycastAt(level, player);
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) {
            return false;
        }
        if (findHitFace(cable, pos, hit.getLocation()) != null) {
            return false;
        }
        //A center-mounted cable's own removal stays the existing instant, hand-breakable behavior (matching
        //every other cable face) - it never gets real mining time the way the frame itself does, so a center
        //hit must NOT be reported as frame-targeted even though it sits inside the frame's own hollow middle.
        if (isCenterHit(cable, pos, hit.getLocation())) {
            return false;
        }
        return isFrameHit(cable, pos, hit.getLocation());
    }

    //Real mining time for Aluminum Frame, matching a normal tool-tiered block, while every OTHER click on this
    //shared block (any cable face) stays the existing instant, hand-breakable behavior - see the class comment
    //for why this can't just be a static per-block hardness/tool property the way Aluminum Bars' own
    //strength(5f, 6f)/requiresCorrectToolForDrops() works.
    //
    //Bug fix (two earlier attempts, both dead ends): letting a frame hit fall through to vanilla's REAL
    //timed-mining state machine (returning the genuine fractional progress here, same as vanilla's own default
    //formula) let the frame visually linger - or, worse, the whole block's texture drop - after it was already
    //removed server-side. Root cause, decompiled: MultiPlayerGameMode#destroyBlock (client-only)
    //unconditionally predicts a completed mine turns the WHOLE position to air, with no way to know in advance
    //the server won't actually do that; reconciling that guess back to RedstoneCableBlock recreates a BRAND
    //NEW, EMPTY block entity (LevelChunk#setBlockState deletes the old one the moment the optimistic air guess
    //first applies), racing unpredictably against whatever sync packet happens to be in flight. No fixed delay
    //reliably outran this, because the race is inherent to the CLIENT's own local prediction timing, not
    //server-side packet scheduling - two rounds of "resync a bit later" attempts both failed for exactly that
    //reason.
    //
    //The actual fix: never let vanilla's own "am I done" check succeed at all. This always returns 0.0F for a
    //genuine frame hit (never the real fractional progress) - vanilla's own timed-mining loop (both client and
    //server independently run the identical accumulation) then perpetually sees "still in progress" from ITS
    //perspective and never runs its own destroy call, so there's nothing left to race. Real elapsed progress is
    //tracked by hand instead (FRAME_MINING below), completing via the exact same removeFrame() call the
    //instant-click path already uses for everything else.
    //
    //Bug fix, round 2: an earlier version of broadcastMiningProgress also sent the crack-overlay packet
    //directly to the MINING player (not just nearby onlookers), reasoning that Level#destroyBlockProgress's
    //own broadcast excludes the breaker. That caused a visible flicker WHILE mining: vanilla's own client-side
    //MultiPlayerGameMode#continueDestroyBlock calls the exact same underlying destroyBlockProgress EVERY
    //client tick too, using ITS OWN local accumulation - which, since this override used to always return 0,
    //never moved off stage 0. That local write and this method's own network-delivered packet were fighting
    //over the SAME breakerId+pos entry every tick: stage 0 (local, every tick) vs. the real stage (this
    //method's packet, whenever it happened to arrive) - alternating fast enough to flicker.
    //
    //Bug fix, round 3: simply dropping the direct-to-breaker packet (broadcast-only, matching vanilla's own
    //exclusion) fixed the flicker but left the MINING player with no crack overlay at all - a real regression,
    //not an acceptable trade-off, especially in singleplayer where the miner IS the only player around to see
    //anything. Fixed properly instead of dropped: VISUAL_PROGRESS (below) lets vanilla's own LOCAL
    //accumulation (continuingDestroyBlock's own, identical on both sides) animate NORMALLY and ACCURATELY off
    //this method's own real per-tick return value, right up until it gets within reach of 1.0 - at which point
    //this starts returning 0 instead, permanently capping that local sum just under 1.0 so vanilla's own
    //client-side "am I done" check still never succeeds (the entire point of this whole redesign - see the
    //comment above). The overlay plateaus at its highest crack stage for whatever's left of the real mining
    //duration (tracked separately and unaffected - see FRAME_MINING) instead of freezing at stage 0 for the
    //whole thing. Runs identically, independently, on BOTH sides (no cross-process state needed - this reads
    //nothing from FRAME_MINING, which stays purely server-authoritative for the actual completion decision),
    //so it works the same in real dedicated multiplayer as it does in this project's own singleplayer testing.
    private static final float VISUAL_PROGRESS_CAP = 0.9F;

    //Never actually invoked for a creative player - BCEventHandlers#onLeftClickCable routes creative frame
    //hits through the same immediate cancel-and-remove path a face hit uses, since creative bypasses
    //getDestroyProgress for its own instant-destroy decision regardless of what it returns anyway.
    //
    //FRAME_HARDNESS mirrors Aluminum Bars' own strength(5f, 6f) - "the aluminum set" already used for Frame's
    //own block properties before this became a shared block. correctTool mirrors this project's own instanceof
    //PickaxeItem convention (see BCEventHandlers#isDesignedFor) rather than a tag lookup.
    private static final float FRAME_HARDNESS = 5.0F;

    private record FrameMiningState(BlockPos pos, float progress) {}

    //Server-side only, keyed per player (a player can only ever be mining one thing at a time) - the real,
    //authoritative "how much progress has this player actually made" driving the real completion decision.
    //Populated and advanced here, cleared on completion (below) and by BCEventHandlers#onLeftClickCable's own
    //STOP/ABORT handling (released early, or aim moved off the frame before finishing).
    private static final Map<UUID, FrameMiningState> FRAME_MINING = new HashMap<>();

    //Runs on WHICHEVER side calls getDestroyProgress (both, independently - see VISUAL_PROGRESS_CAP's own
    //comment) - purely the capped mirror of the same progress, whose only job is answering "how much more can
    //I still report to vanilla's own local accumulator before hitting the cap". Cleared the same way
    //FRAME_MINING is.
    private static final Map<UUID, FrameMiningState> VISUAL_PROGRESS = new HashMap<>();

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        //Bug fix: this override runs on BOTH sides (vanilla's own timed-mining loop is independently duplicated
        //client and server - that's the whole reason the client-side prediction bug this class comment
        //describes exists in the first place). Returning 0 only had real effect on an `instanceof ServerLevel`
        //check here - CLIENT-side, level is a ClientLevel, so that check silently fell through to the OLD
        //return 1.0F below, instantly completing the CLIENT's own local accumulation on the very first tick
        //(worse than before: instant local air-prediction instead of a delayed one). The frame-vs-not
        //resolution (isFrameTargeted) needs to run identically on both sides - only the REAL completion
        //tracking below (FRAME_MINING) is server-only; the capped visual return value is computed on both.
        if (!(level instanceof Level realLevel) || !isFrameTargeted(realLevel, pos, player)) {
            return 1.0F;
        }
        boolean correctTool = player.getMainHandItem().getItem() instanceof PickaxeItem;
        int divisor = correctTool ? 30 : 100;
        float perTick = player.getDestroySpeed(state) / FRAME_HARDNESS / (float) divisor;
        UUID playerId = player.getUUID();
        if (realLevel instanceof ServerLevel serverLevel) {
            FrameMiningState previous = FRAME_MINING.get(playerId);
            float progress = (previous != null && previous.pos().equals(pos) ? previous.progress() : 0.0F) + perTick;
            if (progress >= 1.0F) {
                FRAME_MINING.remove(playerId);
                broadcastMiningProgress(serverLevel, player, pos, -1);
                removeFrame(serverLevel, pos, player);
            } else {
                FRAME_MINING.put(playerId, new FrameMiningState(pos, progress));
                broadcastMiningProgress(serverLevel, player, pos, (int) (progress * 10.0F));
            }
        }
        FrameMiningState previousVisual = VISUAL_PROGRESS.get(playerId);
        float visualProgress = previousVisual != null && previousVisual.pos().equals(pos) ? previousVisual.progress() : 0.0F;
        float delta = Math.max(0.0F, Math.min(perTick, VISUAL_PROGRESS_CAP - visualProgress));
        VISUAL_PROGRESS.put(playerId, new FrameMiningState(pos, visualProgress + delta));
        return delta;
    }

    //Level#destroyBlockProgress broadcasts to nearby players but (confirmed via ServerLevel's own source)
    //explicitly EXCLUDES the breaking player - who instead sees their own progress via vanilla's own client-
    //side local overlay, driven by getDestroyProgress's own (capped) return value - see VISUAL_PROGRESS_CAP's
    //own comment.
    private static void broadcastMiningProgress(ServerLevel level, Player player, BlockPos pos, int stage) {
        level.destroyBlockProgress(player.getId(), pos, stage);
    }

    //Called from BCEventHandlers#onLeftClickCable on STOP (mining actually completed - a no-op for
    //FRAME_MINING here, since getDestroyProgress's own completion branch already cleared that before this ever
    //fires) and ABORT (released early, or aim drifted off the frame) - either way, this player is no longer
    //actively mining a frame, so all of their tracked progress (real AND visual) needs resetting rather than
    //silently resuming on some later, unrelated attempt. Runs on both sides (matching how
    //PlayerInteractEvent.LeftClickBlock itself fires on both) - VISUAL_PROGRESS always clears, since it's
    //tracked on whichever side calls; FRAME_MINING (and the clear-overlay broadcast) only apply server-side.
    public static void clearFrameMining(Level level, Player player) {
        VISUAL_PROGRESS.remove(player.getUUID());
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        FrameMiningState state = FRAME_MINING.remove(player.getUUID());
        if (state != null) {
            broadcastMiningProgress(serverLevel, player, state.pos(), -1);
        }
    }

    //Bug fix: left-click breaking a cable used to always destroy the WHOLE block - every face, every color, at
    //every mounted direction - since vanilla has no concept of "partially break a block", it just removes
    //whatever's at a position outright. Since this block is instabreak (see BCBlocks.REDSTONE_CABLE's own
    //properties), a single click IS the entire break interaction, so BCEventHandlers#onLeftClickCable cancels
    //that click (PlayerInteractEvent.LeftClickBlock - confirmed via ServerPlayerGameMode's own bytecode to fire
    //and get checked BEFORE destroyBlock ever runs) and calls this instead, by-hand replicating just the parts
    //of vanilla's own destroy pipeline that still apply for ONE face: drop, the standard break sound+particle
    //level event, and the mined-block stat. No tool-durability handling is needed - Item.mineBlock's own default
    //only damages a tool when getDestroySpeed() != 0, which is never true for an instabreak block anyway (see
    //BCEventHandlers#onCropDrops's own comment for the same fact about crops).
    //
    //Round 2: the break packet's own Direction field turned out NOT to be the "which face" identifier it looked
    //like (the same assumption useWithoutItem's own right-click removal makes) - logging confirmed it's the
    //outward normal of whichever box surface the ray actually hit, which for a face's own PAD is the OPPOSITE of
    //that face's towardSupport (looking at a floor cable's visible top surface reports UP, not DOWN), while an
    //ARM's own end-cap face reports a THIRD, unrelated direction again - there's no single fixed relationship
    //that works for every box a click could land on. Rather than guess, this performs its OWN server-side
    //raycast (the same ClipContext.Block.OUTLINE technique Player#pick itself uses) using the player's current
    //eye position/look vector/reach - all already available and trustworthy server-side, no client packet
    //needed - and reuses findHitFace's own precise per-box hit-point matching (the same one getCloneItemStack
    //uses for pick-block) to identify the SPECIFIC face actually under the crosshair.
    //Aluminum Frame: since it shares this exact same left-click interception (BCEventHandlers#onLeftClickCable
    //routes ANY click on a RedstoneCableBlock here, unconditionally), this resolves whether the hit landed on a
    //face or on the frame's own geometry using the same raycast - faces are checked first, frame checked only
    //as a fallback if no face box contains the hit point (see isFrameHit's own comment for why that ordering
    //essentially never matters in practice).
    public static void breakFace(Level level, BlockPos pos, Player player) {
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return;
        }
        BlockHitResult hit = raycastAt(level, player);
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) {
            return;
        }
        Direction towardSupport = findHitFace(cable, pos, hit.getLocation());
        if (towardSupport == null) {
            if (isCenterHit(cable, pos, hit.getLocation())) {
                removeCenterCable(level, pos, player);
            } else if (isFrameHit(cable, pos, hit.getLocation())) {
                removeFrame(level, pos, player);
            }
            return;
        }
        FaceState face = cable.getFace(towardSupport);
        if (face == null) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            popResource(level, pos, new ItemStack(itemFor(face.color(), face.bundled())));
        }
        //null, not player: Level#levelEvent(Player, ...) EXCLUDES that player from receiving the event - the
        //normal vanilla assumption being that the acting player's own client already played the effect locally
        //via its own mining-completion prediction. Since this whole pipeline bypasses that prediction entirely
        //(the click was canceled before vanilla's own client-side flow could get that far), passing the actual
        //player here silently skipped the ONE person actually breaking it - null broadcasts to everyone in
        //range, breaker included, which is what actually produced the missing break sound (and matches the
        //identical player-exclusion bug fixed the same way for the place sound - see RedstoneCableBlockItem's
        //own comment).
        level.levelEvent(null, LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(level.getBlockState(pos)));
        player.awardStat(Stats.BLOCK_MINED.get(BCBlocks.REDSTONE_CABLE.get()));
        removeFace(level, pos, towardSupport);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return true;
        }
        //A frame never needs support (the user's own spec: "can be placed anywhere") - present means survives,
        //full stop, regardless of what's happening with any of this position's own cable faces.
        if (cable.hasFrame()) {
            return true;
        }
        for (Direction towardSupport : cable.activeFaces()) {
            BlockPos support = pos.relative(towardSupport);
            if (canSurviveOn(level, support, level.getBlockState(support), towardSupport)) {
                return true;
            }
        }
        return false;
    }

    //Public: RedstoneCableBlockItem needs this to reject an unsupported target BEFORE placing anything (see its
    //own place() override's comment) - canSurvive() below can't do that check itself, since for a brand new
    //placement there's no block entity yet to hold the face that would need checking.
    public static boolean canSurviveOn(BlockGetter level, BlockPos pos, BlockState state, Direction towardSupport) {
        boolean sturdy = state.isFaceSturdy(level, pos, towardSupport.getOpposite());
        //Hopper support only made sense for vanilla dust's own floor-only case (its flat top isn't otherwise
        //sturdy) - kept for the "bottom face resting on top of something" case specifically, matching Phase 1/2.
        return sturdy || (towardSupport == Direction.DOWN && state.is(Blocks.HOPPER));
    }

    //---- Right-click add/remove-face UX (the item handles placing a brand new block; this handles adding a face
    //to an EXISTING cable block, and empty-hand right-click removing one) ----

    //Item -> DyeColor for every item that counts as "a colored carpet" for insulateFace below - matches
    //BCRecipeProvider's own insulatedCableRecipes ingredient set exactly (vanilla wool carpet), just built
    //once here for a fast reverse lookup at interaction time instead of re-deriving it per click. Built
    //lazily (not a static final field initialized at class-load) so this doesn't force BCItems' own static
    //init to run before it's actually needed.
    @Nullable
    private static Map<Item, DyeColor> carpetOrRugColors;

    private static DyeColor colorOfCarpetOrRug(Item item) {
        if (carpetOrRugColors == null) {
            Map<Item, DyeColor> map = new HashMap<>();
            for (DyeColor color : DyeColor.values()) {
                Item carpet = BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color.getName() + "_carpet"));
                map.put(carpet, color);
            }
            carpetOrRugColors = map;
        }
        return carpetOrRugColors.get(item);
    }

    //New feature: right-clicking a BARE (uncolored) cable face with a colored carpet or rug converts just that
    //one face into Insulated Redstone Cable of the matching color, in place - the same 1-carpet-per-face ratio
    //BCRecipeProvider's own crafting recipe uses, just applied directly in the world instead of at a crafting
    //table. Resets the face to FaceState.empty(color) (same as a fresh addFace) rather than just overwriting its
    //color in place, since its OLD connections were computed under "plain, connects to anything" rules that no
    //longer hold once it's colored - a currently-connected differently-colored neighbor needs to actually
    //disconnect, not just keep looking connected with stale data. refreshAndNotify (called via the reset) fully
    //recomputes this face AND notifies neighbors, so any neighbor that needs to drop ITS OWN connection to this
    //now-recolored face gets a chance to recompute too, the same as any other color-compatibility change.
    private static ItemInteractionResult insulateFace(Level level, BlockPos pos, BlockHitResult hitResult, DyeColor color, ItemStack stack, Player player) {
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        Direction towardSupport = findHitFace(cable, pos, hitResult.getLocation());
        if (towardSupport == null) {
            towardSupport = hitResult.getDirection();
        }
        FaceState face = cable.getFace(towardSupport);
        if (face == null || face.color() != null || face.bundled()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        cable.setFace(towardSupport, FaceState.empty(color, false));
        refreshAndNotify(level, pos);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        SoundType soundType = level.getBlockState(pos).getSoundType();
        level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        DyeColor carpetColor = colorOfCarpetOrRug(stack.getItem());
        if (carpetColor != null) {
            return insulateFace(level, pos, hitResult, carpetColor, stack, player);
        }
        //Aluminum Frame: checked BEFORE the generic cable-item branch below, since AluminumFrameBlockItem's own
        //getBlock() also returns this same shared RedstoneCableBlock (see that class's own comment) and would
        //otherwise match the generic "any cable-placing BlockItem" check too, adding a plain face instead of a
        //frame. Directly clicking an existing cable/frame's own geometry here covers the "aim at the block
        //itself" gesture, same as RedstoneCableBlockItem's own place() covers "aim at a neighboring support" for
        //the very first placement - AluminumFrameBlockItem#place covers that other gesture for a frame the same
        //way.
        if (stack.getItem() instanceof AluminumFrameBlockItem) {
            if (level.isClientSide) {
                return ItemInteractionResult.sidedSuccess(true);
            }
            if (!addFrame(level, pos)) {
                return ItemInteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            SoundType frameSoundType = level.getBlockState(pos).getSoundType();
            level.playSound(null, pos, frameSoundType.getPlaceSound(), SoundSource.BLOCKS,
                    (frameSoundType.getVolume() + 1.0F) / 2.0F, frameSoundType.getPitch() * 0.8F);
            return ItemInteractionResult.sidedSuccess(false);
        }
        //Center-mounted cable: checked BEFORE the generic "any cable-placing BlockItem adds a face" branch
        //below, same reasoning as the frame branch above - only Insulated/Bundled Cable items (never plain
        //Redstone Cable, per the user's own spec) qualify, and only when the precise hit (reusing
        //findHitFace/isFrameHit against the hit's own real location, not the coarser hitResult.getDirection()
        //the generic branch below uses) resolves to the frame's own geometry specifically, not a face - i.e.
        //the player aimed at the hollow middle of the frame, not one of its wall-mounted faces.
        if (stack.getItem() instanceof RedstoneCableBlockItem centerCableItem
                && (centerCableItem.getColor() != null || centerCableItem.isBundled())) {
            RedstoneCableBlockEntity centerTarget = getCableAt(level, pos);
            if (centerTarget != null && centerTarget.hasFrame() && !centerTarget.hasCenter()
                    && findHitFace(centerTarget, pos, hitResult.getLocation()) == null
                    && isFrameHit(centerTarget, pos, hitResult.getLocation())) {
                if (level.isClientSide) {
                    return ItemInteractionResult.sidedSuccess(true);
                }
                if (!addCenter(level, pos, centerCableItem.getColor(), centerCableItem.isBundled())) {
                    return ItemInteractionResult.FAIL;
                }
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                SoundType centerSoundType = level.getBlockState(pos).getSoundType();
                level.playSound(null, pos, centerSoundType.getPlaceSound(), SoundSource.BLOCKS,
                        (centerSoundType.getVolume() + 1.0F) / 2.0F, centerSoundType.getPitch() * 0.8F);
                return ItemInteractionResult.sidedSuccess(false);
            }
        }
        if (!(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof RedstoneCableBlock)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        Direction towardSupport = hitResult.getDirection();
        DyeColor color = blockItem instanceof RedstoneCableBlockItem cableItem ? cableItem.getColor() : null;
        boolean bundled = blockItem instanceof RedstoneCableBlockItem cableItem && cableItem.isBundled();
        if (!addFace(level, pos, towardSupport, color, bundled)) {
            return ItemInteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        //Directly clicking an existing cable's own geometry never goes through BlockItem's own placement flow
        //(and therefore never gets its built-in placement sound) the way a fresh block placement does - added
        //explicitly here so this path sounds the same as placing the very first face at a position.
        //
        //null, not player: Level#playSound(Player, ...) EXCLUDES that player from hearing it - see
        //RedstoneCableBlockItem#place's own comment on the identical bug for the other add-face path.
        SoundType soundType = level.getBlockState(pos).getSoundType();
        level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        return ItemInteractionResult.sidedSuccess(false);
    }

    //Right-clicking empty-handed removes whichever face - or, per the same findHitFace-then-isFrameHit fallback
    //breakFace itself now uses, the frame - was actually clicked, matching how the block otherwise has no other
    //empty-hand interaction (Phase 2's dot/cross toggle doesn't apply here - see class comment, there's no more
    //"climbing" state to toggle).
    //
    //Bug fix: this used to resolve the target via the raw hitResult.getDirection() - the same imprecise
    //"outward normal of whichever box surface the ray hit" value breakFace's own comment already documented as
    //unusable for identifying a SPECIFIC face (let alone the frame, which has no towardSupport/direction concept
    //at all). Switched to the same precise findHitFace/isFrameHit point-in-box resolution breakFace already
    //uses - unlike breakFace, this method already receives a proper BlockHitResult with a real world-space hit
    //location from vanilla's own interaction dispatch, so no extra raycast of its own is needed here.
    //
    //Vanilla calls useWithoutItem whenever useItemOn passed through with PASS_TO_DEFAULT_BLOCK_INTERACTION,
    //REGARDLESS of what's actually held (see ServerPlayerGameMode#useItemOn) - not only when the hand is
    //literally empty. useItemOn already passes through for anything that isn't a cable-placing item (see its own
    //comment), so without this guard, right-clicking the cable with an unrelated block (stone, dirt, ...) in
    //hand would silently remove a face here AND report the interaction as consumed - which stops the dispatcher
    //from ever reaching that item's own placement logic, i.e. you could never place a block against the cable
    //without sneaking to bypass this method entirely. Only actually remove something - and only then report the
    //interaction as consumed - when the main hand is genuinely empty.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty() || !player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            RedstoneCableBlockEntity cable = getCableAt(level, pos);
            if (cable != null) {
                Direction towardSupport = findHitFace(cable, pos, hitResult.getLocation());
                if (towardSupport != null) {
                    removeFace(level, pos, towardSupport);
                } else if (isCenterHit(cable, pos, hitResult.getLocation())) {
                    removeCenterCable(level, pos, player);
                } else if (isFrameHit(cable, pos, hitResult.getLocation())) {
                    removeFrame(level, pos, player);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    //---- Connection + power computation (the 3 rules from the class comment) ----

    //Bug fix: a single block position can host up to 6 independent faces, each physically occupying a
    //different side of the block (see the class comment) - but vanilla's own SignalGetter#getBestNeighborSignal
    //checks ALL 6 neighbors of a position indiscriminately, with no concept of "which face". Using it directly
    //here meant a face mounted to the CEILING would still pick up a repeater/button/lever/pressure plate's
    //signal sitting on the FLOOR side of the exact same block position, since both only ever check "this
    //position", never "this specific face" - a signal could "jump" between completely unrelated faces that
    //happen to share a block position, purely because vanilla's own directional signal methods were designed
    //around one wire occupying a whole position (dust), not several independent faces sharing one. Scoped down
    //to just the support block itself, mirroring vanilla dust's own "power from the block I'm resting against"
    //check - the in-plane neighbors (the other direction this face's own territory extends into) are handled
    //directly inside computeFace's own loop instead (see its own comment on why dust specifically needs
    //different treatment there), not here.
    private static int getSupportDirectSignal(Level level, BlockPos pos, Direction towardSupport) {
        return level.getSignal(pos.relative(towardSupport), towardSupport);
    }

    //Bug fix, round 2 (see getFaceDirectSignal's own comment for round 1): checking only towardSupport's own
    //axis wasn't enough either. For a floor/ceiling face that axis IS the height axis, so it happened to catch
    //the original repeater-through-a-ceiling-face case - but for a WALL face, towardSupport's axis is
    //horizontal, and a floor-standing repeater's shape spans the FULL horizontal footprint of its own block
    //(it's a full 16x16 slab, just thin in Y), so it trivially "reaches" any horizontal extreme regardless of
    //height - the axis that actually needed checking (Y, where the wall face's own pad sits vertically
    //centered rather than at floor level) was never examined at all. Generalizes correctly by checking BOTH
    //axes other than d's own (the direction being stepped in to reach the neighbor - that axis doesn't need
    //checking, it's what "in-plane" already means): towardSupport's own axis, same as before, PLUS whichever
    //axis is left over, checked against this face's own default pad range (6-10, i.e. the centered hub every
    //face's own footprint sits in before any arm extends it - see faceBounds) rather than the extreme
    //threshold, since that's genuinely where this face's own geometry sits along that axis.
    private static boolean touchesFace(BlockGetter level, BlockPos pos, Direction towardSupport, Direction d) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        if (!touchesExtreme(shape, towardSupport)) {
            return false;
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == d.getAxis() || axis == towardSupport.getAxis()) {
                continue;
            }
            if (shape.max(axis) < 6.0 / 16.0 || shape.min(axis) > 10.0 / 16.0) {
                return false;
            }
        }
        return true;
    }

    private static boolean touchesExtreme(VoxelShape shape, Direction towardSupport) {
        Direction.Axis axis = towardSupport.getAxis();
        double threshold = 4.0 / 16.0;
        return towardSupport.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? shape.max(axis) >= 1.0 - threshold
                : shape.min(axis) <= threshold;
    }

    //Insulated Redstone Cable: two cable faces are only allowed to connect (share power, draw a visible arm
    //between them) when their colors are compatible - a plain (null) face connects to anything, but two
    //different colors never do, matching the user's own spec ("won't connect to or power each other, but they
    //all occupy the same block space"). A generic redstone component (dust, repeater, lever, ...) isn't a cable
    //at all, so it has no color of its own to check against - every cable, any color, connects to those exactly
    //like a plain cable always has.
    private static boolean colorsCompatible(@Nullable DyeColor a, @Nullable DyeColor b) {
        return a == null || b == null || a == b;
    }

    //Bundled Redstone Cable: connects to itself and to Insulated Redstone Cable of ANY color - never to plain
    //(uncolored) Redstone Cable, and never to a generic redstone component (that's handled separately below by
    //gating redstoneComponent itself off entirely for a bundled self face, since a component has no FaceState of
    //its own to test here). Falls through to the ordinary colorsCompatible rule when NEITHER side is bundled, so
    //existing plain/insulated behavior is completely unchanged.
    private static boolean cablesCompatible(@Nullable DyeColor selfColor, boolean selfBundled, @Nullable DyeColor otherColor, boolean otherBundled) {
        if (selfBundled || otherBundled) {
            if (selfBundled && otherBundled) {
                return true;
            }
            return selfBundled ? otherColor != null : selfColor != null;
        }
        return colorsCompatible(selfColor, otherColor);
    }

    //True only for a genuine insulated-to-insulated hop (both ends the SAME non-null color) - the boundary
    //computeFace uses to keep a node's own basePower completely independent of any same-color neighbor's power
    //(see computeFace's own comment, and propagateNetwork below for why that independence is required).
    private static boolean sameInsulatedColor(@Nullable DyeColor a, @Nullable DyeColor b) {
        return a != null && a == b;
    }

    //Returns this face's own "base" power: everything it would read from real, always-freshly-queried sources -
    //direct components (repeaters, levers, redstone blocks, ...) and dust/plain-cable/different-color-cable via
    //the ordinary decaying wireMax - but NEVER anything contributed by a same-exact-color insulated neighbor
    //(see sameInsulatedColor's own filtering below). For a plain (uncolored) face this is simply its full,
    //final power, unchanged from before Insulated Redstone Cable existed. For an insulated face it's only an
    //ingredient: propagateNetwork floods the whole connected same-color island and overwrites every
    //member's stored power with the STRONGEST basePower found anywhere in it (see that method's own comment for
    //why network-wide flooding, not this per-node value alone, is what the island actually gets persisted as).
    private static FaceState computeFace(Level level, BlockPos pos, Direction towardSupport) {
        BlockPos support = pos.relative(towardSupport);
        RedstoneCableBlockEntity self = getCableAt(level, pos);
        FaceState selfFace = self != null ? self.getFace(towardSupport) : null;
        DyeColor selfColor = selfFace != null ? selfFace.color() : null;
        boolean selfBundled = selfFace != null && selfFace.bundled();
        Set<Direction> connections = EnumSet.noneOf(Direction.class);
        int wireMax = 0;
        int inPlaneDirect = 0;
        for (Direction d : inPlaneDirections(towardSupport)) {
            FaceState sibling = self != null ? self.getFace(d) : null;
            if (sibling != null && !cablesCompatible(selfColor, selfBundled, sibling.color(), sibling.bundled())) {
                sibling = null;
            }
            RedstoneCableBlockEntity sameFaceNeighbor = getCableAt(level, pos.relative(d));
            FaceState sameFace = sameFaceNeighbor != null ? sameFaceNeighbor.getFace(towardSupport) : null;
            if (sameFace != null && !cablesCompatible(selfColor, selfBundled, sameFace.color(), sameFace.bundled())) {
                sameFace = null;
            }

            //Rule 2 only: connect (visually and for power) to a plain redstone component in the same plane too
            //(dust, a repeater, a comparator, a lever, ...), not just another cable - vanilla dust's own
            //equivalent (RedStoneWireBlock#shouldConnectTo) uses the same isSignalSource() check. Rules 1 and 3
            //stay cable-only: 1 is the same block's own other faces (nothing else could ever occupy that), and 3
            //is a diagonal relationship - redstone power itself never passes diagonally, only cable-to-cable
            //connections do, so a non-cable component two steps away isn't a candidate there at all. touchesFace
            //is the round-2 geometry fix - without it, a floor-standing repeater would visually connect to (and
            //draw a wire arm toward) a ceiling-mounted face in the next block over, even though their geometry
            //doesn't overlap at all.
            //Bundled Redstone Cable never connects to a generic redstone component (dust, repeater, lever, ...) -
            //see cablesCompatible's own comment for the full connectivity rule. A component has no FaceState to
            //run through that helper, so it's gated here directly instead.
            BlockPos componentPos = pos.relative(d);
            BlockState componentState = level.getBlockState(componentPos);
            boolean touchesComponent = sameFaceNeighbor == null && touchesFace(level, componentPos, towardSupport, d);
            boolean redstoneComponent = !selfBundled && touchesComponent && componentState.isSignalSource();

            //Rule 3 (shared-anchor, different face) connects two wires that are two steps apart diagonally (pos
            //and support.relative(d) differ by towardSupport+d), wrapping around the OUTSIDE corner of their
            //shared support block. pos.relative(d) - the same height as pos, one step over - is the actual open
            //corner cell a real wire would have to curl through to make that connection; if something solid
            //occupies it, the wire's path is physically blocked and the two sides shouldn't connect, even though
            //the direct position lookup below would otherwise still find an active face there.
            FaceState wrapped = null;
            if (level.getBlockState(pos.relative(d)).getCollisionShape(level, pos.relative(d)).isEmpty()) {
                RedstoneCableBlockEntity anchorNeighbor = getCableAt(level, support.relative(d));
                wrapped = anchorNeighbor != null ? anchorNeighbor.getFace(d.getOpposite()) : null;
                if (wrapped != null && !cablesCompatible(selfColor, selfBundled, wrapped.color(), wrapped.bundled())) {
                    wrapped = null;
                }
            }

            if (sibling != null || sameFace != null || wrapped != null || redstoneComponent) {
                connections.add(d);
            }
            //basePower deliberately EXCLUDES a same-exact-color neighbor's contribution entirely (not merely
            //decaying it) - connections above still include it (the wire visually/logically joins its neighbor
            //either way), but letting that neighbor's own cached power feed back in here, even decayed, is what
            //let power in a same-color loop keep re-confirming itself as "no change" forever once a real source
            //was removed: nothing was left in the loop that ever queried the real world instead of another
            //cable's own last-computed value. Excluding it outright means this face's basePower can only ever
            //come from something propagateNetwork's flood can't mistake for network-internal state -
            //genuine external sources, and dust/plain-cable/different-color-cable hops, all of which are
            //recomputed fresh from the live world every single call.
            if (sibling != null && !sameInsulatedColor(selfColor, sibling.color())) {
                wireMax = Math.max(wireMax, sibling.power());
            }
            if (sameFace != null && !sameInsulatedColor(selfColor, sameFace.color())) {
                wireMax = Math.max(wireMax, sameFace.power());
            }
            if (wrapped != null && !sameInsulatedColor(selfColor, wrapped.color())) {
                wireMax = Math.max(wireMax, wrapped.power());
            }
            //Bug fix: redstone wire is itself a decaying conductor, not an undying direct source - feeding its
            //current POWER straight into `direct` (never decremented) let a cable and an adjacent dust mutually
            //sustain each other forever once connected: the cable would mirror dust's power as its own
            //undecremented "direct" signal, dust would in turn read the cable's reported power back as ITS OWN
            //undecremented direct source (since our cable isn't a RedStoneWireBlock, vanilla dust has no reason
            //to decay what it reads from it either) - neither side ever decremented, so removing the real
            //source left them locked at whatever power they last had, until one was broken. Routing dust into
            //wireMax instead (same -1 decay every cable-to-cable hop already gets) matches how vanilla dust
            //treats an adjacent dust tile's own power, and lets the chain actually decay to 0 like everything
            //else. Every OTHER signal source (levers, buttons, repeaters, comparators, redstone blocks) keeps
            //the old undecremented behavior - those are genuine, non-decaying sources, not conductors.
            //
            //Bug fix: power itself uses touchesComponent (ANY touching neighbor), not redstoneComponent
            //(isSignalSource() only) - isSignalSource() is a per-BLOCK-TYPE flag ("this kind of block actively
            //produces a signal"), not "is this position currently powered". A plain block soft-powered by an
            //attached button/lever on one of ITS OTHER faces is never isSignalSource() (stone never is,
            //regardless of what's stuck to it) - so the old isSignalSource()-gated check could never see that
            //soft power at all, even though level.getSignal already correctly reports it via vanilla's own
            //shouldCheckWeakPower mechanism (the exact same mechanism the support-direction check already
            //relies on unconditionally). Connections stay gated by redstoneComponent/isSignalSource() alone,
            //unchanged - matches vanilla dust's own behavior of receiving soft power without drawing a visible
            //connecting arm toward the block providing it.
            if (touchesComponent && !selfBundled) {
                if (componentState.is(Blocks.REDSTONE_WIRE)) {
                    wireMax = Math.max(wireMax, componentState.getValue(RedStoneWireBlock.POWER));
                } else {
                    inPlaneDirect = Math.max(inPlaneDirect, level.getSignal(componentPos, d));
                }
            }
        }
        //Aluminum Frame's center-mounted cable (see the class comment): counts as one more sibling for POWER
        //purposes only - unlike every other sibling above, it never adds anything to this face's own visual
        //connections set (Direction can't represent "inward to the block's center", and the arm toward this
        //face is drawn entirely from the CENTER's own side - see RedstoneCableBakedModel's own center-arm
        //rendering).
        //
        //It DOES, however, still count as a real connection for the "complete the straight line" padding
        //below - the user's own spec: a face with only one real directional connection PLUS a center
        //connection is already a genuine two-way junction (wire in from one side, wire in from the center),
        //the same as a real corner already suppresses the padding, so it must NOT also pad out into a full
        //straight line. But the center connection alone never TRIGGERS that padding by itself (a face with
        //ONLY a center connection and no directional one has nothing to extend in the first place - there's
        //no axis to complete) - hasCenterConnection below is deliberately kept separate from
        //axisAConnected/axisBConnected rather than folded into either, so it can only ever suppress the pad,
        //never cause it.
        boolean hasCenterConnection = false;
        if (self != null) {
            FaceState center = self.getCenter();
            if (center != null && cablesCompatible(selfColor, selfBundled, center.color(), center.bundled())) {
                hasCenterConnection = true;
                if (!sameInsulatedColor(selfColor, center.color())) {
                    wireMax = Math.max(wireMax, center.power());
                }
            }
        }
        //Same "complete the straight line" rule vanilla dust's own getConnectionState uses: if this face has any
        //real connection at all, and the perpendicular axis-pair it's on (e.g. east/west) is the only one with a
        //connection - the OTHER pair (north/south) has none - then the missing direction of ITS OWN pair gets
        //added too, so a wire extending one way always runs the full length of the block instead of stopping at
        //the middle. A genuine turn (both axis-pairs already have a real connection) is left alone, matching how
        //dust doesn't extend a corner to full length either. This padding also feeds getSignal's own connection
        //check (see FaceState's own comment) - a known, accepted quirk where the padded arm can hard-power a
        //block it never actually touches, since there's no clean way to distinguish that from a genuine
        //connection once the block itself is queried by something else (a piston, dust, ...).
        Direction.Axis[] perpendicularAxes = Arrays.stream(Direction.Axis.values())
                .filter(axis -> axis != towardSupport.getAxis())
                .toArray(Direction.Axis[]::new);
        Direction axisAPositive = Direction.get(Direction.AxisDirection.POSITIVE, perpendicularAxes[0]);
        Direction axisANegative = axisAPositive.getOpposite();
        Direction axisBPositive = Direction.get(Direction.AxisDirection.POSITIVE, perpendicularAxes[1]);
        Direction axisBNegative = axisBPositive.getOpposite();
        boolean axisAConnected = connections.contains(axisAPositive) || connections.contains(axisANegative);
        boolean axisBConnected = connections.contains(axisBPositive) || connections.contains(axisBNegative);
        //A center connection acts as an already-present "second" connection here (see hasCenterConnection's
        //own comment above) - skips both padding branches below entirely, same as a genuine corner
        //(axisAConnected && axisBConnected) already does, without ever being one of the two connections a
        //branch itself checks for.
        if (!hasCenterConnection) {
            if (axisAConnected && !axisBConnected) {
                connections.add(axisAPositive);
                connections.add(axisANegative);
            }
            if (axisBConnected && !axisAConnected) {
                connections.add(axisBPositive);
                connections.add(axisBNegative);
            }
        }
        int direct = Math.max(inPlaneDirect, getSupportDirectSignal(level, pos, towardSupport));
        //Bundled Redstone Cable's own power (as opposed to its channels map) stays a flat 0 always - it's a
        //multi-channel relay, not a single-value conductor, and nothing outside a cable ever queries it directly
        //(see cablesCompatible's own comment: it never connects to a generic redstone component). Its real
        //payload - per-color strength - is computed separately by propagateNetwork below, not here; this
        //(computeFace) only ever supplies that flood's own per-node "local" ingredient, never the final channels.
        int power = selfBundled ? 0 : Math.max(direct, wireMax - 1);
        return new FaceState(connections, power, selfColor, selfBundled, Map.of());
    }

    //Aluminum Frame's center-mounted cable (see the class comment) - mirrors computeFace's own structure but
    //simplified: a center cable has no support block to be mounted against or wrap around, so only 2 of
    //computeFace's 3 rules apply, generalized from "4 in-plane directions" to all 6 (center has no
    //towardSupport axis to exclude any of them by):
    //  - SIBLING: this SAME position's own wall-mounted face in direction D (mirrors computeFace's own rule 1
    //    exactly - computeFace's own new center-sibling check above is the exact reverse of this one).
    //  - NEIGHBOR-CENTER: the block at pos+D has its OWN center cable - center-to-center only, never a
    //    neighbor's ordinary wall-mounted face even if it happens to be mounted facing back this way
    //    (confirmed with the user) - this is NOT computeFace's own rule 2 (which matches a neighbor's
    //    SAME-DIRECTION face), since a center cable has no "direction of its own" to match against.
    //No rule 3 (shared-anchor wrap) - that rule exists purely to route around the OUTSIDE corner of a shared
    //support block, which a center cable, having no support of its own, has no corner to route around. No
    //generic-redstone-component rule either, same as Bundled Cable's own exclusion - a center cable is always
    //Insulated or Bundled (see addCenter), never plain, so there's no case where selfColor/selfBundled would
    //ever both be "plain" here in the first place.
    private static FaceState computeCenterFace(Level level, BlockPos pos) {
        RedstoneCableBlockEntity self = getCableAt(level, pos);
        FaceState selfCenter = self != null ? self.getCenter() : null;
        DyeColor selfColor = selfCenter != null ? selfCenter.color() : null;
        boolean selfBundled = selfCenter != null && selfCenter.bundled();
        Set<Direction> connections = EnumSet.noneOf(Direction.class);
        int wireMax = 0;
        for (Direction d : Direction.values()) {
            FaceState sibling = self != null ? self.getFace(d) : null;
            if (sibling != null && !cablesCompatible(selfColor, selfBundled, sibling.color(), sibling.bundled())) {
                sibling = null;
            }
            RedstoneCableBlockEntity neighbor = getCableAt(level, pos.relative(d));
            FaceState neighborCenter = neighbor != null ? neighbor.getCenter() : null;
            if (neighborCenter != null && !cablesCompatible(selfColor, selfBundled, neighborCenter.color(), neighborCenter.bundled())) {
                neighborCenter = null;
            }
            if (sibling != null || neighborCenter != null) {
                connections.add(d);
            }
            if (sibling != null && !sameInsulatedColor(selfColor, sibling.color())) {
                wireMax = Math.max(wireMax, sibling.power());
            }
            if (neighborCenter != null && !sameInsulatedColor(selfColor, neighborCenter.color())) {
                wireMax = Math.max(wireMax, neighborCenter.power());
            }
        }
        int power = selfBundled ? 0 : Math.max(0, wireMax - 1);
        return new FaceState(connections, power, selfColor, selfBundled, Map.of());
    }

    //---- Insulated/Bundled Redstone Cable: network-wide (loop-safe) power propagation ----

    //towardSupport == null represents the center-mounted cable node at pos (see the class comment) rather
    //than a wall-mounted face - propagateNetwork's own flood needs to treat both kinds of node uniformly so a
    //chain like face -> (same block) center -> (adjacent block) center -> sibling face traverses as one
    //connected island, hence generalizing this from a plain Direction.
    private record FaceKey(BlockPos pos, @Nullable Direction towardSupport) {}

    @Nullable
    private static FaceState getFaceOrCenter(RedstoneCableBlockEntity cable, @Nullable Direction towardSupport) {
        return towardSupport == null ? cable.getCenter() : cable.getFace(towardSupport);
    }

    private static void setFaceOrCenter(RedstoneCableBlockEntity cable, @Nullable Direction towardSupport, FaceState state) {
        if (towardSupport == null) {
            cable.setCenter(state);
        } else {
            cable.setFace(towardSupport, state);
        }
    }

    //Same 3 connectivity rules computeFace itself uses (sibling / same-face-neighbor / wrapped shared-anchor),
    //but restricted to the edge set propagateNetwork floods across - a plain cable, a different insulated color,
    //or a generic redstone component all correctly act as hard boundaries rather than being swept into the
    //lossless island. Which edges are valid depends on what kind of node we're currently STANDING ON, not a
    //single fixed target color (needed now that Bundled Redstone Cable can relay several different colors
    //through the same physical line at once - see this method's own callers for the full picture):
    //  - standing on a BUNDLED face: continues onto any bundled OR any insulated (any color) neighbor - matches
    //    cablesCompatible's own "connects to itself and Insulated of any color" rule.
    //  - standing on an INSULATED face of color C: continues onto any bundled neighbor, OR an insulated neighbor
    //    of that SAME exact color C - never a different color, matching the existing insulated-network boundary.
    private static boolean continuesNetwork(@Nullable DyeColor nodeColor, boolean nodeBundled, FaceState candidate) {
        if (nodeBundled) {
            return candidate.bundled() || candidate.color() != null;
        }
        return candidate.bundled() || candidate.color() == nodeColor;
    }

    private static List<FaceKey> networkNeighbors(Level level, BlockPos pos, @Nullable Direction towardSupport, @Nullable DyeColor nodeColor, boolean nodeBundled) {
        List<FaceKey> neighbors = new ArrayList<>();
        RedstoneCableBlockEntity self = getCableAt(level, pos);
        if (self == null) {
            return neighbors;
        }
        //Aluminum Frame's center-mounted cable node (see the class comment) - the same 2 rules
        //computeCenterFace itself uses (sibling face / center-to-center neighbor only, no support to wrap
        //around), generalized to all 6 directions since center has no support axis to exclude any of them by.
        if (towardSupport == null) {
            for (Direction d : Direction.values()) {
                FaceState sibling = self.getFace(d);
                if (sibling != null && continuesNetwork(nodeColor, nodeBundled, sibling)) {
                    neighbors.add(new FaceKey(pos, d));
                }
                RedstoneCableBlockEntity neighbor = getCableAt(level, pos.relative(d));
                FaceState neighborCenter = neighbor != null ? neighbor.getCenter() : null;
                if (neighborCenter != null && continuesNetwork(nodeColor, nodeBundled, neighborCenter)) {
                    neighbors.add(new FaceKey(pos.relative(d), null));
                }
            }
            return neighbors;
        }
        BlockPos support = pos.relative(towardSupport);
        for (Direction d : inPlaneDirections(towardSupport)) {
            FaceState sibling = self.getFace(d);
            if (sibling != null && continuesNetwork(nodeColor, nodeBundled, sibling)) {
                neighbors.add(new FaceKey(pos, d));
            }
            RedstoneCableBlockEntity sameFaceNeighbor = getCableAt(level, pos.relative(d));
            FaceState sameFace = sameFaceNeighbor != null ? sameFaceNeighbor.getFace(towardSupport) : null;
            if (sameFace != null && continuesNetwork(nodeColor, nodeBundled, sameFace)) {
                neighbors.add(new FaceKey(pos.relative(d), towardSupport));
            }
            if (level.getBlockState(pos.relative(d)).getCollisionShape(level, pos.relative(d)).isEmpty()) {
                RedstoneCableBlockEntity anchorNeighbor = getCableAt(level, support.relative(d));
                FaceState wrapped = anchorNeighbor != null ? anchorNeighbor.getFace(d.getOpposite()) : null;
                if (wrapped != null && continuesNetwork(nodeColor, nodeBundled, wrapped)) {
                    neighbors.add(new FaceKey(support.relative(d), d.getOpposite()));
                }
            }
        }
        //Aluminum Frame's center-mounted cable: one more neighbor candidate alongside the 3 rules above - the
        //exact reverse of the center-node branch's own "sibling" rule (see computeFace's own matching
        //center-sibling check for the power-only equivalent this mirrors topologically here).
        FaceState center = self.getCenter();
        if (center != null && continuesNetwork(nodeColor, nodeBundled, center)) {
            neighbors.add(new FaceKey(pos, null));
        }
        return neighbors;
    }

    //Bug fix: floods the WHOLE connected island reachable from (startPos, startFace) - insulated segments of one
    //or more colors, relayed transparently through any Bundled Redstone Cable in between - in a SINGLE combined
    //pass, and overwrites every member with the single strongest basePower (see computeFace's own comment) found
    //anywhere in the island FOR ITS OWN COLOR, instead of letting each face incrementally relax against its
    //neighbor's own cached power/channels. Incremental relaxation is exactly what let ANY loop of same-network
    //cable - even just two faces on one block connecting to each other as siblings - get permanently stuck at a
    //stale nonzero value once the real source was removed: with no decay term left in a same-network hop, each
    //face kept seeing its neighbor's still-nonzero cached value and reporting "no change" forever. Flooding
    //sidesteps that entirely: basePower never reads another network member's power/channels (by construction,
    //see computeFace and this method's own writeback below), so the island's true per-color value is always
    //re-derived fresh from genuinely external sources on every single call, with nothing left for a cycle to
    //feed back into itself with - the exact same guarantee the original Insulated-only version of this method
    //had, now extended to a graph that can also pass through Bundled Cable.
    //
    //A bundled node is never itself a source - it only relays whatever color(s) reach it from real insulated
    //attachment points elsewhere in the SAME island, so it contributes nothing to colorMax (its own basePower is
    //always 0 - see computeFace) but still gets visited (to keep the flood going) and, at the end, has its own
    //channels map rebuilt from scratch to exactly the colors/values found THIS pass - not merged with whatever
    //it held before, so a color whose only source disappeared correctly vanishes from the map instead of
    //lingering. An insulated-C node gets its own power set to colorMax's C entry, unchanged from before.
    //
    //Returns every BlockPos whose stored FaceState actually changed, so the caller (refreshAndNotify) knows which
    //positions beyond its own need their neighbors notified too - a large island can span far more positions than
    //the one refreshAndNotify was originally called for.
    private static Set<BlockPos> propagateNetwork(Level level, BlockPos startPos, @Nullable Direction startFace) {
        RedstoneCableBlockEntity startCable = getCableAt(level, startPos);
        FaceState startState = startCable != null ? getFaceOrCenter(startCable, startFace) : null;
        if (startState == null || (startState.color() == null && !startState.bundled())) {
            return Set.of();
        }
        Set<FaceKey> visited = new HashSet<>();
        Deque<FaceKey> queue = new ArrayDeque<>();
        Map<FaceKey, FaceState> localStates = new HashMap<>();
        FaceKey startKey = new FaceKey(startPos, startFace);
        visited.add(startKey);
        queue.add(startKey);
        Map<DyeColor, Integer> colorMax = new EnumMap<>(DyeColor.class);
        List<FaceKey> insulatedNodes = new ArrayList<>();
        List<FaceKey> bundledNodes = new ArrayList<>();
        while (!queue.isEmpty()) {
            FaceKey key = queue.poll();
            FaceState local = key.towardSupport() == null
                    ? computeCenterFace(level, key.pos())
                    : computeFace(level, key.pos(), key.towardSupport());
            localStates.put(key, local);
            if (local.bundled()) {
                bundledNodes.add(key);
            } else if (local.color() != null) {
                insulatedNodes.add(key);
                colorMax.merge(local.color(), local.power(), Math::max);
            }
            for (FaceKey neighbor : networkNeighbors(level, key.pos(), key.towardSupport(), local.color(), local.bundled())) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        Set<BlockPos> changedPositions = new HashSet<>();
        for (FaceKey key : insulatedNodes) {
            FaceState local = localStates.get(key);
            int max = colorMax.getOrDefault(local.color(), 0);
            FaceState finalState = local.power() == max ? local : new FaceState(local.connections(), max, local.color(), false, Map.of());
            applyIfChanged(level, key, finalState, changedPositions);
        }
        Map<DyeColor, Integer> channelSnapshot = new EnumMap<>(DyeColor.class);
        for (Map.Entry<DyeColor, Integer> entry : colorMax.entrySet()) {
            if (entry.getValue() > 0) {
                channelSnapshot.put(entry.getKey(), entry.getValue());
            }
        }
        //local.channels() is always empty here (computeFace never populates it - see its own comment), so unlike
        //the insulated case above there's no meaningful "already equals" shortcut to take; always build fresh.
        //applyIfChanged still only writes/reports a real change by comparing against the ACTUAL cached FaceState,
        //not against local, so this never re-flags an already-converged bundled face as changed.
        for (FaceKey key : bundledNodes) {
            FaceState local = localStates.get(key);
            FaceState finalState = new FaceState(local.connections(), 0, null, true, channelSnapshot);
            applyIfChanged(level, key, finalState, changedPositions);
        }
        return changedPositions;
    }

    private static void applyIfChanged(Level level, FaceKey key, FaceState finalState, Set<BlockPos> changedPositions) {
        RedstoneCableBlockEntity cable = getCableAt(level, key.pos());
        if (cable == null) {
            return;
        }
        if (!finalState.equals(getFaceOrCenter(cable, key.towardSupport()))) {
            setFaceOrCenter(cable, key.towardSupport(), finalState);
            changedPositions.add(key.pos());
        }
    }

    //Recomputes every active face at pos (siblings are included automatically - they're all read straight out of
    //the same block entity) and, ONLY IF something actually changed, pushes updates to whatever might need to
    //know: direct 6-neighbors (covers rule 2 both directions via normal engine adjacency) and, explicitly, each
    //active face's shared-anchor positions (rule 3 isn't reachable through normal adjacency - S+d is two steps
    //from pos, not a direct neighbor, the same diagonal-notification gap Phase 2 needed
    //updateIndirectNeighbourShapes for). The change-gate matters: without it, two adjacent cables just keep
    //re-notifying each other forever even once both have stabilized, tripping the engine's chained-update cap
    //(same stabilization trick vanilla dust and Phase 2 both relied on).
    //
    //The inner recompute loop runs to a fixed point (repeats full passes until one makes no changes) rather than
    //just once, specifically for sibling (rule 1) power: a single pass processes faces in a fixed order (by
    //Direction ordinal), so if a face directly next to a redstone source is processed AFTER a sibling that
    //should draw power from it, that sibling would read the source's stale pre-update power and never pick up
    //the boost - nothing else ever re-triggers a recompute of this same position's own siblings afterward. Since
    //at most 6 faces exist and power only ever decays (wireMax - 1, bounded 0-15), this always converges in a
    //handful of iterations.
    //
    //shouldSignal is suppressed around both recompute passes below (the local fixed point AND the insulated
    //network flood), not the notification step after them. Getting this scope wrong was a real, previously-
    //shipped bug: the notification step (updateNeighborsAt / the explicit wrap-target neighborChanged calls)
    //synchronously triggers neighborChanged on this cable's own support block and beyond - and if the
    //suppression window still covered that call, anything re-querying THIS cable's power AS PART OF processing
    //that very notification (a piston deciding whether to extend, the support block re-evaluating its own
    //state, ...) would see shouldSignal == false and read 0 regardless of the cable's actual, already-settled
    //power. That produced exactly the "soft power works sometimes, not others, depending on which side" symptom
    //- it depended entirely on whether a given external query happened to land inside or outside the suppressed
    //window, not on anything about direction or geometry.
    //
    //Notification now covers every position touched by EITHER pass, not just pos - propagateNetwork
    //(see its own comment) can overwrite far-away members of the same insulated island, and each of those needs
    //its own neighbors told about the change exactly as pos itself would.
    //
    //Recomputes only pos's own PLAIN (uncolored, non-bundled) active faces to a local fixed point - insulated AND
    //bundled ones are always skipped (see refreshAndNotify's own comment on why persisting their local
    //computeFace result here caused the update-storm bug - bundled's own channels map is exactly as vulnerable
    //to that as insulated's power was, so it gets the same exclusion). Returns whether anything actually changed.
    private static boolean recomputePlainFaces(Level level, BlockPos pos, RedstoneCableBlockEntity cable) {
        boolean anyChanged = false;
        boolean changedThisPass;
        do {
            changedThisPass = false;
            for (Direction towardSupport : Set.copyOf(cable.activeFaces())) {
                FaceState current = cable.getFace(towardSupport);
                if (current != null && (current.color() != null || current.bundled())) {
                    continue;
                }
                FaceState recomputed = computeFace(level, pos, towardSupport);
                if (!recomputed.equals(current)) {
                    cable.setFace(towardSupport, recomputed);
                    changedThisPass = true;
                    anyChanged = true;
                }
            }
        } while (changedThisPass);
        return anyChanged;
    }

    //Aluminum Frame connectivity: a plain direct-neighbor check, unlike cable faces' own 3-rule computeFace -
    //"Frames connect to adjacent frames" per the user's own spec, no diagonal/shared-anchor relationships at
    //all, so this only ever needs to look at the 6 positions immediately touching pos, not pos's own support
    //block or any wrap-around corner. Returns whether the arm set actually changed, same contract as
    //recomputePlainFaces above, so refreshAndNotify's own change-tracking treats it identically.
    private static boolean recomputeFrameArms(Level level, BlockPos pos, RedstoneCableBlockEntity cable) {
        if (!cable.hasFrame()) {
            if (cable.frameArms().isEmpty()) {
                return false;
            }
            cable.setFrameArms(Set.of());
            return true;
        }
        Set<Direction> arms = EnumSet.noneOf(Direction.class);
        FaceState center = cable.getCenter();
        for (Direction direction : Direction.values()) {
            RedstoneCableBlockEntity neighbor = getCableAt(level, pos.relative(direction));
            boolean frameToFrame = neighbor != null && neighbor.hasFrame();
            //A center cable's own arm extends both a cable arm AND a frame arm toward whatever it connects
            //to, per the user's own spec - so a frame arm activates here too, regardless of whether the
            //neighbor in that direction even has a frame of its own.
            boolean centerConnected = center != null && center.connections().contains(direction);
            if (frameToFrame || centerConnected) {
                arms.add(direction);
            }
        }
        if (arms.equals(cable.frameArms())) {
            return false;
        }
        cable.setFrameArms(arms);
        return true;
    }

    private static void refreshAndNotify(Level level, BlockPos pos) {
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return;
        }
        Set<BlockPos> changedPositions = new HashSet<>();
        shouldSignal = false;
        //Bug fix: an insulated face's own computeFace result is only a "local" candidate (see its own comment) -
        //it deliberately ignores same-color neighbors, so it's essentially never equal to the island's actual,
        //network-wide converged value already stored here. Persisting it in this loop (the way a plain face's
        //result always can be, since a plain face's computeFace result IS its final value) meant every single
        //refreshAndNotify on ANY insulated face briefly overwrote the correct power with the wrong local one,
        //got flagged as "changed", and fired a fresh round of notifications - which triggered more
        //refreshAndNotify calls elsewhere, which did the exact same thing again, forever. That was the "breaking
        //one cable makes the whole server grind to a halt" report: an infinite notification storm, visible
        //in-game as vanilla's own "Too many chained neighbor updates" spam and a server stuck thousands of ticks
        //behind. Insulated AND bundled faces are written EXCLUSIVELY by propagateNetwork below now - it already
        //recomputes connections AND power/channels together per node (see its own comment), so skipping them
        //here loses nothing.
        //
        //Run once before AND once after the network pass: a plain face can read an insulated sibling's power
        //(via the ordinary decaying wireMax - see computeFace), and an insulated face's own basePower can
        //likewise read a plain sibling's - each direction needs the OTHER type already settled to pick up a
        //same-block cross-dependency within this one call instead of lagging a full extra notification
        //round-trip before it catches up.
        if (recomputePlainFaces(level, pos, cable)) {
            changedPositions.add(pos);
        }
        for (Direction towardSupport : Set.copyOf(cable.activeFaces())) {
            FaceState face = cable.getFace(towardSupport);
            if (face != null && (face.color() != null || face.bundled())) {
                changedPositions.addAll(propagateNetwork(level, pos, towardSupport));
            }
        }
        //Center cables are always Insulated/Bundled (plain Redstone Cable is never allowed in the center),
        //so - same reasoning as the loop above - they're always handled by the network flood, never by
        //recomputePlainFaces.
        if (cable.hasCenter()) {
            changedPositions.addAll(propagateNetwork(level, pos, null));
        }
        if (recomputePlainFaces(level, pos, cable)) {
            changedPositions.add(pos);
        }
        shouldSignal = true;
        //Aluminum Frame: outside the shouldSignal window on purpose - frame connectivity carries no redstone
        //power of its own, nothing here ever touches getSignal/getDirectSignal, so none of the suppression
        //reasoning above applies to it.
        if (recomputeFrameArms(level, pos, cable)) {
            changedPositions.add(pos);
        }
        if (changedPositions.isEmpty()) {
            return;
        }
        Block self = BCBlocks.REDSTONE_CABLE.get();
        for (BlockPos changedPos : changedPositions) {
            RedstoneCableBlockEntity changedCable = getCableAt(level, changedPos);
            if (changedCable == null) {
                continue;
            }
            //Same 2-hop pattern vanilla dust's own updatePowerStrength uses: notify not just this position's own
            //6 neighbors, but call updateNeighborsAt on EACH of those neighbors too (including the support
            //block). A single hop leaves plain, non-reactive blocks (a support block has no custom
            //neighborChanged of its own) with no way to pass the change along to THEIR other neighbors - a
            //piston sitting on a different face of the same support block would otherwise never get told to
            //re-check itself at all, regardless of what getSignal would have correctly reported if only
            //something had bothered to ask again.
            level.updateNeighborsAt(changedPos, self);
            for (Direction direction : Direction.values()) {
                level.updateNeighborsAt(changedPos.relative(direction), self);
            }
            for (Direction towardSupport : changedCable.activeFaces()) {
                notifyWrapNeighbors(level, changedPos, towardSupport);
            }
        }
    }

    //When every active face loses its support at once, the whole block is destroyed via Level#destroyBlock
    //(dropBlock=true), which drops through the normal Block.dropResources -> getDrops path - reading the block
    //entity's still-fully-intact face count for the correct total. When only SOME faces lose support, the block
    //survives, but the normal "drop on destroy" path never fires (the block isn't being destroyed) - so each
    //individually-lost face needs its own manual drop here, one item per face, matching how a full destroy would
    //have dropped one per face anyway (see getDrops's own comment).
    //
    //Support loss is only PART of what this needs to handle, though - neighborChanged fires for any change
    //nearby (a new adjacent cable placed, an existing one updating, redstone power changing, ...), and
    //refreshAndNotify (recomputing this cable's own connections) needs to run unconditionally whenever the block
    //survives, not only when support was actually lost - an early return here for the "nothing lost" case would
    //mean a freshly-placed cable never re-checks for a neighbor that appeared after it, only picking up the
    //connection later if THAT neighbor happens to independently trigger another update of its own.
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) {
            return;
        }
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return;
        }
        //Defensive: this block is only ever supposed to exist with at least one active face or a frame (see
        //removeFace's own comment) - an empty instance is corrupt (e.g. a leftover from the addFace-placed-
        //before-validated ordering bug this session fixed) and would otherwise sit invisible and hitbox-less
        //forever, since its own getShape() has nothing to union together. Clean it up the instant anything
        //nearby changes rather than leaving it permanently stuck.
        if (!cable.hasAnyFace() && !cable.hasFrame()) {
            level.removeBlock(pos, false);
            return;
        }
        Set<Direction> activeFaces = Set.copyOf(cable.activeFaces());
        Set<Direction> lost = EnumSet.noneOf(Direction.class);
        for (Direction towardSupport : activeFaces) {
            BlockPos support = pos.relative(towardSupport);
            if (!canSurviveOn(level, support, level.getBlockState(support), towardSupport)) {
                lost.add(towardSupport);
            }
        }
        //A frame present keeps the whole block alive even if every cable face just lost its own support - it
        //never needed any support of its own (see canSurvive's own comment).
        if (!lost.isEmpty() && lost.size() == activeFaces.size() && !cable.hasFrame()) {
            //Same wrap-notification bug fix as removeFace's own else-branch (see notifyWrapNeighbors' own
            //comment) - every face here is about to disappear at once, so each one's own diagonal wrap
            //partner (if any) needs telling before the block itself goes away.
            for (Direction towardSupport : lost) {
                notifyWrapNeighbors(level, pos, towardSupport);
            }
            level.destroyBlock(pos, true);
            return;
        }
        for (Direction towardSupport : lost) {
            FaceState face = cable.getFace(towardSupport);
            popResource(level, pos, new ItemStack(itemFor(face != null ? face.color() : null, face != null && face.bundled())));
            cable.removeFace(towardSupport);
        }
        refreshAndNotify(level, pos);
    }

    //Maps a face's own color/bundled marker back to the item that placed it - null+false (plain Redstone Cable),
    //one of the 16 Insulated Redstone Cable colors, or true (Bundled Redstone Cable). Used wherever a specific
    //face needs to drop/report the item it actually IS, rather than always assuming plain (see this method's own
    //callers for the bugs that produced when this was hardcoded to BCItems.REDSTONE_CABLE_ITEM regardless of the
    //face's real color).
    private static Item itemFor(@Nullable DyeColor color, boolean bundled) {
        if (bundled) {
            return BCItems.BUNDLED_CABLE_ITEM.get();
        }
        return color == null ? BCItems.REDSTONE_CABLE_ITEM.get() : BCItems.INSULATED_CABLE_ITEMS.get(color).get();
    }

    //Bug fix: pick-block (middle-click). This is a NeoForge extension default (IBlockExtension), not a vanilla
    //Block method - its own default implementation just returns `new ItemStack(state.getBlock().asItem())`,
    //which resolves via Item.byBlock(Block) - a single global Block->Item map that EVERY RedstoneCableBlockItem
    //constructor call (all 17 of them - plain plus 16 colors, all wrapping this same shared Block, see that
    //class's own comment) overwrites with itself. Whichever one is constructed LAST wins that map permanently -
    //DyeColor.values() ends on BLACK, so BCItems' own registration order left pick-block always returning Black
    //Insulated Redstone Cable regardless of what was actually under the cursor.
    //
    //Round 2: hitResult.getDirection() alone (the face of whichever box the ray happened to intersect) isn't
    //enough to identify WHICH face's box that was, now that multiple faces can share a block position - unlike
    //plain cable's thin 2x2/4x4 boxes, Insulated Redstone Cable's much larger 4x4 visual / 6x6 collision pads
    //(insulatedFaceBounds/insulatedArmBounds) can genuinely overlap a different face's own box near a corner,
    //so two different faces can each present the SAME hit direction at the same position. Resolved by using the
    //ray's own exact hit point (target.getLocation(), converted to this block's local 0-16 coordinate space)
    //and testing it directly against each active face's own box list (its pad plus any connected arms, using
    //that face's own plain-vs-insulated bounds) - the same box list getShape() unions together, just checked
    //per-face here instead of merged. This finds the SPECIFIC box actually clicked rather than just its facing
    //direction, which stays correct even when two faces' boxes are adjacent or overlapping.
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, @Nullable Player player) {
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return super.getCloneItemStack(state, target, level, pos, player);
        }
        Direction hitFaceKey = findHitFace(cable, pos, target.getLocation());
        FaceState face = hitFaceKey != null ? cable.getFace(hitFaceKey) : null;
        if (face == null && isCenterHit(cable, pos, target.getLocation())) {
            FaceState center = cable.getCenter();
            return new ItemStack(itemFor(center.color(), center.bundled()));
        }
        if (face == null && isFrameHit(cable, pos, target.getLocation())) {
            return new ItemStack(BCItems.ALUMINUM_FRAME_ITEM.get());
        }
        if (face == null) {
            //No box actually contains the hit point (shouldn't normally happen for a real hit against this
            //block's own shape) - fall back to the direction-based guess, then to any active face, rather than
            //the buggy vanilla default.
            Direction hitFace = target instanceof BlockHitResult blockHit ? blockHit.getDirection() : null;
            face = hitFace != null ? cable.getFace(hitFace) : null;
        }
        if (face == null) {
            face = cable.activeFaces().stream().findFirst().map(cable::getFace).orElse(null);
        }
        return face != null ? new ItemStack(itemFor(face.color(), face.bundled())) : super.getCloneItemStack(state, target, level, pos, player);
    }

    //Shared by getCloneItemStack (pick-block) and breakFace (breaking): given a precise world-space hit point,
    //finds which active face's own box (its pad, or one of its connected arms) actually contains it, checking
    //each face's real plain-vs-insulated bounds - the same box list getShape() unions together, just tested
    //per-face here instead of merged. Returns the matching face's own towardSupport key, or null if the point
    //doesn't land inside any of them (shouldn't normally happen for a real hit against this block's own shape).
    @Nullable
    private static Direction findHitFace(RedstoneCableBlockEntity cable, BlockPos pos, Vec3 hitLocation) {
        double x = (hitLocation.x - pos.getX()) * 16.0;
        double y = (hitLocation.y - pos.getY()) * 16.0;
        double z = (hitLocation.z - pos.getZ()) * 16.0;
        for (Direction towardSupport : cable.activeFaces()) {
            FaceState candidate = cable.getFace(towardSupport);
            if (candidate == null) {
                continue;
            }
            boolean bundled = candidate.bundled();
            boolean insulated = candidate.color() != null;
            double[] pad = bundled ? bundledFaceBounds(towardSupport) : insulated ? insulatedFaceBounds(towardSupport) : faceBounds(towardSupport);
            if (boxContains(pad, x, y, z)) {
                return towardSupport;
            }
            for (Direction d : candidate.connections()) {
                double[] arm = bundled ? bundledArmBounds(towardSupport, d) : insulated ? insulatedArmBounds(towardSupport, d) : armBounds(towardSupport, d);
                if (boxContains(arm, x, y, z)) {
                    return towardSupport;
                }
            }
        }
        return null;
    }

    private static boolean boxContains(double[] bounds, double x, double y, double z) {
        double eps = 1.0E-4;
        return x >= bounds[0] - eps && x <= bounds[3] + eps
                && y >= bounds[1] - eps && y <= bounds[4] + eps
                && z >= bounds[2] - eps && z <= bounds[5] + eps;
    }

    //Center-mounted cable's own version of findHitFace above - the center slot has no towardSupport/Direction
    //concept of its own (it isn't mounted against any particular wall, see the class comment), so this only
    //ever needs to test the one centered pad plus whichever arms are currently connected, not a per-direction
    //loop over 6 candidate faces.
    private static boolean isCenterHit(RedstoneCableBlockEntity cable, BlockPos pos, Vec3 hitLocation) {
        FaceState center = cable.getCenter();
        if (center == null) {
            return false;
        }
        double x = (hitLocation.x - pos.getX()) * 16.0;
        double y = (hitLocation.y - pos.getY()) * 16.0;
        double z = (hitLocation.z - pos.getZ()) * 16.0;
        double[] pad = centerFaceBounds(center.bundled());
        if (boxContains(pad, x, y, z)) {
            return true;
        }
        for (Direction d : center.connections()) {
            double[] arm = centerArmBounds(center.bundled(), d);
            if (boxContains(arm, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    //Aluminum Frame's own version of findHitFace above - only ever called once findHitFace has already come
    //back empty (see breakFace's/useWithoutItem's own call sites), so there's no real "which one is closer"
    //ambiguity to resolve most of the time: frame's core sits in the 4-12 range on every axis, well inside
    //where any cable face's own pad (hugging the 0-3/13-16 flush-to-wall band) would be, and only an ACTIVE
    //arm reaches out to the wall a face might also be mounted on - a real but narrow overlap window, not worth
    //a full closest-hit-along-the-ray comparison for.
    private static boolean isFrameHit(RedstoneCableBlockEntity cable, BlockPos pos, Vec3 hitLocation) {
        if (!cable.hasFrame()) {
            return false;
        }
        double x = (hitLocation.x - pos.getX()) * 16.0;
        double y = (hitLocation.y - pos.getY()) * 16.0;
        double z = (hitLocation.z - pos.getZ()) * 16.0;
        if (boxContains(FRAME_CORE_BOX, x, y, z)) {
            return true;
        }
        for (Direction direction : cable.frameArms()) {
            if (boxContains(FRAME_ARM_BOXES.get(direction), x, y, z)) {
                return true;
            }
        }
        return false;
    }

    //Reverted the brief getDirectSignal/getSignal strictness split (see git history/session notes) - it
    //Vanilla's own SignalGetter#getDirectSignal javadoc: "directions in redstone signal related methods are
    //backwards" - `side` here is the direction the QUERYING neighbor moved to reach this position, i.e. the
    //OPPOSITE of the direction from this cable TO that neighbor (confirmed directly from SignalGetter's own
    //getBestNeighborSignal: this.getSignal(pos.relative(direction), direction) - `direction` is from the
    //querier's own position outward, not from the queried block back to the querier). Every comparison here
    //needs the direction FROM this cable TOWARD the querier, so it's side.getOpposite(), not side. This never
    //got exercised - and never got caught - until cables started connecting to plain redstone components:
    //cable-to-cable power (FaceState.power(), see computeFace) is read directly off the neighbor's own block
    //entity and never goes through getSignal/getDirectSignal at all.
    //
    //getDirectSignal deliberately just delegates here rather than using a stricter check - an earlier attempt
    //tried making getDirectSignal only honor genuine (non-padded) connections, specifically so this cable's
    //cosmetic "complete the straight line" arm (see FaceState's own comment) could still soft-power a piston
    //without also being able to power dust through an intermediate block. That didn't work: a piston and dust
    //sitting on/against the SAME intermediate block both read that block's getSignal, which resolves through
    //vanilla's own shouldCheckWeakPower/getDirectSignalTo chain identically regardless of which of them is
    //ultimately asking - there's no "who's asking" information available to split on from this side. Accepted
    //as a known limitation: this cable's padded arm can hard-power dust the way real vanilla wire wouldn't.
    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return !shouldSignal ? 0 : getSignal(state, level, pos, side);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (!shouldSignal) {
            return 0;
        }
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return 0;
        }
        Direction towardQuerier = side.getOpposite();
        int max = 0;
        for (Direction towardSupport : cable.activeFaces()) {
            FaceState face = cable.getFace(towardSupport);
            if (face == null) {
                continue;
            }
            //Querier is this face's own support block - report power unconditionally, matching vanilla dust's
            //own "side == UP always returns power regardless of connection" rule (RedStoneWireBlock#getSignal) -
            //this is what lets the cable soft-power whatever it's mounted against.
            if (towardQuerier == towardSupport) {
                max = Math.max(max, face.power());
            } else if (face.isConnected(towardQuerier)) {
                max = Math.max(max, face.power());
            }
        }
        return max;
    }

    //Same spirit as vanilla dust's own animateTick (a low per-tick chance per visible segment, colored by power)
    //but simplified to this block's own geometry instead of replicating dust's floor-plane-specific line-drawing
    //math: one roll per active face's pad and per arm, spawning a single particle at a random point within that
    //box (visualFaceBounds/visualArmBounds - the same coordinates RedstoneCableBakedModel renders, so particles
    //appear to come from the visible wire itself, not the larger collision pad).
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return;
        }
        for (Direction towardSupport : cable.activeFaces()) {
            FaceState face = cable.getFace(towardSupport);
            if (face == null || face.power() == 0) {
                continue;
            }
            //Insulated Redstone Cable particles use the face's own dye color instead of the power gradient -
            //matches the render/tint choice (see BetterCircuits.ClientModEvents#onRegisterBlockColors's own comment):
            //an insulated cable's whole point is showing ITS OWN color, not a shared power indicator, so its
            //particles shouldn't turn red/gray like plain wire's do. Bounds use the same thick/thin split as the
            //baked model (insulatedVisual* for a colored face, the plain visual* pair otherwise) so particles
            //still appear to come from the visible wire itself regardless of which thickness it's rendering at.
            Vector3f color = face.color() != null ? dyeColor(face.color()) : dustColor(face.power());
            double[] faceBox = face.color() != null ? faceBounds(towardSupport) : visualFaceBounds(towardSupport);
            if (random.nextFloat() < 0.1F) {
                spawnDustParticle(level, random, pos, color, faceBox);
            }
            for (Direction d : face.connections()) {
                double[] armBox = face.color() != null ? armBounds(towardSupport, d) : visualArmBounds(towardSupport, d);
                if (random.nextFloat() < 0.1F) {
                    spawnDustParticle(level, random, pos, color, armBox);
                }
            }
        }
    }

    private static void spawnDustParticle(Level level, RandomSource random, BlockPos pos, Vector3f color, double[] bounds) {
        double x = pos.getX() + Mth.lerp(random.nextDouble(), bounds[0], bounds[3]) / 16.0;
        double y = pos.getY() + Mth.lerp(random.nextDouble(), bounds[1], bounds[4]) / 16.0;
        double z = pos.getZ() + Mth.lerp(random.nextDouble(), bounds[2], bounds[5]) / 16.0;
        level.addParticle(new DustParticleOptions(color, 1.0F), x, y, z, 0.0, 0.0, 0.0);
    }

    private static Vector3f dyeColor(DyeColor color) {
        int packed = color.getTextureDiffuseColor();
        float r = ((packed >> 16) & 0xFF) / 255F;
        float g = ((packed >> 8) & 0xFF) / 255F;
        float b = (packed & 0xFF) / 255F;
        return new Vector3f(r, g, b);
    }

    private static Vector3f dustColor(int power) {
        int packed = RedStoneWireBlock.getColorForPower(power);
        float r = ((packed >> 16) & 0xFF) / 255F;
        float g = ((packed >> 8) & 0xFF) / 255F;
        float b = (packed & 0xFF) / 255F;
        return new Vector3f(r, g, b);
    }

    //---- Outline shape + collision box-coordinate math ----

    //Only covers ACTIVE faces - this shape drives both raycasting AND the hover-outline wireframe (vanilla's
    //LevelRenderer.renderHitOutline calls state.getShape(...) directly, same as raycasting - there's no separate
    //"outline-only" hook to keep the two independent). An earlier version of this also included a speculative pad
    //for every direction that COULD still support a face, so clicking an otherwise-empty spot would register a
    //hit for RedstoneCableBlock#useItemOn's "click a face to add a face" UX - but that made every open direction
    //around an existing cable show its own phantom wireframe box even when nothing was there. That UX is now
    //covered a different way instead: RedstoneCableBlockItem#place redirects to addFace whenever the target
    //position already has a cable (the SAME "aim at the neighboring support block's face" gesture as the very
    //first placement, which only ever needs that neighbor's own normal collision, not any geometry on this
    //block), so useItemOn firing directly on this block's own shape is no longer needed for that case.
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        RedstoneCableBlockEntity cable = getCableAt(level, pos);
        if (cable == null) {
            return Shapes.empty();
        }
        VoxelShape shape = Shapes.empty();
        for (Direction towardSupport : cable.activeFaces()) {
            FaceState face = cable.getFace(towardSupport);
            boolean bundled = face != null && face.bundled();
            boolean insulated = face != null && face.color() != null;
            double[] pad = bundled ? bundledFaceBounds(towardSupport) : insulated ? insulatedFaceBounds(towardSupport) : faceBounds(towardSupport);
            shape = Shapes.joinUnoptimized(shape, box(pad), BooleanOp.OR);
            if (face != null) {
                for (Direction d : face.connections()) {
                    double[] arm = bundled ? bundledArmBounds(towardSupport, d) : insulated ? insulatedArmBounds(towardSupport, d) : armBounds(towardSupport, d);
                    shape = Shapes.joinUnoptimized(shape, box(arm), BooleanOp.OR);
                }
            }
        }
        if (cable.hasFrame()) {
            shape = Shapes.joinUnoptimized(shape, FRAME_CORE_SHAPE, BooleanOp.OR);
            for (Direction direction : cable.frameArms()) {
                shape = Shapes.joinUnoptimized(shape, FRAME_ARM_SHAPES.get(direction), BooleanOp.OR);
            }
        }
        FaceState center = cable.getCenter();
        if (center != null) {
            shape = Shapes.joinUnoptimized(shape, box(centerFaceBounds(center.bundled())), BooleanOp.OR);
            for (Direction d : center.connections()) {
                shape = Shapes.joinUnoptimized(shape, box(centerArmBounds(center.bundled(), d)), BooleanOp.OR);
            }
        }
        return shape.optimize();
    }

    private static VoxelShape box(double[] bounds) {
        return Block.box(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]);
    }

    //Collision/outline pad: a 4x4x3 box flush against the support, one extra layer of padding wrapped around the
    //actual 2x2x2 visual cable (visualFaceBounds below) on every exposed side, for a more forgiving click/select
    //target than the thin cable itself would give. {minX,minY,minZ,maxX,maxY,maxZ}.
    public static double[] faceBounds(Direction towardSupport) {
        double[] bounds = {6, 6, 6, 10, 10, 10};
        int i = axisIndex(towardSupport);
        if (towardSupport.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            bounds[i] = 13;
            bounds[i + 3] = 16;
        } else {
            bounds[i] = 0;
            bounds[i + 3] = 3;
        }
        return bounds;
    }

    //A thin extension of the collision pad, reaching from its edge out to the block boundary in in-plane
    //direction d - same thickness/height as the pad so it stays flush against the support.
    public static double[] armBounds(Direction towardSupport, Direction d) {
        double[] bounds = faceBounds(towardSupport);
        int j = axisIndex(d);
        if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            bounds[j] = 10;
            bounds[j + 3] = 16;
        } else {
            bounds[j] = 0;
            bounds[j + 3] = 6;
        }
        return bounds;
    }

    //Insulated Redstone Cable's own collision pad: a 6x6x4 box, one extra layer of padding wrapped around the
    //thicker 4x4x3 insulated visual cable (which reuses faceBounds/armBounds above directly - Insulated Redstone
    //Cable's visual profile IS exactly the plain cable's own collision profile, see the class's own comment on
    //why "4x4 instead of 2x2" lines up with faceBounds/armBounds's existing numbers) on every exposed side, same
    //+1-layer relationship the plain pad above has to its own (thinner) visual cable.
    public static double[] insulatedFaceBounds(Direction towardSupport) {
        double[] bounds = {5, 5, 5, 11, 11, 11};
        int i = axisIndex(towardSupport);
        if (towardSupport.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            bounds[i] = 12;
            bounds[i + 3] = 16;
        } else {
            bounds[i] = 0;
            bounds[i + 3] = 4;
        }
        return bounds;
    }

    public static double[] insulatedArmBounds(Direction towardSupport, Direction d) {
        double[] bounds = insulatedFaceBounds(towardSupport);
        int j = axisIndex(d);
        if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            bounds[j] = 11;
            bounds[j + 3] = 16;
        } else {
            bounds[j] = 0;
            bounds[j + 3] = 5;
        }
        return bounds;
    }

    //Bundled Redstone Cable's own collision pad: an 8x8x5 box, continuing the same +1-layer-per-tier pattern one
    //step further out - one extra layer wrapped around its own 6x6x4 VISUAL cable, which in turn reuses
    //insulatedFaceBounds/insulatedArmBounds directly as ITS profile (the user's own spec: "the dot for this
    //cable should be 6x6x4, which is 1 pixel larger than the insulated cables" - i.e. Insulated Redstone Cable's
    //own COLLISION pad becomes Bundled Redstone Cable's VISUAL one, exactly the same reuse relationship
    //insulatedFaceBounds already has with the plain cable's collision pad above it).
    public static double[] bundledFaceBounds(Direction towardSupport) {
        double[] bounds = {4, 4, 4, 12, 12, 12};
        int i = axisIndex(towardSupport);
        if (towardSupport.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            bounds[i] = 11;
            bounds[i + 3] = 16;
        } else {
            bounds[i] = 0;
            bounds[i + 3] = 5;
        }
        return bounds;
    }

    public static double[] bundledArmBounds(Direction towardSupport, Direction d) {
        double[] bounds = bundledFaceBounds(towardSupport);
        int j = axisIndex(d);
        if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            bounds[j] = 12;
            bounds[j + 3] = 16;
        } else {
            bounds[j] = 0;
            bounds[j + 3] = 4;
        }
        return bounds;
    }

    //Center-mounted cable's own collision pad, following the same "reuse the next tier's own bounds, just
    //centered instead of offset to a wall" relationship insulatedFaceBounds/bundledFaceBounds already have to
    //their own visual profile - bundledFaceBounds's own DEFAULT cross-section (before it offsets one axis out
    //to the wall) IS its own collision pad centered on all 3 axes, and likewise for insulatedFaceBounds.
    public static double[] centerFaceBounds(boolean bundled) {
        return bundled ? new double[]{4, 4, 4, 12, 12, 12} : new double[]{5, 5, 5, 11, 11, 11};
    }

    //A thin extension of the centered COLLISION pad, reaching from its own edge out to the block boundary in
    //direction d - unlike armBounds/insulatedArmBounds/bundledArmBounds above (restricted to the 4 in-plane
    //directions around one fixed towardSupport axis), a center arm can reach in any of all 6 directions, since
    //the center slot has no support axis to exclude one from.
    public static double[] centerArmBounds(boolean bundled, Direction d) {
        return extendToEdge(centerFaceBounds(bundled), d);
    }

    //Bug fix: RedstoneCableBakedModel's own rendering was reusing centerFaceBounds/centerArmBounds above (the
    //COLLISION pad) directly as the VISUAL geometry too, rendering both profiles one whole tier too big (bundled
    //8x8 instead of 6x6, insulated 6x6 instead of 4x4 - exactly the "one extra layer of padding" every OTHER
    //tier's own collision pad already has over its own visual, per faceBounds' own class comment). The user's
    //actual spec - "reuse the existing Insulated/Bundled visual profile, just centered" - means the center
    //cable's VISUAL size has to follow the same one-tier-down relationship every wall-mounted face's visual
    //already has to its own collision pad (see RedstoneCableBakedModel's own faceBounds selection: insulated
    //renders at PLAIN's own collision size, bundled renders at INSULATED's own collision size) - so this is
    //centerFaceBounds's plain/insulated centered cross-section shifted down one tier, not centerFaceBounds
    //itself.
    public static double[] centerVisualFaceBounds(boolean bundled) {
        return bundled ? new double[]{5, 5, 5, 11, 11, 11} : new double[]{6, 6, 6, 10, 10, 10};
    }

    public static double[] centerVisualArmBounds(boolean bundled, Direction d) {
        return extendToEdge(centerVisualFaceBounds(bundled), d);
    }

    //Shared by centerArmBounds/centerVisualArmBounds above: extends a centered, isotropic pad from its own edge
    //out to the block boundary along axis d - the exact same "pad edge -> 0 or 16" relationship
    //insulatedArmBounds/bundledArmBounds already have to their own wall-mounted pad, generalized to work from
    //ANY starting pad size instead of being duplicated per tier.
    private static double[] extendToEdge(double[] pad, Direction d) {
        double[] bounds = pad.clone();
        int j = axisIndex(d);
        if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            bounds[j] = bounds[j + 3];
            bounds[j + 3] = 16;
        } else {
            bounds[j + 3] = bounds[j];
            bounds[j] = 0;
        }
        return bounds;
    }

    //---- Visual geometry box-coordinate math (RedstoneCableBakedModel - see its class comment). Deliberately
    //separate from the collision bounds above: the actual cable is a thinner 2x2x2 box, one layer smaller than
    //the collision pad on every exposed side, matching the "collision has an extra layer wrapped around the
    //visual model" split requested for this block. Insulated Redstone Cable's own visual geometry reuses
    //faceBounds/armBounds directly instead of adding a redundant pair of identical methods - see this class's own
    //comment on insulatedFaceBounds for why those numbers already ARE exactly the thicker 4x4 profile. ----

    public static double[] visualFaceBounds(Direction towardSupport) {
        double[] bounds = {7, 7, 7, 9, 9, 9};
        int i = axisIndex(towardSupport);
        if (towardSupport.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            bounds[i] = 14;
            bounds[i + 3] = 16;
        } else {
            bounds[i] = 0;
            bounds[i + 3] = 2;
        }
        return bounds;
    }

    public static double[] visualArmBounds(Direction towardSupport, Direction d) {
        double[] bounds = visualFaceBounds(towardSupport);
        int j = axisIndex(d);
        if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            bounds[j] = 9;
            bounds[j + 3] = 16;
        } else {
            bounds[j] = 0;
            bounds[j + 3] = 7;
        }
        return bounds;
    }

    private static int axisIndex(Direction direction) {
        return switch (direction.getAxis()) {
            case X -> 0;
            case Y -> 1;
            case Z -> 2;
        };
    }

    //---- Drops ----

    //The loot table itself just drops a single plain-cable item (the normal "drop self" case every other block
    //uses) - it has no way to know how many independent faces THIS particular block instance was hosting, or
    //what color each one is, since both live in the block entity, not the blockstate. Rather than just scaling
    //that single stack's count (which would always drop plain Redstone Cable, even for faces that were actually
    //colored Insulated Redstone Cable), this builds one stack per active face directly from that face's own
    //color via itemFor - the loot table's own drops list is only consulted to check whether anything should
    //drop AT ALL (e.g. under whatever conditions the loot table might someday gain), not for its actual item.
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (drops.isEmpty()) {
            return drops;
        }
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(blockEntity instanceof RedstoneCableBlockEntity cable)) {
            return drops;
        }
        List<ItemStack> perFace = new ArrayList<>(cable.activeFaces().size() + 1);
        for (Direction towardSupport : cable.activeFaces()) {
            FaceState face = cable.getFace(towardSupport);
            perFace.add(new ItemStack(itemFor(face != null ? face.color() : null, face != null && face.bundled())));
        }
        //Aluminum Frame: covers vanilla-triggered whole-block removal (explosions, fire, world edits, ...) -
        //the manual click-to-remove path (removeFrame) never goes through getDrops at all, it pops its own item
        //directly the same way breakFace already does for a single face.
        if (cable.hasFrame()) {
            perFace.add(new ItemStack(BCItems.ALUMINUM_FRAME_ITEM.get()));
        }
        //Same reasoning as the frame append above, applied to a center-mounted cable - vanilla-triggered
        //removal needs its own drop added here too, since the manual click-to-remove path (removeCenterCable)
        //never goes through getDrops either.
        FaceState center = cable.getCenter();
        if (center != null) {
            perFace.add(new ItemStack(itemFor(center.color(), center.bundled())));
        }
        return perFace;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!isMoving && !state.is(newState.getBlock())) {
            RedstoneCableBlockEntity cable = getCableAt(level, pos);
            Set<Direction> faces = cable != null ? Set.copyOf(cable.activeFaces()) : Set.of();
            super.onRemove(state, level, pos, newState, isMoving);
            if (!level.isClientSide) {
                for (Direction towardSupport : faces) {
                    BlockPos support = pos.relative(towardSupport);
                    for (Direction d : inPlaneDirections(towardSupport)) {
                        BlockPos wrapTarget = support.relative(d);
                        if (getCableAt(level, wrapTarget) != null) {
                            level.neighborChanged(wrapTarget, this, pos);
                        }
                    }
                }
                level.updateNeighborsAt(pos, this);
            }
        }
    }
}
