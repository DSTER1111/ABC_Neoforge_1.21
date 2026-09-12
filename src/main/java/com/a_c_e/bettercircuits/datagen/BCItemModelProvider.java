package com.a_c_e.bettercircuits.datagen;

import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.item.BCItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

//Datagen port of MoreBetter's MBItemModelProvider, restricted to the redstone-only subset of features in scope for
//Better Circuits. Almost every other item in this mod gets its model from BCBlockStateProvider's own itemModels()
//calls (block items, redstone cable/insulated/bundled variants, comparator rail, filtered hopper, blower/vacuum,
//every gate/sensor) - this provider only needs the handful of items that have no associated block, or (Filtered
//Hopper Minecart) aren't placed via a plain BlockItem despite existing: the Screwdriver, the three raw aluminum
//material items, and the Filtered Hopper Minecart item. Everything else the source's own MBItemModelProvider
//handles (cotton, bat flower, fruits, spawn eggs, sickles, furnace boats) is out of scope and intentionally omitted.
public class BCItemModelProvider extends ItemModelProvider {
    public BCItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, BetterCircuits.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(BCItems.SCREWDRIVER_ITEM.get());

        basicItem(BCItems.RAW_ALUMINUM.get());
        basicItem(BCItems.ALUMINUM_INGOT.get());
        basicItem(BCItems.ALUMINUM_NUGGET.get());

        //Not a BlockItem (see FilteredHopperMinecartItem's own comment), so BCBlockStateProvider never covers it.
        basicItem(BCItems.FILTERED_HOPPER_MINECART_ITEM.get());
    }
}
