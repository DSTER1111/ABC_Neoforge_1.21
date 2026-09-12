package com.a_c_e.bettercircuits.datagen;

import com.a_c_e.bettercircuits.block.BCBlocks;
import com.a_c_e.bettercircuits.item.BCItems;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.Set;

//Datagen port of MoreBetter's MBBlockLootTableProvider, restricted to the redstone-only subset of features in scope
//for Better Circuits (see the porting task's own scope list). Method bodies are a mechanical 1:1 port - most
//blocks are plain dropSelf, the aluminum ore pair reuses the source's own createMultipleOreDrops helper, and the
//aluminum door uses the same vanilla createDoorTable helper the source uses. Aluminum Frame is NOT ported - this
//mod's Redstone Cable block has zero blockstate properties and no frame support, so it just drops itself like
//vanilla's own redstone dust (per-face drop logic lives entirely in RedstoneCableBlock's own break code, same as
//the source's own comment notes for its cable).
public class BCBlockLootTableProvider extends BlockLootSubProvider {
    protected BCBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        //Aluminum ore/material chain
        dropSelf(BCBlocks.ALUMINUM_BLOCK.get());
        dropSelf(BCBlocks.RAW_ALUMINUM_BLOCK.get());

        add(BCBlocks.ALUMINUM_ORE.get(),
                block -> createMultipleOreDrops(BCBlocks.ALUMINUM_ORE.get(), BCItems.RAW_ALUMINUM, 2, 4));
        add(BCBlocks.DEEPSLATE_ALUMINUM_ORE.get(),
                block -> createMultipleOreDrops(BCBlocks.DEEPSLATE_ALUMINUM_ORE.get(), BCItems.RAW_ALUMINUM, 2, 4));

        //Aluminum Fixtures (aluminum_wall_torch drops like aluminum_torch, no table of its own - see BCBlocks)
        dropSelf(BCBlocks.ALUMINUM_LANTERN.get());
        dropSelf(BCBlocks.ALUMINUM_TORCH.get());
        dropSelf(BCBlocks.ALUMINUM_CHAIN.get());
        dropSelf(BCBlocks.ALUMINUM_BARS.get());
        add(BCBlocks.ALUMINUM_DOOR.get(), createDoorTable(BCBlocks.ALUMINUM_DOOR.get()));
        dropSelf(BCBlocks.ALUMINUM_TRAPDOOR.get());
        dropSelf(BCBlocks.ALUMINUM_GRATE.get());

        //Drops itself regardless of POWERED, matching vanilla's own repeater/comparator
        dropSelf(BCBlocks.AND_GATE.get());
        dropSelf(BCBlocks.XOR_GATE.get());
        dropSelf(BCBlocks.INVERTER.get());
        dropSelf(BCBlocks.REDSTONE_THRESHOLD.get());
        dropSelf(BCBlocks.RS_LATCH.get());
        dropSelf(BCBlocks.TIMER.get());
        dropSelf(BCBlocks.RAIN_DETECTOR.get());
        dropSelf(BCBlocks.HEAT_DETECTOR.get());
        dropSelf(BCBlocks.CAPACITOR.get());
        dropSelf(BCBlocks.RANDOMIZER.get());
        dropSelf(BCBlocks.LIGHTWEIGHT_INVERTER.get());
        dropSelf(BCBlocks.LIGHTWEIGHT_REDSTONE_THRESHOLD.get());
        dropSelf(BCBlocks.LIGHTWEIGHT_AND_GATE.get());
        dropSelf(BCBlocks.LIGHTWEIGHT_XOR_GATE.get());
        dropSelf(BCBlocks.LIGHTWEIGHT_REPEATER.get());
        dropSelf(BCBlocks.LIGHTWEIGHT_RS_LATCH.get());
        dropSelf(BCBlocks.LIGHTWEIGHT_TIMER.get());
        dropSelf(BCBlocks.LIGHTWEIGHT_COMPARATOR.get());
        dropSelf(BCBlocks.LIGHTWEIGHT_CAPACITOR.get());
        dropSelf(BCBlocks.LIGHTWEIGHT_RANDOMIZER.get());

        //Drops itself regardless of power/connection state, matching vanilla's own redstone dust. Covers plain
        //Redstone Cable, every Insulated Redstone Cable color, and Bundled Redstone Cable alike - they all place
        //the exact same underlying Block/BlockEntity (see RedstoneCableBlockItem), so there's only ever this one
        //block-level loot table to write no matter how many item variants place it.
        dropSelf(BCBlocks.REDSTONE_CABLE.get());

        //Drops itself regardless of shape/signal state, matching every vanilla rail
        dropSelf(BCBlocks.COMPARATOR_RAIL.get());
        dropSelf(BCBlocks.GOLD_BUTTON.get());
        dropSelf(BCBlocks.IRON_BUTTON.get());

        //Drops only the hopper itself - the framed filter item is a separate drop, handled directly in
        //FilteredHopperBlock's own onRemove override (matching how breaking a real ItemFrame always ejects its
        //contents too), not by this static loot table.
        dropSelf(BCBlocks.FILTERED_HOPPER.get());

        dropSelf(BCBlocks.BLOWER.get());

        dropSelf(BCBlocks.VACUUM.get());
    }

    //Custom Ore Drops Method
    protected LootTable.Builder createMultipleOreDrops(Block pBlock, DeferredItem<Item> item, float minDrops, float maxDrops) {
        HolderLookup.RegistryLookup<Enchantment> registryLookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        return this.createSilkTouchDispatchTable(pBlock,
                this.applyExplosionDecay(pBlock, LootItem.lootTableItem(item)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(minDrops, maxDrops)))
                        .apply(ApplyBonusCount.addUniformBonusCount(registryLookup.getOrThrow(Enchantments.FORTUNE)))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return BCBlocks.BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }
}
