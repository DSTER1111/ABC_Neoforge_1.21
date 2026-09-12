package com.a_c_e.bettercircuits.datagen;

import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.block.AndGateBlock;
import com.a_c_e.bettercircuits.block.BCBlocks;
import com.a_c_e.bettercircuits.block.BlowerBlock;
import com.a_c_e.bettercircuits.block.CapacitorBlock;
import com.a_c_e.bettercircuits.block.ComparatorRailBlock;
import com.a_c_e.bettercircuits.block.InverterBlock;
import com.a_c_e.bettercircuits.block.RSLatchBlock;
import com.a_c_e.bettercircuits.block.RainDetectorBlock;
import com.a_c_e.bettercircuits.block.HeatSensorBlock;
import com.a_c_e.bettercircuits.block.RandomizerBlock;
import com.a_c_e.bettercircuits.block.RedstoneThresholdBlock;
import com.a_c_e.bettercircuits.block.TimerBlock;
import com.a_c_e.bettercircuits.block.VacuumBlock;
import com.a_c_e.bettercircuits.block.XorGateBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightAndGateBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightCapacitorBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightComparatorBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightInverterBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightRSLatchBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightRandomizerBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightRedstoneThresholdBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightRepeaterBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightTimerBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightXorGateBlock;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

//Datagen port of MoreBetter's MBBlockStateProvider, restricted to the redstone-only subset of features in scope
//for Better Circuits (see the porting task's own scope list) - method bodies are a mechanical 1:1 port (same
//geometry/texture logic), just re-targeted at BCBlocks/BetterCircuits.MOD_ID. Aluminum Frame is NOT ported - the
//Redstone Cable block in this mod has zero blockstate properties (no WATERLOGGED, no frame), so redstoneCableItem
//below only ever needs the plain "invisible render, item gets its own icon" setup, never a frame-core model.
public class BCBlockStateProvider extends BlockStateProvider {
    private static final ResourceLocation CUTOUT = ResourceLocation.fromNamespaceAndPath("minecraft", "cutout");
    private static final ResourceLocation CUTOUT_MIPPED = ResourceLocation.fromNamespaceAndPath("minecraft", "cutout_mipped");

    public BCBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
        super(output, BetterCircuits.MOD_ID, exFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        blockWithItem(BCBlocks.ALUMINUM_BLOCK);
        blockWithItem(BCBlocks.ALUMINUM_ORE);
        blockWithItem(BCBlocks.DEEPSLATE_ALUMINUM_ORE);
        blockWithItem(BCBlocks.RAW_ALUMINUM_BLOCK);

        aluminumLantern();
        aluminumTorch();
        aluminumChain();
        aluminumBars();
        aluminumDoor();
        aluminumTrapdoor();
        aluminumGrate();

        andGate();
        xorGate();
        inverter();
        redstoneThreshold();
        lightweightRedstoneThreshold();
        rsLatch();
        timer();
        rainDetector();
        heatSensor();
        capacitor();
        randomizer();
        lightweightInverter();
        lightweightAndGate();
        lightweightXorGate();
        lightweightRepeater();
        lightweightRSLatch();
        lightweightTimer();
        lightweightComparator();
        lightweightCapacitor();
        lightweightRandomizer();

        redstoneCableItem();
        for (DyeColor color : DyeColor.values()) {
            insulatedCableItem(color);
        }
        bundledCableItem();
        aluminumFrameItem();

        comparatorRail();
        filteredHopper();
        blower();
        vacuum();
    }

    //RedstoneCableBlock renders as RenderShape.MODEL but the real geometry comes from RedstoneCableBakedModel via
    //ModelData (see BetterCircuits.ClientModEvents), so this model is never actually used to draw geometry - it
    //exists only so the block has SOME model to point to (avoiding a "missing model for variant" warning) and so
    //break particles have a texture to sample. The item still needs its own separate icon regardless.
    private void redstoneCableItem() {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/redstone_cable");
        ModelFile blockModel = models().getBuilder("redstone_cable").texture("particle", texture);
        simpleBlock(BCBlocks.REDSTONE_CABLE.get(), blockModel);
        itemModels().withExistingParent("redstone_cable", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/redstone_cable"));
    }

    //Insulated Redstone Cable items only ever need their own icon - there's no separate Block/blockstate per
    //color (a single RedstoneCableBlock/BlockEntity hosts every color, see RedstoneCableBlockItem), so the one
    //blockmodel/simpleBlock call redstoneCableItem() already made covers every color's in-world rendering too
    //(RedstoneCableBakedModel picks the matching colored sprite itself, per face, at render time).
    private void insulatedCableItem(DyeColor color) {
        String name = color.getName() + "_insulated_cable";
        itemModels().withExistingParent(name, ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/" + name));
    }

    //Bundled Redstone Cable - same "icon only, no separate block/blockstate" situation as insulatedCableItem
    //above, just a single item instead of one per DyeColor.
    private void bundledCableItem() {
        String name = "bundled_redstone_cable";
        itemModels().withExistingParent(name, ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/" + name));
    }

    //Aluminum Frame - places the exact same shared RedstoneCableBlock as the cable items above (see
    //AluminumFrameBlockItem's own class comment), so this only ever needs its own item icon, parented onto a
    //literal transcription of the reference model's own core lattice geometry (the frame's real rendered
    //geometry when placed in the world comes from RedstoneCableBakedModel, not this model - this is purely for
    //the inventory/hand icon).
    private void aluminumFrameItem() {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_frame");
        BlockModelBuilder coreModel = frameModel("aluminum_frame_core", texture);
        addFrameCoreBoxes(coreModel);
        itemModels().withExistingParent("aluminum_frame", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_frame_core"));
    }

    //Parented onto vanilla's own block/block purely for its "display" transforms (gui/ground/fixed/*person-hand)
    //- same reasoning as blower/vacuum's own models above, needed because the item icon parents directly onto
    //the "core" block model rather than a flat 2D icon.
    private BlockModelBuilder frameModel(String name, ResourceLocation texture) {
        return models().withExistingParent(name, ResourceLocation.fromNamespaceAndPath("minecraft", "block/block"))
                .texture("particle", texture)
                .texture("1", texture);
    }

    private static void addBox(BlockModelBuilder model, float[] from, float[] to,
                                int[] north, int[] east, int[] south, int[] west, int[] up, int[] down) {
        model.element()
                .from(from[0], from[1], from[2]).to(to[0], to[1], to[2])
                .face(Direction.NORTH).uvs(north[0], north[1], north[2], north[3]).texture("#1").end()
                .face(Direction.EAST).uvs(east[0], east[1], east[2], east[3]).texture("#1").end()
                .face(Direction.SOUTH).uvs(south[0], south[1], south[2], south[3]).texture("#1").end()
                .face(Direction.WEST).uvs(west[0], west[1], west[2], west[3]).texture("#1").end()
                .face(Direction.UP).uvs(up[0], up[1], up[2], up[3]).texture("#1").end()
                .face(Direction.DOWN).uvs(down[0], down[1], down[2], down[3]).texture("#1").end()
                .end();
    }

    private static void addFrameCoreBoxes(BlockModelBuilder model) {
        addBox(model, new float[]{4, 4, 4}, new float[]{6, 12, 6},
                new int[]{6, 0, 8, 8}, new int[]{0, 0, 2, 8}, new int[]{6, 0, 8, 8}, new int[]{0, 0, 2, 8}, new int[]{0, 0, 2, 2}, new int[]{0, 6, 2, 8});
        addBox(model, new float[]{4, 10, 6}, new float[]{6, 12, 10},
                new int[]{1, 1, 3, 3}, new int[]{2, 6, 6, 8}, new int[]{0, 1, 2, 3}, new int[]{2, 0, 6, 2}, new int[]{0, 2, 2, 6}, new int[]{6, 2, 8, 6});
        addBox(model, new float[]{10, 10, 6}, new float[]{12, 12, 10},
                new int[]{1, 1, 3, 3}, new int[]{2, 0, 6, 2}, new int[]{0, 1, 2, 3}, new int[]{2, 6, 6, 8}, new int[]{6, 2, 8, 6}, new int[]{0, 2, 2, 6});
        addBox(model, new float[]{6, 10, 4}, new float[]{10, 12, 6},
                new int[]{2, 0, 6, 2}, new int[]{2, 1, 4, 3}, new int[]{2, 6, 6, 8}, new int[]{0, 1, 2, 3}, new int[]{2, 0, 6, 2}, new int[]{2, 0, 6, 2});
        addBox(model, new float[]{6, 10, 10}, new float[]{10, 12, 12},
                new int[]{2, 6, 6, 8}, new int[]{2, 1, 4, 3}, new int[]{2, 0, 6, 2}, new int[]{0, 1, 2, 3}, new int[]{2, 6, 6, 8}, new int[]{2, 6, 6, 8});
        addBox(model, new float[]{6, 4, 10}, new float[]{10, 6, 12},
                new int[]{2, 0, 6, 2}, new int[]{2, 1, 4, 3}, new int[]{2, 6, 6, 8}, new int[]{0, 1, 2, 3}, new int[]{2, 0, 6, 2}, new int[]{2, 0, 6, 2});
        addBox(model, new float[]{6, 4, 4}, new float[]{10, 6, 6},
                new int[]{2, 6, 6, 8}, new int[]{2, 1, 4, 3}, new int[]{2, 0, 6, 2}, new int[]{0, 1, 2, 3}, new int[]{2, 6, 6, 8}, new int[]{2, 6, 6, 8});
        addBox(model, new float[]{10, 4, 6}, new float[]{12, 6, 10},
                new int[]{1, 1, 3, 3}, new int[]{2, 6, 6, 8}, new int[]{0, 1, 2, 3}, new int[]{2, 0, 6, 2}, new int[]{0, 2, 2, 6}, new int[]{6, 2, 8, 6});
        addBox(model, new float[]{4, 4, 6}, new float[]{6, 6, 10},
                new int[]{1, 1, 3, 3}, new int[]{2, 0, 6, 2}, new int[]{0, 1, 2, 3}, new int[]{2, 6, 6, 8}, new int[]{6, 2, 8, 6}, new int[]{0, 2, 2, 6});
        addBox(model, new float[]{4, 4, 10}, new float[]{6, 12, 12},
                new int[]{0, 0, 2, 8}, new int[]{6, 0, 8, 8}, new int[]{0, 0, 2, 8}, new int[]{6, 0, 8, 8}, new int[]{0, 6, 2, 8}, new int[]{0, 0, 2, 2});
        addBox(model, new float[]{10, 4, 10}, new float[]{12, 12, 12},
                new int[]{6, 0, 8, 8}, new int[]{0, 0, 2, 8}, new int[]{6, 0, 8, 8}, new int[]{0, 0, 2, 8}, new int[]{6, 6, 8, 8}, new int[]{6, 0, 8, 2});
        addBox(model, new float[]{10, 4, 4}, new float[]{12, 12, 6},
                new int[]{0, 0, 2, 8}, new int[]{6, 0, 8, 8}, new int[]{0, 0, 2, 8}, new int[]{6, 0, 8, 8}, new int[]{6, 0, 8, 2}, new int[]{6, 6, 8, 8});
    }

    //Comparator Rail (see ComparatorRailBlock) - same 3-shape/2-texture structure vanilla's own Detector Rail
    //model set uses (block/rail_flat for the flat shapes, block/template_rail_raised_ne/sw for the two ascending
    //directions, each reused with a 90-degree y rotation for its mirrored counterpart), just swapping vanilla's
    //own POWERED boolean for "is SIGNAL > 0".
    private void comparatorRail() {
        ResourceLocation offTexture = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/comparator_rail");
        ResourceLocation onTexture = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/comparator_rail_on");
        ResourceLocation railFlat = ResourceLocation.fromNamespaceAndPath("minecraft", "block/rail_flat");
        ResourceLocation railRaisedNe = ResourceLocation.fromNamespaceAndPath("minecraft", "block/template_rail_raised_ne");
        ResourceLocation railRaisedSw = ResourceLocation.fromNamespaceAndPath("minecraft", "block/template_rail_raised_sw");
        ModelFile flatOff = models().withExistingParent("comparator_rail", railFlat).texture("rail", offTexture).renderType(CUTOUT);
        ModelFile flatOn = models().withExistingParent("comparator_rail_on", railFlat).texture("rail", onTexture).renderType(CUTOUT);
        ModelFile raisedNeOff = models().withExistingParent("comparator_rail_raised_ne", railRaisedNe).texture("rail", offTexture).renderType(CUTOUT);
        ModelFile raisedNeOn = models().withExistingParent("comparator_rail_on_raised_ne", railRaisedNe).texture("rail", onTexture).renderType(CUTOUT);
        ModelFile raisedSwOff = models().withExistingParent("comparator_rail_raised_sw", railRaisedSw).texture("rail", offTexture).renderType(CUTOUT);
        ModelFile raisedSwOn = models().withExistingParent("comparator_rail_on_raised_sw", railRaisedSw).texture("rail", onTexture).renderType(CUTOUT);

        getVariantBuilder(BCBlocks.COMPARATOR_RAIL.get()).forAllStates(state -> {
            boolean on = state.getValue(ComparatorRailBlock.SIGNAL) > 0;
            int yRotation = 0;
            ModelFile model;
            switch (state.getValue(ComparatorRailBlock.SHAPE)) {
                case EAST_WEST -> { model = on ? flatOn : flatOff; yRotation = 90; }
                case ASCENDING_EAST -> { model = on ? raisedNeOn : raisedNeOff; yRotation = 90; }
                case ASCENDING_WEST -> { model = on ? raisedSwOn : raisedSwOff; yRotation = 90; }
                case ASCENDING_SOUTH -> model = on ? raisedSwOn : raisedSwOff;
                case ASCENDING_NORTH -> model = on ? raisedNeOn : raisedNeOff;
                default -> model = on ? flatOn : flatOff;
            }
            return ConfiguredModel.builder().modelFile(model).rotationY(yRotation).build();
        });

        itemModels().withExistingParent("comparator_rail", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", offTexture);
    }

    //Filtered Hopper (see FilteredHopperBlock) - looks exactly like a regular hopper (the frame+item are drawn
    //separately by FilteredHopperBlockEntityRenderer), so this just parents straight onto vanilla's own two
    //hopper models with the exact same per-facing y-rotation vanilla's own hopper.json blockstate uses.
    private void filteredHopper() {
        ModelFile hopperModel = models().withExistingParent("filtered_hopper",
                ResourceLocation.fromNamespaceAndPath("minecraft", "block/hopper"));
        ModelFile hopperSideModel = models().withExistingParent("filtered_hopper_side",
                ResourceLocation.fromNamespaceAndPath("minecraft", "block/hopper_side"));
        getVariantBuilder(BCBlocks.FILTERED_HOPPER.get()).forAllStates(state -> {
            Direction facing = state.getValue(HopperBlock.FACING);
            if (facing == Direction.DOWN) {
                return ConfiguredModel.builder().modelFile(hopperModel).build();
            }
            int yRotation = switch (facing) {
                case EAST -> 90;
                case SOUTH -> 180;
                case WEST -> 270;
                default -> 0;
            };
            return ConfiguredModel.builder().modelFile(hopperSideModel).rotationY(yRotation).build();
        });

        itemModels().withExistingParent("filtered_hopper", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/filtered_hopper"));
    }

    //Blower (see BlowerBlock) - a single base model authored with front on the north face, back (furnace_top) on
    //south, and blower_side on the other 4 faces, each given the exact per-face rotation vanilla's own
    //template_piston.json uses for its own "side" texture.
    private void blower() {
        ResourceLocation front = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/blower_front");
        ResourceLocation side = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/blower_side");
        ResourceLocation back = ResourceLocation.fromNamespaceAndPath("minecraft", "block/furnace_top");

        BlockModelBuilder builder = models().withExistingParent("blower", ResourceLocation.fromNamespaceAndPath("minecraft", "block/block"))
                .texture("particle", front)
                .texture("front", front)
                .texture("side", side)
                .texture("back", back);

        builder.element()
                .from(0, 0, 0).to(16, 16, 16)
                .face(Direction.NORTH).uvs(0, 0, 16, 16).texture("#front").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 0, 16, 16).texture("#back").cullface(Direction.SOUTH).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#side").cullface(Direction.UP).end()
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#side").rotation(ModelBuilder.FaceRotation.UPSIDE_DOWN).cullface(Direction.DOWN).end()
                .face(Direction.EAST).uvs(0, 0, 16, 16).texture("#side").rotation(ModelBuilder.FaceRotation.CLOCKWISE_90).cullface(Direction.EAST).end()
                .face(Direction.WEST).uvs(0, 0, 16, 16).texture("#side").rotation(ModelBuilder.FaceRotation.COUNTERCLOCKWISE_90).cullface(Direction.WEST).end()
                .end();

        getVariantBuilder(BCBlocks.BLOWER.get()).forAllStates(state -> {
            Direction facing = state.getValue(BlowerBlock.FACING);
            int xRotation = switch (facing) {
                case DOWN -> 90;
                case UP -> 270;
                default -> 0;
            };
            int yRotation = switch (facing) {
                case EAST -> 90;
                case SOUTH -> 180;
                case WEST -> 270;
                default -> 0;
            };
            return ConfiguredModel.builder().modelFile(builder).rotationX(xRotation).rotationY(yRotation).build();
        });

        itemModels().withExistingParent("blower", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/blower"));
    }

    //Vacuum (see VacuumBlock) - literally Blower's own model in reverse: same textures, same base geometry and
    //the same Piston-derived x/y whole-model rotation set for the 6 facings, but each of the 4 side faces'
    //rotation is flipped 180 degrees from Blower's own so blower_side's "up" edge points at the BACK face
    //instead of the front.
    private void vacuum() {
        ResourceLocation front = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/blower_front");
        ResourceLocation side = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/blower_side");
        ResourceLocation back = ResourceLocation.fromNamespaceAndPath("minecraft", "block/furnace_top");

        BlockModelBuilder builder = models().withExistingParent("vacuum", ResourceLocation.fromNamespaceAndPath("minecraft", "block/block"))
                .texture("particle", front)
                .texture("front", front)
                .texture("side", side)
                .texture("back", back);

        builder.element()
                .from(0, 0, 0).to(16, 16, 16)
                .face(Direction.NORTH).uvs(0, 0, 16, 16).texture("#front").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 0, 16, 16).texture("#back").cullface(Direction.SOUTH).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#side").rotation(ModelBuilder.FaceRotation.UPSIDE_DOWN).cullface(Direction.UP).end()
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#side").cullface(Direction.DOWN).end()
                .face(Direction.EAST).uvs(0, 0, 16, 16).texture("#side").rotation(ModelBuilder.FaceRotation.COUNTERCLOCKWISE_90).cullface(Direction.EAST).end()
                .face(Direction.WEST).uvs(0, 0, 16, 16).texture("#side").rotation(ModelBuilder.FaceRotation.CLOCKWISE_90).cullface(Direction.WEST).end()
                .end();

        getVariantBuilder(BCBlocks.VACUUM.get()).forAllStates(state -> {
            Direction facing = state.getValue(VacuumBlock.FACING);
            int xRotation = switch (facing) {
                case DOWN -> 90;
                case UP -> 270;
                default -> 0;
            };
            int yRotation = switch (facing) {
                case EAST -> 90;
                case SOUTH -> 180;
                case WEST -> 270;
                default -> 0;
            };
            return ConfiguredModel.builder().modelFile(builder).rotationX(xRotation).rotationY(yRotation).build();
        });

        itemModels().withExistingParent("vacuum", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/vacuum"));
    }

    //Custom element-based model - no vanilla template fits a redstone diode, so this mirrors vanilla's own
    //comparator.json/repeater.json geometry technique directly: a 2px smooth_stone slab plus 3 short torches
    //built from simple boxes.
    private void andGate() {
        Block andGate = BCBlocks.AND_GATE.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath("minecraft", "block/smooth_stone");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");
        ResourceLocation topOff = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/and_gate");
        ResourceLocation topOn = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/and_gate_on");

        ModelFile[][][] models = new ModelFile[2][2][2];
        for (int l = 0; l < 2; l++) {
            for (int r = 0; r < 2; r++) {
                for (int p = 0; p < 2; p++) {
                    boolean leftPowered = l == 1;
                    boolean rightPowered = r == 1;
                    boolean powered = p == 1;
                    String name = "and_gate" + (powered ? "_on" : "") + "_l" + l + "_r" + r;
                    models[l][r][p] = andGateModel(name, slab, powered ? topOn : topOff, lit, unlit, lock,
                            leftPowered, rightPowered, powered, false);
                }
            }
        }
        ModelFile lockedOnModel = andGateModel("and_gate_on_locked", slab, topOn, lit, unlit, lock, false, false, true, true);
        ModelFile lockedOffModel = andGateModel("and_gate_locked", slab, topOff, lit, unlit, lock, false, false, false, true);

        getVariantBuilder(andGate).forAllStates(state -> {
            Direction facing = state.getValue(AndGateBlock.FACING);
            boolean leftPowered = state.getValue(AndGateBlock.LEFT_POWERED);
            boolean rightPowered = state.getValue(AndGateBlock.RIGHT_POWERED);
            boolean powered = state.getValue(AndGateBlock.POWERED);
            boolean locked = state.getValue(AndGateBlock.LOCKED);
            int yRot = switch (facing) {
                case SOUTH -> 0;
                case WEST -> 90;
                case NORTH -> 180;
                case EAST -> 270;
                default -> 0;
            };
            ModelFile model = locked
                    ? (powered ? lockedOnModel : lockedOffModel)
                    : models[leftPowered ? 1 : 0][rightPowered ? 1 : 0][powered ? 1 : 0];
            return ConfiguredModel.builder().modelFile(model).rotationY(yRot).build();
        });

        itemModels().withExistingParent("and_gate", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/and_gate"));
    }

    private ModelFile andGateModel(String name, ResourceLocation slab, ResourceLocation top,
                                    ResourceLocation lit, ResourceLocation unlit, ResourceLocation lock,
                                    boolean leftPowered, boolean rightPowered, boolean outputActive, boolean locked) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lit", lit)
                .texture("unlit", unlit)
                .texture("lock", lock);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        if (locked) {
            addLockBar(builder);
        } else {
            addAndGateTorch(builder, 12, 14, 7, 9, !leftPowered);
            addAndGateTorch(builder, 2, 4, 7, 9, !rightPowered);
        }
        addAndGateTorch(builder, 7, 9, 2, 4, outputActive);

        return builder;
    }

    private void addAndGateTorch(BlockModelBuilder builder, float x1, float x2, float z1, float z2, boolean lit) {
        String texture = lit ? "#lit" : "#unlit";
        if (!lit) {
            builder.element()
                    .from(x1, 2, z1).to(x2, 7, z2)
                    .face(Direction.DOWN).uvs(7, 13, 9, 15).texture(texture).end()
                    .face(Direction.UP).uvs(7, 6, 9, 8).texture(texture).end()
                    .face(Direction.NORTH).uvs(7, 6, 9, 11).texture(texture).end()
                    .face(Direction.SOUTH).uvs(7, 6, 9, 11).texture(texture).end()
                    .face(Direction.WEST).uvs(7, 6, 9, 11).texture(texture).end()
                    .face(Direction.EAST).uvs(7, 6, 9, 11).texture(texture).end()
                    .end();
            return;
        }
        builder.element()
                .from(x1, 7, z1).to(x2, 7, z2)
                .face(Direction.UP).uvs(7, 6, 9, 8).texture(texture).end()
                .end();
        builder.element()
                .from(x1, 2, z1 - 1).to(x2, 8, z2 + 1)
                .face(Direction.WEST).uvs(6, 5, 10, 11).texture(texture).end()
                .face(Direction.EAST).uvs(6, 5, 10, 11).texture(texture).end()
                .end();
        builder.element()
                .from(x1 - 1, 2, z1).to(x2 + 1, 8, z2)
                .face(Direction.NORTH).uvs(6, 5, 10, 11).texture(texture).end()
                .face(Direction.SOUTH).uvs(6, 5, 10, 11).texture(texture).end()
                .end();
    }

    private void xorGate() {
        Block xorGate = BCBlocks.XOR_GATE.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath("minecraft", "block/smooth_stone");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");
        ResourceLocation offOff = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/xor_gate_off_off");
        ResourceLocation offOn = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/xor_gate_off_on");
        ResourceLocation onLeft = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/xor_gate_on_left");
        ResourceLocation onRight = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/xor_gate_on_right");

        ModelFile offOffModel = xorGateModel("xor_gate_off_off", slab, offOff, lock, false);
        ModelFile offOnModel = xorGateModel("xor_gate_off_on", slab, offOn, lock, false);
        ModelFile onLeftModel = xorGateModel("xor_gate_on_left", slab, onLeft, lock, false);
        ModelFile onRightModel = xorGateModel("xor_gate_on_right", slab, onRight, lock, false);
        ModelFile offOffLockedModel = xorGateModel("xor_gate_off_off_locked", slab, offOff, lock, true);
        ModelFile offOnLockedModel = xorGateModel("xor_gate_off_on_locked", slab, offOn, lock, true);
        ModelFile onLeftLockedModel = xorGateModel("xor_gate_on_left_locked", slab, onLeft, lock, true);
        ModelFile onRightLockedModel = xorGateModel("xor_gate_on_right_locked", slab, onRight, lock, true);

        getVariantBuilder(xorGate).forAllStates(state -> {
            Direction facing = state.getValue(XorGateBlock.FACING);
            boolean leftPowered = state.getValue(XorGateBlock.LEFT_POWERED);
            boolean rightPowered = state.getValue(XorGateBlock.RIGHT_POWERED);
            boolean locked = state.getValue(XorGateBlock.LOCKED);
            int yRot = switch (facing) {
                case SOUTH -> 0;
                case WEST -> 90;
                case NORTH -> 180;
                case EAST -> 270;
                default -> 0;
            };
            ModelFile model;
            if (leftPowered && rightPowered) {
                model = locked ? offOnLockedModel : offOnModel;
            } else if (leftPowered) {
                model = locked ? onRightLockedModel : onRightModel;
            } else if (rightPowered) {
                model = locked ? onLeftLockedModel : onLeftModel;
            } else {
                model = locked ? offOffLockedModel : offOffModel;
            }
            return ConfiguredModel.builder().modelFile(model).rotationY(yRot).build();
        });

        itemModels().withExistingParent("xor_gate", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/xor_gate"));
    }

    private ModelFile xorGateModel(String name, ResourceLocation slab, ResourceLocation top, ResourceLocation lock, boolean locked) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lock", lock);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        if (locked) {
            addLockBar(builder);
        }

        return builder;
    }

    private void inverter() {
        Block inverter = BCBlocks.INVERTER.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath("minecraft", "block/smooth_stone");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");
        ModelFile onModel = inverterModel("inverter_on", slab,
                ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/inverter_on"), lit, unlit, lock, true, false);
        ModelFile offModel = inverterModel("inverter_off", slab,
                ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/inverter_off"), lit, unlit, lock, false, false);
        ModelFile onLockedModel = inverterModel("inverter_on_locked", slab,
                ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/inverter_on"), lit, unlit, lock, true, true);
        ModelFile offLockedModel = inverterModel("inverter_off_locked", slab,
                ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/inverter_off"), lit, unlit, lock, false, true);

        getVariantBuilder(inverter).forAllStates(state -> {
            Direction facing = state.getValue(InverterBlock.FACING);
            boolean powered = state.getValue(InverterBlock.POWERED);
            boolean locked = state.getValue(InverterBlock.LOCKED);
            int yRot = switch (facing) {
                case SOUTH -> 0;
                case WEST -> 90;
                case NORTH -> 180;
                case EAST -> 270;
                default -> 0;
            };
            ModelFile model;
            if (locked) {
                model = powered ? onLockedModel : offLockedModel;
            } else {
                model = powered ? onModel : offModel;
            }
            return ConfiguredModel.builder().modelFile(model).rotationY(yRot).build();
        });

        itemModels().withExistingParent("inverter", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/inverter"));
    }

    private ModelFile inverterModel(String name, ResourceLocation slab, ResourceLocation top,
                                     ResourceLocation lit, ResourceLocation unlit, ResourceLocation lock,
                                     boolean powered, boolean locked) {
        return inverterModel(name, slab, top, lit, unlit, lock, powered, locked, 0);
    }

    private ModelFile inverterModel(String name, ResourceLocation slab, ResourceLocation top,
                                     ResourceLocation lit, ResourceLocation unlit, ResourceLocation lock,
                                     boolean powered, boolean locked, int localRotation) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lit", lit)
                .texture("unlit", unlit)
                .texture("lock", lock);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").rotation(faceRotation(localRotation)).end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        if (locked) {
            addRotatedLockBar(builder, localRotation);
        } else {
            addAndGateTorch(builder, 7, 9, 7, 9, powered);
        }

        return builder;
    }

    //LOCKED replaces the torch with a bedrock bar, same as InverterBlock's own model - see that method's own
    //comment for the exact geometry/UV source. The top texture still reflects the (now-frozen) POWERED value,
    //only the torch element is swapped out.
    private void redstoneThreshold() {
        Block redstoneThreshold = BCBlocks.REDSTONE_THRESHOLD.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath("minecraft", "block/smooth_stone");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");
        ResourceLocation onTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/threshold_on");
        ResourceLocation offTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/threshold");

        ModelFile onModel = redstoneThresholdModel("redstone_threshold_on", slab, onTop, lit, unlit, lock, true, false);
        ModelFile offModel = redstoneThresholdModel("redstone_threshold_off", slab, offTop, lit, unlit, lock, false, false);
        ModelFile onLockedModel = redstoneThresholdModel("redstone_threshold_on_locked", slab, onTop, lit, unlit, lock, true, true);
        ModelFile offLockedModel = redstoneThresholdModel("redstone_threshold_off_locked", slab, offTop, lit, unlit, lock, false, true);

        getVariantBuilder(redstoneThreshold).forAllStates(state -> {
            Direction facing = state.getValue(RedstoneThresholdBlock.FACING);
            boolean powered = state.getValue(RedstoneThresholdBlock.POWERED);
            boolean locked = state.getValue(RedstoneThresholdBlock.LOCKED);
            int yRot = switch (facing) {
                case SOUTH -> 0;
                case WEST -> 90;
                case NORTH -> 180;
                case EAST -> 270;
                default -> 0;
            };
            ModelFile model = locked
                    ? (powered ? onLockedModel : offLockedModel)
                    : (powered ? onModel : offModel);
            return ConfiguredModel.builder().modelFile(model).rotationY(yRot).build();
        });

        itemModels().withExistingParent("redstone_threshold", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/threshold"));
    }

    private ModelFile redstoneThresholdModel(String name, ResourceLocation slab, ResourceLocation top,
                                              ResourceLocation lit, ResourceLocation unlit, ResourceLocation lock,
                                              boolean powered, boolean locked) {
        return redstoneThresholdModel(name, slab, top, lit, unlit, lock, powered, locked, 0);
    }

    private ModelFile redstoneThresholdModel(String name, ResourceLocation slab, ResourceLocation top,
                                              ResourceLocation lit, ResourceLocation unlit, ResourceLocation lock,
                                              boolean powered, boolean locked, int localRotation) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lit", lit)
                .texture("unlit", unlit)
                .texture("lock", lock);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").rotation(faceRotation(localRotation)).end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        if (locked) {
            addRotatedLockBar(builder, localRotation);
        } else {
            addAndGateTorch(builder, 7, 9, 7, 9, powered);
        }

        return builder;
    }

    private void lightweightRedstoneThreshold() {
        Block threshold = BCBlocks.LIGHTWEIGHT_REDSTONE_THRESHOLD.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_block");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");
        ResourceLocation onTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_threshold_on");
        ResourceLocation offTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_threshold");

        Map<Integer, ModelFile> onModels = new HashMap<>();
        Map<Integer, ModelFile> offModels = new HashMap<>();
        Map<Integer, ModelFile> onLockedModels = new HashMap<>();
        Map<Integer, ModelFile> offLockedModels = new HashMap<>();
        for (int uvRot : new int[]{0, 90, 180, 270}) {
            onModels.put(uvRot, redstoneThresholdModel("lightweight_redstone_threshold_on_model_" + uvRot, slab, onTop, lit, unlit, lock, true, false, uvRot));
            offModels.put(uvRot, redstoneThresholdModel("lightweight_redstone_threshold_off_model_" + uvRot, slab, offTop, lit, unlit, lock, false, false, uvRot));
            onLockedModels.put(uvRot, redstoneThresholdModel("lightweight_redstone_threshold_on_locked_model_" + uvRot, slab, onTop, lit, unlit, lock, true, true, uvRot));
            offLockedModels.put(uvRot, redstoneThresholdModel("lightweight_redstone_threshold_off_locked_model_" + uvRot, slab, offTop, lit, unlit, lock, false, true, uvRot));
        }

        getVariantBuilder(threshold).forAllStates(state -> {
            Direction support = state.getValue(LightweightRedstoneThresholdBlock.SUPPORT);
            Direction facing = state.getValue(LightweightRedstoneThresholdBlock.FACING);
            boolean powered = state.getValue(LightweightRedstoneThresholdBlock.POWERED);
            boolean locked = state.getValue(LightweightRedstoneThresholdBlock.LOCKED);
            int[] rot = supportRotation(support);
            int uvRot = localFacingRotation(support, facing);
            Map<Integer, ModelFile> models = locked ? (powered ? onLockedModels : offLockedModels) : (powered ? onModels : offModels);
            return ConfiguredModel.builder().modelFile(models.get(uvRot)).rotationX(rot[0]).rotationY(rot[1]).build();
        });

        itemModels().withExistingParent("lightweight_redstone_threshold", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/lightweight_threshold"));
    }

    private void rsLatch() {
        Block rsLatch = BCBlocks.RS_LATCH.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath("minecraft", "block/smooth_stone");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation top = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/rs_latch");

        BlockModelBuilder builder = models().getBuilder("rs_latch")
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lit", lit)
                .texture("unlit", unlit);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        addAndGateTorch(builder, 2, 4, 7, 9, true);
        addAndGateTorch(builder, 12, 14, 7, 9, false);

        getVariantBuilder(rsLatch).forAllStates(state -> {
            Direction facing = state.getValue(RSLatchBlock.FACING);
            boolean flipped = state.getValue(RSLatchBlock.FLIPPED);
            int yRot = switch (facing) {
                case SOUTH -> 0;
                case WEST -> 90;
                case NORTH -> 180;
                case EAST -> 270;
                default -> 0;
            };
            if (flipped) {
                yRot = (yRot + 180) % 360;
            }
            return ConfiguredModel.builder().modelFile(builder).rotationY(yRot).build();
        });

        itemModels().withExistingParent("rs_latch", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/rs_latch"));
    }

    private void timer() {
        Block timer = BCBlocks.TIMER.get();
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");

        BlockModelBuilder defaultUnlocked = timerSlabModel("timer", "timer", lock, false);
        BlockModelBuilder onUnlocked = timerSlabModel("timer_on", "timer_on", lock, false);
        BlockModelBuilder outputUnlocked = timerSlabModel("timer_output", "timer_output", lock, false);
        BlockModelBuilder defaultLocked = timerSlabModel("timer_locked", "timer", lock, true);
        BlockModelBuilder onLocked = timerSlabModel("timer_on_locked", "timer_on", lock, true);
        BlockModelBuilder outputLocked = timerSlabModel("timer_output_locked", "timer_output", lock, true);

        getVariantBuilder(timer).forAllStates(state -> {
            Direction facing = state.getValue(TimerBlock.FACING);
            int yRot = switch (facing) {
                case SOUTH -> 0;
                case WEST -> 90;
                case NORTH -> 180;
                case EAST -> 270;
                default -> 0;
            };
            boolean locked = state.getValue(TimerBlock.LOCKED);
            BlockModelBuilder model;
            if (state.getValue(TimerBlock.POWERED)) {
                model = locked ? outputLocked : outputUnlocked;
            } else if (state.getValue(TimerBlock.RUNNING)) {
                model = locked ? onLocked : onUnlocked;
            } else {
                model = locked ? defaultLocked : defaultUnlocked;
            }
            return ConfiguredModel.builder().modelFile(model).rotationY(yRot).build();
        });

        itemModels().withExistingParent("timer", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/timer"));

        timerSpinner();
    }

    private void rainDetector() {
        Block rainDetector = BCBlocks.RAIN_DETECTOR.get();
        ResourceLocation template = ResourceLocation.fromNamespaceAndPath("minecraft", "block/template_daylight_detector");
        ResourceLocation side = ResourceLocation.fromNamespaceAndPath("minecraft", "block/daylight_detector_side");
        ResourceLocation top = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/rain_detector");
        ResourceLocation topInverted = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/rain_detector_inverted");

        BlockModelBuilder normal = models().withExistingParent("rain_detector", template)
                .texture("top", top).texture("side", side);
        BlockModelBuilder inverted = models().withExistingParent("rain_detector_inverted", template)
                .texture("top", topInverted).texture("side", side);

        getVariantBuilder(rainDetector).forAllStates(state ->
                ConfiguredModel.builder().modelFile(state.getValue(RainDetectorBlock.INVERTED) ? inverted : normal).build());

        itemModels().withExistingParent("rain_detector", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/rain_detector"));
    }

    private void heatSensor() {
        Block heatSensor = BCBlocks.HEAT_SENSOR.get();

        BlockModelBuilder off = models().cubeAll("heat_sensor",
                ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/heat_sensor"));
        BlockModelBuilder low = models().cubeAll("heat_sensor_5",
                ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/heat_sensor_5"));
        BlockModelBuilder medium = models().cubeAll("heat_sensor_10",
                ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/heat_sensor_10"));
        BlockModelBuilder high = models().cubeAll("heat_sensor_15",
                ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/heat_sensor_15"));

        getVariantBuilder(heatSensor).forAllStates(state -> {
            BlockModelBuilder model = switch (state.getValue(HeatSensorBlock.POWER)) {
                case 15 -> high;
                case 10 -> medium;
                case 5 -> low;
                default -> off;
            };
            return ConfiguredModel.builder().modelFile(model).build();
        });

        itemModels().withExistingParent("heat_sensor", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/heat_sensor"));
    }

    private void capacitor() {
        Block capacitor = BCBlocks.CAPACITOR.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath("minecraft", "block/smooth_stone");
        ResourceLocation top = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/capacitor");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");

        ModelFile onModel = capacitorModel("capacitor_on", slab, top, lit, unlit, lock, true, false);
        ModelFile offModel = capacitorModel("capacitor_off", slab, top, lit, unlit, lock, false, false);
        ModelFile onLockedModel = capacitorModel("capacitor_on_locked", slab, top, lit, unlit, lock, true, true);
        ModelFile offLockedModel = capacitorModel("capacitor_off_locked", slab, top, lit, unlit, lock, false, true);

        getVariantBuilder(capacitor).forAllStates(state -> {
            Direction facing = state.getValue(CapacitorBlock.FACING);
            boolean on = state.getValue(CapacitorBlock.STRENGTH) > 0;
            boolean locked = state.getValue(CapacitorBlock.LOCKED);
            int yRot = switch (facing) {
                case SOUTH -> 0;
                case WEST -> 90;
                case NORTH -> 180;
                case EAST -> 270;
                default -> 0;
            };
            ModelFile model;
            if (locked) {
                model = on ? onLockedModel : offLockedModel;
            } else {
                model = on ? onModel : offModel;
            }
            return ConfiguredModel.builder().modelFile(model).rotationY(yRot).build();
        });

        itemModels().withExistingParent("capacitor", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/capacitor"));
    }

    private ModelFile capacitorModel(String name, ResourceLocation slab, ResourceLocation top,
                                      ResourceLocation lit, ResourceLocation unlit, ResourceLocation lock,
                                      boolean on, boolean locked) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false).renderType(CUTOUT)
                .texture("particle", top).texture("slab", slab).texture("top", top)
                .texture("lit", lit).texture("unlit", unlit).texture("lock", lock);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        if (locked) {
            addLockBar(builder, 2, 4);
        } else {
            addAndGateTorch(builder, 7, 9, 2, 4, on);
        }
        return builder;
    }

    private void randomizer() {
        Block randomizer = BCBlocks.RANDOMIZER.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath("minecraft", "block/smooth_stone");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");

        ModelFile noneModel = randomizerModel("randomizer", slab, "randomizer", lit, unlit, false);
        ModelFile leftModel = randomizerModel("randomizer_on_left", slab, "randomizer_on_right", lit, unlit, true);
        ModelFile middleModel = randomizerModel("randomizer_on_middle", slab, "randomizer_on_middle", lit, unlit, true);
        ModelFile rightModel = randomizerModel("randomizer_on_right", slab, "randomizer_on_left", lit, unlit, true);

        getVariantBuilder(randomizer).forAllStates(state -> {
            Direction facing = state.getValue(RandomizerBlock.FACING);
            int yRot = switch (facing) {
                case SOUTH -> 0;
                case WEST -> 90;
                case NORTH -> 180;
                case EAST -> 270;
                default -> 0;
            };
            ModelFile model = switch (state.getValue(RandomizerBlock.SELECTION)) {
                case NONE -> noneModel;
                case LEFT -> leftModel;
                case MIDDLE -> middleModel;
                case RIGHT -> rightModel;
            };
            return ConfiguredModel.builder().modelFile(model).rotationY(yRot).build();
        });

        itemModels().withExistingParent("randomizer", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/randomizer"));
    }

    private ModelFile randomizerModel(String modelName, ResourceLocation slab, String topTextureName,
                                       ResourceLocation lit, ResourceLocation unlit, boolean torchLit) {
        ResourceLocation top = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/" + topTextureName);
        BlockModelBuilder builder = models().getBuilder(modelName)
                .ao(false).renderType(CUTOUT)
                .texture("particle", top).texture("slab", slab).texture("top", top)
                .texture("lit", lit).texture("unlit", unlit);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        addAndGateTorch(builder, 7, 9, 7, 9, torchLit);
        return builder;
    }

    private static int[] supportRotation(Direction support) {
        return switch (support) {
            case DOWN -> new int[]{0, 0};
            case UP -> new int[]{180, 0};
            case NORTH -> new int[]{90, 180};
            case SOUTH -> new int[]{90, 0};
            case EAST -> new int[]{90, 270};
            case WEST -> new int[]{90, 90};
        };
    }

    private static int[] rotateX(int[] v, int deg) {
        int x = v[0], y = v[1], z = v[2];
        return switch (((deg % 360) + 360) % 360) {
            case 90 -> new int[]{x, -z, y};
            case 180 -> new int[]{x, -y, -z};
            case 270 -> new int[]{x, z, -y};
            default -> new int[]{x, y, z};
        };
    }

    private static int[] rotateY(int[] v, int deg) {
        int x = v[0], y = v[1], z = v[2];
        return switch (((deg % 360) + 360) % 360) {
            case 90 -> new int[]{z, y, -x};
            case 180 -> new int[]{-x, y, -z};
            case 270 -> new int[]{-z, y, x};
            default -> new int[]{x, y, z};
        };
    }

    private static int localFacingRotation(Direction support, Direction facing) {
        int[] rot = supportRotation(support);
        int[] local = rotateX(rotateY(new int[]{facing.getStepX(), facing.getStepY(), facing.getStepZ()}, rot[1]), rot[0]);
        if (local[2] == 1) return 0;
        if (local[0] == -1) return 90;
        if (local[2] == -1) return 180;
        if (local[0] == 1) return 270;
        return 0;
    }

    private static ModelBuilder.FaceRotation faceRotation(int degrees) {
        return switch (degrees) {
            case 90 -> ModelBuilder.FaceRotation.CLOCKWISE_90;
            case 180 -> ModelBuilder.FaceRotation.UPSIDE_DOWN;
            case 270 -> ModelBuilder.FaceRotation.COUNTERCLOCKWISE_90;
            default -> ModelBuilder.FaceRotation.ZERO;
        };
    }

    private static double[] rotateBoxXZ(double x1, double z1, double x2, double z2, int degrees) {
        double[] c1 = rotatePointXZ(x1, z1, degrees);
        double[] c2 = rotatePointXZ(x2, z2, degrees);
        return new double[]{Math.min(c1[0], c2[0]), Math.min(c1[1], c2[1]), Math.max(c1[0], c2[0]), Math.max(c1[1], c2[1])};
    }

    private static double[] rotatePointXZ(double x, double z, int degrees) {
        double dx = x - 8;
        double dz = z - 8;
        double[] rotated = switch (((degrees % 360) + 360) % 360) {
            case 90 -> new double[]{-dz, dx};
            case 180 -> new double[]{-dx, -dz};
            case 270 -> new double[]{dz, -dx};
            default -> new double[]{dx, dz};
        };
        return new double[]{rotated[0] + 8, rotated[1] + 8};
    }

    private void lightweightInverter() {
        Block inverter = BCBlocks.LIGHTWEIGHT_INVERTER.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_block");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");
        ResourceLocation onTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_inverter_on");
        ResourceLocation offTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_inverter");

        Map<Integer, ModelFile> onModels = new HashMap<>();
        Map<Integer, ModelFile> offModels = new HashMap<>();
        Map<Integer, ModelFile> onLockedModels = new HashMap<>();
        Map<Integer, ModelFile> offLockedModels = new HashMap<>();
        for (int uvRot : new int[]{0, 90, 180, 270}) {
            onModels.put(uvRot, inverterModel("lightweight_inverter_on_model_" + uvRot, slab, onTop, lit, unlit, lock, true, false, uvRot));
            offModels.put(uvRot, inverterModel("lightweight_inverter_off_model_" + uvRot, slab, offTop, lit, unlit, lock, false, false, uvRot));
            onLockedModels.put(uvRot, inverterModel("lightweight_inverter_on_locked_model_" + uvRot, slab, onTop, lit, unlit, lock, true, true, uvRot));
            offLockedModels.put(uvRot, inverterModel("lightweight_inverter_off_locked_model_" + uvRot, slab, offTop, lit, unlit, lock, false, true, uvRot));
        }

        getVariantBuilder(inverter).forAllStates(state -> {
            Direction support = state.getValue(LightweightInverterBlock.SUPPORT);
            Direction facing = state.getValue(LightweightInverterBlock.FACING);
            boolean powered = state.getValue(LightweightInverterBlock.POWERED);
            boolean locked = state.getValue(LightweightInverterBlock.LOCKED);
            int[] rot = supportRotation(support);
            int uvRot = localFacingRotation(support, facing);
            Map<Integer, ModelFile> models = locked ? (powered ? onLockedModels : offLockedModels) : (powered ? onModels : offModels);
            return ConfiguredModel.builder().modelFile(models.get(uvRot)).rotationX(rot[0]).rotationY(rot[1]).build();
        });

        itemModels().withExistingParent("lightweight_inverter", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/lightweight_inverter"));
    }

    private void lightweightXorGate() {
        Block xorGate = BCBlocks.LIGHTWEIGHT_XOR_GATE.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_block");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");
        ResourceLocation offOff = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_xor_gate");
        ResourceLocation offOn = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_xor_gate_off");
        ResourceLocation onLeft = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_xor_gate_on_left");
        ResourceLocation onRight = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_xor_gate_on_right");

        Map<Integer, ModelFile> offOffModels = new HashMap<>();
        Map<Integer, ModelFile> offOnModels = new HashMap<>();
        Map<Integer, ModelFile> onLeftModels = new HashMap<>();
        Map<Integer, ModelFile> onRightModels = new HashMap<>();
        Map<Integer, ModelFile> offOffLockedModels = new HashMap<>();
        Map<Integer, ModelFile> offOnLockedModels = new HashMap<>();
        Map<Integer, ModelFile> onLeftLockedModels = new HashMap<>();
        Map<Integer, ModelFile> onRightLockedModels = new HashMap<>();
        for (int rot : new int[]{0, 90, 180, 270}) {
            offOffModels.put(rot, lightweightXorGateModel("lightweight_xor_gate_off_off_" + rot, slab, offOff, lock, false, rot));
            offOnModels.put(rot, lightweightXorGateModel("lightweight_xor_gate_off_on_" + rot, slab, offOn, lock, false, rot));
            onLeftModels.put(rot, lightweightXorGateModel("lightweight_xor_gate_on_left_" + rot, slab, onLeft, lock, false, rot));
            onRightModels.put(rot, lightweightXorGateModel("lightweight_xor_gate_on_right_" + rot, slab, onRight, lock, false, rot));
            offOffLockedModels.put(rot, lightweightXorGateModel("lightweight_xor_gate_off_off_locked_" + rot, slab, offOff, lock, true, rot));
            offOnLockedModels.put(rot, lightweightXorGateModel("lightweight_xor_gate_off_on_locked_" + rot, slab, offOn, lock, true, rot));
            onLeftLockedModels.put(rot, lightweightXorGateModel("lightweight_xor_gate_on_left_locked_" + rot, slab, onLeft, lock, true, rot));
            onRightLockedModels.put(rot, lightweightXorGateModel("lightweight_xor_gate_on_right_locked_" + rot, slab, onRight, lock, true, rot));
        }

        getVariantBuilder(xorGate).forAllStates(state -> {
            Direction support = state.getValue(LightweightXorGateBlock.SUPPORT);
            Direction facing = state.getValue(LightweightXorGateBlock.FACING);
            boolean leftPowered = state.getValue(LightweightXorGateBlock.LEFT_POWERED);
            boolean rightPowered = state.getValue(LightweightXorGateBlock.RIGHT_POWERED);
            boolean locked = state.getValue(LightweightXorGateBlock.LOCKED);
            int[] rot = supportRotation(support);
            int uvRot = localFacingRotation(support, facing);
            Map<Integer, ModelFile> models;
            if (leftPowered && rightPowered) {
                models = locked ? offOnLockedModels : offOnModels;
            } else if (leftPowered) {
                models = locked ? onRightLockedModels : onRightModels;
            } else if (rightPowered) {
                models = locked ? onLeftLockedModels : onLeftModels;
            } else {
                models = locked ? offOffLockedModels : offOffModels;
            }
            return ConfiguredModel.builder().modelFile(models.get(uvRot)).rotationX(rot[0]).rotationY(rot[1]).build();
        });

        itemModels().withExistingParent("lightweight_xor_gate", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/lightweight_xor_gate"));
    }

    private ModelFile lightweightXorGateModel(String name, ResourceLocation slab, ResourceLocation top,
                                               ResourceLocation lock, boolean locked, int localRotation) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lock", lock);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").rotation(faceRotation(localRotation)).end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        if (locked) {
            addRotatedLockBar(builder, localRotation);
        }

        return builder;
    }

    private void lightweightRepeater() {
        Block repeater = BCBlocks.LIGHTWEIGHT_REPEATER.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_block");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");
        ResourceLocation onTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_repeater_on");
        ResourceLocation offTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_repeater");
        int[] rotations = {0, 90, 180, 270};

        ModelFile[][][][] models = new ModelFile[4][4][2][2];
        for (int ri = 0; ri < rotations.length; ri++) {
            int rot = rotations[ri];
            for (int delay = 1; delay <= 4; delay++) {
                for (int p = 0; p < 2; p++) {
                    for (int l = 0; l < 2; l++) {
                        boolean powered = p == 1;
                        boolean locked = l == 1;
                        String name = "lightweight_repeater_" + delay + (powered ? "_on" : "") + (locked ? "_locked" : "") + "_" + rot;
                        models[ri][delay - 1][p][l] = lightweightRepeaterModel(name, slab, powered ? onTop : offTop,
                                lit, unlit, lock, powered, locked, delay, rot);
                    }
                }
            }
        }

        getVariantBuilder(repeater).forAllStates(state -> {
            Direction support = state.getValue(LightweightRepeaterBlock.SUPPORT);
            Direction facing = state.getValue(LightweightRepeaterBlock.FACING);
            boolean powered = state.getValue(LightweightRepeaterBlock.POWERED);
            boolean locked = state.getValue(LightweightRepeaterBlock.LOCKED);
            int delay = state.getValue(LightweightRepeaterBlock.DELAY);
            int[] rot = supportRotation(support);
            int localRot = localFacingRotation(support, facing);
            int ri = switch (localRot) {
                case 90 -> 1;
                case 180 -> 2;
                case 270 -> 3;
                default -> 0;
            };
            ModelFile model = models[ri][delay - 1][powered ? 1 : 0][locked ? 1 : 0];
            return ConfiguredModel.builder().modelFile(model).rotationX(rot[0]).rotationY(rot[1]).build();
        });

        itemModels().withExistingParent("lightweight_repeater", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/lightweight_repeater"));
    }

    private ModelFile lightweightRepeaterModel(String name, ResourceLocation slab, ResourceLocation top,
                                                ResourceLocation lit, ResourceLocation unlit, ResourceLocation lock,
                                                boolean powered, boolean locked, int delay, int localRotation) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lit", lit)
                .texture("unlit", unlit)
                .texture("lock", lock);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").rotation(faceRotation(localRotation)).end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        float delayZ1 = 4 + 2 * delay;
        float delayZ2 = 6 + 2 * delay;
        if (locked) {
            addRotatedLockBar(builder, delayZ1, delayZ2, localRotation);
        } else {
            addRotatedAndGateTorch(builder, 7, 9, delayZ1, delayZ2, powered, localRotation);
        }
        addRotatedAndGateTorch(builder, 7, 9, 2, 4, powered, localRotation);

        return builder;
    }

    private void lightweightRSLatch() {
        Block rsLatch = BCBlocks.LIGHTWEIGHT_RS_LATCH.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_block");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation top = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_rs_latch");

        Map<Integer, ModelFile> models = new HashMap<>();
        for (int rot : new int[]{0, 90, 180, 270}) {
            models.put(rot, lightweightRSLatchModel("lightweight_rs_latch_model_" + rot, slab, top, lit, unlit, rot));
        }

        getVariantBuilder(rsLatch).forAllStates(state -> {
            Direction support = state.getValue(LightweightRSLatchBlock.SUPPORT);
            Direction facing = state.getValue(LightweightRSLatchBlock.FACING);
            boolean flipped = state.getValue(LightweightRSLatchBlock.FLIPPED);
            int[] rot = supportRotation(support);
            int localRot = (localFacingRotation(support, facing) + (flipped ? 180 : 0)) % 360;
            return ConfiguredModel.builder().modelFile(models.get(localRot)).rotationX(rot[0]).rotationY(rot[1]).build();
        });

        itemModels().withExistingParent("lightweight_rs_latch", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/lightweight_rs_latch"));
    }

    private ModelFile lightweightRSLatchModel(String name, ResourceLocation slab, ResourceLocation top,
                                               ResourceLocation lit, ResourceLocation unlit, int localRotation) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lit", lit)
                .texture("unlit", unlit);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").rotation(faceRotation(localRotation)).end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        addRotatedAndGateTorch(builder, 2, 4, 7, 9, true, localRotation);
        addRotatedAndGateTorch(builder, 12, 14, 7, 9, false, localRotation);

        return builder;
    }

    private void lightweightTimer() {
        Block timer = BCBlocks.LIGHTWEIGHT_TIMER.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_block");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");
        ResourceLocation defaultTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_timer");
        ResourceLocation onTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_timer_on");
        ResourceLocation outputTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_timer_output");

        Map<Integer, ModelFile> defaultUnlocked = new HashMap<>();
        Map<Integer, ModelFile> onUnlocked = new HashMap<>();
        Map<Integer, ModelFile> outputUnlocked = new HashMap<>();
        Map<Integer, ModelFile> defaultLocked = new HashMap<>();
        Map<Integer, ModelFile> onLocked = new HashMap<>();
        Map<Integer, ModelFile> outputLocked = new HashMap<>();
        for (int rot : new int[]{0, 90, 180, 270}) {
            defaultUnlocked.put(rot, lightweightTimerModel("lightweight_timer_model_" + rot, slab, defaultTop, lock, false, rot));
            onUnlocked.put(rot, lightweightTimerModel("lightweight_timer_on_model_" + rot, slab, onTop, lock, false, rot));
            outputUnlocked.put(rot, lightweightTimerModel("lightweight_timer_output_model_" + rot, slab, outputTop, lock, false, rot));
            defaultLocked.put(rot, lightweightTimerModel("lightweight_timer_locked_model_" + rot, slab, defaultTop, lock, true, rot));
            onLocked.put(rot, lightweightTimerModel("lightweight_timer_on_locked_model_" + rot, slab, onTop, lock, true, rot));
            outputLocked.put(rot, lightweightTimerModel("lightweight_timer_output_locked_model_" + rot, slab, outputTop, lock, true, rot));
        }

        getVariantBuilder(timer).forAllStates(state -> {
            Direction support = state.getValue(LightweightTimerBlock.SUPPORT);
            Direction facing = state.getValue(LightweightTimerBlock.FACING);
            boolean locked = state.getValue(LightweightTimerBlock.LOCKED);
            boolean powered = state.getValue(LightweightTimerBlock.POWERED);
            boolean running = state.getValue(LightweightTimerBlock.RUNNING);
            int[] rot = supportRotation(support);
            int localRot = localFacingRotation(support, facing);
            Map<Integer, ModelFile> models;
            if (powered) {
                models = locked ? outputLocked : outputUnlocked;
            } else if (running) {
                models = locked ? onLocked : onUnlocked;
            } else {
                models = locked ? defaultLocked : defaultUnlocked;
            }
            return ConfiguredModel.builder().modelFile(models.get(localRot)).rotationX(rot[0]).rotationY(rot[1]).build();
        });

        itemModels().withExistingParent("lightweight_timer", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/lightweight_timer"));
    }

    private ModelFile lightweightTimerModel(String name, ResourceLocation slab, ResourceLocation top,
                                             ResourceLocation lock, boolean locked, int localRotation) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lock", lock);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").rotation(faceRotation(localRotation)).end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        if (locked) {
            addRotatedLockBar(builder, 3, 5, localRotation);
            addRotatedLockBar(builder, 11, 13, localRotation);
        }

        return builder;
    }

    private void lightweightComparator() {
        Block comparator = BCBlocks.LIGHTWEIGHT_COMPARATOR.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_block");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation offTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_comparator");
        ResourceLocation onTop = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_comparator_on");

        ModelFile[][][] models = new ModelFile[4][2][2];
        int[] rotations = {0, 90, 180, 270};
        for (int ri = 0; ri < rotations.length; ri++) {
            int rot = rotations[ri];
            for (int p = 0; p < 2; p++) {
                for (int m = 0; m < 2; m++) {
                    boolean powered = p == 1;
                    boolean subtract = m == 1;
                    String name = "lightweight_comparator" + (powered ? "_on" : "") + (subtract ? "_subtract" : "") + "_" + rot;
                    models[ri][p][m] = lightweightComparatorModel(name, slab, powered ? onTop : offTop, lit, unlit, powered, subtract, rot);
                }
            }
        }

        getVariantBuilder(comparator).forAllStates(state -> {
            Direction support = state.getValue(LightweightComparatorBlock.SUPPORT);
            Direction facing = state.getValue(LightweightComparatorBlock.FACING);
            boolean powered = state.getValue(LightweightComparatorBlock.POWERED);
            boolean subtract = state.getValue(LightweightComparatorBlock.MODE) == ComparatorMode.SUBTRACT;
            int[] rot = supportRotation(support);
            int localRot = localFacingRotation(support, facing);
            int ri = switch (localRot) {
                case 90 -> 1;
                case 180 -> 2;
                case 270 -> 3;
                default -> 0;
            };
            ModelFile model = models[ri][powered ? 1 : 0][subtract ? 1 : 0];
            return ConfiguredModel.builder().modelFile(model).rotationX(rot[0]).rotationY(rot[1]).build();
        });

        itemModels().withExistingParent("lightweight_comparator", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/lightweight_comparator"));
    }

    private ModelFile lightweightComparatorModel(String name, ResourceLocation slab, ResourceLocation top,
                                                  ResourceLocation lit, ResourceLocation unlit,
                                                  boolean powered, boolean subtract, int localRotation) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lit", lit)
                .texture("unlit", unlit);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").rotation(faceRotation(localRotation)).end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        addRotatedAndGateTorch(builder, 4, 6, 11, 13, powered, localRotation);
        addRotatedAndGateTorch(builder, 10, 12, 11, 13, powered, localRotation);
        addRotatedAndGateTorch(builder, 7, 9, 2, 4, subtract, localRotation);

        return builder;
    }

    private void lightweightCapacitor() {
        Block capacitor = BCBlocks.LIGHTWEIGHT_CAPACITOR.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_block");
        ResourceLocation top = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_capacitor");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");

        Map<Integer, ModelFile> onModels = new HashMap<>();
        Map<Integer, ModelFile> offModels = new HashMap<>();
        Map<Integer, ModelFile> onLockedModels = new HashMap<>();
        Map<Integer, ModelFile> offLockedModels = new HashMap<>();
        for (int rot : new int[]{0, 90, 180, 270}) {
            onModels.put(rot, lightweightCapacitorModel("lightweight_capacitor_on_model_" + rot, slab, top, lit, unlit, lock, true, false, rot));
            offModels.put(rot, lightweightCapacitorModel("lightweight_capacitor_off_model_" + rot, slab, top, lit, unlit, lock, false, false, rot));
            onLockedModels.put(rot, lightweightCapacitorModel("lightweight_capacitor_on_locked_model_" + rot, slab, top, lit, unlit, lock, true, true, rot));
            offLockedModels.put(rot, lightweightCapacitorModel("lightweight_capacitor_off_locked_model_" + rot, slab, top, lit, unlit, lock, false, true, rot));
        }

        getVariantBuilder(capacitor).forAllStates(state -> {
            Direction support = state.getValue(LightweightCapacitorBlock.SUPPORT);
            Direction facing = state.getValue(LightweightCapacitorBlock.FACING);
            boolean on = state.getValue(LightweightCapacitorBlock.STRENGTH) > 0;
            boolean locked = state.getValue(LightweightCapacitorBlock.LOCKED);
            int[] rot = supportRotation(support);
            int uvRot = localFacingRotation(support, facing);
            Map<Integer, ModelFile> models = locked ? (on ? onLockedModels : offLockedModels) : (on ? onModels : offModels);
            return ConfiguredModel.builder().modelFile(models.get(uvRot)).rotationX(rot[0]).rotationY(rot[1]).build();
        });

        itemModels().withExistingParent("lightweight_capacitor", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/lightweight_capacitor"));
    }

    private ModelFile lightweightCapacitorModel(String name, ResourceLocation slab, ResourceLocation top,
                                                 ResourceLocation lit, ResourceLocation unlit, ResourceLocation lock,
                                                 boolean on, boolean locked, int localRotation) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lit", lit)
                .texture("unlit", unlit)
                .texture("lock", lock);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").rotation(faceRotation(localRotation)).end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        if (locked) {
            addRotatedLockBar(builder, 2, 4, localRotation);
        } else {
            addRotatedAndGateTorch(builder, 7, 9, 2, 4, on, localRotation);
        }
        return builder;
    }

    private void lightweightRandomizer() {
        Block randomizer = BCBlocks.LIGHTWEIGHT_RANDOMIZER.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_block");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");

        Map<Integer, ModelFile> noneModels = new HashMap<>();
        Map<Integer, ModelFile> leftModels = new HashMap<>();
        Map<Integer, ModelFile> middleModels = new HashMap<>();
        Map<Integer, ModelFile> rightModels = new HashMap<>();
        for (int rot : new int[]{0, 90, 180, 270}) {
            noneModels.put(rot, lightweightRandomizerModel("lightweight_randomizer_model_" + rot, slab, "lightweight_randomizer", lit, unlit, false, rot));
            leftModels.put(rot, lightweightRandomizerModel("lightweight_randomizer_on_left_model_" + rot, slab, "lightweight_randomizer_on_right", lit, unlit, true, rot));
            middleModels.put(rot, lightweightRandomizerModel("lightweight_randomizer_on_middle_model_" + rot, slab, "lightweight_randomizer_on_middle", lit, unlit, true, rot));
            rightModels.put(rot, lightweightRandomizerModel("lightweight_randomizer_on_right_model_" + rot, slab, "lightweight_randomizer_on_left", lit, unlit, true, rot));
        }

        getVariantBuilder(randomizer).forAllStates(state -> {
            Direction support = state.getValue(LightweightRandomizerBlock.SUPPORT);
            Direction facing = state.getValue(LightweightRandomizerBlock.FACING);
            int[] rot = supportRotation(support);
            int uvRot = localFacingRotation(support, facing);
            Map<Integer, ModelFile> models = switch (state.getValue(LightweightRandomizerBlock.SELECTION)) {
                case NONE -> noneModels;
                case LEFT -> leftModels;
                case MIDDLE -> middleModels;
                case RIGHT -> rightModels;
            };
            return ConfiguredModel.builder().modelFile(models.get(uvRot)).rotationX(rot[0]).rotationY(rot[1]).build();
        });

        itemModels().withExistingParent("lightweight_randomizer", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/lightweight_randomizer"));
    }

    private ModelFile lightweightRandomizerModel(String name, ResourceLocation slab, String topTextureName,
                                                  ResourceLocation lit, ResourceLocation unlit,
                                                  boolean torchLit, int localRotation) {
        ResourceLocation top = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/" + topTextureName);
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lit", lit)
                .texture("unlit", unlit);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").rotation(faceRotation(localRotation)).end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        addAndGateTorch(builder, 7, 9, 7, 9, torchLit);
        return builder;
    }

    private void lightweightAndGate() {
        Block andGate = BCBlocks.LIGHTWEIGHT_AND_GATE.get();
        ResourceLocation slab = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_block");
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation unlit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch_off");
        ResourceLocation lock = ResourceLocation.fromNamespaceAndPath("minecraft", "block/bedrock");
        ResourceLocation topOff = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_and_gate");
        ResourceLocation topOn = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/lightweight_and_gate_on");
        int[] rotations = {0, 90, 180, 270};

        ModelFile[][][][] models = new ModelFile[4][2][2][2];
        for (int ri = 0; ri < rotations.length; ri++) {
            int rot = rotations[ri];
            for (int l = 0; l < 2; l++) {
                for (int r = 0; r < 2; r++) {
                    for (int p = 0; p < 2; p++) {
                        boolean leftPowered = l == 1;
                        boolean rightPowered = r == 1;
                        boolean powered = p == 1;
                        String name = "lightweight_and_gate" + (powered ? "_on" : "") + "_l" + l + "_r" + r + "_" + rot;
                        models[ri][l][r][p] = lightweightAndGateModel(name, slab, powered ? topOn : topOff, lit, unlit, lock,
                                leftPowered, rightPowered, powered, false, rot);
                    }
                }
            }
        }
        Map<Integer, ModelFile> lockedOnModels = new HashMap<>();
        Map<Integer, ModelFile> lockedOffModels = new HashMap<>();
        for (int rot : rotations) {
            lockedOnModels.put(rot, lightweightAndGateModel("lightweight_and_gate_on_locked_" + rot, slab, topOn, lit, unlit, lock, false, false, true, true, rot));
            lockedOffModels.put(rot, lightweightAndGateModel("lightweight_and_gate_locked_" + rot, slab, topOff, lit, unlit, lock, false, false, false, true, rot));
        }

        getVariantBuilder(andGate).forAllStates(state -> {
            Direction support = state.getValue(LightweightAndGateBlock.SUPPORT);
            Direction facing = state.getValue(LightweightAndGateBlock.FACING);
            boolean leftPowered = state.getValue(LightweightAndGateBlock.LEFT_POWERED);
            boolean rightPowered = state.getValue(LightweightAndGateBlock.RIGHT_POWERED);
            boolean powered = state.getValue(LightweightAndGateBlock.POWERED);
            boolean locked = state.getValue(LightweightAndGateBlock.LOCKED);
            int[] rot = supportRotation(support);
            int localRot = localFacingRotation(support, facing);
            int ri = switch (localRot) {
                case 90 -> 1;
                case 180 -> 2;
                case 270 -> 3;
                default -> 0;
            };
            ModelFile model = locked
                    ? (powered ? lockedOnModels.get(localRot) : lockedOffModels.get(localRot))
                    : models[ri][leftPowered ? 1 : 0][rightPowered ? 1 : 0][powered ? 1 : 0];
            return ConfiguredModel.builder().modelFile(model).rotationX(rot[0]).rotationY(rot[1]).build();
        });

        itemModels().withExistingParent("lightweight_and_gate", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/lightweight_and_gate"));
    }

    private ModelFile lightweightAndGateModel(String name, ResourceLocation slab, ResourceLocation top,
                                               ResourceLocation lit, ResourceLocation unlit, ResourceLocation lock,
                                               boolean leftPowered, boolean rightPowered, boolean outputActive,
                                               boolean locked, int localRotation) {
        BlockModelBuilder builder = models().getBuilder(name)
                .ao(false)
                .renderType(CUTOUT)
                .texture("particle", top)
                .texture("slab", slab)
                .texture("top", top)
                .texture("lit", lit)
                .texture("unlit", unlit)
                .texture("lock", lock);

        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#slab").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").rotation(faceRotation(localRotation)).end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#slab").cullface(Direction.EAST).end()
                .end();

        if (locked) {
            addRotatedLockBar(builder, localRotation);
        } else {
            addRotatedAndGateTorch(builder, 12, 14, 7, 9, !leftPowered, localRotation);
            addRotatedAndGateTorch(builder, 2, 4, 7, 9, !rightPowered, localRotation);
        }
        addRotatedAndGateTorch(builder, 7, 9, 2, 4, outputActive, localRotation);

        return builder;
    }

    private BlockModelBuilder timerSlabModel(String modelName, String topTextureName, ResourceLocation lock, boolean locked) {
        ResourceLocation top = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/" + topTextureName);
        BlockModelBuilder builder = models().getBuilder(modelName)
                .ao(false).renderType(CUTOUT)
                .texture("particle", top).texture("top", top).texture("lock", lock);
        addTimerSlab(builder);
        if (locked) {
            addLockBar(builder, 3, 5);
            addLockBar(builder, 11, 13);
        }
        return builder;
    }

    private void addTimerSlab(BlockModelBuilder builder) {
        builder.element()
                .from(0, 0, 0).to(16, 2, 16)
                .face(Direction.DOWN).uvs(0, 0, 16, 16).texture("#top").cullface(Direction.DOWN).end()
                .face(Direction.UP).uvs(0, 0, 16, 16).texture("#top").end()
                .face(Direction.NORTH).uvs(0, 14, 16, 16).texture("#top").cullface(Direction.NORTH).end()
                .face(Direction.SOUTH).uvs(0, 14, 16, 16).texture("#top").cullface(Direction.SOUTH).end()
                .face(Direction.WEST).uvs(0, 14, 16, 16).texture("#top").cullface(Direction.WEST).end()
                .face(Direction.EAST).uvs(0, 14, 16, 16).texture("#top").cullface(Direction.EAST).end()
                .end();
    }

    private void timerSpinner() {
        ResourceLocation lit = ResourceLocation.fromNamespaceAndPath("minecraft", "block/redstone_torch");
        ResourceLocation gold = ResourceLocation.fromNamespaceAndPath("minecraft", "block/gold_block");

        BlockModelBuilder builder = models().getBuilder("timer_spinner")
                .ao(false).renderType(CUTOUT)
                .texture("particle", gold).texture("lit", lit).texture("pointer", gold);

        addAndGateTorch(builder, 7, 9, 7, 9, true);

        builder.element()
                .from(6.5f, 3, 5.5f).to(9.5f, 5, 9.5f)
                .face(Direction.DOWN).uvs(6.5f, 5.5f, 9.5f, 9.5f).texture("#pointer").end()
                .face(Direction.UP).uvs(6.5f, 5.5f, 9.5f, 9.5f).texture("#pointer").end()
                .face(Direction.NORTH).uvs(6.5f, 3, 9.5f, 5).texture("#pointer").end()
                .face(Direction.SOUTH).uvs(6.5f, 3, 9.5f, 5).texture("#pointer").end()
                .face(Direction.WEST).uvs(5.5f, 3, 9.5f, 5).texture("#pointer").end()
                .face(Direction.EAST).uvs(5.5f, 3, 9.5f, 5).texture("#pointer").end()
                .end();

        builder.element()
                .from(7.5f, 3, 4.5f).to(8.5f, 5, 5.5f)
                .face(Direction.DOWN).uvs(7.5f, 4.5f, 8.5f, 5.5f).texture("#pointer").end()
                .face(Direction.UP).uvs(7.5f, 4.5f, 8.5f, 5.5f).texture("#pointer").end()
                .face(Direction.NORTH).uvs(7.5f, 3, 8.5f, 5).texture("#pointer").end()
                .face(Direction.SOUTH).uvs(7.5f, 3, 8.5f, 5).texture("#pointer").end()
                .face(Direction.WEST).uvs(4.5f, 3, 5.5f, 5).texture("#pointer").end()
                .face(Direction.EAST).uvs(4.5f, 3, 5.5f, 5).texture("#pointer").end()
                .end();
    }

    private void addLockBar(BlockModelBuilder builder) {
        addLockBar(builder, 7, 9);
    }

    private void addRotatedLockBar(BlockModelBuilder builder, int localRotation) {
        addRotatedLockBar(builder, 7, 9, localRotation);
    }

    private void addRotatedLockBar(BlockModelBuilder builder, float z1, float z2, int localRotation) {
        double[] box = rotateBoxXZ(2, z1, 14, z2, localRotation);
        float x1 = (float) box[0], rz1 = (float) box[1], x2 = (float) box[2], rz2 = (float) box[3];
        builder.element()
                .from(x1, 2, rz1).to(x2, 4, rz2)
                .face(Direction.DOWN).uvs(x1, rz1, x2, rz2).texture("#lock").end()
                .face(Direction.UP).uvs(x1, rz1, x2, rz2).texture("#lock").end()
                .face(Direction.NORTH).uvs(x1, 2, x2, 4).texture("#lock").end()
                .face(Direction.SOUTH).uvs(x1, 2, x2, 4).texture("#lock").end()
                .face(Direction.WEST).uvs(rz1, 2, rz2, 4).texture("#lock").end()
                .face(Direction.EAST).uvs(rz1, 2, rz2, 4).texture("#lock").end()
                .end();
    }

    private void addRotatedAndGateTorch(BlockModelBuilder builder, float x1, float x2, float z1, float z2, boolean lit, int localRotation) {
        double[] box = rotateBoxXZ(x1, z1, x2, z2, localRotation);
        addAndGateTorch(builder, (float) box[0], (float) box[2], (float) box[1], (float) box[3], lit);
    }

    private void addLockBar(BlockModelBuilder builder, float z1, float z2) {
        builder.element()
                .from(2, 2, z1).to(14, 4, z2)
                .face(Direction.DOWN).uvs(z1, 2, z2, 14).texture("#lock").rotation(ModelBuilder.FaceRotation.CLOCKWISE_90).end()
                .face(Direction.UP).uvs(z1, 2, z2, 14).texture("#lock").rotation(ModelBuilder.FaceRotation.CLOCKWISE_90).end()
                .face(Direction.NORTH).uvs(2, z1, 14, z2).texture("#lock").end()
                .face(Direction.SOUTH).uvs(2, z1, 14, z2).texture("#lock").end()
                .face(Direction.WEST).uvs(6, z1, 8, z2).texture("#lock").end()
                .face(Direction.EAST).uvs(6, z1, 8, z2).texture("#lock").end()
                .end();
    }

    private void blockWithItem(DeferredBlock<?> deferredBlock) {
        simpleBlockWithItem(deferredBlock.get(), cubeAll(deferredBlock.get()));
    }

    //Texture is a 3-frame animated strip (aluminum_lantern.png.mcmeta) with transparent cage gaps, so it needs
    //cutout like vanilla's own lantern. Item is a flat icon with its own dedicated texture.
    private void aluminumLantern() {
        LanternBlock lantern = BCBlocks.ALUMINUM_LANTERN.get();
        ResourceLocation texture = blockTexture(lantern);
        ModelFile standing = models().withExistingParent("aluminum_lantern", ResourceLocation.fromNamespaceAndPath("minecraft", "block/template_lantern"))
                .texture("lantern", texture)
                .renderType(CUTOUT);
        ModelFile hanging = models().withExistingParent("aluminum_lantern_hanging", ResourceLocation.fromNamespaceAndPath("minecraft", "block/template_hanging_lantern"))
                .texture("lantern", texture)
                .renderType(CUTOUT);
        getVariantBuilder(lantern).forAllStatesExcept(state ->
                        ConfiguredModel.builder()
                                .modelFile(state.getValue(LanternBlock.HANGING) ? hanging : standing)
                                .build(),
                LanternBlock.WATERLOGGED);
        itemModels().withExistingParent("aluminum_lantern", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/aluminum_lantern"));
    }

    //Standing torch has no properties (cross model); wall torch needs a rotation per facing. Item is a flat
    //item/generated icon, not the 3D block model.
    private void aluminumTorch() {
        Block standing = BCBlocks.ALUMINUM_TORCH.get();
        Block wall = BCBlocks.ALUMINUM_WALL_TORCH.get();
        ResourceLocation texture = blockTexture(standing);

        ModelFile standingModel = models().withExistingParent("aluminum_torch", ResourceLocation.fromNamespaceAndPath("minecraft", "block/template_torch"))
                .texture("torch", texture)
                .renderType(CUTOUT);
        simpleBlock(standing, standingModel);
        itemModels().withExistingParent("aluminum_torch", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", texture);

        ModelFile wallModel = models().withExistingParent("aluminum_wall_torch", ResourceLocation.fromNamespaceAndPath("minecraft", "block/template_torch_wall"))
                .texture("torch", texture)
                .renderType(CUTOUT);
        getVariantBuilder(wall).forAllStates(state -> {
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            int yRot = switch (facing) {
                case EAST -> 0;
                case SOUTH -> 90;
                case WEST -> 180;
                case NORTH -> 270;
                default -> 0;
            };
            return ConfiguredModel.builder().modelFile(wallModel).rotationY(yRot).build();
        });
    }

    //Reuses vanilla's own chain geometry (block/chain) with our texture swapped in. Vanilla uses cutout_mipped
    //for chain specifically. Item is a flat icon with its own dedicated texture.
    private void aluminumChain() {
        Block chain = BCBlocks.ALUMINUM_CHAIN.get();
        ResourceLocation texture = blockTexture(chain);
        ModelFile model = models().withExistingParent("aluminum_chain", ResourceLocation.fromNamespaceAndPath("minecraft", "block/chain"))
                .texture("all", texture)
                .renderType(CUTOUT_MIPPED);
        getVariantBuilder(chain).forAllStatesExcept(state -> {
            Direction.Axis axis = state.getValue(BlockStateProperties.AXIS);
            ConfiguredModel.Builder<?> builder = ConfiguredModel.builder().modelFile(model);
            if (axis == Direction.Axis.X) {
                builder.rotationX(90).rotationY(90);
            } else if (axis == Direction.Axis.Z) {
                builder.rotationX(90);
            }
            return builder.build();
        }, BlockStateProperties.WATERLOGGED);
        itemModels().withExistingParent("aluminum_chain", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/aluminum_chain"));
    }

    //Bars had no item model at all before (paneBlock only builds the blockstate) - flat icon from the block
    //texture, matching vanilla's own iron_bars item. Vanilla also uses cutout_mipped for bars specifically.
    private void aluminumBars() {
        Block bars = BCBlocks.ALUMINUM_BARS.get();
        ResourceLocation texture = blockTexture(bars);
        paneBlockWithRenderType(BCBlocks.ALUMINUM_BARS.get(), texture, texture, CUTOUT_MIPPED);
        itemModels().withExistingParent("aluminum_bars", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", texture);
    }

    //Door item uses its own dedicated flat texture (aluminum_door.png), matching vanilla's own iron_door item.
    private void aluminumDoor() {
        doorBlockWithRenderType(BCBlocks.ALUMINUM_DOOR.get(),
                ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_door_bottom"),
                ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_door_top"),
                CUTOUT);
        itemModels().withExistingParent("aluminum_door", ResourceLocation.fromNamespaceAndPath("minecraft", "item/generated"))
                .texture("layer0", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "item/aluminum_door"));
    }

    //Trapdoor had no item model at all before - parents off its own closed-bottom block model (3D, not flat).
    private void aluminumTrapdoor() {
        trapdoorBlockWithRenderType(BCBlocks.ALUMINUM_TRAPDOOR.get(), blockTexture(BCBlocks.ALUMINUM_TRAPDOOR.get()), false, CUTOUT);
        itemModels().withExistingParent("aluminum_trapdoor", ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_trapdoor_bottom"));
    }

    private void aluminumGrate() {
        Block grate = BCBlocks.ALUMINUM_GRATE.get();
        ModelFile model = models().cubeAll("aluminum_grate", blockTexture(grate)).renderType(CUTOUT);
        simpleBlockWithItem(grate, model);
    }
}
