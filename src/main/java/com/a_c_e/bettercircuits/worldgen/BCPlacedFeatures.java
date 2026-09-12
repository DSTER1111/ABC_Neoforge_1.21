package com.a_c_e.bettercircuits.worldgen;

import com.a_c_e.bettercircuits.BetterCircuits;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

import java.util.List;

//Datagen port of MoreBetter's MBPlacedFeatures, restricted to the aluminum ore placed feature.
public class BCPlacedFeatures {
    //Resource Keys
    public static final ResourceKey<PlacedFeature> ALUMINUM_ORE_PLACED_KEY = registerKey("aluminum_ore_placed");

    //Feature Placements
    public static void bootstrap(BootstrapContext<PlacedFeature> context) {
        //Get Configured Features
        var configuredFeatures = context.lookup(Registries.CONFIGURED_FEATURE);

        register(context, ALUMINUM_ORE_PLACED_KEY, configuredFeatures.getOrThrow(BCConfiguredFeatures.OVERWORLD_ALUMINUM_ORE_KEY),
                BCOrePlacement.commonOrePlacement(12, HeightRangePlacement.uniform(VerticalAnchor.absolute(-64), VerticalAnchor.absolute(80))));
    }

    //Register Key Method
    private static ResourceKey<PlacedFeature> registerKey(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, name));
    }

    //Register Method
    private static void register(BootstrapContext<PlacedFeature> context, ResourceKey<PlacedFeature> key, Holder<ConfiguredFeature<?, ?>> configuration,
                                 List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }
}
