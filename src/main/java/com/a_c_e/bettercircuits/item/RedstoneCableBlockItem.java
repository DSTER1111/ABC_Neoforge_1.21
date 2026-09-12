package com.a_c_e.bettercircuits.item;

import com.a_c_e.bettercircuits.block.BCBlocks;
import com.a_c_e.bettercircuits.block.RedstoneCableBlock;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;

import javax.annotation.Nullable;

//RedstoneCableBlock has no blockstate to carry a "which face" placement decision (see its own class comment) -
//placing a brand new cable against a bare block still goes through the normal BlockItem placement flow (so it
//behaves exactly like placing any other block against the clicked face), but afterward there's no face active
//yet, since the block entity starts empty. This adds the first one, using the same clickedFace.getOpposite()
//convention Phase 1/2 used ("clicking the top of a block places a cable whose support is below it").
//
//Every face after the first can ALSO be added this same way: context.getClickedPos() already resolves to
//wherever the new block would actually go (BlockPlaceContext adjusts for whether the raycast-hit block itself is
//replaceable), so if that position is already a RedstoneCableBlock, this adds a face there directly instead of
//going through the normal (would otherwise just fail, since an existing cable isn't replaceable) placement flow.
//This is really the same "click a support block's face" gesture as the very first placement, just aimed at a
//spot that happens to already have a cable - RedstoneCableBlock.useItemOn() covers the OTHER case (directly
//clicking the existing cable's own small geometry) redundantly, which is fine; either path gets you there.
//color is null for the plain Redstone Cable item, or one of the 16 DyeColor values for an Insulated Redstone
//Cable item - both are the exact same Block/BlockEntity underneath (see RedstoneCableBlock.addFace's own
//comment for why: a single block position needs to host different colors on different faces at once, and
//Minecraft only ever allows one Block per position), so a single generalized item class handles every color
//instead of needing 17 near-identical subclasses.
//
//bundled marks the single Bundled Redstone Cable item - always paired with color == null (there's no per-color
//Bundled Cable, see BCItems.BUNDLED_CABLE_ITEM), reusing this same generalized class rather than a dedicated
//18th subclass.
public class RedstoneCableBlockItem extends BlockItem {
    @Nullable
    private final DyeColor color;
    private final boolean bundled;

    public RedstoneCableBlockItem(Item.Properties properties) {
        this(properties, null, false);
    }

    public RedstoneCableBlockItem(Item.Properties properties, @Nullable DyeColor color) {
        this(properties, color, false);
    }

    public RedstoneCableBlockItem(Item.Properties properties, @Nullable DyeColor color, boolean bundled) {
        super(BCBlocks.REDSTONE_CABLE.get(), properties);
        this.color = color;
        this.bundled = bundled;
    }

    @Nullable
    public DyeColor getColor() {
        return color;
    }

    public boolean isBundled() {
        return bundled;
    }

    //BlockItem's own default getDescriptionId() delegates straight to getBlock().getDescriptionId() - fine for
    //every OTHER block in this mod, where the item and block registry names always match, but wrong here: every
    //one of these 17 items (plain + 16 colors) places the SAME Block (see this class's own comment on why), so
    //that default would show "Redstone Cable" as every single one of their names, including every Insulated
    //color. Overriding back to Item's own un-overridden behavior (keyed off THIS item's own registry name, not
    //the block's) is what actually lets "white_insulated_cable" show its own translation instead.
    @Override
    public String getDescriptionId() {
        return Util.makeDescriptionId("item", BuiltInRegistries.ITEM.getKey(this));
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos targetPos = context.getClickedPos();
        Direction towardSupport = context.getClickedFace().getOpposite();
        if (level.getBlockState(targetPos).getBlock() instanceof RedstoneCableBlock) {
            if (!RedstoneCableBlock.addFace(level, targetPos, towardSupport, color, bundled)) {
                return InteractionResult.FAIL;
            }
            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
            //This branch never goes through super.place(), so it never gets BlockItem's own built-in placement
            //sound either (that's only triggered by the normal placement flow below) - added explicitly here so
            //adding a face to an ALREADY-existing cable sounds the same as placing the very first one.
            //
            //null, not player: Level#playSound(Player, ...) EXCLUDES that player from hearing it (the vanilla
            //assumption being their own client already played it locally via normal placement prediction) -
            //since this branch never goes through that normal flow, passing the actual player here would
            //silently skip the one person actually placing it. null broadcasts to everyone in range instead.
            SoundType soundType = level.getBlockState(targetPos).getSoundType();
            level.playSound(null, targetPos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                    (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        //Checked BEFORE super.place() (rather than discarding addFace's result afterward, like the branch
        //above used to) so an unsupported target (a non-sturdy face - a repeater's side, an anvil's side, an
        //end rod, ...) fails outright: nothing gets placed and the item is never consumed. canSurvive() can't
        //do this check itself during super.place()'s own placement flow, since there's no block entity yet at
        //that point to hold the face being tested - it just vacuously returns true, which used to let
        //super.place() succeed and leave behind an empty, faceless, invisible, hitbox-less cable block that
        //could never be broken or built over (bug report: repeater/anvil/end rod sides).
        BlockPos support = targetPos.relative(towardSupport);
        if (!RedstoneCableBlock.canSurviveOn(level, support, level.getBlockState(support), towardSupport)) {
            return InteractionResult.FAIL;
        }
        InteractionResult result = super.place(context);
        if (result.consumesAction()) {
            RedstoneCableBlock.addFace(level, context.getClickedPos(), towardSupport, color, bundled);
        }
        return result;
    }
}
