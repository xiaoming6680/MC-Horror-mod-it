package com.xm6680.it.cavestalker;

import java.util.UUID;

public class CaveStalkerState {
    public final UUID targetPlayerId;
    public final long startGameTime;
    public UUID stalkerEntityId;
    public CaveStalkerPhase phase = CaveStalkerPhase.WARNING;
    public int phaseTicksRemaining;
    public int warningTicksRemaining;
    public int chaseTicksRemaining;
    public final int initialChaseTicks;
    public int blockedTicks;
    public int noLineOfSightTicks;
    public int nearPlayerTicks;
    public int escapeDistanceTicks;
    public int receiverWarningTicks;
    public int lookBackTicksRemaining;
    public int lookBackDelayTicks;
    public int turnBackTicksRemaining;
    public long lastFootstepTime;
    public long nextAtmosphereSoundTime;
    public long nextFakeRescueAttemptTick;
    public double lastDistanceToPlayer;
    public double lastStalkerX;
    public double lastStalkerY;
    public double lastStalkerZ;
    public double trapForwardX;
    public double trapForwardZ;
    public int notMovingTicks;
    public boolean forced;
    public boolean caught;
    public boolean trapTriggered;
    public boolean lookedBackOnce;
    public boolean fakeRescueSent;

    public CaveStalkerState(UUID targetPlayerId, long startGameTime, int warningTicks, int chaseTicks, boolean forced) {
        this.targetPlayerId = targetPlayerId;
        this.startGameTime = startGameTime;
        this.phaseTicksRemaining = warningTicks;
        this.warningTicksRemaining = warningTicks;
        this.chaseTicksRemaining = chaseTicks;
        this.initialChaseTicks = chaseTicks;
        this.lastFootstepTime = startGameTime;
        this.nextAtmosphereSoundTime = startGameTime;
        this.nextFakeRescueAttemptTick = startGameTime;
        this.lastDistanceToPlayer = Double.MAX_VALUE;
        this.forced = forced;
    }

    public boolean isWaitingForReceiver() {
        return phase == CaveStalkerPhase.RECEIVER_VIBRATION_WARNING
                || phase == CaveStalkerPhase.WAITING_FOR_RECEIVER_OPEN;
    }
}
