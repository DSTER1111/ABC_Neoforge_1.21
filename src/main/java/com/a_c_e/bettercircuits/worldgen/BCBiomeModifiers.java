package com.a_c_e.bettercircuits.worldgen;

import com.a_c_e.bettercircuits.BetterCircuits;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

//Injects Aluminum Ore's placed feature into overworld biomes without replacing their datapack files.
//NOTE: in the source mod (More Better) aluminum ore's own configured/placed feature pair is never actually
//attached to any biome via a BiomeModifier - it's dead/inert code there (the ore never spawns). MoreBetter's
//other ores/features (e.g. its Oremite mob spawn, which - like a generic overworld ore - is unrestricted by
//specific biome, gated instead by BiomeTags.IS_OVERWORLD) DO use this exact AddFeaturesBiomeModifier /
//AddSpawnsBiomeModifier + BiomeTags.IS_OVERWORLD pattern successfully. This class replicates that same working
//pattern for aluminum specifically, so - unlike the source mod - Better Circuits' aluminum ore actually generates.
public class BCBiomeModifiers {
    public static final ResourceKey<BiomeModifier> ADD_ALUMINUM_ORE_KEY = registerKey("add_aluminum_ore");

    public static void bootstrap(BootstrapContext<BiomeModifier> context) {
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);

        context.register(ADD_ALUMINUM_ORE_KEY, new BiomeModifiers.AddFeaturesBiomeModifier(
                biomes.getOrThrow(BiomeTags.IS_OVERWORLD),
                HolderSet.direct(placedFeatures.getOrThrow(BCPlacedFeatures.ALUMINUM_ORE_PLACED_KEY)),
                GenerationStep.Decoration.UNDERGROUND_ORES));
    }

    private static ResourceKey<BiomeModifier> registerKey(String name) {
        return ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, name));
    }
}
