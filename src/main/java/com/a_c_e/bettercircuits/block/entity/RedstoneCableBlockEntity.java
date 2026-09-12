package com.a_c_e.bettercircuits.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

//Phase 3: holds up to 6 independent per-face cables for a single block position (see RedstoneCableBlock's own
//class comment for the 3 connection rules this drives). Blockstate alone can't represent this - even the 3-way
//RedstoneSide × 4-direction state Phase 2 used per block already sat near vanilla's own practical ceiling for
//state-permutation count, and this is up to 6 independent copies of a similar space - so all of it lives here
//instead, and rendering is driven by this data via ModelData (see RedstoneCableBlock.getModelData/Phase 3b)
//rather than by blockstate JSON.
//
//Needs real client sync (getUpdateTag/getUpdatePacket below) unlike this mod's only other block entity
//(MosslingDenBlockEntity, server-only) - this data directly drives what gets rendered.
public class RedstoneCableBlockEntity extends BlockEntity {
    //Snapshot of `faces` handed to the baked model (RedstoneCableBakedModel) via ModelData - a plain
    //Map<Direction, FaceState> rather than anything fancier since the model just needs to read it back out
    //per active face, same shape as the live map here (copied immutably per ModelData's own thread-safety rule).
    public static final ModelProperty<Map<Direction, FaceState>> FACES_PROPERTY = new ModelProperty<>();

    //Aluminum Frame: a third, independent kind of thing this same block entity can hold at a position,
    //alongside whatever cable faces (if any) already live here - see RedstoneCableBlock's own addFrame/
    //removeFrame. hasFrame is the presence flag (a frame with zero connected arms still has hasFrame == true,
    //so this can't just be "frameArms is non-empty"); frameArms is which of the 6 directions currently has an
    //adjacent Frame to connect to (a plain direct-neighbor check - see RedstoneCableBlock's own
    //refreshAndNotify comment for why this needs no diagonal/shared-anchor rules the way cable faces do).
    public static final ModelProperty<Boolean> HAS_FRAME_PROPERTY = new ModelProperty<>();
    public static final ModelProperty<Set<Direction>> FRAME_ARMS_PROPERTY = new ModelProperty<>();

    //Aluminum Frame's actual purpose: a 7th slot alongside the 6 wall-mounted faces, only ever occupied when
    //hasFrame() is true (see RedstoneCableBlock's own addCenter/removeFrame) - a single Bundled or Insulated
    //Redstone Cable (never plain - see addCenter's own check) mounted in the middle of the block, in the gap
    //inside the frame's own hollow lattice. Reuses FaceState wholesale rather than a parallel type - a center
    //cable needs the exact same (connections, power, color, bundled, channels) shape a face already has, just
    //without a Direction key of its own (it isn't mounted against any one support). null means empty.
    public static final ModelProperty<FaceState> CENTER_PROPERTY = new ModelProperty<>();

    private final Map<Direction, FaceState> faces = new EnumMap<>(Direction.class);
    private boolean hasFrame;
    private Set<Direction> frameArms = EnumSet.noneOf(Direction.class);
    @Nullable
    private FaceState centerFace;

    public RedstoneCableBlockEntity(BlockPos pos, BlockState state) {
        super(BCBlockEntityTypes.REDSTONE_CABLE.get(), pos, state);
    }

    public boolean hasFace(Direction towardSupport) {
        return faces.containsKey(towardSupport);
    }

    public boolean hasAnyFace() {
        return !faces.isEmpty();
    }

    public Set<Direction> activeFaces() {
        return faces.keySet();
    }

    @Nullable
    public FaceState getFace(Direction towardSupport) {
        return faces.get(towardSupport);
    }

    public void setFace(Direction towardSupport, FaceState state) {
        faces.put(towardSupport, state);
        changed();
    }

    public void removeFace(Direction towardSupport) {
        if (faces.remove(towardSupport) != null) {
            changed();
        }
    }

    public boolean hasFrame() {
        return hasFrame;
    }

    public void setHasFrame(boolean hasFrame) {
        if (this.hasFrame != hasFrame) {
            this.hasFrame = hasFrame;
            changed();
        }
    }

    public Set<Direction> frameArms() {
        return frameArms;
    }

    public void setFrameArms(Set<Direction> frameArms) {
        Set<Direction> copy = frameArms.isEmpty() ? EnumSet.noneOf(Direction.class) : EnumSet.copyOf(frameArms);
        if (!this.frameArms.equals(copy)) {
            this.frameArms = copy;
            changed();
        }
    }

    public boolean hasCenter() {
        return centerFace != null;
    }

    @Nullable
    public FaceState getCenter() {
        return centerFace;
    }

    public void setCenter(FaceState centerFace) {
        this.centerFace = centerFace;
        changed();
    }

    public void removeCenter() {
        if (centerFace != null) {
            centerFace = null;
            changed();
        }
    }

    private void changed() {
        setChanged();
        requestModelDataUpdate();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    //One face's own data: which of its 4 in-plane directions are connected (see RedstoneCableBlock for how these
    //are computed - rules 2 and 3 from the class comment), its own power level, and (Insulated Redstone Cable)
    //which color it is - null for a plain Redstone Cable face, one of the 16 DyeColor values for an Insulated
    //Redstone Cable face. color is fixed for the lifetime of a face (set once in RedstoneCableBlock.addFace, never
    //reassigned by computeFace) - it's what a face IS, not something that gets recomputed. Immutable - a face's
    //state is always replaced wholesale via setFace() rather than mutated in place, matching how BlockState itself
    //works.
    //
    //connections includes the purely cosmetic "complete the straight line" padding RedstoneCableBlock.computeFace
    //applies (matching vanilla dust's own "a dead-end always runs the full length of the block" rendering
    //convention) - it drives the visual model, the collision shape, particle spawning, AND getSignal's power
    //check. A prior attempt split this into a separate real-vs-padded pair specifically so the padded arm
    //couldn't hard-power a block it never actually touched - abandoned (see RedstoneCableBlock.getSignal's own
    //comment) once it turned out unable to reproduce the "soft-powers a piston, doesn't power dust" distinction
    //real redstone dust has for an analogous vanilla setup: a piston and dust sitting on/against the SAME
    //intermediate block both read that block's getSignal, which resolves identically for both regardless of
    //which one is asking - there was no lever left to pull from the cable's own side. Accepted as a known
    //limitation instead: this cable's padded arm can hard-power dust the way real vanilla wire wouldn't.
    //Bundled Redstone Cable: a THIRD kind of face alongside plain (color == null, bundled == false) and
    //Insulated (color != null) - bundled is always uncolored (there's only one Bundled Cable item, not one per
    //DyeColor) so it needs its own marker rather than overloading color. Fixed for the lifetime of a face, same
    //as color - see this record's own comment above color for why. Bundled Cable's own connectivity rule (self +
    //Insulated only, never plain cable or a generic redstone component) lives in RedstoneCableBlock's own
    //cablesCompatible, not here; this field only records WHAT a face is, matching color's own role.
    //
    //channels is Bundled Cable's own payload: a sparse per-DyeColor strength map, one entry per color currently
    //carried through this bundled line (absent = 0/unpowered, matching how a color that was never fed in, or
    //whose only source was removed, should read on a freshly attached Insulated Cable of that color). Always
    //Map.of() for plain/Insulated faces - only RedstoneCableBlock's own network flood (propagateNetwork) ever
    //writes a non-empty map here, and only onto bundled faces.
    public record FaceState(Set<Direction> connections, int power, @Nullable DyeColor color, boolean bundled, Map<DyeColor, Integer> channels) {
        public static FaceState empty(@Nullable DyeColor color, boolean bundled) {
            return new FaceState(Set.of(), 0, color, bundled, Map.of());
        }

        public FaceState {
            connections = Set.copyOf(connections);
            channels = Map.copyOf(channels);
        }

        public int channel(DyeColor color) {
            return channels.getOrDefault(color, 0);
        }

        public boolean isConnected(Direction direction) {
            return connections.contains(direction);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        faces.clear();
        ListTag list = tag.getList("Faces", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Direction towardSupport = Direction.values()[entry.getInt("Face")];
            faces.put(towardSupport, readFaceState(entry));
        }
        hasFrame = tag.getBoolean("HasFrame");
        frameArms = EnumSet.noneOf(Direction.class);
        for (int ordinal : tag.getIntArray("FrameArms")) {
            frameArms.add(Direction.values()[ordinal]);
        }
        centerFace = tag.contains("Center") ? readFaceState(tag.getCompound("Center")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Map.Entry<Direction, FaceState> entry : faces.entrySet()) {
            CompoundTag faceTag = writeFaceState(entry.getValue());
            faceTag.putInt("Face", entry.getKey().ordinal());
            list.add(faceTag);
        }
        tag.put("Faces", list);
        tag.putBoolean("HasFrame", hasFrame);
        tag.putIntArray("FrameArms", frameArms.stream().mapToInt(Direction::ordinal).toArray());
        if (centerFace != null) {
            tag.put("Center", writeFaceState(centerFace));
        }
    }

    //Shared by a Faces list entry and the single Center tag - both store the exact same FaceState fields, just
    //at different tag shapes (Center has no "Face" direction key of its own - see FaceState's own comment on
    //why a center cable doesn't need one).
    private static FaceState readFaceState(CompoundTag entry) {
        Set<Direction> connections = EnumSet.noneOf(Direction.class);
        for (int ordinal : entry.getIntArray("Connections")) {
            connections.add(Direction.values()[ordinal]);
        }
        //-1 sentinel (see writeFaceState) means a plain, uncolored face - DyeColor has no "none" value of its
        //own to round-trip through byId. A face saved BEFORE this field existed has no "Color" key at all -
        //CompoundTag#getInt silently returns 0 (not -1) for a missing key, which would otherwise reinterpret
        //every already-placed plain cable face in an existing world as White Insulated Cable, so a missing
        //key needs its own explicit check rather than falling through getInt's own default.
        int colorId = entry.contains("Color") ? entry.getInt("Color") : -1;
        DyeColor color = colorId >= 0 ? DyeColor.byId(colorId) : null;
        boolean bundled = entry.getBoolean("Bundled");
        //Channels: a flat int array, one slot per DyeColor ordinal (16 total), 0 meaning absent - simpler and
        //more robust than a sparse list against a missing/short array from a face saved before this field
        //existed (getIntArray silently returns an empty array, which the loop below just skips entirely).
        Map<DyeColor, Integer> channels = new EnumMap<>(DyeColor.class);
        int[] channelArray = entry.getIntArray("Channels");
        for (int c = 0; c < channelArray.length; c++) {
            if (channelArray[c] > 0) {
                channels.put(DyeColor.byId(c), channelArray[c]);
            }
        }
        return new FaceState(connections, entry.getInt("Power"), color, bundled, channels);
    }

    private static CompoundTag writeFaceState(FaceState state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Power", state.power());
        tag.putIntArray("Connections", state.connections().stream().mapToInt(Direction::ordinal).toArray());
        DyeColor color = state.color();
        tag.putInt("Color", color != null ? color.getId() : -1);
        tag.putBoolean("Bundled", state.bundled());
        Map<DyeColor, Integer> channels = state.channels();
        if (!channels.isEmpty()) {
            int[] channelArray = new int[DyeColor.values().length];
            for (Map.Entry<DyeColor, Integer> channelEntry : channels.entrySet()) {
                channelArray[channelEntry.getKey().getId()] = channelEntry.getValue();
            }
            tag.putIntArray("Channels", channelArray);
        }
        return tag;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder()
                .with(FACES_PROPERTY, Map.copyOf(faces))
                .with(HAS_FRAME_PROPERTY, hasFrame)
                .with(FRAME_ARMS_PROPERTY, Set.copyOf(frameArms))
                .with(CENTER_PROPERTY, centerFace)
                .build();
    }

    //requestModelDataUpdate() (called from changed() below) only has any effect when invoked CLIENT-SIDE - but
    //changed() only ever runs server-side (setFace/removeFace are only called from RedstoneCableBlock's
    //server-only code paths). The client's own copy of this block entity only learns about updated face data
    //through these two vanilla sync entry points, so the model-data refresh + explicit chunk-rebuild trigger
    //have to happen here instead, on receipt, not in changed(). Without this, only the very first placement ever
    //rendered correctly (that one goes through a real blockstate transition, air -> cable, which independently
    //triggers a chunk rebuild) - every later face/connection/power change updated the block entity's data just
    //fine but never told the client to rebuild that chunk's mesh.
    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(connection, packet, registries);
        onClientDataUpdated();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        onClientDataUpdated();
    }

    private void onClientDataUpdated() {
        requestModelDataUpdate();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
