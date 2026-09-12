package com.a_c_e.bettercircuits.item;

import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.block.BCBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public class BCItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BetterCircuits.MOD_ID);

    //Durability matches Shears (238), enchantable with Unbreaking/Mending via the durability.json tag.
    public static final DeferredItem<ScrewdriverItem> SCREWDRIVER_ITEM = ITEMS.register("screwdriver",
            () -> new ScrewdriverItem(new Item.Properties().durability(238)));

    public static final DeferredItem<Item> RAW_ALUMINUM = ITEMS.register("raw_aluminum",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> ALUMINUM_INGOT = ITEMS.register("aluminum_ingot",
            () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> ALUMINUM_NUGGET = ITEMS.register("aluminum_nugget",
            () -> new Item(new Item.Properties()));

    public static final DeferredItem<BlockItem> AND_GATE_ITEM = blockItem("and_gate", BCBlocks.AND_GATE);
    public static final DeferredItem<BlockItem> XOR_GATE_ITEM = blockItem("xor_gate", BCBlocks.XOR_GATE);
    public static final DeferredItem<BlockItem> INVERTER_ITEM = blockItem("inverter", BCBlocks.INVERTER);
    public static final DeferredItem<BlockItem> REDSTONE_THRESHOLD_ITEM = blockItem("redstone_threshold", BCBlocks.REDSTONE_THRESHOLD);
    public static final DeferredItem<BlockItem> LIGHTWEIGHT_REDSTONE_THRESHOLD_ITEM = blockItem("lightweight_redstone_threshold", BCBlocks.LIGHTWEIGHT_REDSTONE_THRESHOLD);
    public static final DeferredItem<BlockItem> RS_LATCH_ITEM = blockItem("rs_latch", BCBlocks.RS_LATCH);
    public static final DeferredItem<BlockItem> TIMER_ITEM = blockItem("timer", BCBlocks.TIMER);
    public static final DeferredItem<BlockItem> RAIN_DETECTOR_ITEM = blockItem("rain_detector", BCBlocks.RAIN_DETECTOR);
    public static final DeferredItem<BlockItem> HEAT_DETECTOR_ITEM = blockItem("heat_detector", BCBlocks.HEAT_DETECTOR);
    public static final DeferredItem<BlockItem> CAPACITOR_ITEM = blockItem("capacitor", BCBlocks.CAPACITOR);
    public static final DeferredItem<BlockItem> RANDOMIZER_ITEM = blockItem("randomizer", BCBlocks.RANDOMIZER);

    public static final DeferredItem<BlockItem> LIGHTWEIGHT_INVERTER_ITEM = blockItem("lightweight_inverter", BCBlocks.LIGHTWEIGHT_INVERTER);
    public static final DeferredItem<BlockItem> LIGHTWEIGHT_AND_GATE_ITEM = blockItem("lightweight_and_gate", BCBlocks.LIGHTWEIGHT_AND_GATE);
    public static final DeferredItem<BlockItem> LIGHTWEIGHT_XOR_GATE_ITEM = blockItem("lightweight_xor_gate", BCBlocks.LIGHTWEIGHT_XOR_GATE);
    public static final DeferredItem<BlockItem> LIGHTWEIGHT_REPEATER_ITEM = blockItem("lightweight_repeater", BCBlocks.LIGHTWEIGHT_REPEATER);
    public static final DeferredItem<BlockItem> LIGHTWEIGHT_RS_LATCH_ITEM = blockItem("lightweight_rs_latch", BCBlocks.LIGHTWEIGHT_RS_LATCH);
    public static final DeferredItem<BlockItem> LIGHTWEIGHT_TIMER_ITEM = blockItem("lightweight_timer", BCBlocks.LIGHTWEIGHT_TIMER);
    public static final DeferredItem<BlockItem> LIGHTWEIGHT_COMPARATOR_ITEM = blockItem("lightweight_comparator", BCBlocks.LIGHTWEIGHT_COMPARATOR);
    public static final DeferredItem<BlockItem> LIGHTWEIGHT_CAPACITOR_ITEM = blockItem("lightweight_capacitor", BCBlocks.LIGHTWEIGHT_CAPACITOR);
    public static final DeferredItem<BlockItem> LIGHTWEIGHT_RANDOMIZER_ITEM = blockItem("lightweight_randomizer", BCBlocks.LIGHTWEIGHT_RANDOMIZER);

    //Custom placement - see RedstoneCableBlockItem for why a plain BlockItem isn't enough (the block has no
    //blockstate to carry which face was placed).
    public static final DeferredItem<RedstoneCableBlockItem> REDSTONE_CABLE_ITEM = ITEMS.register("redstone_cable",
            () -> new RedstoneCableBlockItem(new Item.Properties()));

    //Insulated Redstone Cable - one item per DyeColor, all placing the exact same underlying Block as
    //REDSTONE_CABLE_ITEM (a single block position needs to host different colors on different faces at once).
    public static final Map<DyeColor, DeferredItem<RedstoneCableBlockItem>> INSULATED_CABLE_ITEMS = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : DyeColor.values()) {
            INSULATED_CABLE_ITEMS.put(color, ITEMS.register(color.getName() + "_insulated_cable",
                    () -> new RedstoneCableBlockItem(new Item.Properties(), color)));
        }
    }

    //Bundled Redstone Cable - a single, uncolored variant, same underlying Block/BlockEntity as the above.
    public static final DeferredItem<RedstoneCableBlockItem> BUNDLED_CABLE_ITEM = ITEMS.register("bundled_redstone_cable",
            () -> new RedstoneCableBlockItem(new Item.Properties(), null, true));

    public static final DeferredItem<BlockItem> COMPARATOR_RAIL_ITEM = blockItem("comparator_rail", BCBlocks.COMPARATOR_RAIL);

    public static final DeferredItem<BlockItem> GOLD_BUTTON_ITEM = blockItem("gold_button", BCBlocks.GOLD_BUTTON);

    public static final DeferredItem<BlockItem> IRON_BUTTON_ITEM = blockItem("iron_button", BCBlocks.IRON_BUTTON);

    public static final DeferredItem<BlockItem> FILTERED_HOPPER_ITEM = blockItem("filtered_hopper", BCBlocks.FILTERED_HOPPER);

    //Not a BlockItem or vanilla MinecartItem (its useOn spawns via the fixed AbstractMinecart.Type enum, which
    //can't target a custom entity type).
    public static final DeferredItem<FilteredHopperMinecartItem> FILTERED_HOPPER_MINECART_ITEM = ITEMS.register("filtered_hopper_minecart",
            () -> new FilteredHopperMinecartItem(new Item.Properties()));

    public static final DeferredItem<BlockItem> BLOWER_ITEM = blockItem("blower", BCBlocks.BLOWER);
    public static final DeferredItem<BlockItem> VACUUM_ITEM = blockItem("vacuum", BCBlocks.VACUUM);

    //---- Block Items ----
    public static final DeferredItem<BlockItem> ALUMINUM_BLOCK_ITEM =
            ITEMS.register("aluminum_block", () -> new BlockItem(BCBlocks.ALUMINUM_BLOCK.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> ALUMINUM_ORE_ITEM =
            ITEMS.register("aluminum_ore", () -> new BlockItem(BCBlocks.ALUMINUM_ORE.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> DEEPSLATE_ALUMINUM_ORE_ITEM =
            ITEMS.register("deepslate_aluminum_ore", () -> new BlockItem(BCBlocks.DEEPSLATE_ALUMINUM_ORE.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> RAW_ALUMINUM_BLOCK_ITEM =
            ITEMS.register("raw_aluminum_block", () -> new BlockItem(BCBlocks.RAW_ALUMINUM_BLOCK.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> ALUMINUM_LANTERN_ITEM =
            ITEMS.register("aluminum_lantern", () -> new BlockItem(BCBlocks.ALUMINUM_LANTERN.get(), new Item.Properties()));

    public static final DeferredItem<StandingAndWallBlockItem> ALUMINUM_TORCH_ITEM =
            ITEMS.register("aluminum_torch", () -> new StandingAndWallBlockItem(
                    BCBlocks.ALUMINUM_TORCH.get(), BCBlocks.ALUMINUM_WALL_TORCH.get(), new Item.Properties(), Direction.DOWN));

    public static final DeferredItem<BlockItem> ALUMINUM_CHAIN_ITEM =
            ITEMS.register("aluminum_chain", () -> new BlockItem(BCBlocks.ALUMINUM_CHAIN.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> ALUMINUM_BARS_ITEM =
            ITEMS.register("aluminum_bars", () -> new BlockItem(BCBlocks.ALUMINUM_BARS.get(), new Item.Properties()));

    //Places RedstoneCableBlock (the same shared Block Insulated/Bundled Cable already use), not its own block -
    //see AluminumFrameBlockItem's own class comment.
    public static final DeferredItem<AluminumFrameBlockItem> ALUMINUM_FRAME_ITEM =
            ITEMS.register("aluminum_frame", () -> new AluminumFrameBlockItem(new Item.Properties()));

    public static final DeferredItem<DoubleHighBlockItem> ALUMINUM_DOOR_ITEM =
            ITEMS.register("aluminum_door", () -> new DoubleHighBlockItem(BCBlocks.ALUMINUM_DOOR.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> ALUMINUM_TRAPDOOR_ITEM =
            ITEMS.register("aluminum_trapdoor", () -> new BlockItem(BCBlocks.ALUMINUM_TRAPDOOR.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> ALUMINUM_GRATE_ITEM =
            ITEMS.register("aluminum_grate", () -> new BlockItem(BCBlocks.ALUMINUM_GRATE.get(), new Item.Properties()));

    private static DeferredItem<BlockItem> blockItem(String name, DeferredBlock<? extends Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
