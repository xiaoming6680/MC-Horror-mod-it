package com.xm6680.it.director;

import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Collectors;

public record PlayerContextSnapshot(
        UUID playerId,
        EnumSet<PlayerContext> contexts,
        int lightLevel,
        int nearbyPlayers,
        long airborneTicks,
        long mountedTicks,
        long idleTicks,
        long stableAreaTicks,
        int unreadReceiverMessages,
        boolean activeAfterAfk
) {
    public boolean has(PlayerContext context) {
        return contexts.contains(context);
    }

    public String describe() {
        if (contexts.isEmpty()) {
            return "NONE";
        }

        return contexts.stream()
                .map(Enum::name)
                .collect(Collectors.joining("+"));
    }
}
