package com.xm6680.it.progression;

import com.xm6680.it.watching.HorrorPhase;

import java.util.UUID;

/**
 * Horror progression state for one player. This is persisted to the active
 * world save by the persistence manager.
 */
public class PlayerHorrorData {
    public final UUID playerId;
    public int watchingLevel;
    public HorrorPhase currentPhase;
    public long phaseEnteredTime;
    public long lastPhaseAdvanceTime;
    public long totalPlayTicks;
    public int caveFootstepEvents;
    public int miningEchoEvents;
    public int fakeChatEvents;
    public int handDropAnomalies;
    public int inventoryOpenAnomalies;
    public int mysteriousContactMessages;
    public int eerieSoundEvents;
    public int animalStareEvents;
    public int familiarSoundEvents;
    public int separationWarnings;
    public int fakeTeammateFootstepEvents;
    public int teamGazeEvents;
    public int fakeTabEvents;
    public int fakeAdvancementEvents;
    public int fakeRescueMessages;
    public int animalDisguiseEvents;
    public int animalDisguiseKilledEvents;
    public int animalDisguiseAnimalAttacks;
    public int animalDisguiseVictims;
    public int animalDisguiseRetaliations;
    public int watcherSightings;
    public int receiverMessagesReceived;
    public int receiverOpenedCount;
    public long receiverFirstReceivedGameTime;
    public long lastReceiverMessageGameTime;
    public long lastReceiverOpenedGameTime;
    public int lastReceiverOpenedMessageCount;
    public int receiverUnreadPressureEvents;
    public long nextReceiverFallbackGameTime;
    public long receiverPhaseCooldownUntilGameTime;
    public int receiverFallbackPhaseNumber;
    public int receiverFallbacksInPhase;
    public double avoidanceScore;
    public double individualDread;
    public double groupDread;
    public double unreadSignalPressure;
    public long airborneTicks;
    public long mountedTicks;
    public long groupedTicks;
    public long idleTicks;
    public long fastTravelTicks;
    public long stableAreaTicks;
    public long safeBaseTicks;
    public long firstNetherEntryGameTime;
    public long lastDimensionChangeGameTime;
    public int skyborneEvents;
    public int avoidanceTriggeredEvents;
    public int groupDreadEvents;
    public int baseIntrusionEvents;
    public int afkReleasedEvents;
    public int netherRushEvents;
    public int jumpscareEvents;
    public int phaseFiveInterferenceEvents;
    public int chaseEvents;
    public int chaseEscapes;
    public int chaseCaughtEvents;
    public long lastChaseGameTime;
    public int caveStalkerEvents;
    public int caveStalkerEscapes;
    public int caveStalkerCaughtEvents;
    public int caveStalkerTrapTriggers;
    public int caveStalkerEasterEggs;
    public long lastCaveStalkerGameTime;
    public int undergroundTicks;
    public int darkAloneTicks;
    public int nightAloneTicks;
    public boolean hasReceivedReceiver;
    public boolean hasSeenWatcher;
    public boolean hasTriggeredManifestation;

    public PlayerHorrorData(UUID playerId, long currentTick) {
        this.playerId = playerId;
        resetProgression(currentTick, false);
    }

    public long getTimeInPhaseTicks(long currentTick) {
        return Math.max(0L, currentTick - phaseEnteredTime);
    }

    public void resetProgression(long currentTick, boolean keepReceiverState) {
        boolean receivedReceiver = keepReceiverState && hasReceivedReceiver;
        watchingLevel = 0;
        currentPhase = HorrorPhase.DORMANT;
        phaseEnteredTime = currentTick;
        lastPhaseAdvanceTime = currentTick;
        totalPlayTicks = 0L;
        caveFootstepEvents = 0;
        miningEchoEvents = 0;
        fakeChatEvents = 0;
        handDropAnomalies = 0;
        inventoryOpenAnomalies = 0;
        mysteriousContactMessages = 0;
        eerieSoundEvents = 0;
        animalStareEvents = 0;
        familiarSoundEvents = 0;
        separationWarnings = 0;
        fakeTeammateFootstepEvents = 0;
        teamGazeEvents = 0;
        fakeTabEvents = 0;
        fakeAdvancementEvents = 0;
        fakeRescueMessages = 0;
        animalDisguiseEvents = 0;
        animalDisguiseKilledEvents = 0;
        animalDisguiseAnimalAttacks = 0;
        animalDisguiseVictims = 0;
        animalDisguiseRetaliations = 0;
        watcherSightings = 0;
        receiverMessagesReceived = 0;
        receiverOpenedCount = 0;
        receiverFirstReceivedGameTime = receivedReceiver ? currentTick : 0L;
        lastReceiverMessageGameTime = 0L;
        lastReceiverOpenedGameTime = 0L;
        lastReceiverOpenedMessageCount = 0;
        receiverUnreadPressureEvents = 0;
        nextReceiverFallbackGameTime = 0L;
        receiverPhaseCooldownUntilGameTime = 0L;
        receiverFallbackPhaseNumber = HorrorPhase.DORMANT.getNumber();
        receiverFallbacksInPhase = 0;
        avoidanceScore = 0.0D;
        individualDread = 0.0D;
        groupDread = 0.0D;
        unreadSignalPressure = 0.0D;
        airborneTicks = 0L;
        mountedTicks = 0L;
        groupedTicks = 0L;
        idleTicks = 0L;
        fastTravelTicks = 0L;
        stableAreaTicks = 0L;
        safeBaseTicks = 0L;
        firstNetherEntryGameTime = 0L;
        lastDimensionChangeGameTime = 0L;
        skyborneEvents = 0;
        avoidanceTriggeredEvents = 0;
        groupDreadEvents = 0;
        baseIntrusionEvents = 0;
        afkReleasedEvents = 0;
        netherRushEvents = 0;
        jumpscareEvents = 0;
        phaseFiveInterferenceEvents = 0;
        chaseEvents = 0;
        chaseEscapes = 0;
        chaseCaughtEvents = 0;
        lastChaseGameTime = 0L;
        caveStalkerEvents = 0;
        caveStalkerEscapes = 0;
        caveStalkerCaughtEvents = 0;
        caveStalkerTrapTriggers = 0;
        caveStalkerEasterEggs = 0;
        lastCaveStalkerGameTime = 0L;
        undergroundTicks = 0;
        darkAloneTicks = 0;
        nightAloneTicks = 0;
        hasReceivedReceiver = receivedReceiver;
        hasSeenWatcher = false;
        hasTriggeredManifestation = false;
    }

    public String toDebugString(long currentTick) {
        return "PlayerHorrorData{"
                + "playerId=" + playerId
                + ", watchingLevel=" + watchingLevel
                + ", currentPhase=" + currentPhase.getNumber() + "/" + currentPhase.getDisplayName()
                + ", timeInPhaseTicks=" + getTimeInPhaseTicks(currentTick)
                + ", totalPlayTicks=" + totalPlayTicks
                + ", caveFootstepEvents=" + caveFootstepEvents
                + ", miningEchoEvents=" + miningEchoEvents
                + ", fakeChatEvents=" + fakeChatEvents
                + ", handDropAnomalies=" + handDropAnomalies
                + ", inventoryOpenAnomalies=" + inventoryOpenAnomalies
                + ", mysteriousContactMessages=" + mysteriousContactMessages
                + ", eerieSoundEvents=" + eerieSoundEvents
                + ", animalStareEvents=" + animalStareEvents
                + ", familiarSoundEvents=" + familiarSoundEvents
                + ", separationWarnings=" + separationWarnings
                + ", fakeTeammateFootstepEvents=" + fakeTeammateFootstepEvents
                + ", teamGazeEvents=" + teamGazeEvents
                + ", fakeTabEvents=" + fakeTabEvents
                + ", fakeAdvancementEvents=" + fakeAdvancementEvents
                + ", fakeRescueMessages=" + fakeRescueMessages
                + ", animalDisguiseEvents=" + animalDisguiseEvents
                + ", animalDisguiseKilledEvents=" + animalDisguiseKilledEvents
                + ", animalDisguiseAnimalAttacks=" + animalDisguiseAnimalAttacks
                + ", animalDisguiseVictims=" + animalDisguiseVictims
                + ", animalDisguiseRetaliations=" + animalDisguiseRetaliations
                + ", watcherSightings=" + watcherSightings
                + ", receiverMessagesReceived=" + receiverMessagesReceived
                + ", receiverOpenedCount=" + receiverOpenedCount
                + ", receiverFirstReceivedGameTime=" + receiverFirstReceivedGameTime
                + ", lastReceiverMessageGameTime=" + lastReceiverMessageGameTime
                + ", lastReceiverOpenedGameTime=" + lastReceiverOpenedGameTime
                + ", lastReceiverOpenedMessageCount=" + lastReceiverOpenedMessageCount
                + ", receiverUnreadPressureEvents=" + receiverUnreadPressureEvents
                + ", nextReceiverFallbackGameTime=" + nextReceiverFallbackGameTime
                + ", receiverPhaseCooldownUntilGameTime=" + receiverPhaseCooldownUntilGameTime
                + ", receiverFallbackPhaseNumber=" + receiverFallbackPhaseNumber
                + ", receiverFallbacksInPhase=" + receiverFallbacksInPhase
                + ", avoidanceScore=" + avoidanceScore
                + ", individualDread=" + individualDread
                + ", groupDread=" + groupDread
                + ", unreadSignalPressure=" + unreadSignalPressure
                + ", airborneTicks=" + airborneTicks
                + ", mountedTicks=" + mountedTicks
                + ", groupedTicks=" + groupedTicks
                + ", idleTicks=" + idleTicks
                + ", fastTravelTicks=" + fastTravelTicks
                + ", stableAreaTicks=" + stableAreaTicks
                + ", safeBaseTicks=" + safeBaseTicks
                + ", firstNetherEntryGameTime=" + firstNetherEntryGameTime
                + ", lastDimensionChangeGameTime=" + lastDimensionChangeGameTime
                + ", skyborneEvents=" + skyborneEvents
                + ", avoidanceTriggeredEvents=" + avoidanceTriggeredEvents
                + ", groupDreadEvents=" + groupDreadEvents
                + ", baseIntrusionEvents=" + baseIntrusionEvents
                + ", afkReleasedEvents=" + afkReleasedEvents
                + ", netherRushEvents=" + netherRushEvents
                + ", jumpscareEvents=" + jumpscareEvents
                + ", phaseFiveInterferenceEvents=" + phaseFiveInterferenceEvents
                + ", chaseEvents=" + chaseEvents
                + ", chaseEscapes=" + chaseEscapes
                + ", chaseCaughtEvents=" + chaseCaughtEvents
                + ", lastChaseGameTime=" + lastChaseGameTime
                + ", caveStalkerEvents=" + caveStalkerEvents
                + ", caveStalkerEscapes=" + caveStalkerEscapes
                + ", caveStalkerCaughtEvents=" + caveStalkerCaughtEvents
                + ", caveStalkerTrapTriggers=" + caveStalkerTrapTriggers
                + ", caveStalkerEasterEggs=" + caveStalkerEasterEggs
                + ", lastCaveStalkerGameTime=" + lastCaveStalkerGameTime
                + ", undergroundTicks=" + undergroundTicks
                + ", darkAloneTicks=" + darkAloneTicks
                + ", nightAloneTicks=" + nightAloneTicks
                + ", hasReceivedReceiver=" + hasReceivedReceiver
                + ", hasSeenWatcher=" + hasSeenWatcher
                + ", hasTriggeredManifestation=" + hasTriggeredManifestation
                + '}';
    }
}
