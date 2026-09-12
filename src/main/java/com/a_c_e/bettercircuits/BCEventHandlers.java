package com.a_c_e.bettercircuits;

import com.a_c_e.bettercircuits.block.RedstoneCableBlock;
import com.a_c_e.bettercircuits.block.entity.FilteredHopperBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

//Event handlers for the ported feature set only - registered as a plain instance to NeoForge.EVENT_BUS from
//BetterCircuits' own constructor (matches More Better's own convention).
public class BCEventHandlers {
    //Left-click breaking a cable used to always destroy the WHOLE block - every face, every color, at every
    //mounted direction - since vanilla has no concept of "partially break a block", it just removes whatever's
    //at a position outright. Since this block is instabreak (see BCBlocks.REDSTONE_CABLE's own properties), a
    //single click IS the entire break interaction, so this cancels that click (PlayerInteractEvent.LeftClickBlock
    //fires and is checked BEFORE destroyBlock ever runs) and calls RedstoneCableBlock.breakFace instead, which
    //does its own precise server-side raycast to resolve exactly which face was hit.
    //
    //Runs for Creative too - re-enabling this lets the server's correct single-face result reconcile against
    //the client's own optimistic prediction normally, same as any other case where a creative player's local
    //guess differs from the server's actual outcome.
    //
    //Aluminum Frame: unlike every cable face, Frame wants REAL mining time (a pickaxe, an actual multi-tick
    //delay) for a survival player - rather than trying to interoperate with vanilla's own timed-mining state
    //machine (a dead end - see RedstoneCableBlock#getDestroyProgress's own comment for the client-side
    //optimistic-air-prediction bug), a frame-targeted click just skips cancellation here entirely for a
    //survival player, so RedstoneCableBlock#getDestroyProgress (queried every tick by vanilla's own mining
    //loop, but permanently kept from ever reporting >= 1.0) tracks real elapsed progress by hand instead and
    //performs the actual removal itself once done. A CREATIVE frame hit does NOT get this treatment - creative
    //bypasses getDestroyProgress entirely for its own instant-destroy decision, so it takes the exact same
    //immediate cancel-and-remove path a face hit already does.
    @SubscribeEvent
    public void onLeftClickCable(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        PlayerInteractEvent.LeftClickBlock.Action action = event.getAction();
        //STOP (mining actually completed - handled entirely by getDestroyProgress itself) and ABORT (released
        //early, or aim drifted off target) both mean "this player is no longer actively mining a frame" - clear
        //their tracked progress either way. Checked BEFORE the RedstoneCableBlock instanceof gate below since
        //the tracked entry needs clearing even if whatever they were mining is no longer a RedstoneCableBlock
        //by the time this fires (e.g. an explosion removed it mid-mine).
        if (action == PlayerInteractEvent.LeftClickBlock.Action.ABORT
                || action == PlayerInteractEvent.LeftClickBlock.Action.STOP) {
            RedstoneCableBlock.clearFrameMining(level, player);
            return;
        }
        if (action != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        if (!(level.getBlockState(pos).getBlock() instanceof RedstoneCableBlock)) {
            return;
        }
        if (!player.getAbilities().mayBuild) {
            return;
        }
        if (RedstoneCableBlock.isFrameTargeted(level, pos, player) && !player.getAbilities().instabuild) {
            return;
        }
        event.setCanceled(true);
        if (level.isClientSide) {
            return;
        }
        if (RedstoneCableBlock.isFrameTargeted(level, pos, player)) {
            RedstoneCableBlock.removeFrame(level, pos, player);
        } else {
            RedstoneCableBlock.breakFace(level, pos, player);
        }
    }

    //Historical note / defensive fallback only - NOT load-bearing. This exists purely for a CREATIVE frame hit
    //(onLeftClickCable routes those through the instant cancel-and-remove path above instead) or any other
    //unanticipated path that still reaches vanilla's real destroy call - see More Better's own MBEventHandlers
    //for the full history of why this can't be the primary completion hook (a client-side optimistic-air-
    //prediction bug that no server-side timing adjustment could outrun). The retried delayed resync below is
    //leftover insurance from that old design.
    private static final int[] FRAME_RESYNC_DELAY_TICKS = {5, 15, 40};

    @SubscribeEvent
    public void onBreakCable(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof RedstoneCableBlock)) {
            return;
        }
        event.setCanceled(true);
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();
        if (RedstoneCableBlock.isFrameTargeted(level, pos, player)) {
            RedstoneCableBlock.removeFrame(level, pos, player);
        } else {
            RedstoneCableBlock.breakFace(level, pos, player);
        }
        MinecraftServer server = level.getServer();
        for (int delay : FRAME_RESYNC_DELAY_TICKS) {
            server.tell(new TickTask(server.getTickCount() + delay, () -> {
                BlockState currentState = level.getBlockState(pos);
                level.sendBlockUpdated(pos, currentState, currentState, 3);
            }));
        }
    }

    //Attacking a Filtered Hopper's own frame area clears its filter and drops the item, matching a real
    //ItemFrame's own attack-to-remove behavior exactly. event.getFace() is NOT trustworthy here (see
    //onLeftClickCable's own comment on the identical discovery for cable breaking) - this does its own
    //server-side raycast instead to reliably confirm the hit actually landed on the block's own top face
    //(where the frame always renders) rather than trusting the unreliable client-reported direction.
    //
    //Only cancels the event (and only clears the filter) when the frame is genuinely occupied and actually hit
    //- an empty frame, or a hit elsewhere on the hopper body, falls through untouched to normal block-breaking.
    @SubscribeEvent
    public void onLeftClickFilteredHopper(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof FilteredHopperBlockEntity hopper) || hopper.getFilter().isEmpty()) {
            return;
        }
        Player player = event.getEntity();
        if (!player.getAbilities().mayBuild) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 reachEnd = eye.add(look.scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(eye, reachEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos) || hit.getDirection() != Direction.UP) {
            return;
        }
        event.setCanceled(true);
        if (level.isClientSide) {
            return;
        }
        ItemStack filter = hopper.getFilter();
        hopper.setFilter(ItemStack.EMPTY);
        Block.popResource(level, pos, filter);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
