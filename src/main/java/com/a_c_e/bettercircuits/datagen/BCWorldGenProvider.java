package com.a_c_e.bettercircuits.datagen;

import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.worldgen.BCBiomeModifiers;
import com.a_c_e.bettercircuits.worldgen.BCConfiguredFeatures;
import com.a_c_e.bettercircuits.worldgen.BCPlacedFeatures;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BCWorldGenProvider extends DatapackBuiltinEntriesProvider {
    private static final RegistrySetBuilder BUILDER = new RegistrySetBuilder()
            .add(Registries.CONFIGURED_FEATURE, BCConfiguredFeatures::bootstrap)
            .add(Registries.PLACED_FEATURE, BCPlacedFeatures::bootstrap)
            .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, BCBiomeModifiers::bootstrap);

    public BCWorldGenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, BUILDER, Set.of(BetterCircuits.MOD_ID));
    }
}
