package com.a_c_e.bettercircuits;

import com.a_c_e.bettercircuits.block.BCBlocks;
import com.a_c_e.bettercircuits.block.entity.BCBlockEntityTypes;
import com.a_c_e.bettercircuits.block.entity.RedstoneCableBlockEntity;
import com.a_c_e.bettercircuits.block.entity.RedstoneCableBlockEntity.FaceState;
import com.a_c_e.bettercircuits.block.menu.BCMenuTypes;
import com.a_c_e.bettercircuits.client.AluminumFlameParticleProvider;
import com.a_c_e.bettercircuits.client.BlowerWindParticleProvider;
import com.a_c_e.bettercircuits.client.model.RedstoneCableBakedModel;
import com.a_c_e.bettercircuits.client.renderer.FilteredHopperBlockEntityRenderer;
import com.a_c_e.bettercircuits.client.renderer.FilteredHopperMinecartRenderer;
import com.a_c_e.bettercircuits.client.renderer.LightweightTimerBlockEntityRenderer;
import com.a_c_e.bettercircuits.client.renderer.TimerBlockEntityRenderer;
import com.a_c_e.bettercircuits.client.screen.RedstoneThresholdScreen;
import com.a_c_e.bettercircuits.client.screen.TimerScreen;
import com.a_c_e.bettercircuits.entity.BCEntityTypes;
import com.a_c_e.bettercircuits.item.BCItems;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.EnumMap;
import java.util.Map;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(BetterCircuits.MOD_ID)
public class BetterCircuits {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "better_circuits";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public BetterCircuits(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in.
        NeoForge.EVENT_BUS.register(new BCEventHandlers());

        //Register the mod's own single creative tab
        BCCreativeModeTabs.register(modEventBus);

        //Register mod blocks and items
        BCItems.register(modEventBus);
        BCBlocks.register(modEventBus);
        BCParticleTypes.register(modEventBus);
        BCEntityTypes.register(modEventBus);
        BCBlockEntityTypes.register(modEventBus);
        BCMenuTypes.register(modEventBus);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            //Must run before any world loads a Filtered Hopper (see its own class comment) - registers it as a
            //valid block for vanilla's own BlockEntityType.HOPPER, which is what every FilteredHopperBlockEntity
            //instance's own getType() actually reports.
            BCHopperBlockEntityTypeFix.register();

            //Lets dispensers plant vanilla seeds/crops onto farmland instead of just ejecting them.
            BCFarmlandPlantBehavior plantBehavior = new BCFarmlandPlantBehavior();
            DispenserBlock.registerBehavior(Items.WHEAT_SEEDS, plantBehavior);
            DispenserBlock.registerBehavior(Items.BEETROOT_SEEDS, plantBehavior);
            DispenserBlock.registerBehavior(Items.CARROT, plantBehavior);
            DispenserBlock.registerBehavior(Items.POTATO, plantBehavior);
            DispenserBlock.registerBehavior(Items.MELON_SEEDS, plantBehavior);
            DispenserBlock.registerBehavior(Items.PUMPKIN_SEEDS, plantBehavior);
            DispenserBlock.registerBehavior(Items.TORCHFLOWER_SEEDS, plantBehavior);
            DispenserBlock.registerBehavior(Items.PITCHER_POD, plantBehavior);
            DispenserBlock.registerBehavior(Items.NETHER_WART, new BCNetherWartPlantBehavior());

            //Lets dispensers fill/empty cauldrons the same way a player can, using the same items
            BCCauldronDispenseBehaviors.register();

            //Lets dispensers fill/empty a Filtered Hopper's own filter - registered LAST so it wraps whatever
            //every other dispense-behavior registration above already settled on (captures each item's CURRENT
            //behavior as its own fallback).
            BCFilteredHopperDispenseBehaviors.register();
        });
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = BetterCircuits.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    static class ClientModEvents {
        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {

        }

        @SubscribeEvent
        static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
            event.register(BCMenuTypes.TIMER.get(), TimerScreen::new);
            event.register(BCMenuTypes.REDSTONE_THRESHOLD.get(), RedstoneThresholdScreen::new);
        }

        //Reuses vanilla's own FlameParticle visuals/behavior (scaled up) with our own sprite for Aluminum Torch,
        //and vanilla's own small gust sprite (with actual velocity applied) for Blower/Vacuum's wind stream.
        @SubscribeEvent
        static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(BCParticleTypes.ALUMINUM_FLAME, AluminumFlameParticleProvider::new);
            event.registerSpriteSet(BCParticleTypes.BLOWER_WIND, BlowerWindParticleProvider::new);
        }

        @SubscribeEvent
        static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(BCBlockEntityTypes.TIMER.get(), TimerBlockEntityRenderer::new);
            event.registerBlockEntityRenderer(BCBlockEntityTypes.LIGHTWEIGHT_TIMER.get(), LightweightTimerBlockEntityRenderer::new);
            //Registered against vanilla's own BlockEntityType.HOPPER, not a dedicated better_circuits type - see
            //FilteredHopperBlockEntityRenderer's own comment on why (HopperBlockEntity's constructor hardcodes
            //that real type internally, so it's what every instance, including FilteredHopperBlockEntity,
            //actually reports).
            event.registerBlockEntityRenderer(BlockEntityType.HOPPER, FilteredHopperBlockEntityRenderer::new);
            event.registerEntityRenderer(BCEntityTypes.FILTERED_HOPPER_MINECART.get(), FilteredHopperMinecartRenderer::new);
        }

        //The torch+pointer+tip assembly TimerBlockEntityRenderer rotates every frame isn't part of any
        //blockstate variant (a blockstate can't express continuous rotation) - this is the standard NeoForge hook
        //for baking a "loose" model anyway, so TimerBlockEntityRenderer can look it up by its own standalone
        //ModelResourceLocation at render time.
        @SubscribeEvent
        static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
            event.register(ModelResourceLocation.standalone(
                    ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/timer_spinner")));
        }

        //Substitutes RedstoneCableBakedModel in for this block's (otherwise-empty) placeholder baked model - the
        //standard NeoForge hook for wholesale-replacing a block's baked model with a custom one. Also loads one
        //additional sprite per DyeColor (block/{color}_insulated_cable) for Insulated Redstone Cable faces -
        //unlike the plain sprite, these are already-colored final art (not a grayscale mask meant for tinting),
        //so RedstoneCableBakedModel samples the matching one directly per face instead of relying on BlockColor
        //to recolor a shared texture.
        @SubscribeEvent
        static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
            ResourceLocation textureLocation = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/redstone_cable");
            TextureAtlasSprite sprite = event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS, textureLocation));
            Map<DyeColor, TextureAtlasSprite> coloredSprites = new EnumMap<>(DyeColor.class);
            for (DyeColor color : DyeColor.values()) {
                ResourceLocation coloredTextureLocation = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/" + color.getName() + "_insulated_cable");
                coloredSprites.put(color, event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS, coloredTextureLocation)));
            }
            //Bundled Redstone Cable - a single, already-final-colored sprite (there's no per-DyeColor variant),
            //loaded the same way the plain wire's own sprite is above.
            ResourceLocation bundledTextureLocation = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/bundled_redstone_cable");
            TextureAtlasSprite bundledSprite = event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS, bundledTextureLocation));
            //Aluminum Frame - a real painted texture (not a tintable stripe like plain wire's own sprite), see
            //RedstoneCableBakedModel#addFrameBox's own comment.
            ResourceLocation frameTextureLocation = ResourceLocation.fromNamespaceAndPath(BetterCircuits.MOD_ID, "block/aluminum_frame");
            TextureAtlasSprite frameSprite = event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS, frameTextureLocation));
            //Bug fix: WATERLOGGED (added to RedstoneCableBlock for Aluminum Frame's own "no support needed,
            //waterloggable" requirement - see that class's own class comment) means this block now has TWO
            //distinct blockstates, each with its own separate ModelResourceLocation key - wrapping only
            //defaultBlockState()'s key (waterlogged=false) would leave an underwater cable/frame's own
            //waterlogged=true model as the unwrapped, empty placeholder instead of the real geometry. Wraps
            //every real state instead of just the default one.
            for (BlockState possibleState : BCBlocks.REDSTONE_CABLE.get().getStateDefinition().getPossibleStates()) {
                ModelResourceLocation key = BlockModelShaper.stateToModelLocation(possibleState);
                BakedModel original = event.getModels().get(key);
                if (original != null) {
                    event.getModels().put(key, new RedstoneCableBakedModel(original, sprite, coloredSprites, bundledSprite, frameSprite));
                }
            }
        }

        //Redstone Cable has no blockstate POWER property (power lives per-face in RedstoneCableBlockEntity), so
        //a single tintindex can't be tied to a blockstate value the way vanilla dust does. Instead each face's
        //quads (see RedstoneCableBakedModel) are baked with tintIndex = towardSupport.ordinal(), and this
        //handler reads that same face's power back out of the block entity per tintIndex.
        //Insulated Redstone Cable faces (face.color() != null) return plain white (0xFFFFFF, i.e. "don't tint
        //this quad at all") instead of the power gradient - their sprite is already the final, solid-colored art,
        //and tinting a pre-colored blue texture with a red/gray power gradient would just muddy it. Only plain
        //(uncolored) faces still get vanilla dust's own power-based tint.
        @SubscribeEvent
        static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
            event.register((state, level, pos, tintIndex) -> {
                if (level == null || pos == null || tintIndex < 0 || tintIndex >= Direction.values().length) {
                    return RedStoneWireBlock.getColorForPower(0);
                }
                RedstoneCableBlockEntity cable = level.getBlockEntity(pos) instanceof RedstoneCableBlockEntity c ? c : null;
                FaceState face = cable != null ? cable.getFace(Direction.values()[tintIndex]) : null;
                if (face != null && (face.color() != null || face.bundled())) {
                    return 0xFFFFFF;
                }
                return RedStoneWireBlock.getColorForPower(face != null ? face.power() : 0);
            }, BCBlocks.REDSTONE_CABLE.get());
        }
    }
}
