package com.a_c_e.bettercircuits.util;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

//Shared by BlowerBlock/VacuumBlock (and anything else that needs to shove an entity around continuously from
//server-side game logic, not combat). Entity.push alone (deltaMovement + hasImpulse) reliably moves simple
//entities like dropped items, but not mobs or minecarts - both recompute their own velocity from scratch each
//tick (LivingEntity.travel's own moveRelative/friction chain, AbstractMinecart.moveAlongTrack's rail-projected
//speed), discarding a small nudge added from a completely separate tick phase before it ever shows up as an
//actual position change. Entity.move(MoverType.SELF, ...) - the same collision-aware displacement vanilla's own
//entity movement code uses internally - forces a real, immediate position change regardless of what any
//entity-type-specific movement logic does afterward, matching how e.g. bubble columns/currents reliably push
//every entity type.
//
//Players are the one case that breaks this: a player's position is client-authoritative (the server only
//accepts what that player's own client reports, based on its own local prediction), so silently moving the
//server's copy of a player via move() just gets overwritten by their own next movement packet, which has no
//idea a push happened. hasImpulse's own sync mechanism doesn't save this either - decompiled ServerEntity to
//confirm it broadcasts ClientboundSetEntityMotionPacket only to OTHER players tracking this entity, explicitly
//excluding the entity's own controlling player (who doesn't need networked updates about themselves under
//normal circumstances). So a player needs that same packet sent directly to their own connection instead.
public final class EntityForceUtil {
    private EntityForceUtil() {
    }

    public static void applyForce(Entity entity, Vec3 force) {
        entity.push(force.x, force.y, force.z);
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetEntityMotionPacket(entity));
        } else {
            entity.move(MoverType.SELF, force);
        }
    }
}
