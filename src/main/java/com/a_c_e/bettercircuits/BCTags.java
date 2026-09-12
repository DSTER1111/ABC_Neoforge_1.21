package com.a_c_e.bettercircuits;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

//Shared custom tags this mod needs at runtime (not just at datagen time - see the datagen providers themselves
//for where each one's actual contents get written).
public class BCTags {
    public static final TagKey<Block> HOT_BLOCK = blockTag("hot_block");
    public static final TagKey<Block> COLD_BLOCK = blockTag("cold_block");

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, path));
    }
}
