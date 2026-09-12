package com.a_c_e.bettercircuits.block;

import com.a_c_e.bettercircuits.BCParticleTypes;
import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.block.lightweight.LightweightAndGateBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightInverterBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightRedstoneThresholdBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightRepeaterBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightRSLatchBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightTimerBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightComparatorBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightCapacitorBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightRandomizerBlock;
import com.a_c_e.bettercircuits.block.lightweight.LightweightXorGateBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.WaterloggedTransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class BCBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(BetterCircuits.MOD_ID);

    //---- Aluminum ore/material chain ----
    public static final DeferredBlock<Block> ALUMINUM_BLOCK = BLOCKS.register("aluminum_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4f).requiresCorrectToolForDrops().sound(SoundType.COPPER)));
    public static final DeferredBlock<Block> ALUMINUM_ORE = BLOCKS.register("aluminum_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4f).requiresCorrectToolForDrops().sound(SoundType.STONE)));
    public static final DeferredBlock<Block> DEEPSLATE_ALUMINUM_ORE = BLOCKS.register("deepslate_aluminum_ore",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE)));

    //Mirrors vanilla's raw_iron_block/raw_copper_block properties (raw_gold_block uses a higher tool tier, which aluminum doesn't need)
    public static final DeferredBlock<Block> RAW_ALUMINUM_BLOCK = BLOCKS.register("raw_aluminum_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops().strength(5f, 6f)));

    //---- Aluminum Fixtures ----
    public static final DeferredBlock<LanternBlock> ALUMINUM_LANTERN = BLOCKS.register("aluminum_lantern",
            () -> new LanternBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).forceSolidOn().requiresCorrectToolForDrops().strength(3.5f)
                    .sound(SoundType.LANTERN).lightLevel(state -> 15).noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<TorchBlock> ALUMINUM_TORCH = BLOCKS.register("aluminum_torch",
            () -> new TorchBlock(BCParticleTypes.ALUMINUM_FLAME, BlockBehaviour.Properties.of()
                    .noCollission().instabreak().lightLevel(state -> 14).sound(SoundType.WOOD)
                    .pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<WallTorchBlock> ALUMINUM_WALL_TORCH = BLOCKS.register("aluminum_wall_torch",
            () -> new WallTorchBlock(BCParticleTypes.ALUMINUM_FLAME, BlockBehaviour.Properties.of()
                    .noCollission().instabreak().lightLevel(state -> 14).sound(SoundType.WOOD)
                    .dropsLike(ALUMINUM_TORCH.get())));

    public static final DeferredBlock<ChainBlock> ALUMINUM_CHAIN = BLOCKS.register("aluminum_chain",
            () -> new ChainBlock(BlockBehaviour.Properties.of()
                    .forceSolidOn().requiresCorrectToolForDrops().strength(5f, 6f).sound(SoundType.CHAIN).noOcclusion()));

    public static final DeferredBlock<IronBarsBlock> ALUMINUM_BARS = BLOCKS.register("aluminum_bars",
            () -> new IronBarsBlock(BlockBehaviour.Properties.of()
                    .requiresCorrectToolForDrops().strength(5f, 6f).sound(SoundType.METAL).noOcclusion()));

    //COPPER (not IRON) since copper is vanilla's own "hand-openable metal door" set - iron requires redstone
    public static final DeferredBlock<DoorBlock> ALUMINUM_DOOR = BLOCKS.register("aluminum_door",
            () -> new DoorBlock(BlockSetType.COPPER, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5f).noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<TrapDoorBlock> ALUMINUM_TRAPDOOR = BLOCKS.register("aluminum_trapdoor",
            () -> new TrapDoorBlock(BlockSetType.COPPER, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5f).noOcclusion().isValidSpawn(BCBlocks::neverSpawn)));

    public static final DeferredBlock<WaterloggedTransparentBlock> ALUMINUM_GRATE = BLOCKS.register("aluminum_grate",
            () -> new WaterloggedTransparentBlock(BlockBehaviour.Properties.of()
                    .strength(3f, 6f).sound(SoundType.COPPER_GRATE).mapColor(MapColor.METAL).noOcclusion()
                    .requiresCorrectToolForDrops().isValidSpawn(BCBlocks::neverSpawn).isRedstoneConductor(BCBlocks::never)
                    .isSuffocating(BCBlocks::never).isViewBlocking(BCBlocks::never)));

    //---- Heavy logic gates ----
    public static final DeferredBlock<AndGateBlock> AND_GATE = BLOCKS.register("and_gate",
            () -> new AndGateBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<XorGateBlock> XOR_GATE = BLOCKS.register("xor_gate",
            () -> new XorGateBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<InverterBlock> INVERTER = BLOCKS.register("inverter",
            () -> new InverterBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<RedstoneThresholdBlock> REDSTONE_THRESHOLD = BLOCKS.register("redstone_threshold",
            () -> new RedstoneThresholdBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<RSLatchBlock> RS_LATCH = BLOCKS.register("rs_latch",
            () -> new RSLatchBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<TimerBlock> TIMER = BLOCKS.register("timer",
            () -> new TimerBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<CapacitorBlock> CAPACITOR = BLOCKS.register("capacitor",
            () -> new CapacitorBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<RandomizerBlock> RANDOMIZER = BLOCKS.register("randomizer",
            () -> new RandomizerBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    //---- Lightweight Redstone Gate Family ----
    public static final DeferredBlock<LightweightInverterBlock> LIGHTWEIGHT_INVERTER = BLOCKS.register("lightweight_inverter",
            () -> new LightweightInverterBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<LightweightRedstoneThresholdBlock> LIGHTWEIGHT_REDSTONE_THRESHOLD = BLOCKS.register("lightweight_redstone_threshold",
            () -> new LightweightRedstoneThresholdBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<LightweightAndGateBlock> LIGHTWEIGHT_AND_GATE = BLOCKS.register("lightweight_and_gate",
            () -> new LightweightAndGateBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<LightweightXorGateBlock> LIGHTWEIGHT_XOR_GATE = BLOCKS.register("lightweight_xor_gate",
            () -> new LightweightXorGateBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<LightweightRepeaterBlock> LIGHTWEIGHT_REPEATER = BLOCKS.register("lightweight_repeater",
            () -> new LightweightRepeaterBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<LightweightRSLatchBlock> LIGHTWEIGHT_RS_LATCH = BLOCKS.register("lightweight_rs_latch",
            () -> new LightweightRSLatchBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<LightweightTimerBlock> LIGHTWEIGHT_TIMER = BLOCKS.register("lightweight_timer",
            () -> new LightweightTimerBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<LightweightComparatorBlock> LIGHTWEIGHT_COMPARATOR = BLOCKS.register("lightweight_comparator",
            () -> new LightweightComparatorBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<LightweightCapacitorBlock> LIGHTWEIGHT_CAPACITOR = BLOCKS.register("lightweight_capacitor",
            () -> new LightweightCapacitorBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<LightweightRandomizerBlock> LIGHTWEIGHT_RANDOMIZER = BLOCKS.register("lightweight_randomizer",
            () -> new LightweightRandomizerBlock(BlockBehaviour.Properties.of()
                    .instabreak().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY)));

    //---- Sensors ----
    public static final DeferredBlock<RainDetectorBlock> RAIN_DETECTOR = BLOCKS.register("rain_detector",
            () -> new RainDetectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(0.2f).sound(SoundType.WOOD).ignitedByLava()));

    public static final DeferredBlock<HeatDetectorBlock> HEAT_DETECTOR = BLOCKS.register("heat_detector",
            () -> new HeatDetectorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS).strength(0.2f).sound(SoundType.WOOD).ignitedByLava()));

    //---- Redstone Cable / Insulated / Bundled ----
    public static final DeferredBlock<RedstoneCableBlock> REDSTONE_CABLE = BLOCKS.register("redstone_cable",
            () -> new RedstoneCableBlock(BlockBehaviour.Properties.of()
                    .noCollission().instabreak().pushReaction(PushReaction.DESTROY)));

    //---- Comparator Rail ----
    public static final DeferredBlock<ComparatorRailBlock> COMPARATOR_RAIL = BLOCKS.register("comparator_rail",
            () -> new ComparatorRailBlock(BlockBehaviour.Properties.of()
                    .noCollission().strength(0.7f).sound(SoundType.METAL)));

    //---- Gold Button ----
    //Plain vanilla ButtonBlock, same properties as Blocks.STONE_BUTTON/OAK_BUTTON's own registration, just with
    //BlockSetType.GOLD (a real vanilla BlockSetType - currently only used for the gold weighted pressure plate
    //- reused here rather than defining a new one) and a GOLD_BUTTON_TICKS_TO_STAY_PRESSED pulse shorter than
    //either vanilla button (wood 30 ticks, stone 20 ticks - see Blocks.java's own button registrations).
    public static final int GOLD_BUTTON_TICKS_TO_STAY_PRESSED = 10;
    public static final DeferredBlock<ButtonBlock> GOLD_BUTTON = BLOCKS.register("gold_button",
            () -> new ButtonBlock(BlockSetType.GOLD, GOLD_BUTTON_TICKS_TO_STAY_PRESSED, BlockBehaviour.Properties.of()
                    .noCollission().strength(0.5f).pushReaction(PushReaction.DESTROY)));

    //---- Iron Button ----
    //Same idea as Gold Button, but BlockSetType.IRON (also a real vanilla BlockSetType, currently only used for
    //the iron door/trapdoor) and a pulse LONGER than either vanilla button instead of shorter.
    public static final int IRON_BUTTON_TICKS_TO_STAY_PRESSED = 40;
    public static final DeferredBlock<ButtonBlock> IRON_BUTTON = BLOCKS.register("iron_button",
            () -> new ButtonBlock(BlockSetType.IRON, IRON_BUTTON_TICKS_TO_STAY_PRESSED, BlockBehaviour.Properties.of()
                    .noCollission().strength(0.5f).pushReaction(PushReaction.DESTROY)));

    //---- Filtered Hopper ----
    public static final DeferredBlock<FilteredHopperBlock> FILTERED_HOPPER = BLOCKS.register("filtered_hopper",
            () -> new FilteredHopperBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.HOPPER)));

    //---- Blower / Vacuum ----
    public static final DeferredBlock<BlowerBlock> BLOWER = BLOCKS.register("blower",
            () -> new BlowerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DISPENSER)));

    public static final DeferredBlock<VacuumBlock> VACUUM = BLOCKS.register("vacuum",
            () -> new VacuumBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.DISPENSER)));

    //Register Event Bus
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    private static boolean neverSpawn(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.entity.EntityType<?> entityType) {
        return false;
    }
}
