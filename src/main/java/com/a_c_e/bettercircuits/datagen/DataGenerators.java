package com.a_c_e.bettercircuits.datagen;

import com.a_c_e.bettercircuits.BetterCircuits;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = BetterCircuits.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        //Block Loot Table Generator
        generator.addProvider(event.includeServer(), new LootTableProvider(packOutput, Collections.emptySet(),
                List.of(new LootTableProvider.SubProviderEntry(BCBlockLootTableProvider::new, LootContextParamSets.BLOCK)),
                lookupProvider));

        //Recipe Generator
        generator.addProvider(event.includeServer(), new BCRecipeProvider(packOutput, lookupProvider));

        //Block Tags Generator
        BlockTagsProvider blockTagsProvider = new BCBlockTagProvider(packOutput, lookupProvider, existingFileHelper);
        generator.addProvider(event.includeServer(), blockTagsProvider);

        //Item Tags Generator
        generator.addProvider(event.includeServer(), new BCItemTagProvider(packOutput, lookupProvider, blockTagsProvider.contentsGetter(), existingFileHelper));

        //Item Model Generator
        generator.addProvider(event.includeClient(), new BCItemModelProvider(packOutput, existingFileHelper));

        //BlockState Generator
        generator.addProvider(event.includeClient(), new BCBlockStateProvider(packOutput, existingFileHelper));

        //World Generation (Configured/Placed Features/Biome Modifiers)
        generator.addProvider(event.includeServer(), new BCWorldGenProvider(packOutput, lookupProvider));
    }
}
