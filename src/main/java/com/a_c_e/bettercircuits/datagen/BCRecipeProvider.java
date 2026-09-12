package com.a_c_e.bettercircuits.datagen;

import com.a_c_e.bettercircuits.block.BCBlocks;
import com.a_c_e.bettercircuits.item.BCItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.conditions.IConditionBuilder;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BCRecipeProvider extends RecipeProvider implements IConditionBuilder {
    public BCRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        //Recipe Lists
        List<ItemLike> ALUMINUM_SMELTABLES = List.of(BCItems.RAW_ALUMINUM, BCBlocks.ALUMINUM_ORE, BCBlocks.DEEPSLATE_ALUMINUM_ORE);

        //Shaped Crafting Recipes
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, BCBlocks.ALUMINUM_BLOCK.get())
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_aluminum_ingot", has(BCItems.ALUMINUM_INGOT)).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BCItems.ALUMINUM_INGOT.get())
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', BCItems.ALUMINUM_NUGGET.get())
                .unlockedBy("has_aluminum_ingot", has(BCItems.ALUMINUM_NUGGET)).save(recipeOutput);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, BCBlocks.RAW_ALUMINUM_BLOCK.get())
                .pattern("AAA")
                .pattern("AAA")
                .pattern("AAA")
                .define('A', BCItems.RAW_ALUMINUM.get())
                .unlockedBy("has_raw_aluminum", has(BCItems.RAW_ALUMINUM)).save(recipeOutput);

        //Shapeless Crafting Recipes
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, BCItems.ALUMINUM_INGOT.get(), 9)
                .requires(BCBlocks.ALUMINUM_BLOCK)
                .unlockedBy("has_aluminum_block", has(BCBlocks.ALUMINUM_BLOCK)).save(recipeOutput, "aluminum_ingot_2");
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, BCItems.ALUMINUM_NUGGET.get(), 9)
                .requires(BCItems.ALUMINUM_INGOT)
                .unlockedBy("has_aluminum_block", has(BCItems.ALUMINUM_INGOT)).save(recipeOutput);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, BCItems.RAW_ALUMINUM.get(), 9)
                .requires(BCBlocks.RAW_ALUMINUM_BLOCK)
                .unlockedBy("has_raw_aluminum_block", has(BCBlocks.RAW_ALUMINUM_BLOCK)).save(recipeOutput);

        //Smelting Recipes
        oreSmelting(recipeOutput, ALUMINUM_SMELTABLES, RecipeCategory.MISC, BCItems.ALUMINUM_INGOT.get(), 0.25f, 200, "aluminum_ingot");
        oreBlasting(recipeOutput, ALUMINUM_SMELTABLES, RecipeCategory.MISC, BCItems.ALUMINUM_INGOT.get(), 0.25f, 100, "aluminum_ingot");

        //Raw ore block -> ingot block (no vanilla equivalent - vanilla only smelts the raw ITEM, never the compressed block).
        //XP is the per-item smelting XP vanilla already uses for that material, scaled by 9 for the full block.
        rawBlockSmelting(recipeOutput, BCBlocks.RAW_ALUMINUM_BLOCK.get(), BCBlocks.ALUMINUM_BLOCK.get(), 0.25f * 9, "aluminum_block");

        aluminumFixtureRecipes(recipeOutput);

        //Shape mirrors vanilla's own comparator recipe (3 torches + a center item over stone), substituting
        //redstone dust in for the comparator's quartz
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.AND_GATE_ITEM.get())
                .pattern(" T ")
                .pattern("TDT")
                .pattern("SSS")
                .define('T', Items.REDSTONE_TORCH)
                .define('D', Items.REDSTONE)
                .define('S', Items.STONE)
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.XOR_GATE_ITEM.get())
                .pattern(" R ")
                .pattern("RCR")
                .pattern("SSS")
                .define('R', Items.REDSTONE)
                .define('C', Items.QUARTZ)
                .define('S', Items.STONE)
                .unlockedBy("has_quartz", has(Items.QUARTZ))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.LIGHTWEIGHT_XOR_GATE_ITEM.get())
                .pattern(" R ")
                .pattern("RCR")
                .pattern("SSS")
                .define('R', BCItems.REDSTONE_CABLE_ITEM.get())
                .define('C', Items.QUARTZ)
                .define('S', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_quartz", has(Items.QUARTZ))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.INVERTER_ITEM.get())
                .pattern("   ")
                .pattern("DTD")
                .pattern("SSS")
                .define('D', Items.REDSTONE)
                .define('T', Items.REDSTONE_TORCH)
                .define('S', Items.STONE)
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.REDSTONE_THRESHOLD_ITEM.get())
                .pattern("   ")
                .pattern("QTD")
                .pattern("SSS")
                .define('Q', Items.QUARTZ)
                .define('T', Items.REDSTONE_TORCH)
                .define('D', Items.REDSTONE)
                .define('S', Items.STONE)
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.LIGHTWEIGHT_INVERTER_ITEM.get())
                .pattern("   ")
                .pattern("DTD")
                .pattern("SSS")
                .define('D', BCItems.REDSTONE_CABLE_ITEM.get())
                .define('T', Items.REDSTONE_TORCH)
                .define('S', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.LIGHTWEIGHT_REDSTONE_THRESHOLD_ITEM.get())
                .pattern("   ")
                .pattern("QTD")
                .pattern("SSS")
                .define('Q', Items.QUARTZ)
                .define('T', Items.REDSTONE_TORCH)
                .define('D', BCItems.REDSTONE_CABLE_ITEM.get())
                .define('S', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.LIGHTWEIGHT_AND_GATE_ITEM.get())
                .pattern(" T ")
                .pattern("TDT")
                .pattern("SSS")
                .define('T', Items.REDSTONE_TORCH)
                .define('D', BCItems.REDSTONE_CABLE_ITEM.get())
                .define('S', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        //Matches vanilla's own Repeater recipe shape exactly (2 redstone torches flanking a dust, over 3
        //stone), substituting dust -> Redstone Cable and stone -> Aluminum Ingot per the usual convention.
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.LIGHTWEIGHT_REPEATER_ITEM.get())
                .pattern("TDT")
                .pattern("SSS")
                .define('T', Items.REDSTONE_TORCH)
                .define('D', BCItems.REDSTONE_CABLE_ITEM.get())
                .define('S', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.RS_LATCH_ITEM.get())
                .pattern("DDT")
                .pattern("TDD")
                .pattern("SSS")
                .define('D', Items.REDSTONE)
                .define('T', Items.REDSTONE_TORCH)
                .define('S', Items.STONE)
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.LIGHTWEIGHT_RS_LATCH_ITEM.get())
                .pattern("DDT")
                .pattern("TDD")
                .pattern("SSS")
                .define('D', BCItems.REDSTONE_CABLE_ITEM.get())
                .define('T', Items.REDSTONE_TORCH)
                .define('S', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.TIMER_ITEM.get())
                .pattern(" T ")
                .pattern("DCD")
                .pattern("SSS")
                .define('T', Items.REDSTONE_TORCH)
                .define('D', Items.REDSTONE)
                .define('C', Items.CLOCK)
                .define('S', Items.STONE)
                .unlockedBy("has_clock", has(Items.CLOCK))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, BCItems.SCREWDRIVER_ITEM.get())
                .pattern("  A")
                .pattern("DA ")
                .pattern("AD ")
                .define('A', BCItems.ALUMINUM_INGOT.get())
                .define('D', Items.REDSTONE)
                .unlockedBy("has_aluminum_ingot", has(BCItems.ALUMINUM_INGOT.get()))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.LIGHTWEIGHT_RANDOMIZER_ITEM.get())
                .pattern(" D ")
                .pattern("DTD")
                .pattern("SSS")
                .define('D', BCItems.REDSTONE_CABLE_ITEM.get())
                .define('T', Items.REDSTONE_TORCH)
                .define('S', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.LIGHTWEIGHT_CAPACITOR_ITEM.get())
                .pattern(" T ")
                .pattern("QQQ")
                .pattern("SSS")
                .define('T', Items.REDSTONE_TORCH)
                .define('Q', Items.QUARTZ)
                .define('S', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.LIGHTWEIGHT_TIMER_ITEM.get())
                .pattern(" T ")
                .pattern("DCD")
                .pattern("SSS")
                .define('T', Items.REDSTONE_TORCH)
                .define('D', BCItems.REDSTONE_CABLE_ITEM.get())
                .define('C', Items.CLOCK)
                .define('S', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_clock", has(Items.CLOCK))
                .save(recipeOutput);

        //Matches vanilla's own Comparator recipe shape exactly (torch, quartz, torch over 3 stone),
        //substituting stone -> Aluminum Ingot per the usual convention (no dust in this recipe to substitute).
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.LIGHTWEIGHT_COMPARATOR_ITEM.get())
                .pattern(" T ")
                .pattern("TQT")
                .pattern("SSS")
                .define('T', Items.REDSTONE_TORCH)
                .define('Q', Items.QUARTZ)
                .define('S', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.RAIN_DETECTOR_ITEM.get())
                .pattern("GGG")
                .pattern("SSS")
                .pattern("WWW")
                .define('G', Items.GLASS)
                .define('S', Items.SLIME_BALL)
                .define('W', ItemTags.WOODEN_SLABS)
                .unlockedBy("has_slime_ball", has(Items.SLIME_BALL))
                .save(recipeOutput);

        //Same shape as Rain Detector's own recipe (glass/slime ball/wooden slabs), just with a snowball instead
        //of the slime ball - matching Heat Detector's own "hot/cold" theming.
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.HEAT_DETECTOR_ITEM.get())
                .pattern("GGG")
                .pattern("SSS")
                .pattern("WWW")
                .define('G', Items.GLASS)
                .define('S', Items.SNOWBALL)
                .define('W', ItemTags.WOODEN_SLABS)
                .unlockedBy("has_snowball", has(Items.SNOWBALL))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.CAPACITOR_ITEM.get())
                .pattern(" T ")
                .pattern("QQQ")
                .pattern("SSS")
                .define('T', Items.REDSTONE_TORCH)
                .define('Q', Items.QUARTZ)
                .define('S', Items.STONE)
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.RANDOMIZER_ITEM.get())
                .pattern(" D ")
                .pattern("DTD")
                .pattern("SSS")
                .define('D', Items.REDSTONE)
                .define('T', Items.REDSTONE_TORCH)
                .define('S', Items.STONE)
                .unlockedBy("has_redstone_torch", has(Items.REDSTONE_TORCH))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.REDSTONE_CABLE_ITEM.get(), 8)
                .pattern("RRR")
                .pattern("RCR")
                .pattern("RRR")
                .define('R', Items.REDSTONE)
                .define('C', Items.COPPER_INGOT)
                .unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
                .save(recipeOutput);

        insulatedCableRecipes(recipeOutput);
        bundledCableRecipes(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.COMPARATOR_RAIL_ITEM.get(), 6)
                .pattern("III")
                .pattern("ICI")
                .pattern("III")
                .define('I', Items.IRON_INGOT)
                .define('C', Items.COMPARATOR)
                .unlockedBy("has_comparator", has(Items.COMPARATOR))
                .save(recipeOutput);

        //A 2x2 of nuggets rather than vanilla's own 1-ingredient-in, 1-button-out shape (stone -> stone button,
        //planks -> wood button) - per the source mod's own spec, a cheaper nugget-based recipe for these two.
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.GOLD_BUTTON_ITEM.get())
                .pattern("NN")
                .pattern("NN")
                .define('N', Items.GOLD_NUGGET)
                .unlockedBy("has_gold_nugget", has(Items.GOLD_NUGGET))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.IRON_BUTTON_ITEM.get())
                .pattern("NN")
                .pattern("NN")
                .define('N', Items.IRON_NUGGET)
                .unlockedBy("has_iron_nugget", has(Items.IRON_NUGGET))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, BCItems.FILTERED_HOPPER_ITEM.get())
                .requires(Items.HOPPER)
                .requires(Items.ITEM_FRAME)
                .unlockedBy("has_hopper", has(Items.HOPPER))
                .save(recipeOutput);

        //Matches vanilla's own Hopper Minecart recipe shape (Hopper + Minecart), substituting our own Filtered
        //Hopper for the plain one.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TRANSPORTATION, BCItems.FILTERED_HOPPER_MINECART_ITEM.get())
                .requires(BCItems.FILTERED_HOPPER_ITEM.get())
                .requires(Items.MINECART)
                .unlockedBy("has_filtered_hopper", has(BCItems.FILTERED_HOPPER_ITEM))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.BLOWER_ITEM.get())
                .pattern("CCC")
                .pattern("RDW")
                .pattern("CCC")
                .define('C', Items.COBBLESTONE)
                .define('R', Items.REDSTONE)
                .define('D', Items.DISPENSER)
                .define('W', Items.WIND_CHARGE)
                .unlockedBy("has_dispenser", has(Items.DISPENSER))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.VACUUM_ITEM.get())
                .pattern("CCC")
                .pattern("RWH")
                .pattern("CCC")
                .define('C', Items.COBBLESTONE)
                .define('R', Items.REDSTONE)
                .define('W', Items.WIND_CHARGE)
                .define('H', Items.HOPPER)
                .unlockedBy("has_hopper", has(Items.HOPPER))
                .save(recipeOutput);
    }

    //Mirrors the vanilla iron/copper recipes for these fixtures, substituting in aluminum ingot/nugget/block
    private void aluminumFixtureRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BCItems.ALUMINUM_TORCH_ITEM.get(), 4)
                .pattern("X")
                .pattern("#")
                .define('X', BCItems.ALUMINUM_NUGGET.get())
                .define('#', Items.STICK)
                .unlockedBy("has_aluminum_nugget", has(BCItems.ALUMINUM_NUGGET))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BCItems.ALUMINUM_LANTERN_ITEM.get())
                .pattern("XXX")
                .pattern("X#X")
                .pattern("XXX")
                .define('X', BCItems.ALUMINUM_NUGGET.get())
                .define('#', BCItems.ALUMINUM_TORCH_ITEM.get())
                .unlockedBy("has_aluminum_ingot", has(BCItems.ALUMINUM_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BCItems.ALUMINUM_CHAIN_ITEM.get())
                .pattern("N")
                .pattern("I")
                .pattern("N")
                .define('N', BCItems.ALUMINUM_NUGGET.get())
                .define('I', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_aluminum_ingot", has(BCItems.ALUMINUM_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BCItems.ALUMINUM_BARS_ITEM.get(), 16)
                .pattern("###")
                .pattern("###")
                .define('#', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_aluminum_ingot", has(BCItems.ALUMINUM_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BCItems.ALUMINUM_FRAME_ITEM.get(), 4)
                .pattern("NBN")
                .pattern("B B")
                .pattern("NBN")
                .define('N', BCItems.ALUMINUM_NUGGET.get())
                .define('B', BCItems.ALUMINUM_BARS_ITEM.get())
                .unlockedBy("has_aluminum_ingot", has(BCItems.ALUMINUM_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.ALUMINUM_DOOR_ITEM.get(), 3)
                .pattern("##")
                .pattern("##")
                .pattern("##")
                .define('#', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_aluminum_ingot", has(BCItems.ALUMINUM_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.ALUMINUM_TRAPDOOR_ITEM.get())
                .pattern("##")
                .pattern("##")
                .define('#', BCItems.ALUMINUM_INGOT.get())
                .unlockedBy("has_aluminum_ingot", has(BCItems.ALUMINUM_INGOT))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, BCItems.ALUMINUM_GRATE_ITEM.get(), 4)
                .pattern(" M ")
                .pattern("M M")
                .pattern(" M ")
                .define('M', BCBlocks.ALUMINUM_BLOCK.get())
                .unlockedBy("has_aluminum_block", has(BCBlocks.ALUMINUM_BLOCK))
                .save(recipeOutput);
    }

    //1 colored carpet + 1 Redstone Cable -> 1 Insulated Redstone Cable of that color.
    //DEVIATION FROM SOURCE: More Better also accepts an alternate Cotton Rug ingredient here; Cotton is not
    //part of this port (no Cotton Rug item exists in Better Circuits), so only the vanilla carpet is used.
    private void insulatedCableRecipes(RecipeOutput recipeOutput) {
        for (DyeColor color : DyeColor.values()) {
            Item carpet = vanillaItem(color.getName() + "_carpet");
            Item insulatedCable = BCItems.INSULATED_CABLE_ITEMS.get(color).get();
            ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, insulatedCable)
                    .requires(carpet)
                    .requires(BCItems.REDSTONE_CABLE_ITEM.get())
                    .unlockedBy("has_redstone_cable", has(BCItems.REDSTONE_CABLE_ITEM))
                    .save(recipeOutput);
        }
    }

    //5 Dried Kelp + 4 Insulated Redstone Cable (any color, mixed colors allowed - see Ingredient.of below) ->
    //1 Bundled Redstone Cable.
    private void bundledCableRecipes(RecipeOutput recipeOutput) {
        ItemLike[] insulatedCableItems = BCItems.INSULATED_CABLE_ITEMS.values().stream()
                .map(item -> (ItemLike) item.get()).toArray(ItemLike[]::new);
        Ingredient anyInsulatedCable = Ingredient.of(insulatedCableItems);
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, BCItems.BUNDLED_CABLE_ITEM.get())
                .pattern("KIK")
                .pattern("IKI")
                .pattern("KIK")
                .define('K', Items.DRIED_KELP)
                .define('I', anyInsulatedCable)
                .unlockedBy("has_dried_kelp", has(Items.DRIED_KELP))
                .save(recipeOutput);
    }

    //No vanilla equivalent - vanilla never lets you smelt a compressed raw ore block directly, only the raw item
    private void rawBlockSmelting(RecipeOutput recipeOutput, ItemLike rawBlock, ItemLike ingotBlock, float experience, String resultName) {
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(rawBlock), RecipeCategory.MISC, ingotBlock, experience, 200)
                .unlockedBy(getHasName(rawBlock.asItem()), has(rawBlock))
                .save(recipeOutput, resultName + "_from_smelting_" + getItemName(rawBlock.asItem()));
        SimpleCookingRecipeBuilder.blasting(Ingredient.of(rawBlock), RecipeCategory.MISC, ingotBlock, experience, 100)
                .unlockedBy(getHasName(rawBlock.asItem()), has(rawBlock))
                .save(recipeOutput, resultName + "_from_blasting_" + getItemName(rawBlock.asItem()));
    }

    private static Item vanillaItem(String path) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(path));
    }
}
