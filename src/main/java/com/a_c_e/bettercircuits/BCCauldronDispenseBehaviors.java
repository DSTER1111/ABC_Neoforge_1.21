package com.a_c_e.bettercircuits;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

//No vanilla equivalent to mirror directly - vanilla's cauldron interactions (CauldronInteraction) are entirely
//player-only (AbstractCauldronBlock.useItemOn requires a real Player, and CauldronInteraction's own lambdas call
//player.setItemInHand/awardStat unconditionally, so they'd NPE if handed a null player the way e.g.
//BucketItem.emptyContents tolerates). None of the existing bucket/bottle/potion dispense behaviors check for
//cauldrons either - AbstractCauldronBlock implements neither LiquidBlockContainer nor BucketPickup - so this
//re-implements the specific interactions CauldronInteraction defines for water/lava/powder snow buckets, the
//empty bucket, glass bottles, and water potions, translated from Player+InteractionHand into BlockSource+ItemStack.
//
//Every behavior here WRAPS (never replaces) whatever vanilla already had registered for that item, falling back
//to it whenever the block in front isn't a relevant cauldron state - this preserves vanilla's own dispenser
//behavior for these items everywhere else (e.g. bucket-of-fish release, beehive bottling, water-potion-on-dirt
//mud conversion, world fluid pickup/placement).
public class BCCauldronDispenseBehaviors {
    private BCCauldronDispenseBehaviors() {
    }

    public static void register() {
        //Mirrors CauldronInteraction.FILL_WATER/FILL_LAVA/FILL_POWDER_SNOW - unconditionally overwrites whatever
        //cauldron is there (even a different liquid) with a full one of this type
        registerBucketFill(Items.WATER_BUCKET, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), SoundEvents.BUCKET_EMPTY);
        registerBucketFill(Items.LAVA_BUCKET, Blocks.LAVA_CAULDRON.defaultBlockState(), SoundEvents.BUCKET_EMPTY_LAVA);
        registerBucketFill(Items.POWDER_SNOW_BUCKET, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), SoundEvents.BUCKET_EMPTY_POWDER_SNOW);

        registerBucketEmpty();
        registerGlassBottle();
        registerWaterPotion();
    }

    private static void registerBucketFill(Item bucketItem, BlockState fillState, SoundEvent sound) {
        DispenseItemBehavior original = DispenserBlock.DISPENSER_REGISTRY.get(bucketItem);
        DispenserBlock.registerBehavior(bucketItem, new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                Level level = source.level();
                BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                if (level.getBlockState(pos).getBlock() instanceof AbstractCauldronBlock) {
                    level.setBlockAndUpdate(pos, fillState);
                    level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                    return this.consumeWithRemainder(source, stack, new ItemStack(Items.BUCKET));
                }
                return original.dispense(source, stack);
            }
        });
    }

    //Mirrors CauldronInteraction.fillBucket, used by the WATER/LAVA/POWDER_SNOW maps' own BUCKET entries: lava
    //always empties in one go, water/powder snow only when full (level 3)
    private static void registerBucketEmpty() {
        DispenseItemBehavior original = DispenserBlock.DISPENSER_REGISTRY.get(Items.BUCKET);
        DispenserBlock.registerBehavior(Items.BUCKET, new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                Level level = source.level();
                BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                BlockState state = level.getBlockState(pos);

                Item filled;
                SoundEvent sound;
                if (state.is(Blocks.LAVA_CAULDRON)) {
                    filled = Items.LAVA_BUCKET;
                    sound = SoundEvents.BUCKET_FILL_LAVA;
                } else if (state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) == 3) {
                    filled = Items.WATER_BUCKET;
                    sound = SoundEvents.BUCKET_FILL;
                } else if (state.is(Blocks.POWDER_SNOW_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) == 3) {
                    filled = Items.POWDER_SNOW_BUCKET;
                    sound = SoundEvents.BUCKET_FILL_POWDER_SNOW;
                } else {
                    return original.dispense(source, stack);
                }

                level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
                level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                return this.consumeWithRemainder(source, stack, new ItemStack(filled));
            }
        });
    }

    //Mirrors CauldronInteraction.WATER's own GLASS_BOTTLE entry: takes exactly 1 water potion and lowers the
    //cauldron by 1 level (unlike the empty bucket, which always takes everything at once)
    private static void registerGlassBottle() {
        DispenseItemBehavior original = DispenserBlock.DISPENSER_REGISTRY.get(Items.GLASS_BOTTLE);
        DispenserBlock.registerBehavior(Items.GLASS_BOTTLE, new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                Level level = source.level();
                BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                BlockState state = level.getBlockState(pos);
                if (state.is(Blocks.WATER_CAULDRON)) {
                    LayeredCauldronBlock.lowerFillLevel(state, level, pos);
                    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
                    return this.consumeWithRemainder(source, stack, PotionContents.createItemStack(Items.POTION, Potions.WATER));
                }
                return original.dispense(source, stack);
            }
        });
    }

    //Mirrors CauldronInteraction.EMPTY's and WATER's own POTION entries: fills an empty cauldron to level 1, or
    //raises a non-full water cauldron by 1 level. Does nothing to a lava/powder snow cauldron or an already-full
    //water cauldron, matching vanilla exactly (neither of those maps has a POTION entry of their own).
    private static void registerWaterPotion() {
        DispenseItemBehavior original = DispenserBlock.DISPENSER_REGISTRY.get(Items.POTION);
        DispenserBlock.registerBehavior(Items.POTION, new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                PotionContents potionContents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                if (potionContents.is(Potions.WATER)) {
                    Level level = source.level();
                    BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                    BlockState state = level.getBlockState(pos);

                    BlockState newState;
                    if (state.is(Blocks.CAULDRON)) {
                        newState = Blocks.WATER_CAULDRON.defaultBlockState();
                    } else if (state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) < 3) {
                        newState = state.cycle(LayeredCauldronBlock.LEVEL);
                    } else {
                        newState = null;
                    }

                    if (newState != null) {
                        level.setBlockAndUpdate(pos, newState);
                        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                        level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                        return this.consumeWithRemainder(source, stack, new ItemStack(Items.GLASS_BOTTLE));
                    }
                }
                return original.dispense(source, stack);
            }
        });
    }
}
