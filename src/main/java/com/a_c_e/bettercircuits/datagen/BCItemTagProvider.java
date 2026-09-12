package com.a_c_e.bettercircuits.datagen;

import com.a_c_e.bettercircuits.BetterCircuits;
import com.a_c_e.bettercircuits.item.BCItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

//Datagen port of MoreBetter's MBItemTagProvider, restricted to the redstone-only subset of features in scope for
//Better Circuits (see the porting task's own scope list). The Sickle-specific tags (SICKLES, FORTUNE_ELIGIBLE, and
//every enchantable tag the source hangs off them) are intentionally omitted - the Sickle doesn't exist in this
//mod. Cotton's wool/wool-carpet item tag mirroring and Fertilized Dirt's dirt-item tag are likewise out of scope.
public class BCItemTagProvider extends ItemTagsProvider {
    //Common Convention Tags (per-material) - mirrors MBItemTagProvider's own aluminum tags exactly, since the
    //Aluminum ore/material chain is in scope here too.
    public static final TagKey<Item> ORES_ALUMINUM = itemTag("ores/aluminum");
    public static final TagKey<Item> STORAGE_BLOCKS_ALUMINUM = itemTag("storage_blocks/aluminum");
    public static final TagKey<Item> STORAGE_BLOCKS_RAW_ALUMINUM = itemTag("storage_blocks/raw_aluminum");
    public static final TagKey<Item> INGOTS_ALUMINUM = itemTag("ingots/aluminum");
    public static final TagKey<Item> NUGGETS_ALUMINUM = itemTag("nuggets/aluminum");
    public static final TagKey<Item> RAW_MATERIALS_ALUMINUM = itemTag("raw_materials/aluminum");

    public BCItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, CompletableFuture<TagLookup<Block>> blockTags,
                              @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, blockTags, BetterCircuits.MOD_ID, existingFileHelper);
    }

    @Override
    public void addTags(HolderLookup.Provider provider) {
        //Mirror block tags onto their block items
        copy(Tags.Blocks.ORES, Tags.Items.ORES);
        copy(BCBlockTagProvider.ORES_ALUMINUM, ORES_ALUMINUM);
        copy(Tags.Blocks.STORAGE_BLOCKS, Tags.Items.STORAGE_BLOCKS);
        copy(BCBlockTagProvider.STORAGE_BLOCKS_ALUMINUM, STORAGE_BLOCKS_ALUMINUM);
        copy(BCBlockTagProvider.STORAGE_BLOCKS_RAW_ALUMINUM, STORAGE_BLOCKS_RAW_ALUMINUM);

        //Common Convention Tags
        tag(Tags.Items.INGOTS).add(BCItems.ALUMINUM_INGOT.get());
        tag(INGOTS_ALUMINUM).add(BCItems.ALUMINUM_INGOT.get());

        tag(Tags.Items.NUGGETS).add(BCItems.ALUMINUM_NUGGET.get());
        tag(NUGGETS_ALUMINUM).add(BCItems.ALUMINUM_NUGGET.get());

        tag(Tags.Items.RAW_MATERIALS).add(BCItems.RAW_ALUMINUM.get());
        tag(RAW_MATERIALS_ALUMINUM).add(BCItems.RAW_ALUMINUM.get());

        //Aluminum Fixtures
        copy(BlockTags.DOORS, ItemTags.DOORS);
        copy(BlockTags.TRAPDOORS, ItemTags.TRAPDOORS);

        //Durability (Unbreaking) and Mending both key off this same tag - matches the source mod's own spec
        //directly (comparable durability to Shears, enchantable with Unbreaking and Mending).
        tag(ItemTags.DURABILITY_ENCHANTABLE).add(BCItems.SCREWDRIVER_ITEM.get());
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }
}
