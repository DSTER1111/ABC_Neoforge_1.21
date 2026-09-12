package com.a_c_e.bettercircuits.item;

import com.a_c_e.bettercircuits.entity.FilteredHopperMinecartEntity;
import com.a_c_e.bettercircuits.entity.BCEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;

//Places a FilteredHopperMinecartEntity on a rail. Not a plain MinecartItem (vanilla's own useOn spawns via
//AbstractMinecart.createMinecart, which switches on the fixed AbstractMinecart.Type enum and can't be pointed at
//a custom entity type - decompiled to confirm) - this mirrors MinecartItem.useOn's own rail-detection and
//positioning logic exactly, just constructing our own entity type directly instead.
public class FilteredHopperMinecartItem extends Item {
    public FilteredHopperMinecartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.RAILS)) {
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        if (level instanceof ServerLevel serverLevel) {
            RailShape railShape = state.getBlock() instanceof BaseRailBlock railBlock
                    ? railBlock.getRailDirection(state, level, pos, null)
                    : RailShape.NORTH_SOUTH;
            double yOffset = railShape.isAscending() ? 0.5 : 0.0;

            FilteredHopperMinecartEntity minecart = new FilteredHopperMinecartEntity(BCEntityTypes.FILTERED_HOPPER_MINECART.get(), level);
            minecart.setPos(pos.getX() + 0.5, pos.getY() + 0.0625 + yOffset, pos.getZ() + 0.5);
            EntityType.createDefaultStackConfig(serverLevel, stack, context.getPlayer()).accept(minecart);
            serverLevel.addFreshEntity(minecart);
            serverLevel.gameEvent(GameEvent.ENTITY_PLACE, pos, GameEvent.Context.of(context.getPlayer(), serverLevel.getBlockState(pos.below())));
        }

        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
