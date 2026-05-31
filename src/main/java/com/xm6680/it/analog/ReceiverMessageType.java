package com.xm6680.it.analog;

import com.xm6680.it.watching.HorrorPhase;

public enum ReceiverMessageType {
    WEATHER(HorrorPhase.DORMANT),
    LOCAL_ALERT(HorrorPhase.WATCHING),
    OBSERVATION(HorrorPhase.WATCHING),
    IDENTITY_WARNING(HorrorPhase.IMITATING),
    SYSTEM_ERROR(HorrorPhase.INTRUSION),
    PERSONAL_SIGNAL(HorrorPhase.MANIFESTATION),
    MANIFESTATION(HorrorPhase.MANIFESTATION),
    CHASE(HorrorPhase.MANIFESTATION),
    CAVE_STALKER(HorrorPhase.MANIFESTATION);

    private final HorrorPhase minimumPhase;

    ReceiverMessageType(HorrorPhase minimumPhase) {
        this.minimumPhase = minimumPhase;
    }

    public HorrorPhase getMinimumPhase() {
        return minimumPhase;
    }
}
