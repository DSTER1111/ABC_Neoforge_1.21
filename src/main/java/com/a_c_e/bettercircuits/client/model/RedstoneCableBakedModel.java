package com.a_c_e.bettercircuits.client.model;

import com.a_c_e.bettercircuits.block.RedstoneCableBlock;
import com.a_c_e.bettercircuits.block.entity.RedstoneCableBlockEntity;
import com.a_c_e.bettercircuits.block.entity.RedstoneCableBlockEntity.FaceState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

//Real Phase 3b geometry: for each active face reported by RedstoneCableBlockEntity's ModelData (see
//RedstoneCableBlockEntity#getModelData/FACES_PROPERTY), bakes a small box flush against the support plus one
//thin "arm" box per connected in-plane direction, using RedstoneCableBlock's own visualFaceBounds/visualArmBounds
//coordinate math (kept there, common-side). Deliberately NOT the same bounds RedstoneCableBlock's getShape() uses
//for collision/outline (faceBounds/armBounds) - the actual cable is a thinner 2x2x2 box, one layer smaller than
//the more forgiving collision pad on every exposed side.
//No distinction is drawn between the 3 connection rules geometrically - same as vanilla dust, which doesn't
//visually differ a "reaches a plain neighbor" arm from a "reaches a wire that climbs a wall" arm either; both
//just extend to the block edge and let the neighboring wire's own geometry carry on from there.
//
//Wrapped in over RedstoneCableBlock's own (otherwise-empty) placeholder baked model via
//BetterCircuits.ClientModEvents#onModifyBakingResult (ModelEvent.ModifyBakingResult - the standard NeoForge hook for
//substituting a block's baked model wholesale). Power-based coloring is NOT baked into vertex data here; each
//box's quads get tintIndex = towardSupport.ordinal(), and BetterCircuits.ClientModEvents#onRegisterBlockColors reads
//the matching face's power back out of the block entity per tintIndex - the standard BlockColor mechanism,
//extended to distinguish faces by tintIndex instead of a single blockstate property the way vanilla dust does.
public class RedstoneCableBakedModel extends BakedModelWrapper<BakedModel> {
    private static final FaceBakery FACE_BAKERY = new FaceBakery();

    private final TextureAtlasSprite sprite;
    private final Map<DyeColor, TextureAtlasSprite> coloredSprites;
    private final TextureAtlasSprite bundledSprite;
    private final TextureAtlasSprite frameSprite;

    public RedstoneCableBakedModel(BakedModel originalModel, TextureAtlasSprite sprite, Map<DyeColor, TextureAtlasSprite> coloredSprites, TextureAtlasSprite bundledSprite, TextureAtlasSprite frameSprite) {
        super(originalModel);
        this.sprite = sprite;
        this.coloredSprites = coloredSprites;
        this.bundledSprite = bundledSprite;
        this.frameSprite = frameSprite;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        //None of this geometry is a full block face, so nothing should ever be culled away by a neighbor -
        //return everything on the uncalled "general" pass (side == null) and nothing for specific sides.
        if (side != null) {
            return List.of();
        }
        Map<Direction, FaceState> faces = extraData.get(RedstoneCableBlockEntity.FACES_PROPERTY);
        Boolean hasFrame = extraData.get(RedstoneCableBlockEntity.HAS_FRAME_PROPERTY);
        boolean frame = hasFrame != null && hasFrame;
        if ((faces == null || faces.isEmpty()) && !frame) {
            return List.of();
        }
        List<BakedQuad> quads = new ArrayList<>();
        if (faces == null) {
            faces = Map.of();
        }
        for (Map.Entry<Direction, FaceState> entry : faces.entrySet()) {
            Direction towardSupport = entry.getKey();
            FaceState face = entry.getValue();
            int tintIndex = towardSupport.ordinal();
            //Insulated Redstone Cable renders thicker (reuses RedstoneCableBlock's own collision-pad coordinates
            //as its visual profile - see that class's own comment on insulatedFaceBounds/insulatedArmBounds for
            //why those numbers already ARE the "4x4 instead of 2x2" the user asked for) and samples its own
            //dedicated per-color sprite instead of the plain wire's tintable one. Bundled Redstone Cable renders
            //bigger still - its own visual profile reuses insulatedFaceBounds/insulatedArmBounds directly (see
            //those methods' own comment on bundledFaceBounds for the "one pixel larger" derivation) and samples
            //its own single, uncolored sprite (there's no per-color Bundled Cable to pick between).
            DyeColor color = face.color();
            boolean bundled = face.bundled();
            TextureAtlasSprite faceSprite = bundled ? bundledSprite : color != null ? coloredSprites.getOrDefault(color, sprite) : sprite;
            //color != null uses faceBounds/armBounds (RedstoneCableBlock's own PLAIN collision-pad numbers,
            //which already are exactly the thicker 4x4 profile) rather than insulatedFaceBounds/insulatedArmBounds
            //- those are Insulated Redstone Cable's own, even bigger, COLLISION pad (see that method's own
            //comment), one layer further out than what actually gets rendered here. Bundled reuses that SAME
            //insulated collision pad as its own visual instead.
            double[] faceBounds = bundled ? RedstoneCableBlock.insulatedFaceBounds(towardSupport)
                    : color != null ? RedstoneCableBlock.faceBounds(towardSupport) : RedstoneCableBlock.visualFaceBounds(towardSupport);
            addBox(quads, faceBounds, tintIndex, faceSprite, towardSupport, bundled ? UvProfile.BUNDLED : color != null ? UvProfile.INSULATED : UvProfile.PLAIN);
            for (Direction d : face.connections()) {
                double[] armBounds = bundled ? RedstoneCableBlock.insulatedArmBounds(towardSupport, d)
                        : color != null ? RedstoneCableBlock.armBounds(towardSupport, d) : RedstoneCableBlock.visualArmBounds(towardSupport, d);
                addBox(quads, armBounds, tintIndex, faceSprite, towardSupport, bundled ? UvProfile.BUNDLED : color != null ? UvProfile.INSULATED : UvProfile.PLAIN);
            }
        }
        if (frame) {
            for (FrameBox box : FRAME_CORE_BOXES) {
                addFrameBox(quads, box);
            }
            Set<Direction> frameArms = extraData.get(RedstoneCableBlockEntity.FRAME_ARMS_PROPERTY);
            if (frameArms != null) {
                for (Direction direction : frameArms) {
                    for (FrameBox box : FRAME_ARM_BOXES.get(direction)) {
                        addFrameBox(quads, box);
                    }
                }
            }
        }
        //Center-mounted cable: only ever Insulated or Bundled (see RedstoneCableBlock#addCenter's own
        //rejection of plain cable), reusing that same visual profile/sprite selection, just centered -
        //centerVisualFaceBounds/centerVisualArmBounds (RedstoneCableBlock - deliberately NOT
        //centerFaceBounds/centerArmBounds, which are one tier bigger and collision-only, see that method's own
        //bug-fix comment) give the pad and each connected arm's own isotropic-cube-then-elongated bounds;
        //addCenterBox below is addBox's own UV logic generalized to an OPTIONAL depth axis, since the pad
        //itself (a perfect cube - no wall it's flush against) has no single "depth" side the way every
        //wall-mounted face's pad always does.
        FaceState center = extraData.get(RedstoneCableBlockEntity.CENTER_PROPERTY);
        if (center != null) {
            boolean bundled = center.bundled();
            TextureAtlasSprite centerSprite = bundled ? bundledSprite : center.color() != null ? coloredSprites.getOrDefault(center.color(), sprite) : sprite;
            UvProfile centerProfile = bundled ? UvProfile.BUNDLED : UvProfile.INSULATED;
            addCenterBox(quads, RedstoneCableBlock.centerVisualFaceBounds(bundled), centerSprite, null, centerProfile);
            for (Direction d : center.connections()) {
                addCenterBox(quads, RedstoneCableBlock.centerVisualArmBounds(bundled, d), centerSprite, d, centerProfile);
            }
        }
        return quads;
    }

    private enum UvProfile { PLAIN, INSULATED, BUNDLED }

    //redstone_cable.png isn't a tileable material and isn't meant to be sampled as a whole - it's a hand-drawn
    //wire cross-section: a solid 2px-wide vertical stripe (pixel columns 7-8) down the middle of an otherwise
    //fully-transparent 16x16 canvas. Neither auto-projecting UV from a box's own world-space footprint nor
    //stretching the full 0-16 canvas across a quad can ever look "solid" - both include the transparent margin,
    //so at best a thin sliver of the stripe shows through. The fix is to stop deriving UV from box geometry
    //entirely and hand-pick a fixed window matching the visual cable's own 2x2 footprint (U:[7,9], V:[7,9]) -
    //pulling only a 2x2 clip of the texture rather than stretching the full 16-tall stripe (which has its own
    //subtle per-row shading detail, not a uniform flat color) down into a 2-unit-tall quad.
    private static final float[] SOLID_UV = {7F, 7F, 9F, 9F};

    //Insulated Redstone Cable's own textures (block/{color}_insulated_cable.png) were inspected directly
    //(decoded pixel-by-pixel): every one is a UNIFORM solid-color 4px-wide vertical stripe at columns 6-9,
    //opaque across the ENTIRE 16-row height, with columns 0-5 and 10-15 fully transparent. Since the color is
    //flat, any sub-window fully inside 6-9 samples identically - so the "top/bottom" (the 2 faces along the
    //box's own depth axis, one flush against the support and one facing away from it) use the full 4-wide
    //profile window on both axes (a 4x4 square, matching that pair's own real 4x4 world shape), while the 4
    //"side" faces use a 3-wide subset of that SAME opaque range (6-9, not the earlier 0-3 - see below) for
    //whichever axis actually carries the box's own 3-unit depth, keeping the other axis at the full 4-wide
    //profile - matching the side faces' own real 4x3 world shape instead of squaring it off too.
    //
    //Getting "which axis carries depth" right needs FaceBakery's own per-facing (u-axis, v-axis) convention,
    //not just "u=width, v=height" uniformly - vanilla assigns u/v differently depending on which way a quad
    //faces (DOWN/UP: u=X,v=Z; NORTH/SOUTH: u=X,v=Y; WEST/EAST: u=Z,v=Y), so whether a side face's own u or v
    //slot lands on the depth axis flips depending on both the facing AND which axis towardSupport itself is on
    //- a single fixed UV rectangle applied uniformly to every facing (an earlier attempt) put the narrow depth
    //crop on the wrong slot for some orientations. The FIRST attempt at this axis-aware split anchored the depth
    //crop at columns 0-3 (matching the box's own local depth-axis coordinate range) - which happens to fall
    //entirely in the texture's transparent margin, not its opaque stripe, so any face where that crop landed on
    //U (a wall-mounted/"vertical" cable's own side faces) rendered completely blank. DEPTH_LO/HI below use 6-9
    //instead - a real 3px subset of the texture's own known-opaque range - so every combination stays visible.
    private static final float PROFILE_LO = 6F;
    private static final float PROFILE_HI = 10F;
    private static final float DEPTH_LO = 6F;
    private static final float DEPTH_HI = 9F;

    //Bundled Redstone Cable's own texture (block/bundled_redstone_cable.png) follows the exact same "solid
    //color, opaque full-height stripe" convention as Insulated Redstone Cable above, just one column wider on
    //each side - the user's own spec is "one 6 pixel wide column in the center of the texture" (columns 5-10),
    //matching bundledFaceBounds' own 6-wide hub exactly (see that method's own comment). DEPTH shrinks off the
    //high end the same way insulated's own DEPTH does relative to its PROFILE (narrower by the hub-vs-thickness
    //delta: insulated is 4 wide/3 deep, a delta of 1; bundled is 6 wide/4 deep, a delta of 2), keeping the same
    //"flush at the low end, one narrower window on the high end" convention rather than inventing a new one.
    private static final float BUNDLED_PROFILE_LO = 5F;
    private static final float BUNDLED_PROFILE_HI = 11F;
    private static final float BUNDLED_DEPTH_LO = 5F;
    private static final float BUNDLED_DEPTH_HI = 9F;

    private static float[] solidUv(float profileLo, float profileHi, float depthLo, float depthHi, Direction towardSupport, Direction facing) {
        Direction.Axis depthAxis = towardSupport.getAxis();
        Direction.Axis uAxis;
        Direction.Axis vAxis;
        switch (facing.getAxis()) {
            case Y -> { uAxis = Direction.Axis.X; vAxis = Direction.Axis.Z; }
            case Z -> { uAxis = Direction.Axis.X; vAxis = Direction.Axis.Y; }
            default -> { uAxis = Direction.Axis.Z; vAxis = Direction.Axis.Y; }
        }
        float u0 = uAxis == depthAxis ? depthLo : profileLo;
        float u1 = uAxis == depthAxis ? depthHi : profileHi;
        float v0 = vAxis == depthAxis ? depthLo : profileLo;
        float v1 = vAxis == depthAxis ? depthHi : profileHi;
        return new float[]{u0, v0, u1, v1};
    }

    private void addBox(List<BakedQuad> quads, double[] bounds, int tintIndex, TextureAtlasSprite boxSprite, Direction towardSupport, UvProfile profile) {
        Vector3f from = new Vector3f((float) bounds[0], (float) bounds[1], (float) bounds[2]);
        Vector3f to = new Vector3f((float) bounds[3], (float) bounds[4], (float) bounds[5]);
        for (Direction facing : Direction.values()) {
            float[] uv = switch (profile) {
                case PLAIN -> SOLID_UV.clone();
                case INSULATED -> solidUv(PROFILE_LO, PROFILE_HI, DEPTH_LO, DEPTH_HI, towardSupport, facing);
                case BUNDLED -> solidUv(BUNDLED_PROFILE_LO, BUNDLED_PROFILE_HI, BUNDLED_DEPTH_LO, BUNDLED_DEPTH_HI, towardSupport, facing);
            };
            BlockElementFace face = new BlockElementFace(null, tintIndex, "#particle", new BlockFaceUV(uv, 0));
            quads.add(FACE_BAKERY.bakeQuad(from, to, face, boxSprite, facing, BlockModelRotation.X0_Y0, null, true));
        }
    }

    //Center-mounted cable's own version of addBox above - no tint (tintIndex -1, matching Aluminum Frame's own
    //reasoning: a center cable is always Insulated/Bundled - see this class's own comment above - and those
    //two profiles already render at full, untinted brightness whenever a per-face BlockColor handler WOULD
    //run, so skipping the handler entirely here is the same visual result without needing a new tintIndex slot
    //or any change to BetterCircuits's own onRegisterBlockColors). depthDirection is null for the pad itself (a
    //perfect isotropic cube with no single wall it's flush against, unlike every other box this model bakes),
    //using the full square profile window on every face; non-null for an arm (the direction it was extended
    //in becomes its own "depth" axis, the exact same role towardSupport plays for a wall-mounted face's arm).
    private void addCenterBox(List<BakedQuad> quads, double[] bounds, TextureAtlasSprite boxSprite, @Nullable Direction depthDirection, UvProfile profile) {
        Vector3f from = new Vector3f((float) bounds[0], (float) bounds[1], (float) bounds[2]);
        Vector3f to = new Vector3f((float) bounds[3], (float) bounds[4], (float) bounds[5]);
        for (Direction facing : Direction.values()) {
            float[] uv;
            if (depthDirection == null) {
                float lo = profile == UvProfile.BUNDLED ? BUNDLED_PROFILE_LO : PROFILE_LO;
                float hi = profile == UvProfile.BUNDLED ? BUNDLED_PROFILE_HI : PROFILE_HI;
                uv = new float[]{lo, lo, hi, hi};
            } else {
                uv = profile == UvProfile.BUNDLED
                        ? solidUv(BUNDLED_PROFILE_LO, BUNDLED_PROFILE_HI, BUNDLED_DEPTH_LO, BUNDLED_DEPTH_HI, depthDirection, facing)
                        : solidUv(PROFILE_LO, PROFILE_HI, DEPTH_LO, DEPTH_HI, depthDirection, facing);
            }
            BlockElementFace face = new BlockElementFace(null, -1, "#particle", new BlockFaceUV(uv, 0));
            quads.add(FACE_BAKERY.bakeQuad(from, to, face, boxSprite, facing, BlockModelRotation.X0_Y0, null, true));
        }
    }

    //---- Aluminum Frame geometry: a literal transcription of the uploaded reference model (frame.json), the
    //same box data as RedstoneCableBlock's own FRAME_CORE_BOXES/FRAME_ARM_BOXES (those are from/to bounds only,
    //for shape/hit-testing) with each box's own real per-face UV rectangles added for actual rendering. Unlike
    //every other box this model bakes (addBox above), these use literal UV straight from the reference model
    //rather than a derived tint-stripe profile - Aluminum Frame's texture is a real painted material, not a
    //solid color meant to be tinted (see addFrameBox's own tintIndex). ----

    private record FrameBox(float x1, float y1, float z1, float x2, float y2, float z2,
                             float[] north, float[] east, float[] south, float[] west, float[] up, float[] down) {}

    private static final FrameBox[] FRAME_CORE_BOXES = {
            new FrameBox(4F, 4F, 4F, 6F, 12F, 6F,
                    new float[]{6F, 0F, 8F, 8F}, new float[]{0F, 0F, 2F, 8F}, new float[]{6F, 0F, 8F, 8F}, new float[]{0F, 0F, 2F, 8F}, new float[]{0F, 0F, 2F, 2F}, new float[]{0F, 6F, 2F, 8F}),
            new FrameBox(4F, 10F, 6F, 6F, 12F, 10F,
                    new float[]{1F, 1F, 3F, 3F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 1F, 2F, 3F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 2F, 2F, 6F}, new float[]{6F, 2F, 8F, 6F}),
            new FrameBox(10F, 10F, 6F, 12F, 12F, 10F,
                    new float[]{1F, 1F, 3F, 3F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 1F, 2F, 3F}, new float[]{2F, 6F, 6F, 8F}, new float[]{6F, 2F, 8F, 6F}, new float[]{0F, 2F, 2F, 6F}),
            new FrameBox(6F, 10F, 4F, 10F, 12F, 6F,
                    new float[]{2F, 0F, 6F, 2F}, new float[]{2F, 1F, 4F, 3F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 1F, 2F, 3F}, new float[]{2F, 0F, 6F, 2F}, new float[]{2F, 0F, 6F, 2F}),
            new FrameBox(6F, 10F, 10F, 10F, 12F, 12F,
                    new float[]{2F, 6F, 6F, 8F}, new float[]{2F, 1F, 4F, 3F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 1F, 2F, 3F}, new float[]{2F, 6F, 6F, 8F}, new float[]{2F, 6F, 6F, 8F}),
            new FrameBox(6F, 4F, 10F, 10F, 6F, 12F,
                    new float[]{2F, 0F, 6F, 2F}, new float[]{2F, 1F, 4F, 3F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 1F, 2F, 3F}, new float[]{2F, 0F, 6F, 2F}, new float[]{2F, 0F, 6F, 2F}),
            new FrameBox(6F, 4F, 4F, 10F, 6F, 6F,
                    new float[]{2F, 6F, 6F, 8F}, new float[]{2F, 1F, 4F, 3F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 1F, 2F, 3F}, new float[]{2F, 6F, 6F, 8F}, new float[]{2F, 6F, 6F, 8F}),
            new FrameBox(10F, 4F, 6F, 12F, 6F, 10F,
                    new float[]{1F, 1F, 3F, 3F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 1F, 2F, 3F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 2F, 2F, 6F}, new float[]{6F, 2F, 8F, 6F}),
            new FrameBox(4F, 4F, 6F, 6F, 6F, 10F,
                    new float[]{1F, 1F, 3F, 3F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 1F, 2F, 3F}, new float[]{2F, 6F, 6F, 8F}, new float[]{6F, 2F, 8F, 6F}, new float[]{0F, 2F, 2F, 6F}),
            new FrameBox(4F, 4F, 10F, 6F, 12F, 12F,
                    new float[]{0F, 0F, 2F, 8F}, new float[]{6F, 0F, 8F, 8F}, new float[]{0F, 0F, 2F, 8F}, new float[]{6F, 0F, 8F, 8F}, new float[]{0F, 6F, 2F, 8F}, new float[]{0F, 0F, 2F, 2F}),
            new FrameBox(10F, 4F, 10F, 12F, 12F, 12F,
                    new float[]{6F, 0F, 8F, 8F}, new float[]{0F, 0F, 2F, 8F}, new float[]{6F, 0F, 8F, 8F}, new float[]{0F, 0F, 2F, 8F}, new float[]{6F, 6F, 8F, 8F}, new float[]{6F, 0F, 8F, 2F}),
            new FrameBox(10F, 4F, 4F, 12F, 12F, 6F,
                    new float[]{0F, 0F, 2F, 8F}, new float[]{6F, 0F, 8F, 8F}, new float[]{0F, 0F, 2F, 8F}, new float[]{6F, 0F, 8F, 8F}, new float[]{6F, 0F, 8F, 2F}, new float[]{6F, 6F, 8F, 8F}),
    };

    private static final Map<Direction, FrameBox[]> FRAME_ARM_BOXES = new EnumMap<>(Direction.class);

    static {
        FRAME_ARM_BOXES.put(Direction.DOWN, new FrameBox[]{
                new FrameBox(4F, 0F, 4F, 6F, 4F, 6F,
                        new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 0F, 2F, 2F}, new float[]{0F, 6F, 2F, 8F}),
                new FrameBox(4F, 0F, 10F, 6F, 4F, 12F,
                        new float[]{6F, 2F, 8F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{0F, 6F, 2F, 8F}, new float[]{0F, 0F, 2F, 2F}),
                new FrameBox(10F, 0F, 10F, 12F, 4F, 12F,
                        new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 6F, 8F, 8F}, new float[]{6F, 0F, 8F, 2F}),
                new FrameBox(10F, 0F, 4F, 12F, 4F, 6F,
                        new float[]{0F, 2F, 2F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{6F, 0F, 8F, 2F}, new float[]{6F, 6F, 8F, 8F}),
        });
        FRAME_ARM_BOXES.put(Direction.UP, new FrameBox[]{
                new FrameBox(4F, 12F, 4F, 6F, 16F, 6F,
                        new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 0F, 2F, 2F}, new float[]{0F, 6F, 2F, 8F}),
                new FrameBox(4F, 12F, 10F, 6F, 16F, 12F,
                        new float[]{6F, 2F, 8F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{0F, 6F, 2F, 8F}, new float[]{0F, 0F, 2F, 2F}),
                new FrameBox(10F, 12F, 10F, 12F, 16F, 12F,
                        new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 6F, 8F, 8F}, new float[]{6F, 0F, 8F, 2F}),
                new FrameBox(10F, 12F, 4F, 12F, 16F, 6F,
                        new float[]{0F, 2F, 2F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 2F, 8F, 6F}, new float[]{0F, 2F, 2F, 6F}, new float[]{6F, 0F, 8F, 2F}, new float[]{6F, 6F, 8F, 8F}),
        });
        FRAME_ARM_BOXES.put(Direction.NORTH, new FrameBox[]{
                new FrameBox(4F, 4F, 0F, 6F, 6F, 4F,
                        new float[]{6F, 6F, 8F, 8F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 2F, 2F, 6F}),
                new FrameBox(10F, 4F, 0F, 12F, 6F, 4F,
                        new float[]{0F, 6F, 2F, 8F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 6F, 6F, 8F}, new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 2F, 8F, 6F}),
                new FrameBox(10F, 10F, 0F, 12F, 12F, 4F,
                        new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 2F, 8F, 6F}),
                new FrameBox(4F, 10F, 0F, 6F, 12F, 4F,
                        new float[]{6F, 0F, 8F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 2F, 2F, 6F}),
        });
        FRAME_ARM_BOXES.put(Direction.SOUTH, new FrameBox[]{
                new FrameBox(4F, 4F, 12F, 6F, 6F, 16F,
                        new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 6F, 2F, 8F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 2F, 2F, 6F}),
                new FrameBox(10F, 4F, 12F, 12F, 6F, 16F,
                        new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 6F, 6F, 8F}, new float[]{6F, 6F, 8F, 8F}, new float[]{2F, 6F, 6F, 8F}, new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 2F, 8F, 6F}),
                new FrameBox(10F, 10F, 12F, 12F, 12F, 16F,
                        new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{6F, 0F, 8F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{6F, 2F, 8F, 6F}, new float[]{6F, 2F, 8F, 6F}),
                new FrameBox(4F, 10F, 12F, 6F, 12F, 16F,
                        new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 2F, 2F, 6F}, new float[]{0F, 2F, 2F, 6F}),
        });
        FRAME_ARM_BOXES.put(Direction.WEST, new FrameBox[]{
                new FrameBox(0F, 4F, 4F, 4F, 6F, 6F,
                        new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 6F, 2F, 8F}, new float[]{2F, 0F, 6F, 2F}, new float[]{2F, 6F, 6F, 8F}),
                new FrameBox(0F, 10F, 4F, 4F, 12F, 6F,
                        new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{2F, 6F, 6F, 8F}),
                new FrameBox(0F, 10F, 10F, 4F, 12F, 12F,
                        new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{6F, 0F, 8F, 2F}, new float[]{2F, 6F, 6F, 8F}, new float[]{2F, 0F, 6F, 2F}),
                new FrameBox(0F, 4F, 10F, 4F, 6F, 12F,
                        new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 6F, 6F, 8F}, new float[]{6F, 6F, 8F, 8F}, new float[]{2F, 6F, 6F, 8F}, new float[]{2F, 0F, 6F, 2F}),
        });
        FRAME_ARM_BOXES.put(Direction.EAST, new FrameBox[]{
                new FrameBox(12F, 4F, 4F, 16F, 6F, 6F,
                        new float[]{2F, 6F, 6F, 8F}, new float[]{6F, 6F, 8F, 8F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{2F, 6F, 6F, 8F}),
                new FrameBox(12F, 4F, 10F, 16F, 6F, 12F,
                        new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 6F, 2F, 8F}, new float[]{2F, 6F, 6F, 8F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 6F, 6F, 8F}, new float[]{2F, 0F, 6F, 2F}),
                new FrameBox(12F, 10F, 10F, 16F, 12F, 12F,
                        new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 6F, 6F, 8F}, new float[]{2F, 0F, 6F, 2F}),
                new FrameBox(12F, 10F, 4F, 16F, 12F, 6F,
                        new float[]{2F, 0F, 6F, 2F}, new float[]{6F, 0F, 8F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{0F, 0F, 2F, 2F}, new float[]{2F, 0F, 6F, 2F}, new float[]{2F, 6F, 6F, 8F}),
        });
    }

    //tintIndex -1 (no tint at all) - unlike every other box addBox draws, Aluminum Frame's own texture is a
    //real painted material sampled with real UV, not a solid-color stripe meant to be recolored per face (see
    //this class's own comment on why plain wire specifically needs that tinting and Insulated/Bundled don't).
    private void addFrameBox(List<BakedQuad> quads, FrameBox box) {
        Vector3f from = new Vector3f(box.x1(), box.y1(), box.z1());
        Vector3f to = new Vector3f(box.x2(), box.y2(), box.z2());
        bakeFrameFace(quads, from, to, Direction.NORTH, box.north());
        bakeFrameFace(quads, from, to, Direction.EAST, box.east());
        bakeFrameFace(quads, from, to, Direction.SOUTH, box.south());
        bakeFrameFace(quads, from, to, Direction.WEST, box.west());
        bakeFrameFace(quads, from, to, Direction.UP, box.up());
        bakeFrameFace(quads, from, to, Direction.DOWN, box.down());
    }

    private void bakeFrameFace(List<BakedQuad> quads, Vector3f from, Vector3f to, Direction facing, float[] uv) {
        BlockElementFace face = new BlockElementFace(null, -1, "#particle", new BlockFaceUV(uv, 0));
        quads.add(FACE_BAKERY.bakeQuad(from, to, face, frameSprite, facing, BlockModelRotation.X0_Y0, null, true));
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutout());
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return sprite;
    }
}
