package com.xm6680.it.entity;

import java.util.UUID;

/**
 * Marker for server-side horror entities that should only be visible to one player.
 */
public interface TargetOnlyVisibleEntity {
    UUID getVisibleTargetUuid();
}
