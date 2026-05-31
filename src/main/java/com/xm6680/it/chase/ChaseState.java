package com.xm6680.it.chase;

import java.util.UUID;

/**
 * Runtime state for one target player's hunt event.
 */
public class ChaseState {
    public final UUID targetPlayerId;
    public UUID chaserEntityId;
    public final long startGameTime;
    public int warningTicksRemaining;
    public int chaseTicksRemaining;
    public final int initialChaseTicks;
    public int brightLightTicks;
    public int nearPlayerTicks;
    public boolean active;
    public boolean warning;
    public boolean caught;
    public int intensity;
    public long lastDistanceMessageTime;
    public long lastReceiverDistanceHintTime;
    public long lastFootstepTime;
    public long lastHeartbeatTime;
    public long nextAtmosphereSoundTime;
    public long lastChaseWarningChatTime;
    public long nextFakeRescueAttemptTick;
    public boolean chaseWarningChatSent;
    public boolean fakeRescueSent;

    public ChaseState(UUID targetPlayerId, long startGameTime, int warningTicks, int chaseTicks) {
        this.targetPlayerId = targetPlayerId;
        this.startGameTime = startGameTime;
        this.warningTicksRemaining = warningTicks;
        this.chaseTicksRemaining = chaseTicks;
        this.initialChaseTicks = chaseTicks;
        this.warning = true;
        this.active = false;
        this.intensity = 1;
        this.lastDistanceMessageTime = startGameTime;
        this.lastReceiverDistanceHintTime = startGameTime;
        this.lastFootstepTime = startGameTime;
        this.lastHeartbeatTime = startGameTime;
        this.nextAtmosphereSoundTime = startGameTime;
        this.lastChaseWarningChatTime = startGameTime;
        this.nextFakeRescueAttemptTick = startGameTime;
    }
}
