package com.xm6680.it.analog;

import com.xm6680.it.watching.HorrorPhase;

import java.util.UUID;

public record PendingReceiverMessage(
        UUID playerId,
        ReceiverMessageType type,
        HorrorPhase phase,
        String text,
        long deliverServerTick
) {
}
