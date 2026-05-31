package com.xm6680.it.entity;

import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps horror manifestations client-visible only to their target player.
 */
public final class TargetOnlyEntityVisibility {
    private static final Map<UUID, UUID> EXTERNAL_TARGETS = new ConcurrentHashMap<>();

    private TargetOnlyEntityVisibility() {
    }

    public static void register() {
        EntityTrackingEvents.START_TRACKING.register(TargetOnlyEntityVisibility::hideIfNotTarget);
    }

    public static void registerExternalTarget(Entity entity, ServerPlayerEntity target) {
        EXTERNAL_TARGETS.put(entity.getUuid(), target.getUuid());
    }

    public static void unregisterExternalTarget(Entity entity) {
        EXTERNAL_TARGETS.remove(entity.getUuid());
    }

    public static void hideFromNonTargetPlayers(Entity entity) {
        if (!(entity.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        UUID targetUuid = visibleTargetUuid(entity);
        if (targetUuid == null) {
            return;
        }

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.getUuid().equals(targetUuid)) {
                destroyFor(player, entity);
            }
        }
    }

    private static void hideIfNotTarget(Entity entity, ServerPlayerEntity trackingPlayer) {
        UUID targetUuid = visibleTargetUuid(entity);
        if (targetUuid == null || trackingPlayer.getUuid().equals(targetUuid)) {
            return;
        }

        destroyFor(trackingPlayer, entity);
    }

    private static UUID visibleTargetUuid(Entity entity) {
        if (entity instanceof TargetOnlyVisibleEntity targetOnlyVisibleEntity) {
            return targetOnlyVisibleEntity.getVisibleTargetUuid();
        }

        return EXTERNAL_TARGETS.get(entity.getUuid());
    }

    private static void destroyFor(ServerPlayerEntity player, Entity entity) {
        player.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(entity.getId()));
    }
}
