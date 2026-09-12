package com.a_c_e.bettercircuits.item;

import com.a_c_e.bettercircuits.block.BCBlocks;
import com.a_c_e.bettercircuits.block.RedstoneCableBlock;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;

//Aluminum Frame targets the exact same shared Block/BlockEntity as RedstoneCableBlockItem (see
//RedstoneCableBlock's own class comment for why: a single position needs to be able to host a frame
//alongside whatever cable faces already live there, and Minecraft only ever allows one Block per position).
//Mirrors RedstoneCableBlockItem's own place() shape - add-to-existing vs. fresh-placement - but simpler: a
//frame needs no support at all (RedstoneCableBlock.canSurvive already returns true unconditionally whenever
//a frame is present), so there's no canSurviveOn pre-check to fail on the way a face placement can.
public class AluminumFrameBlockItem extends BlockItem {
    public AluminumFrameBlockItem(Item.Properties properties) {
        super(BCBlocks.REDSTONE_CABLE.get(), properties);
    }

    //Same reasoning as RedstoneCableBlockItem's own override - BlockItem's default getDescriptionId()
    //delegates to getBlock().getDescriptionId(), which would show "Redstone Cable" here too since this item
    //places that same shared block.
    @Override
    public String getDescriptionId() {
        return Util.makeDescriptionId("item", BuiltInRegistries.ITEM.getKey(this));
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos targetPos = context.getClickedPos();
        if (level.getBlockState(targetPos).getBlock() instanceof RedstoneCableBlock) {
            if (!RedstoneCableBlock.addFrame(level, targetPos)) {
                return InteractionResult.FAIL;
            }
            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
            //Never goes through super.place(), so it never gets BlockItem's own built-in placement sound -
            //see RedstoneCableBlockItem#place's own comment on the identical null-vs-player exclusion bug.
            SoundType soundType = level.getBlockState(targetPos).getSoundType();
            level.playSound(null, targetPos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                    (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        InteractionResult result = super.place(context);
        if (result.consumesAction()) {
            RedstoneCableBlock.addFrame(level, context.getClickedPos());
        }
        return result;
    }
}
