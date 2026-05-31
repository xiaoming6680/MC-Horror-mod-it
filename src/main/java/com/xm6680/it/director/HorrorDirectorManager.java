package com.xm6680.it.director;

import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.analog.ReceiverMessageType;
import com.xm6680.it.cavestalker.CaveStalkerManager;
import com.xm6680.it.chase.ChaseManager;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.effect.ClientDistortionManager;
import com.xm6680.it.event.AnimalDisguiseManager;
import com.xm6680.it.event.FamiliarSoundEventType;
import com.xm6680.it.event.HorrorEventManager;
import com.xm6680.it.event.MultiplayerDreadManager;
import com.xm6680.it.event.NetherSignalManager;
import com.xm6680.it.event.WatcherSpawnManager;
import com.xm6680.it.event.WorldAnomalyManager;
import com.xm6680.it.jumpscare.JumpscareManager;
import com.xm6680.it.manifestation.ManifestationManager;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class HorrorDirectorManager {
    private static final int TICKS_PER_SECOND = 20;

    private final HorrorProgressionManager progressionManager;
    private final ReceiverManager receiverManager;
    private final AnalogHorrorManager analogHorrorManager;
    private final HorrorEventManager horrorEventManager;
    private final WatcherSpawnManager watcherSpawnManager;
    private final WorldAnomalyManager worldAnomalyManager;
    private final MultiplayerDreadManager multiplayerDreadManager;
    private final AnimalDisguiseManager animalDisguiseManager;
    private final NetherSignalManager netherSignalManager;
    private final ManifestationManager manifestationManager;
    private final JumpscareManager jumpscareManager;
    private final ChaseManager chaseManager;
    private final CaveStalkerManager caveStalkerManager;
    private final ClientDistortionManager clientDistortionManager;
    private final PlayerContextDetector contextDetector;
    private final AvoidanceManager avoidanceManager;
    private final GroupDreadManager groupDreadManager;
    private final SkyborneHorrorManager skyborneHorrorManager;
    private final Map<UUID, DirectorState> states = new HashMap<>();
    private final Random random = new Random();

    public HorrorDirectorManager(
            HorrorProgressionManager progressionManager,
            ReceiverManager receiverManager,
            AnalogHorrorManager analogHorrorManager,
            HorrorEventManager horrorEventManager,
            WatcherSpawnManager watcherSpawnManager,
            WorldAnomalyManager worldAnomalyManager,
            MultiplayerDreadManager multiplayerDreadManager,
            AnimalDisguiseManager animalDisguiseManager,
            NetherSignalManager netherSignalManager,
            ManifestationManager manifestationManager,
            JumpscareManager jumpscareManager,
            ChaseManager chaseManager,
            CaveStalkerManager caveStalkerManager,
            ClientDistortionManager clientDistortionManager,
            PlayerContextDetector contextDetector,
            AvoidanceManager avoidanceManager,
            GroupDreadManager groupDreadManager,
            SkyborneHorrorManager skyborneHorrorManager
    ) {
        this.progressionManager = progressionManager;
        this.receiverManager = receiverManager;
        this.analogHorrorManager = analogHorrorManager;
        this.horrorEventManager = horrorEventManager;
        this.watcherSpawnManager = watcherSpawnManager;
        this.worldAnomalyManager = worldAnomalyManager;
        this.multiplayerDreadManager = multiplayerDreadManager;
        this.animalDisguiseManager = animalDisguiseManager;
        this.netherSignalManager = netherSignalManager;
        this.manifestationManager = manifestationManager;
        this.jumpscareManager = jumpscareManager;
        this.chaseManager = chaseManager;
        this.caveStalkerManager = caveStalkerManager;
        this.clientDistortionManager = clientDistortionManager;
        this.contextDetector = contextDetector;
        this.avoidanceManager = avoidanceManager;
        this.groupDreadManager = groupDreadManager;
        this.skyborneHorrorManager = skyborneHorrorManager;
    }

    public void tick(MinecraftServer server, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.horrorDirectorEnabled) {
            return;
        }

        int interval = Math.max(20, config.horrorDirectorIntervalTicks);
        if (currentTick % interval != 0) {
            return;
        }

        analogHorrorManager.tick(server, currentTick);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }

            tickPlayer(player, currentTick, config);
        }
    }

    public String getStatusLine(ServerPlayerEntity player, long currentTick) {
        DirectorState state = getState(player, currentTick);
        PlayerContextSnapshot snapshot = contextDetector.getSnapshot(player);
        return "director=" + state.lastEventType
                + ", context=" + snapshot.describe()
                + ", avoidance=" + Math.round(avoidanceManager.getAvoidance(player))
                + ", groupDread=" + Math.round(groupDreadManager.getGroupDread(player))
                + ", minorSince=" + formatTicks(currentTick - state.lastMinorTick)
                + ", noticeableSince=" + formatTicks(currentTick - state.lastNoticeableTick)
                + ", majorSince=" + formatTicks(currentTick - state.lastMajorTick);
    }

    public boolean forceEvent(ServerPlayerEntity player, String eventType) {
        long currentTick = player.getEntityWorld().getServer().getTicks();
        PlayerContextSnapshot snapshot = contextDetector.getSnapshot(player);
        return switch (eventType.toLowerCase()) {
            case "minor" -> triggerMinor(player, snapshot, currentTick, true);
            case "noticeable", "medium" -> triggerNoticeable(player, snapshot, currentTick, true);
            case "major" -> triggerMajor(player, snapshot, currentTick, true);
            case "skyborne", "aerial" -> skyborneHorrorManager.triggerSkyborneEvent(player, snapshot, currentTick, true);
            case "group" -> triggerGroupEvent(player, snapshot, currentTick, true);
            case "base" -> triggerBaseEvent(player, true);
            case "nether" -> triggerNetherEvent(player, snapshot, true);
            case "afk" -> triggerAfkRelease(player, currentTick);
            default -> false;
        };
    }

    public void remove(ServerPlayerEntity player) {
        states.remove(player.getUuid());
    }

    private void tickPlayer(ServerPlayerEntity player, long currentTick, ItConfig config) {
        DirectorState state = getState(player, currentTick);
        PlayerContextSnapshot snapshot = contextDetector.getSnapshot(player);
        HorrorPhase phase = progressionManager.getPhase(player);

        if (phase == HorrorPhase.DORMANT) {
            return;
        }

        if (snapshot.activeAfterAfk() && triggerAfkRelease(player, currentTick)) {
            record(player, state, DirectorEventType.NOTICEABLE, snapshot, currentTick);
            return;
        }

        if (snapshot.has(PlayerContext.AFK_OR_IDLE)) {
            if (currentTick - state.lastMinorTick >= secondsToTicks(config.minorEventFailsafeSeconds)) {
                scheduleQuietReceiver(player, "静止记录过长。周围声音仍在变化。", 40);
                record(player, state, DirectorEventType.MINOR, snapshot, currentTick);
            }
            return;
        }

        boolean forcedMinor = currentTick - state.lastMinorTick >= secondsToTicks(config.minorEventFailsafeSeconds);
        boolean forcedNoticeable = currentTick - state.lastNoticeableTick >= secondsToTicks(config.noticeableEventFailsafeSeconds);
        boolean forcedMajor = progressionManager.getPhase(player).isAtLeast(HorrorPhase.INTRUSION)
                && currentTick - state.lastMajorTick >= secondsToTicks(config.majorEventFailsafeSeconds);

        if (forcedMajor && triggerMajor(player, snapshot, currentTick, false)) {
            record(player, state, DirectorEventType.MAJOR, snapshot, currentTick);
            return;
        }

        if (forcedNoticeable && triggerNoticeable(player, snapshot, currentTick, false)) {
            record(player, state, DirectorEventType.NOTICEABLE, snapshot, currentTick);
            return;
        }

        if (forcedMinor && triggerMinor(player, snapshot, currentTick, false)) {
            record(player, state, DirectorEventType.MINOR, snapshot, currentTick);
            return;
        }

        double avoidance = avoidanceManager.getAvoidance(player);
        double groupDread = groupDreadManager.getGroupDread(player);
        double minorChance = 0.20D * config.eventChanceMultiplier * pressureMultiplier(avoidance, groupDread);
        double noticeableChance = 0.070D * config.eventChanceMultiplier * config.noticeableEventChanceMultiplier * pressureMultiplier(avoidance, groupDread);
        double majorChance = 0.015D * config.eventChanceMultiplier * config.majorEventChanceMultiplier * pressureMultiplier(avoidance, groupDread);

        if (random.nextDouble() < majorChance && triggerMajor(player, snapshot, currentTick, false)) {
            record(player, state, DirectorEventType.MAJOR, snapshot, currentTick);
        } else if (random.nextDouble() < noticeableChance && triggerNoticeable(player, snapshot, currentTick, false)) {
            record(player, state, DirectorEventType.NOTICEABLE, snapshot, currentTick);
        } else if (random.nextDouble() < minorChance && triggerMinor(player, snapshot, currentTick, false)) {
            record(player, state, DirectorEventType.MINOR, snapshot, currentTick);
        }
    }

    private boolean triggerMinor(ServerPlayerEntity player, PlayerContextSnapshot snapshot, long currentTick, boolean forced) {
        HorrorPhase phase = progressionManager.getPhase(player);
        if (!forced && !phase.isAtLeast(HorrorPhase.WATCHING)) {
            return false;
        }

        if (snapshot.has(PlayerContext.SKYBORNE) && skyborneHorrorManager.triggerSkyborneEvent(player, snapshot, currentTick, forced)) {
            return true;
        }

        if (snapshot.has(PlayerContext.NETHER) && triggerNetherEvent(player, snapshot, forced)) {
            return true;
        }

        if (snapshot.has(PlayerContext.GROUPED) && triggerGroupEvent(player, snapshot, currentTick, forced)) {
            return true;
        }

        if (snapshot.has(PlayerContext.UNDERGROUND)) {
            return random.nextBoolean() ? horrorEventManager.triggerCaveSound(player) : horrorEventManager.triggerMiningEcho(player);
        }

        if (snapshot.has(PlayerContext.HOME_OR_BASE) && (forced ? horrorEventManager.triggerFamiliarSound(player, FamiliarSoundEventType.CHEST) : horrorEventManager.triggerNaturalFamiliarSound(player, FamiliarSoundEventType.CHEST))) {
            return true;
        }

        return random.nextBoolean()
                ? (forced ? horrorEventManager.triggerEerieSound(player) : horrorEventManager.triggerNaturalEerieSound(player))
                : (forced ? horrorEventManager.triggerFamiliarSound(player) : horrorEventManager.triggerNaturalFamiliarSound(player));
    }

    private boolean triggerNoticeable(ServerPlayerEntity player, PlayerContextSnapshot snapshot, long currentTick, boolean forced) {
        HorrorPhase phase = progressionManager.getPhase(player);
        ItConfig config = ItConfigManager.getConfig();

        if (!forced && !phase.isAtLeast(HorrorPhase.WATCHING)) {
            return false;
        }

        if (snapshot.has(PlayerContext.SKYBORNE) && skyborneHorrorManager.triggerSkyborneEvent(player, snapshot, currentTick, forced)) {
            return true;
        }

        if (snapshot.has(PlayerContext.GROUPED) && triggerGroupEvent(player, snapshot, currentTick, forced)) {
            return true;
        }

        if (snapshot.has(PlayerContext.NETHER) && triggerNetherEvent(player, snapshot, forced)) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.INTRUSION)) && snapshot.has(PlayerContext.HOME_OR_BASE) && triggerBaseEvent(player, forced)) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.IMITATING)) && snapshot.has(PlayerContext.UNDERGROUND) && config.enableTunnelWatcherSpawns && watcherSpawnManager.forceSpawnTunnelWatcher(player)) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.IMITATING)) && config.enableFakeChat && config.enableMysteriousContact && random.nextBoolean()
                && (forced ? horrorEventManager.triggerFakeChat(player) : horrorEventManager.triggerNaturalFakeChat(player))) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.IMITATING)) && config.enableForcedChatEvent && random.nextInt(4) == 0
                && (forced ? horrorEventManager.triggerForcedChat(player) : horrorEventManager.triggerNaturalForcedChat(player))) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.IMITATING)) && config.enableLegacyTextureAnomalies && random.nextBoolean() && clientDistortionManager.forceLegacyTexture(player, currentTick)) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.IMITATING)) && config.enableRandomSignAnomalies && random.nextInt(5) == 0 && worldAnomalyManager.forceSign(player)) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.IMITATING)) && config.enableNetherrackCrossAnomalies && random.nextInt(6) == 0 && worldAnomalyManager.forceNetherrackCross(player)) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.IMITATING)) && config.enableFakeAdvancementEvent && random.nextInt(5) == 0 && multiplayerDreadManager.triggerFakeAdvancement(player, forced)) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.IMITATING)) && config.enableFakeRescueMessages && random.nextInt(5) == 0 && multiplayerDreadManager.triggerFakeRescue(player, null, forced)) {
            return true;
        }

        if ((forced || phase.isAtLeast(phaseFromNumber(config.animalDisguiseMinPhase))) && config.enableAnimalDisguiseEvent && config.animalDisguiseEnabled && random.nextInt(5) == 0 && animalDisguiseManager.triggerAnimalDisguise(player, forced)) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.WATCHING)) && config.enableViewDistanceAnomalies && random.nextBoolean() && clientDistortionManager.forceViewDistanceDrop(player, currentTick)) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.WATCHING)) && config.enableMonochromeAnomalies && clientDistortionManager.forceMonochrome(player, currentTick)) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.WATCHING)) && config.enableAnimalStareEvents && config.enableAnimalStareEvent && worldAnomalyManager.forceAnimalStare(player, currentTick)) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.WATCHING)) && config.enableHandDropAnomaly && random.nextBoolean()
                && (forced ? horrorEventManager.triggerHandDrop(player) : horrorEventManager.triggerNaturalHandDrop(player))) {
            return true;
        }

        if ((forced || phase.isAtLeast(HorrorPhase.WATCHING)) && config.enableInventoryOpenAnomaly
                && (forced ? horrorEventManager.triggerInventoryOpen(player) : horrorEventManager.triggerNaturalInventoryOpen(player))) {
            return true;
        }

        scheduleQuietReceiver(player, "异常链路没有中断，只是换了位置。", 60);
        return true;
    }

    private boolean triggerMajor(ServerPlayerEntity player, PlayerContextSnapshot snapshot, long currentTick, boolean forced) {
        HorrorPhase phase = progressionManager.getPhase(player);
        ItConfig config = ItConfigManager.getConfig();
        if (!forced && !phase.isAtLeast(HorrorPhase.INTRUSION)) {
            return false;
        }

        if (!forced && isStrongEventActive(player, currentTick)) {
            return false;
        }

        if (snapshot.has(PlayerContext.SKYBORNE) && skyborneHorrorManager.triggerSkyborneEvent(player, snapshot, currentTick, forced)) {
            return true;
        }

        if (snapshot.has(PlayerContext.UNDERGROUND) && phase == HorrorPhase.MANIFESTATION && caveStalkerManager.triggerCaveStalker(player, currentTick, forced)) {
            return true;
        }

        if (phase == HorrorPhase.MANIFESTATION) {
            if (random.nextBoolean() && chaseManager.triggerChase(player, currentTick, forced)) {
                return true;
            }
            if (manifestationManager.triggerInterference(player, currentTick, forced)) {
                return true;
            }
            if (manifestationManager.triggerFaceScare(player, currentTick, forced)) {
                return true;
            }
            return jumpscareManager.triggerJumpscare(player, currentTick, forced);
        }

        if (phase.isAtLeast(HorrorPhase.INTRUSION)) {
            if (snapshot.has(PlayerContext.HOME_OR_BASE) && triggerBaseEvent(player, forced)) {
                return true;
            }
            if (config.enableMimicPlayerEvent && multiplayerDreadManager.triggerMimicPlayer(player, forced)) {
                return true;
            }
            if (config.enableFakeTabListEvent && multiplayerDreadManager.triggerFakeTab(player, forced)) {
                return true;
            }
            if (phase.isAtLeast(phaseFromNumber(config.animalDisguiseMinPhase)) && config.enableAnimalDisguiseEvent && config.animalDisguiseEnabled && animalDisguiseManager.triggerAnimalDisguise(player, forced)) {
                return true;
            }
            return manifestationManager.triggerFakePlayerVisit(player, currentTick, forced);
        }

        return triggerNoticeable(player, snapshot, currentTick, forced);
    }

    private boolean triggerGroupEvent(ServerPlayerEntity player, PlayerContextSnapshot snapshot, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        HorrorPhase phase = progressionManager.getPhase(player);
        if (!forced && !phase.isAtLeast(HorrorPhase.WATCHING)) {
            return false;
        }

        boolean triggered;
        if ((forced || phase.isAtLeast(HorrorPhase.INTRUSION)) && snapshot.nearbyPlayers() >= 2 && config.enableFakeTabListEvent && random.nextBoolean()) {
            triggered = multiplayerDreadManager.triggerFakeTab(player, forced);
        } else if ((forced || phase.isAtLeast(HorrorPhase.IMITATING)) && snapshot.nearbyPlayers() >= 2 && config.enableTeamSynchronizedGaze) {
            triggered = random.nextBoolean()
                    ? multiplayerDreadManager.triggerTeamGaze(player, forced)
                    : multiplayerDreadManager.triggerFakeAdvancement(player, forced);
        } else {
            triggered = random.nextBoolean()
                    ? multiplayerDreadManager.triggerFakeTeammateFootsteps(player, forced)
                    : multiplayerDreadManager.triggerSeparationWarning(player, forced);
        }

        if (triggered) {
            groupDreadManager.recordGroupEvent(player);
        }
        return triggered;
    }

    private boolean triggerNetherEvent(ServerPlayerEntity player, PlayerContextSnapshot snapshot, boolean forced) {
        if (!NetherSignalManager.isInNether(player)) {
            return false;
        }

        if (snapshot.has(PlayerContext.FAST_TRAVELING) && (forced || progressionManager.getPhase(player).isAtLeast(HorrorPhase.IMITATING)) && netherSignalManager.triggerPortalAnomaly(player, forced)) {
            return true;
        }

        int roll = random.nextInt(4);
        return switch (roll) {
            case 0 -> netherSignalManager.triggerReceiverSignal(player, forced);
            case 1 -> netherSignalManager.triggerPhantomGhastCry(player, forced);
            case 2 -> netherSignalManager.triggerSoulSandWhisper(player, forced);
            default -> netherSignalManager.triggerPortalAnomaly(player, forced);
        };
    }

    private boolean triggerBaseEvent(ServerPlayerEntity player, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!forced && !progressionManager.getPhase(player).isAtLeast(HorrorPhase.INTRUSION)) {
            return false;
        }

        if (!config.baseIntrusionEnabled || !config.enableBaseAnomalies) {
            return false;
        }

        boolean triggered = worldAnomalyManager.forceBaseAnomaly(player);
        if (triggered) {
            progressionManager.getData(player).baseIntrusionEvents++;
        }
        return triggered;
    }

    private boolean triggerAfkRelease(ServerPlayerEntity player, long currentTick) {
        PlayerHorrorData data = progressionManager.getData(player);
        data.afkReleasedEvents++;
        if (horrorEventManager.triggerCaveSound(player)) {
            receiverManager.scheduleMessage(player, ReceiverMessageType.OBSERVATION, progressionManager.getPhase(player), "记录恢复。你刚才没有一直站在那里。", 60);
            return true;
        }

        scheduleQuietReceiver(player, "记录恢复。你刚才没有一直站在那里。", 20);
        return true;
    }

    private void scheduleQuietReceiver(ServerPlayerEntity player, String text, int delayTicks) {
        receiverManager.scheduleMessage(player, ReceiverMessageType.OBSERVATION, progressionManager.getPhase(player), text, delayTicks);
    }

    private void record(ServerPlayerEntity player, DirectorState state, DirectorEventType eventType, PlayerContextSnapshot snapshot, long currentTick) {
        state.lastEventType = eventType;
        state.lastEventContext = snapshot.describe();
        state.history.addLast(eventType.name() + "@" + state.lastEventContext);
        while (state.history.size() > 8) {
            state.history.removeFirst();
        }

        if (eventType == DirectorEventType.MINOR) {
            state.lastMinorTick = currentTick;
        } else if (eventType == DirectorEventType.NOTICEABLE) {
            state.lastMinorTick = currentTick;
            state.lastNoticeableTick = currentTick;
        } else if (eventType == DirectorEventType.MAJOR) {
            state.lastMinorTick = currentTick;
            state.lastNoticeableTick = currentTick;
            state.lastMajorTick = currentTick;
        }

        PlayerHorrorData data = progressionManager.getData(player);
        if (data.avoidanceScore >= ItConfigManager.getConfig().avoidanceThresholdMedium) {
            data.avoidanceTriggeredEvents++;
        }
    }

    private DirectorState getState(ServerPlayerEntity player, long currentTick) {
        return states.computeIfAbsent(player.getUuid(), uuid -> new DirectorState(currentTick));
    }

    private boolean isStrongEventActive(ServerPlayerEntity player, long currentTick) {
        return chaseManager.isChasing(player)
                || caveStalkerManager.isActive(player)
                || ItNetwork.isJumpscareActive(player, currentTick)
                || ItNetwork.isFaceScareActive(player, currentTick)
                || ItNetwork.isManifestationOverlayActive(player, currentTick)
                || ItNetwork.isAnimalDisguiseRetaliationActive(player, currentTick);
    }

    private double pressureMultiplier(double avoidance, double groupDread) {
        return 1.0D + Math.min(1.25D, avoidance / 100.0D) + Math.min(0.65D, groupDread / 160.0D);
    }

    private long secondsToTicks(int seconds) {
        return (long) Math.max(0, seconds) * TICKS_PER_SECOND;
    }

    private HorrorPhase phaseFromNumber(int phaseNumber) {
        return HorrorPhase.fromNumber(Math.max(1, Math.min(5, phaseNumber)));
    }

    private String formatTicks(long ticks) {
        return Math.max(0L, ticks) / TICKS_PER_SECOND + "s";
    }

    private enum DirectorEventType {
        NONE,
        MINOR,
        NOTICEABLE,
        MAJOR
    }

    private static final class DirectorState {
        private long lastMinorTick;
        private long lastNoticeableTick;
        private long lastMajorTick;
        private DirectorEventType lastEventType = DirectorEventType.NONE;
        private String lastEventContext = "NONE";
        private final Deque<String> history = new ArrayDeque<>();

        private DirectorState(long currentTick) {
            this.lastMinorTick = currentTick;
            this.lastNoticeableTick = currentTick;
            this.lastMajorTick = currentTick;
        }
    }
}
