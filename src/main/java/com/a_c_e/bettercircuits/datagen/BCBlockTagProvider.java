package com.a_c_e.bettercircuits.datagen;

import com.a_c_e.bettercircuits.BCTags;
import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.block.BCBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

//Datagen port of MoreBetter's MBBlockTagProvider, restricted to the redstone-only subset of features in scope for
//Better Circuits (see the porting task's own scope list). Every brick/oremite/cotton/farmland/lamp tag membership
//from the source is intentionally omitted - only the Aluminum chain, the aluminum door/trapdoor, Redstone Cable,
//Comparator Rail (its BlockTags.RAILS membership is load-bearing - see the comment below), and the new hot_block
//tag are ported.
public class BCBlockTagProvider extends BlockTagsProvider {
    //Common Convention Tags (per-material) - mirrors MBBlockTagProvider's own aluminum tags exactly, since the
    //Aluminum ore/material chain is in scope here too.
    public static final TagKey<Block> ORES_ALUMINUM = blockTag("ores/aluminum");
    public static final TagKey<Block> STORAGE_BLOCKS_ALUMINUM = blockTag("storage_blocks/aluminum");
    public static final TagKey<Block> STORAGE_BLOCKS_RAW_ALUMINUM = blockTag("storage_blocks/raw_aluminum");

    public BCBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, BetterCircuits.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(BCBlocks.ALUMINUM_BLOCK.get())
                .add(BCBlocks.ALUMINUM_ORE.get())
                .add(BCBlocks.DEEPSLATE_ALUMINUM_ORE.get())
                .add(BCBlocks.RAW_ALUMINUM_BLOCK.get())
                .add(BCBlocks.ALUMINUM_LANTERN.get())
                .add(BCBlocks.ALUMINUM_CHAIN.get())
                .add(BCBlocks.ALUMINUM_BARS.get())
                .add(BCBlocks.ALUMINUM_DOOR.get())
                .add(BCBlocks.ALUMINUM_TRAPDOOR.get())
                .add(BCBlocks.ALUMINUM_GRATE.get());

        tag(BlockTags.NEEDS_STONE_TOOL)
                .add(BCBlocks.ALUMINUM_BLOCK.get())
                .add(BCBlocks.ALUMINUM_ORE.get())
                .add(BCBlocks.DEEPSLATE_ALUMINUM_ORE.get())
                .add(BCBlocks.RAW_ALUMINUM_BLOCK.get())
                .add(BCBlocks.ALUMINUM_GRATE.get());

        tag(BlockTags.DOORS).add(BCBlocks.ALUMINUM_DOOR.get());
        tag(BlockTags.TRAPDOORS).add(BCBlocks.ALUMINUM_TRAPDOOR.get());

        //Redstone Cable (shared with Insulated/Bundled Cable - all three item variants place the exact same
        //underlying Block, see RedstoneCableBlockItem) - needed so Player#getDestroySpeed actually returns a
        //pickaxe's boosted speed; a pickaxe only mines faster on blocks in this tag at all, tag membership or not.
        //Harmless for every cable face, which stays flat-instant regardless of held item and never consults this.
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(BCBlocks.REDSTONE_CABLE.get());

        //Common Convention Tags
        tag(Tags.Blocks.ORES)
                .add(BCBlocks.ALUMINUM_ORE.get())
                .add(BCBlocks.DEEPSLATE_ALUMINUM_ORE.get());
        tag(ORES_ALUMINUM)
                .add(BCBlocks.ALUMINUM_ORE.get())
                .add(BCBlocks.DEEPSLATE_ALUMINUM_ORE.get());

        tag(Tags.Blocks.STORAGE_BLOCKS)
                .add(BCBlocks.ALUMINUM_BLOCK.get())
                .add(BCBlocks.RAW_ALUMINUM_BLOCK.get());
        tag(STORAGE_BLOCKS_ALUMINUM)
                .add(BCBlocks.ALUMINUM_BLOCK.get());
        tag(STORAGE_BLOCKS_RAW_ALUMINUM)
                .add(BCBlocks.RAW_ALUMINUM_BLOCK.get());

        //Bug fix: BaseRailBlock.isRail (the check vanilla's own rail-shape connection logic, minecart placement,
        //and minecart movement/pathing ALL gate on) requires BOTH `instanceof BaseRailBlock` AND membership in
        //this exact tag - extending BaseRailBlock alone is not enough, and without this the block never actually
        //behaves like a rail at all (no snapping to neighbors, minecarts couldn't be placed on or ride across it).
        tag(BlockTags.RAILS).add(BCBlocks.COMPARATOR_RAIL.get());

        //Heat Sensor's own candidate list (see HeatSensorBlock) - light sources that are "contained" (lanterns,
        //redstone lamp, glowstone, jack o'lantern, candles, etc.) are deliberately excluded, matching the source
        //mod's own spec: this is about open flame/heat, not brightness. Furnace/Smoker/Blast Furnace/Campfire/Soul
        //Campfire also need their own LIT property checked at runtime (see HeatSensorBlock.isHotBlock) since a
        //tag can't express "only when lit".
        tag(BCTags.HOT_BLOCK)
                .add(Blocks.LAVA)
                .add(Blocks.LAVA_CAULDRON)
                .add(Blocks.FIRE)
                .add(Blocks.SOUL_FIRE)
                .add(Blocks.TORCH)
                .add(Blocks.WALL_TORCH)
                .add(Blocks.SOUL_TORCH)
                .add(Blocks.SOUL_WALL_TORCH)
                .add(Blocks.CAMPFIRE)
                .add(Blocks.SOUL_CAMPFIRE)
                .add(Blocks.FURNACE)
                .add(Blocks.SMOKER)
                .add(Blocks.BLAST_FURNACE)
                .add(Blocks.MAGMA_BLOCK);
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("c", path));
    }
}
