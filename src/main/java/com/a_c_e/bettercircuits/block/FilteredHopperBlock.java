package com.a_c_e.bettercircuits.block;

import com.a_c_e.bettercircuits.block.entity.FilteredHopperBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

//Filtered Hopper: visually a regular hopper with an item-frame-style display inside its own top opening (see
//FilteredHopperBlockEntityRenderer) - the frame is purely a rendering/interaction layer on ONE block, never a
//real ItemFrame entity paired with a hopper. Whatever item sits "in the frame" (FilteredHopperBlockEntity's own
//filter field) restricts what the hopper will suck up - see that class's own comment for how canPlaceItem is
//the entire mechanism, no hopper pickup/push logic reimplemented here at all.
//
//Known, deliberate simplification: does NOT override codec() (inherits HopperBlock's own, bound to
//HopperBlock::new) - HopperBlock.codec() returns the concrete MapCodec<HopperBlock>, not a wildcard, so Java's
//invariant generics make re-narrowing that in a subclass impossible without reimplementing HopperBlock's own
//substantial shape-per-facing/interaction/comparator surface from BaseEntityBlock up. Same class of
//rarely-exercised gap already accepted for Comparator Rail earlier this session (there, avoiding it was cheap
//via BaseRailBlock; here it isn't, so it's accepted instead).
//
//Hit-testing "did the player click the frame" uses hitResult.getDirection() == UP - the frame always renders
//on the block's own top face regardless of which way the hopper's spout points (the funnel opening is always
//up), so this is both simple and semantically exact: clicking down into the top opening is the frame, clicking
//the spout/sides/base is the hopper body.
public class FilteredHopperBlock extends HopperBlock {
    public FilteredHopperBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FilteredHopperBlockEntity(pos, state);
    }

    //HopperBlockEntity's own 2-arg constructor hardcodes vanilla's real BlockEntityType.HOPPER internally
    //(confirmed via decompiling it - getstatic BlockEntityType.HOPPER, passed straight up to
    //RandomizableContainerBlockEntity's own constructor) - there is no protected/3-arg constructor a subclass
    //could use to supply a different one. Every FilteredHopperBlockEntity instance's own getType() therefore
    //ALWAYS reports BlockEntityType.HOPPER, so that's what this compares against (see
    //BCHopperBlockEntityTypeFix's own comment for the OTHER place this same fact matters - LevelChunk's own
    //isValid gate) - this is what makes createTickerHelper actually return HopperBlockEntity::pushItemsTick, the
    //ONLY thing that ever pushes items out the bottom, pulls from a container sitting above, or decrements a
    //hopper's own cooldown after a successful transfer.
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BlockEntityType.HOPPER, HopperBlockEntity::pushItemsTick);
    }

    //Item-in-hand click on the frame: fills an empty frame (consuming 1, matching ItemFrame's own insert-one
    //behavior), or is a no-op on an already-occupied one (a real ItemFrame doesn't swap on right-click either -
    //it only rotates the display, which has no meaning for a pure filter marker, so this just consumes the
    //click and does nothing rather than reimplementing rotation cosmetics). Empty-hand clicks on the frame are
    //also consumed here as a no-op - removal is attack-only (see BCEventHandlers#onLeftClickFilteredHopper).
    //Anything not aimed at the frame passes straight through to HopperBlock's own GUI-opening interaction,
    //completely unaffected.
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (hitResult.getDirection() != Direction.UP || !(level.getBlockEntity(pos) instanceof FilteredHopperBlockEntity hopper)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isEmpty() || !hopper.getFilter().isEmpty()) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }
        hopper.setFilter(stack);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
        return ItemInteractionResult.sidedSuccess(false);
    }

    //Defensive mirror of useItemOn's own frame guard, in case an empty-handed right-click ever reaches this
    //method directly rather than useItemOn's own stack.isEmpty() branch - either way, a click on the frame
    //never falls through to opening the hopper's GUI.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (hitResult.getDirection() == Direction.UP && level.getBlockEntity(pos) instanceof FilteredHopperBlockEntity) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useWithoutItem(state, level, pos, player, hitResult);
    }

    //Breaking the block needs to also drop whatever's currently framed, matching how breaking a real ItemFrame
    //always drops its contents too - the static loot table only ever drops the hopper itself (see
    //BCBlockLootTableProvider), same as vanilla's own hopper loot table never needing to know about a filter
    //that doesn't exist for it. Read before super.onRemove(), which removes the block entity.
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FilteredHopperBlockEntity hopper) {
            ItemStack filter = hopper.getFilter();
            if (!filter.isEmpty()) {
                popResource(level, pos, filter);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
