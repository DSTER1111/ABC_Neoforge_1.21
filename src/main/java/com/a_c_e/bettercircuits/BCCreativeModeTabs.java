package com.a_c_e.bettercircuits;

import com.a_c_e.bettercircuits.block.BCBlocks;
import com.a_c_e.bettercircuits.item.BCItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

//A single tab for the whole mod (unlike More Better's own per-vanilla-category split) - every ported block
//and item lands here, in one logically-grouped sequence.
public class BCCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BetterCircuits.MOD_ID);

    public static final Supplier<CreativeModeTab> BETTER_CIRCUITS = CREATIVE_MODE_TAB.register("better_circuits",
            () -> CreativeModeTab.builder()
                    .icon(() -> new net.minecraft.world.item.ItemStack(BCItems.REDSTONE_CABLE_ITEM.get()))
                    .title(Component.translatable("creativetab.better_circuits"))
                    .displayItems((itemDisplayParameters, output) -> {
                        //Aluminum ore/material chain
                        output.accept(BCItems.RAW_ALUMINUM);
                        output.accept(BCItems.ALUMINUM_INGOT);
                        output.accept(BCItems.ALUMINUM_NUGGET);
                        output.accept(BCBlocks.ALUMINUM_BLOCK);
                        output.accept(BCBlocks.RAW_ALUMINUM_BLOCK);
                        output.accept(BCBlocks.ALUMINUM_ORE);
                        output.accept(BCBlocks.DEEPSLATE_ALUMINUM_ORE);

                        //Aluminum Fixtures
                        output.accept(BCItems.ALUMINUM_LANTERN_ITEM);
                        output.accept(BCItems.ALUMINUM_TORCH_ITEM);
                        output.accept(BCItems.ALUMINUM_CHAIN_ITEM);
                        output.accept(BCItems.ALUMINUM_BARS_ITEM);
                        output.accept(BCItems.ALUMINUM_DOOR_ITEM);
                        output.accept(BCItems.ALUMINUM_TRAPDOOR_ITEM);
                        output.accept(BCItems.ALUMINUM_GRATE_ITEM);

                        //Heavy logic gates
                        output.accept(BCItems.AND_GATE_ITEM);
                        output.accept(BCItems.XOR_GATE_ITEM);
                        output.accept(BCItems.INVERTER_ITEM);
                        output.accept(BCItems.REDSTONE_THRESHOLD_ITEM);
                        output.accept(BCItems.RS_LATCH_ITEM);
                        output.accept(BCItems.TIMER_ITEM);
                        output.accept(BCItems.CAPACITOR_ITEM);
                        output.accept(BCItems.RANDOMIZER_ITEM);

                        //Lightweight Redstone Gate Family
                        output.accept(BCItems.LIGHTWEIGHT_INVERTER_ITEM);
                        output.accept(BCItems.LIGHTWEIGHT_REDSTONE_THRESHOLD_ITEM);
                        output.accept(BCItems.LIGHTWEIGHT_AND_GATE_ITEM);
                        output.accept(BCItems.LIGHTWEIGHT_XOR_GATE_ITEM);
                        output.accept(BCItems.LIGHTWEIGHT_REPEATER_ITEM);
                        output.accept(BCItems.LIGHTWEIGHT_RS_LATCH_ITEM);
                        output.accept(BCItems.LIGHTWEIGHT_TIMER_ITEM);
                        output.accept(BCItems.LIGHTWEIGHT_COMPARATOR_ITEM);
                        output.accept(BCItems.LIGHTWEIGHT_CAPACITOR_ITEM);
                        output.accept(BCItems.LIGHTWEIGHT_RANDOMIZER_ITEM);

                        //Sensors
                        output.accept(BCItems.RAIN_DETECTOR_ITEM);
                        output.accept(BCItems.HEAT_SENSOR_ITEM);

                        //Redstone Cable / Insulated / Bundled
                        output.accept(BCItems.REDSTONE_CABLE_ITEM);
                        for (DyeColor color : DyeColor.values()) {
                            output.accept(BCItems.INSULATED_CABLE_ITEMS.get(color));
                        }
                        output.accept(BCItems.BUNDLED_CABLE_ITEM);
                        output.accept(BCItems.ALUMINUM_FRAME_ITEM);

                        //Comparator Rail
                        output.accept(BCItems.COMPARATOR_RAIL_ITEM);

                        //Filtered Hopper family
                        output.accept(BCItems.FILTERED_HOPPER_ITEM);
                        output.accept(BCItems.FILTERED_HOPPER_MINECART_ITEM);

                        //Screwdriver
                        output.accept(BCItems.SCREWDRIVER_ITEM);

                        //Blower / Vacuum
                        output.accept(BCItems.BLOWER_ITEM);
                        output.accept(BCItems.VACUUM_ITEM);
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }
}
