package com.xm6680.it.progression;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.watching.HorrorPhase;
import com.xm6680.it.watching.WatchingLevelManager;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns narrative phase progression. Watching level is treated as pressure, not
 * as the phase itself.
 */
public class HorrorProgressionManager {
    public static final int TICKS_PER_SECOND = 20;
    private static final int TWO_MINUTES_TICKS = 2 * 60 * TICKS_PER_SECOND;
    private static final int SIX_MINUTES_TICKS = 6 * 60 * TICKS_PER_SECOND;
    private static final int TEN_MINUTES_TICKS = 10 * 60 * TICKS_PER_SECOND;
    private static final double ALONE_DISTANCE = 48.0;

    private final WatchingLevelManager watchingLevelManager;
    private final Map<UUID, PlayerHorrorData> playerData = new HashMap<>();

    public HorrorProgressionManager(WatchingLevelManager watchingLevelManager) {
        this.watchingLevelManager = watchingLevelManager;
    }

    public PlayerHorrorData getData(ServerPlayerEntity player) {
        long currentTick = getProgressionTick(player);
        PlayerHorrorData data = playerData.computeIfAbsent(player.getUuid(), uuid -> new PlayerHorrorData(uuid, currentTick));
        normalizeTimeFields(data, currentTick);
        if (watchingLevelManager.hasWatchingLevel(player.getUuid())) {
            syncWatchingLevel(player, data);
        } else {
            watchingLevelManager.setWatchingLevel(player, data.watchingLevel);
        }
        return data;
    }

    public long getProgressionTick(ServerPlayerEntity player) {
        return player.getEntityWorld().getTime();
    }

    public Map<UUID, PlayerHorrorData> snapshotData() {
        return new HashMap<>(playerData);
    }

    public void replaceData(Map<UUID, PlayerHorrorData> loadedData) {
        playerData.clear();
        playerData.putAll(loadedData);
    }

    public int getStoredPlayerCount() {
        return playerData.size();
    }

    public void syncOnlineWatchingLevels(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            syncWatchingLevel(player);
        }
    }

    public void tickPlayer(ServerPlayerEntity player) {
        if (player.isSpectator()) {
            return;
        }

        PlayerHorrorData data = getData(player);
        data.totalPlayTicks++;

        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        boolean skyVisible = world.isSkyVisible(pos);
        boolean underground = watchingLevelManager.isUnderground(world, player, skyVisible);
        boolean dark = world.getLightLevel(pos) <= 7;
        boolean alone = isAlone(player, ALONE_DISTANCE);
        boolean night = watchingLevelManager.isNight(world);

        if (underground) {
            data.undergroundTicks++;
        }

        if (dark && alone) {
            data.darkAloneTicks++;
        }

        if (night && alone) {
            data.nightAloneTicks++;
        }
    }

    public HorrorPhase tryAdvancePhase(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enablePhaseSystem) {
            return null;
        }

        currentTick = getProgressionTick(player);
        PlayerHorrorData data = getData(player);
        if (data.currentPhase == HorrorPhase.MANIFESTATION) {
            return null;
        }

        HorrorPhase nextPhase = data.currentPhase.next();
        if (!canEnterPhase(player, data, nextPhase, currentTick, config)) {
            return null;
        }

        setPhase(player, nextPhase, currentTick);
        return nextPhase;
    }

    public void setPhase(ServerPlayerEntity player, HorrorPhase phase, long currentTick) {
        currentTick = getProgressionTick(player);
        PlayerHorrorData data = getData(player);
        data.currentPhase = phase;
        data.phaseEnteredTime = currentTick;
        data.lastPhaseAdvanceTime = currentTick;
        data.nextReceiverFallbackGameTime = 0L;
        ItConfig config = ItConfigManager.getConfig();
        data.receiverPhaseCooldownUntilGameTime = config.enableReceiverPhaseCooldown
                ? currentTick + Math.max(0, config.receiverPhaseCooldownTicks)
                : 0L;
        data.receiverFallbackPhaseNumber = phase.getNumber();
        data.receiverFallbacksInPhase = 0;
        if (phase == HorrorPhase.MANIFESTATION) {
            data.hasTriggeredManifestation = true;
        }
    }

    public void resetProgression(ServerPlayerEntity player) {
        resetProgression(player, true);
    }

    public void resetProgression(ServerPlayerEntity player, boolean keepReceiverState) {
        PlayerHorrorData data = getData(player);
        data.resetProgression(getProgressionTick(player), keepReceiverState);
        watchingLevelManager.setWatchingLevel(player, data.watchingLevel);
    }

    public HorrorPhase getPhase(ServerPlayerEntity player) {
        return getData(player).currentPhase;
    }

    public void syncWatchingLevel(ServerPlayerEntity player) {
        syncWatchingLevel(player, getData(player));
    }

    public void recordCaveFootstep(ServerPlayerEntity player) {
        getData(player).caveFootstepEvents++;
    }

    public void recordMiningEcho(ServerPlayerEntity player) {
        getData(player).miningEchoEvents++;
    }

    public void recordFakeChat(ServerPlayerEntity player) {
        getData(player).fakeChatEvents++;
    }

    public void recordHandDropAnomaly(ServerPlayerEntity player) {
        getData(player).handDropAnomalies++;
    }

    public void recordInventoryOpenAnomaly(ServerPlayerEntity player) {
        getData(player).inventoryOpenAnomalies++;
    }

    public void recordMysteriousContact(ServerPlayerEntity player) {
        PlayerHorrorData data = getData(player);
        data.mysteriousContactMessages++;
        data.fakeChatEvents++;
    }

    public void recordEerieSoundEvent(ServerPlayerEntity player) {
        getData(player).eerieSoundEvents++;
    }

    public void recordAnimalStareEvent(ServerPlayerEntity player) {
        getData(player).animalStareEvents++;
    }

    public void recordFamiliarSoundEvent(ServerPlayerEntity player) {
        getData(player).familiarSoundEvents++;
    }

    public void recordSeparationWarning(ServerPlayerEntity player) {
        getData(player).separationWarnings++;
    }

    public void recordFakeTeammateFootstepEvent(ServerPlayerEntity player) {
        getData(player).fakeTeammateFootstepEvents++;
    }

    public void recordTeamGazeEvent(ServerPlayerEntity player) {
        getData(player).teamGazeEvents++;
    }

    public void recordFakeTabEvent(ServerPlayerEntity player) {
        getData(player).fakeTabEvents++;
    }

    public void recordFakeAdvancementEvent(ServerPlayerEntity player) {
        getData(player).fakeAdvancementEvents++;
    }

    public void recordFakeRescueMessage(ServerPlayerEntity player) {
        getData(player).fakeRescueMessages++;
    }

    public void recordAnimalDisguiseEvent(ServerPlayerEntity player) {
        getData(player).animalDisguiseEvents++;
    }

    public void recordAnimalDisguiseKilledEvent(ServerPlayerEntity player) {
        getData(player).animalDisguiseKilledEvents++;
    }

    public void recordAnimalDisguiseAnimalAttack(ServerPlayerEntity player) {
        getData(player).animalDisguiseAnimalAttacks++;
    }

    public void recordAnimalDisguiseVictim(ServerPlayerEntity player) {
        getData(player).animalDisguiseVictims++;
    }

    public void recordAnimalDisguiseRetaliation(ServerPlayerEntity player) {
        getData(player).animalDisguiseRetaliations++;
    }

    public void recordWatcherSighting(ServerPlayerEntity player) {
        PlayerHorrorData data = getData(player);
        data.watcherSightings++;
        data.hasSeenWatcher = true;
    }

    public void recordReceiverMessage(ServerPlayerEntity player) {
        PlayerHorrorData data = getData(player);
        data.receiverMessagesReceived++;
        data.lastReceiverMessageGameTime = getProgressionTick(player);
        data.nextReceiverFallbackGameTime = 0L;
    }

    public void recordReceiverOpened(ServerPlayerEntity player) {
        getData(player).receiverOpenedCount++;
    }

    public boolean isReceiverPhaseCooldownActive(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableReceiverPhaseCooldown || config.receiverPhaseCooldownTicks <= 0) {
            return false;
        }

        PlayerHorrorData data = getData(player);
        return getProgressionTick(player) < data.receiverPhaseCooldownUntilGameTime;
    }

    public long getReceiverPhaseCooldownRemainingTicks(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableReceiverPhaseCooldown || config.receiverPhaseCooldownTicks <= 0) {
            return 0L;
        }

        PlayerHorrorData data = getData(player);
        return Math.max(0L, data.receiverPhaseCooldownUntilGameTime - getProgressionTick(player));
    }

    public void recordJumpscare(ServerPlayerEntity player) {
        getData(player).jumpscareEvents++;
    }

    public void recordPhaseFiveInterference(ServerPlayerEntity player) {
        getData(player).phaseFiveInterferenceEvents++;
    }

    public void recordChaseStarted(ServerPlayerEntity player) {
        getData(player).chaseEvents++;
    }

    public void recordChaseEscape(ServerPlayerEntity player) {
        getData(player).chaseEscapes++;
    }

    public void recordChaseCaught(ServerPlayerEntity player) {
        getData(player).chaseCaughtEvents++;
    }

    public void recordChaseEnded(ServerPlayerEntity player, long currentTick) {
        getData(player).lastChaseGameTime = currentTick;
    }

    public void recordCaveStalkerStarted(ServerPlayerEntity player) {
        getData(player).caveStalkerEvents++;
    }

    public void recordCaveStalkerEscape(ServerPlayerEntity player) {
        getData(player).caveStalkerEscapes++;
    }

    public void recordCaveStalkerCaught(ServerPlayerEntity player) {
        getData(player).caveStalkerCaughtEvents++;
    }

    public void recordCaveStalkerTrapTriggered(ServerPlayerEntity player) {
        getData(player).caveStalkerTrapTriggers++;
    }

    public void recordCaveStalkerEasterEgg(ServerPlayerEntity player) {
        getData(player).caveStalkerEasterEggs++;
    }

    public void recordCaveStalkerEnded(ServerPlayerEntity player, long currentTick) {
        getData(player).lastCaveStalkerGameTime = currentTick;
    }

    public boolean isAlone(ServerPlayerEntity player, double distance) {
        double maxDistanceSquared = distance * distance;

        for (ServerPlayerEntity otherPlayer : player.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            if (otherPlayer == player || otherPlayer.isSpectator()) {
                continue;
            }

            if (otherPlayer.getEntityWorld() != player.getEntityWorld()) {
                continue;
            }

            if (otherPlayer.squaredDistanceTo(player) <= maxDistanceSquared) {
                return false;
            }
        }

        return true;
    }

    public boolean isManifestationEnvironment(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        boolean alone = isAlone(player, ALONE_DISTANCE);
        boolean skyVisible = world.isSkyVisible(pos);
        boolean underground = watchingLevelManager.isUnderground(world, player, skyVisible);
        boolean deepUnderground = !skyVisible && player.getY() < world.getSeaLevel() - 24;
        boolean lowLightAlone = alone && world.getLightLevel(pos) <= 7;
        boolean nightAloneForestOrCave = alone && watchingLevelManager.isNight(world) && (isForestLike(world, pos) || underground);
        boolean isolatedInBadPlace = alone && (world.getLightLevel(pos) <= 9 || underground || watchingLevelManager.isNight(world));

        return lowLightAlone || deepUnderground || nightAloneForestOrCave || isolatedInBadPlace;
    }

    public PhaseDebugInfo getPhaseDebugInfo(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = getProgressionTick(player);
        PlayerHorrorData data = getData(player);
        HorrorPhase currentPhase = data.currentPhase;
        HorrorPhase nextPhase = currentPhase == HorrorPhase.MANIFESTATION ? null : currentPhase.next();
        List<PhaseRequirement> requirements = new ArrayList<>();

        if (!config.enablePhaseSystem) {
            requirements.add(new PhaseRequirement("阶段系统", "关闭", "开启", false));
            return new PhaseDebugInfo(currentPhase, nextPhase, false, config.enablePhaseSystem, config.enableProgressionGates, requirements);
        }

        if (nextPhase == null) {
            requirements.add(new PhaseRequirement("下一阶段", "已在最终阶段", "无", false));
            return new PhaseDebugInfo(currentPhase, null, false, config.enablePhaseSystem, config.enableProgressionGates, requirements);
        }

        addRequirement(requirements, "被注视值", String.valueOf(data.watchingLevel), ">=" + nextPhase.getMinimumWatchingLevel(), data.watchingLevel >= nextPhase.getMinimumWatchingLevel());
        if (config.passiveProgressionEnabled) {
            addRequirement(requirements, "被动推进", formatRequirementTicks(data.totalPlayTicks), ">=" + formatRequirementTicks(passivePhaseTicks(nextPhase, config)), passiveProgressionSatisfied(data, nextPhase, config));
        }
        if (config.avoidanceProgressionEnabled) {
            addRequirement(requirements, "逃避推进", "逃避 " + Math.round(data.avoidanceScore) + " / 未读压力 " + Math.round(data.unreadSignalPressure) + " / 团队压力 " + Math.round(data.groupDread), avoidanceRequirementText(nextPhase, config), avoidanceProgressionSatisfied(data, nextPhase, config));
        }
        if (!config.enableProgressionGates) {
            return new PhaseDebugInfo(currentPhase, nextPhase, requirements.stream().allMatch(PhaseRequirement::satisfied), config.enablePhaseSystem, config.enableProgressionGates, requirements);
        }

        long timeInPhaseTicks = data.getTimeInPhaseTicks(currentTick);
        switch (nextPhase) {
            case DORMANT -> {
            }
            case WATCHING -> {
                addRequirement(requirements, "总游玩时间", formatRequirementTicks(data.totalPlayTicks), ">=" + formatRequirementTicks(secondsToTicks(config.phase2MinPlayTimeSeconds)), data.totalPlayTicks >= secondsToTicks(config.phase2MinPlayTimeSeconds));
                addRequirement(requirements, "Receiver 信息", String.valueOf(data.receiverMessagesReceived), receiverRequirementText(config.phase2RequiredReceiverMessages, config), receiverRequirementSatisfied(data.receiverMessagesReceived, config.phase2RequiredReceiverMessages, config));
                addRequirement(requirements, "Receiver 打开次数", String.valueOf(data.receiverOpenedCount), receiverRequirementText(config.phase2RequiredReceiverOpens, config), receiverRequirementSatisfied(data.receiverOpenedCount, config.phase2RequiredReceiverOpens, config));
                boolean environment = data.undergroundTicks >= TWO_MINUTES_TICKS
                        || data.darkAloneTicks >= TWO_MINUTES_TICKS
                        || data.nightAloneTicks >= TWO_MINUTES_TICKS;
                addRequirement(requirements, "环境累计", "地下 " + formatRequirementTicks(data.undergroundTicks) + " / 黑暗独处 " + formatRequirementTicks(data.darkAloneTicks) + " / 夜晚独处 " + formatRequirementTicks(data.nightAloneTicks), "任一 >= " + formatRequirementTicks(TWO_MINUTES_TICKS), environment);
            }
            case IMITATING -> {
                addRequirement(requirements, "当前阶段停留", formatRequirementTicks(timeInPhaseTicks), ">=" + formatRequirementTicks(secondsToTicks(config.phase3MinTimeInPhaseSeconds)), timeInPhaseTicks >= secondsToTicks(config.phase3MinTimeInPhaseSeconds));
                addRequirement(requirements, "Receiver 信息", String.valueOf(data.receiverMessagesReceived), receiverRequirementText(config.phase3RequiredReceiverMessages, config), receiverRequirementSatisfied(data.receiverMessagesReceived, config.phase3RequiredReceiverMessages, config));
                addRequirement(requirements, "Receiver 打开次数", String.valueOf(data.receiverOpenedCount), receiverRequirementText(config.phase3RequiredReceiverOpens, config), receiverRequirementSatisfied(data.receiverOpenedCount, config.phase3RequiredReceiverOpens, config));
                boolean eventSignal = watchingEventScore(data) >= config.phase3RequiredSmallEventScore
                        || data.caveFootstepEvents >= 2
                        || data.miningEchoEvents >= 1
                        || data.darkAloneTicks >= SIX_MINUTES_TICKS;
                addRequirement(requirements, "模仿阶段异常信号", "小事件分 " + watchingEventScore(data) + " / 脚步 " + data.caveFootstepEvents + " / 挖掘 " + data.miningEchoEvents + " / 黑暗独处 " + formatRequirementTicks(data.darkAloneTicks), "小事件>=" + config.phase3RequiredSmallEventScore + " 或脚步>=2 或挖掘>=1 或黑暗独处>=" + formatRequirementTicks(SIX_MINUTES_TICKS), eventSignal);
            }
            case INTRUSION -> {
                addRequirement(requirements, "当前阶段停留", formatRequirementTicks(timeInPhaseTicks), ">=" + formatRequirementTicks(secondsToTicks(config.phase4MinTimeInPhaseSeconds)), timeInPhaseTicks >= secondsToTicks(config.phase4MinTimeInPhaseSeconds));
                addRequirement(requirements, "Receiver 信息", String.valueOf(data.receiverMessagesReceived), receiverRequirementText(config.phase4RequiredReceiverMessages, config), receiverRequirementSatisfied(data.receiverMessagesReceived, config.phase4RequiredReceiverMessages, config));
                addRequirement(requirements, "Receiver 打开次数", String.valueOf(data.receiverOpenedCount), receiverRequirementText(config.phase4RequiredReceiverOpens, config), receiverRequirementSatisfied(data.receiverOpenedCount, config.phase4RequiredReceiverOpens, config));
                boolean intrusionSignal = intrusionSignalScore(data) >= config.phase4RequiredFakeChatsOrWatcherSightings
                        || phaseEscalationScore(data) >= config.phase4RequiredEventScore
                        || data.darkAloneTicks >= TEN_MINUTES_TICKS;
                addRequirement(requirements, "入侵阶段异常信号", "假聊天/Watcher/Tab 分 " + intrusionSignalScore(data) + " / 总升级分 " + phaseEscalationScore(data) + " / 黑暗独处 " + formatRequirementTicks(data.darkAloneTicks), "入侵分>=" + config.phase4RequiredFakeChatsOrWatcherSightings + " 或升级分>=" + config.phase4RequiredEventScore + " 或黑暗独处>=" + formatRequirementTicks(TEN_MINUTES_TICKS), intrusionSignal);
            }
            case MANIFESTATION -> {
                addRequirement(requirements, "当前阶段停留", formatRequirementTicks(timeInPhaseTicks), ">=" + formatRequirementTicks(secondsToTicks(config.phase5MinTimeInPhaseSeconds)), timeInPhaseTicks >= secondsToTicks(config.phase5MinTimeInPhaseSeconds));
                addRequirement(requirements, "Receiver 信息", String.valueOf(data.receiverMessagesReceived), receiverRequirementText(config.phase5RequiredReceiverMessages, config), receiverRequirementSatisfied(data.receiverMessagesReceived, config.phase5RequiredReceiverMessages, config));
                addRequirement(requirements, "Receiver 打开次数", String.valueOf(data.receiverOpenedCount), receiverRequirementText(config.phase5RequiredReceiverOpens, config), receiverRequirementSatisfied(data.receiverOpenedCount, config.phase5RequiredReceiverOpens, config));
                boolean manifestationSignal = data.watcherSightings >= config.phase5RequiredWatcherSightings
                        || phaseEscalationScore(data) >= config.phase5RequiredEventScore;
                addRequirement(requirements, "显现阶段异常信号", "Watcher " + data.watcherSightings + " / 升级分 " + phaseEscalationScore(data), "Watcher>=" + config.phase5RequiredWatcherSightings + " 或升级分>=" + config.phase5RequiredEventScore, manifestationSignal);
                addRequirement(requirements, "显现环境", manifestationEnvironmentDescription(player), "独处、低光、地下、夜晚森林或洞穴之一", isManifestationEnvironment(player));
            }
        }

        boolean canAdvance = canEnterPhase(player, data, nextPhase, currentTick, config);
        return new PhaseDebugInfo(currentPhase, nextPhase, canAdvance, config.enablePhaseSystem, config.enableProgressionGates, requirements);
    }

    private boolean canEnterPhase(ServerPlayerEntity player, PlayerHorrorData data, HorrorPhase phase, long currentTick, ItConfig config) {
        if (passiveProgressionSatisfied(data, phase, config) || avoidanceProgressionSatisfied(data, phase, config)) {
            return true;
        }

        if (data.watchingLevel < phase.getMinimumWatchingLevel()) {
            return false;
        }

        if (!config.enableProgressionGates) {
            return true;
        }

        long timeInPhaseTicks = data.getTimeInPhaseTicks(currentTick);
        return switch (phase) {
            case DORMANT -> true;
            case WATCHING -> data.totalPlayTicks >= secondsToTicks(config.phase2MinPlayTimeSeconds)
                    && receiverRequirementSatisfied(data.receiverMessagesReceived, config.phase2RequiredReceiverMessages, config)
                    && receiverRequirementSatisfied(data.receiverOpenedCount, config.phase2RequiredReceiverOpens, config)
                    && (data.undergroundTicks >= TWO_MINUTES_TICKS
                    || data.darkAloneTicks >= TWO_MINUTES_TICKS
                    || data.nightAloneTicks >= TWO_MINUTES_TICKS);
            case IMITATING -> timeInPhaseTicks >= secondsToTicks(config.phase3MinTimeInPhaseSeconds)
                    && receiverRequirementSatisfied(data.receiverMessagesReceived, config.phase3RequiredReceiverMessages, config)
                    && receiverRequirementSatisfied(data.receiverOpenedCount, config.phase3RequiredReceiverOpens, config)
                    && (watchingEventScore(data) >= config.phase3RequiredSmallEventScore
                    || data.caveFootstepEvents >= 2
                    || data.miningEchoEvents >= 1
                    || data.darkAloneTicks >= SIX_MINUTES_TICKS);
            case INTRUSION -> timeInPhaseTicks >= secondsToTicks(config.phase4MinTimeInPhaseSeconds)
                    && receiverRequirementSatisfied(data.receiverMessagesReceived, config.phase4RequiredReceiverMessages, config)
                    && receiverRequirementSatisfied(data.receiverOpenedCount, config.phase4RequiredReceiverOpens, config)
                    && (intrusionSignalScore(data) >= config.phase4RequiredFakeChatsOrWatcherSightings
                    || phaseEscalationScore(data) >= config.phase4RequiredEventScore
                    || data.darkAloneTicks >= TEN_MINUTES_TICKS);
            case MANIFESTATION -> timeInPhaseTicks >= secondsToTicks(config.phase5MinTimeInPhaseSeconds)
                    && receiverRequirementSatisfied(data.receiverMessagesReceived, config.phase5RequiredReceiverMessages, config)
                    && receiverRequirementSatisfied(data.receiverOpenedCount, config.phase5RequiredReceiverOpens, config)
                    && (data.watcherSightings >= config.phase5RequiredWatcherSightings
                    || phaseEscalationScore(data) >= config.phase5RequiredEventScore)
                    && isManifestationEnvironment(player);
        };
    }

    private boolean passiveProgressionSatisfied(PlayerHorrorData data, HorrorPhase phase, ItConfig config) {
        if (!config.passiveProgressionEnabled) {
            return false;
        }

        long requiredTicks = passivePhaseTicks(phase, config);
        return requiredTicks > 0L && data.totalPlayTicks >= requiredTicks;
    }

    private long passivePhaseTicks(HorrorPhase phase, ItConfig config) {
        return switch (phase) {
            case DORMANT -> 0L;
            case WATCHING -> secondsToTicks(config.passivePhase2Seconds);
            case IMITATING -> secondsToTicks(config.passivePhase3Seconds);
            case INTRUSION -> secondsToTicks(config.passivePhase4Seconds);
            case MANIFESTATION -> secondsToTicks(config.passivePhase5Seconds);
        };
    }

    private boolean avoidanceProgressionSatisfied(PlayerHorrorData data, HorrorPhase phase, ItConfig config) {
        if (!config.avoidanceProgressionEnabled) {
            return false;
        }

        return switch (phase) {
            case DORMANT -> false;
            case WATCHING -> data.totalPlayTicks >= secondsToTicks(180)
                    && (data.avoidanceScore >= config.avoidanceThresholdLight || data.unreadSignalPressure >= 10.0D);
            case IMITATING -> data.totalPlayTicks >= secondsToTicks(600)
                    && (data.avoidanceScore >= config.avoidanceThresholdMedium
                    || data.unreadSignalPressure >= 30.0D
                    || data.netherRushEvents > 0);
            case INTRUSION -> data.totalPlayTicks >= secondsToTicks(900)
                    && (data.avoidanceScore >= Math.min(config.avoidanceThresholdHigh, 70.0D)
                    || data.groupDread >= 60.0D
                    || data.skyborneEvents > 0
                    || data.baseIntrusionEvents > 0);
            case MANIFESTATION -> data.totalPlayTicks >= secondsToTicks(1500)
                    && data.avoidanceScore >= config.avoidanceThresholdHigh
                    && (data.avoidanceTriggeredEvents >= 2
                    || data.skyborneEvents >= 2
                    || data.groupDreadEvents >= 2
                    || data.netherRushEvents > 0);
        };
    }

    private boolean receiverRequirementSatisfied(int current, int required, ItConfig config) {
        return !config.receiverOpenHardRequirementEnabled || current >= required;
    }

    private String receiverRequirementText(int required, ItConfig config) {
        return config.receiverOpenHardRequirementEnabled ? ">=" + required : "可旁路，目标>=" + required;
    }

    private String avoidanceRequirementText(HorrorPhase phase, ItConfig config) {
        return switch (phase) {
            case DORMANT -> "无";
            case WATCHING -> "逃避>=" + Math.round(config.avoidanceThresholdLight) + " 或未读压力";
            case IMITATING -> "逃避>=" + Math.round(config.avoidanceThresholdMedium) + " / 下界快进 / 未读压力";
            case INTRUSION -> "逃避>=" + Math.round(Math.min(config.avoidanceThresholdHigh, 70.0D)) + " / 团队压力 / 高空 / 基地入侵";
            case MANIFESTATION -> "逃避>=" + Math.round(config.avoidanceThresholdHigh) + " 且已有多次针对性事件";
        };
    }

    private int watchingEventScore(PlayerHorrorData data) {
        return data.caveFootstepEvents
                + data.miningEchoEvents
                + data.handDropAnomalies
                + data.inventoryOpenAnomalies
                + data.eerieSoundEvents
                + data.animalStareEvents
                + data.familiarSoundEvents
                + data.separationWarnings
                + data.fakeTeammateFootstepEvents
                + data.teamGazeEvents
                + data.fakeAdvancementEvents
                + data.fakeRescueMessages
                + data.animalDisguiseEvents
                + data.animalDisguiseKilledEvents
                + data.animalDisguiseAnimalAttacks
                + data.animalDisguiseVictims
                + data.animalDisguiseRetaliations;
    }

    private int intrusionSignalScore(PlayerHorrorData data) {
        return data.fakeChatEvents + data.watcherSightings + data.fakeTabEvents;
    }

    private int phaseEscalationScore(PlayerHorrorData data) {
        return watchingEventScore(data)
                + intrusionSignalScore(data) * 2
                + data.phaseFiveInterferenceEvents
                + data.chaseEvents
                + data.caveStalkerEvents
                + data.jumpscareEvents;
    }

    private void addRequirement(List<PhaseRequirement> requirements, String label, String current, String required, boolean satisfied) {
        requirements.add(new PhaseRequirement(label, current, required, satisfied));
    }

    private String manifestationEnvironmentDescription(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        boolean alone = isAlone(player, ALONE_DISTANCE);
        boolean skyVisible = world.isSkyVisible(pos);
        boolean underground = watchingLevelManager.isUnderground(world, player, skyVisible);
        return "独处=" + alone
                + " / 光照=" + world.getLightLevel(pos)
                + " / 可见天空=" + skyVisible
                + " / 地下=" + underground
                + " / Y=" + pos.getY();
    }

    private String formatRequirementTicks(long ticks) {
        long seconds = Math.max(0L, ticks) / TICKS_PER_SECOND;
        return seconds + "秒";
    }

    private void syncWatchingLevel(ServerPlayerEntity player, PlayerHorrorData data) {
        data.watchingLevel = (int) Math.round(watchingLevelManager.getWatchingLevel(player));
    }

    private void normalizeTimeFields(PlayerHorrorData data, long currentTick) {
        if (data.phaseEnteredTime > currentTick) {
            long retainedPhaseTicks = Math.min(data.totalPlayTicks, currentTick);
            data.phaseEnteredTime = Math.max(0L, currentTick - retainedPhaseTicks);
        }

        if (data.lastPhaseAdvanceTime > currentTick) {
            data.lastPhaseAdvanceTime = data.phaseEnteredTime;
        }
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }

    public record PhaseDebugInfo(
            HorrorPhase currentPhase,
            HorrorPhase nextPhase,
            boolean canAdvance,
            boolean phaseSystemEnabled,
            boolean progressionGatesEnabled,
            List<PhaseRequirement> requirements
    ) {
    }

    public record PhaseRequirement(String label, String current, String required, boolean satisfied) {
    }

    private boolean isForestLike(ServerWorld world, BlockPos center) {
        int leaves = 0;

        for (int x = -5; x <= 5; x += 2) {
            for (int y = -2; y <= 5; y += 2) {
                for (int z = -5; z <= 5; z += 2) {
                    if (world.getBlockState(center.add(x, y, z)).isIn(BlockTags.LEAVES)) {
                        leaves++;
                        if (leaves >= 4) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }
}
