package com.xm6680.it.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xm6680.it.ItMod;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.analog.ReceiverMessage;
import com.xm6680.it.analog.ReceiverMessageType;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persists It's per-world runtime state to JSON files under the active save.
 */
public class ItPersistentDataManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_DIR = "it";
    private static final String PLAYER_DATA_FILE = "player_data.json";
    private static final String RECEIVER_MESSAGES_FILE = "receiver_messages.json";
    private static final int DATA_VERSION = 1;

    private final HorrorProgressionManager progressionManager;
    private final ReceiverManager receiverManager;
    private boolean loaded;

    public ItPersistentDataManager(HorrorProgressionManager progressionManager, ReceiverManager receiverManager) {
        this.progressionManager = progressionManager;
        this.receiverManager = receiverManager;
    }

    public void load(MinecraftServer server) {
        Path dataDir = getDataDir(server);
        loadPlayerData(dataDir.resolve(PLAYER_DATA_FILE), server.getTicks());
        loadReceiverMessages(dataDir.resolve(RECEIVER_MESSAGES_FILE));
        loaded = true;
    }

    public void save(MinecraftServer server) {
        if (!loaded) {
            return;
        }

        progressionManager.syncOnlineWatchingLevels(server);
        Path dataDir = getDataDir(server);
        try {
            Files.createDirectories(dataDir);
            savePlayerData(dataDir.resolve(PLAYER_DATA_FILE));
            saveReceiverMessages(dataDir.resolve(RECEIVER_MESSAGES_FILE));
        } catch (IOException exception) {
            ItMod.LOGGER.error("Failed to save It persistent data.", exception);
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    private void loadPlayerData(Path path, long currentTick) {
        if (Files.notExists(path)) {
            progressionManager.replaceData(Map.of());
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            PlayerDataFile file = GSON.fromJson(json, PlayerDataFile.class);
            Map<UUID, PlayerHorrorData> loadedData = new LinkedHashMap<>();
            if (file != null && file.players != null) {
                for (SavedPlayerData saved : file.players) {
                    PlayerHorrorData data = toPlayerData(saved, currentTick);
                    if (data != null) {
                        loadedData.put(data.playerId, data);
                    }
                }
            }
            progressionManager.replaceData(loadedData);
            ItMod.LOGGER.info("Loaded {} It player progression records.", loadedData.size());
        } catch (IOException | RuntimeException exception) {
            ItMod.LOGGER.error("Failed to load It player progression data.", exception);
            progressionManager.replaceData(Map.of());
        }
    }

    private void savePlayerData(Path path) throws IOException {
        PlayerDataFile file = new PlayerDataFile();
        file.version = DATA_VERSION;
        for (PlayerHorrorData data : progressionManager.snapshotData().values()) {
            file.players.add(fromPlayerData(data));
        }
        Files.writeString(path, GSON.toJson(file), StandardCharsets.UTF_8);
    }

    private void loadReceiverMessages(Path path) {
        if (Files.notExists(path)) {
            receiverManager.replaceMessages(Map.of());
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            ReceiverMessagesFile file = GSON.fromJson(json, ReceiverMessagesFile.class);
            Map<UUID, List<ReceiverMessage>> loadedMessages = new LinkedHashMap<>();
            if (file != null && file.players != null) {
                for (SavedReceiverMessages saved : file.players) {
                    UUID playerId = parseUuid(saved.playerId);
                    if (playerId == null || saved.messages == null) {
                        continue;
                    }

                    List<ReceiverMessage> messages = new ArrayList<>();
                    for (SavedReceiverMessage message : saved.messages) {
                        ReceiverMessage receiverMessage = toReceiverMessage(message);
                        if (receiverMessage != null) {
                            messages.add(receiverMessage);
                        }
                    }
                    loadedMessages.put(playerId, messages);
                }
            }
            receiverManager.replaceMessages(loadedMessages);
            ItMod.LOGGER.info("Loaded {} It receiver message records.", loadedMessages.size());
        } catch (IOException | RuntimeException exception) {
            ItMod.LOGGER.error("Failed to load It receiver messages.", exception);
            receiverManager.replaceMessages(Map.of());
        }
    }

    private void saveReceiverMessages(Path path) throws IOException {
        ReceiverMessagesFile file = new ReceiverMessagesFile();
        file.version = DATA_VERSION;
        for (Map.Entry<UUID, List<ReceiverMessage>> entry : receiverManager.snapshotMessages().entrySet()) {
            SavedReceiverMessages saved = new SavedReceiverMessages();
            saved.playerId = entry.getKey().toString();
            for (ReceiverMessage message : entry.getValue()) {
                saved.messages.add(fromReceiverMessage(message));
            }
            file.players.add(saved);
        }
        Files.writeString(path, GSON.toJson(file), StandardCharsets.UTF_8);
    }

    private Path getDataDir(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve(DATA_DIR);
    }

    private PlayerHorrorData toPlayerData(SavedPlayerData saved, long currentTick) {
        UUID playerId = parseUuid(saved.playerId);
        if (playerId == null) {
            return null;
        }

        PlayerHorrorData data = new PlayerHorrorData(playerId, currentTick);
        data.watchingLevel = saved.watchingLevel;
        data.currentPhase = parsePhase(saved.currentPhase, HorrorPhase.DORMANT);
        data.phaseEnteredTime = saved.phaseEnteredTime;
        data.lastPhaseAdvanceTime = saved.lastPhaseAdvanceTime;
        data.totalPlayTicks = saved.totalPlayTicks;
        data.caveFootstepEvents = saved.caveFootstepEvents;
        data.miningEchoEvents = saved.miningEchoEvents;
        data.fakeChatEvents = saved.fakeChatEvents;
        data.handDropAnomalies = saved.handDropAnomalies;
        data.inventoryOpenAnomalies = saved.inventoryOpenAnomalies;
        data.mysteriousContactMessages = saved.mysteriousContactMessages;
        data.eerieSoundEvents = saved.eerieSoundEvents;
        data.animalStareEvents = saved.animalStareEvents;
        data.familiarSoundEvents = saved.familiarSoundEvents;
        data.separationWarnings = saved.separationWarnings;
        data.fakeTeammateFootstepEvents = saved.fakeTeammateFootstepEvents;
        data.teamGazeEvents = saved.teamGazeEvents;
        data.fakeTabEvents = saved.fakeTabEvents;
        data.fakeAdvancementEvents = saved.fakeAdvancementEvents;
        data.fakeRescueMessages = saved.fakeRescueMessages;
        data.animalDisguiseEvents = saved.animalDisguiseEvents;
        data.animalDisguiseKilledEvents = saved.animalDisguiseKilledEvents;
        data.animalDisguiseAnimalAttacks = saved.animalDisguiseAnimalAttacks;
        data.animalDisguiseVictims = saved.animalDisguiseVictims;
        data.animalDisguiseRetaliations = saved.animalDisguiseRetaliations;
        data.watcherSightings = saved.watcherSightings;
        data.receiverMessagesReceived = saved.receiverMessagesReceived;
        data.receiverOpenedCount = saved.receiverOpenedCount;
        data.receiverFirstReceivedGameTime = saved.receiverFirstReceivedGameTime;
        data.lastReceiverMessageGameTime = saved.lastReceiverMessageGameTime;
        data.lastReceiverOpenedGameTime = saved.lastReceiverOpenedGameTime;
        data.lastReceiverOpenedMessageCount = saved.lastReceiverOpenedMessageCount;
        data.receiverUnreadPressureEvents = saved.receiverUnreadPressureEvents;
        data.nextReceiverFallbackGameTime = saved.nextReceiverFallbackGameTime;
        data.receiverPhaseCooldownUntilGameTime = saved.receiverPhaseCooldownUntilGameTime;
        data.receiverFallbackPhaseNumber = saved.receiverFallbackPhaseNumber;
        data.receiverFallbacksInPhase = saved.receiverFallbacksInPhase;
        data.avoidanceScore = saved.avoidanceScore;
        data.individualDread = saved.individualDread;
        data.groupDread = saved.groupDread;
        data.unreadSignalPressure = saved.unreadSignalPressure;
        data.airborneTicks = saved.airborneTicks;
        data.mountedTicks = saved.mountedTicks;
        data.groupedTicks = saved.groupedTicks;
        data.idleTicks = saved.idleTicks;
        data.fastTravelTicks = saved.fastTravelTicks;
        data.stableAreaTicks = saved.stableAreaTicks;
        data.safeBaseTicks = saved.safeBaseTicks;
        data.firstNetherEntryGameTime = saved.firstNetherEntryGameTime;
        data.lastDimensionChangeGameTime = saved.lastDimensionChangeGameTime;
        data.skyborneEvents = saved.skyborneEvents;
        data.avoidanceTriggeredEvents = saved.avoidanceTriggeredEvents;
        data.groupDreadEvents = saved.groupDreadEvents;
        data.baseIntrusionEvents = saved.baseIntrusionEvents;
        data.afkReleasedEvents = saved.afkReleasedEvents;
        data.netherRushEvents = saved.netherRushEvents;
        data.jumpscareEvents = saved.jumpscareEvents;
        data.phaseFiveInterferenceEvents = saved.phaseFiveInterferenceEvents;
        data.chaseEvents = saved.chaseEvents;
        data.chaseEscapes = saved.chaseEscapes;
        data.chaseCaughtEvents = saved.chaseCaughtEvents;
        data.lastChaseGameTime = saved.lastChaseGameTime;
        data.caveStalkerEvents = saved.caveStalkerEvents;
        data.caveStalkerEscapes = saved.caveStalkerEscapes;
        data.caveStalkerCaughtEvents = saved.caveStalkerCaughtEvents;
        data.caveStalkerTrapTriggers = saved.caveStalkerTrapTriggers;
        data.caveStalkerEasterEggs = saved.caveStalkerEasterEggs;
        data.lastCaveStalkerGameTime = saved.lastCaveStalkerGameTime;
        data.undergroundTicks = saved.undergroundTicks;
        data.darkAloneTicks = saved.darkAloneTicks;
        data.nightAloneTicks = saved.nightAloneTicks;
        data.hasReceivedReceiver = saved.hasReceivedReceiver;
        data.hasSeenWatcher = saved.hasSeenWatcher;
        data.hasTriggeredManifestation = saved.hasTriggeredManifestation;
        return data;
    }

    private SavedPlayerData fromPlayerData(PlayerHorrorData data) {
        SavedPlayerData saved = new SavedPlayerData();
        saved.playerId = data.playerId.toString();
        saved.watchingLevel = data.watchingLevel;
        saved.currentPhase = data.currentPhase.name();
        saved.phaseEnteredTime = data.phaseEnteredTime;
        saved.lastPhaseAdvanceTime = data.lastPhaseAdvanceTime;
        saved.totalPlayTicks = data.totalPlayTicks;
        saved.caveFootstepEvents = data.caveFootstepEvents;
        saved.miningEchoEvents = data.miningEchoEvents;
        saved.fakeChatEvents = data.fakeChatEvents;
        saved.handDropAnomalies = data.handDropAnomalies;
        saved.inventoryOpenAnomalies = data.inventoryOpenAnomalies;
        saved.mysteriousContactMessages = data.mysteriousContactMessages;
        saved.eerieSoundEvents = data.eerieSoundEvents;
        saved.animalStareEvents = data.animalStareEvents;
        saved.familiarSoundEvents = data.familiarSoundEvents;
        saved.separationWarnings = data.separationWarnings;
        saved.fakeTeammateFootstepEvents = data.fakeTeammateFootstepEvents;
        saved.teamGazeEvents = data.teamGazeEvents;
        saved.fakeTabEvents = data.fakeTabEvents;
        saved.fakeAdvancementEvents = data.fakeAdvancementEvents;
        saved.fakeRescueMessages = data.fakeRescueMessages;
        saved.animalDisguiseEvents = data.animalDisguiseEvents;
        saved.animalDisguiseKilledEvents = data.animalDisguiseKilledEvents;
        saved.animalDisguiseAnimalAttacks = data.animalDisguiseAnimalAttacks;
        saved.animalDisguiseVictims = data.animalDisguiseVictims;
        saved.animalDisguiseRetaliations = data.animalDisguiseRetaliations;
        saved.watcherSightings = data.watcherSightings;
        saved.receiverMessagesReceived = data.receiverMessagesReceived;
        saved.receiverOpenedCount = data.receiverOpenedCount;
        saved.receiverFirstReceivedGameTime = data.receiverFirstReceivedGameTime;
        saved.lastReceiverMessageGameTime = data.lastReceiverMessageGameTime;
        saved.lastReceiverOpenedGameTime = data.lastReceiverOpenedGameTime;
        saved.lastReceiverOpenedMessageCount = data.lastReceiverOpenedMessageCount;
        saved.receiverUnreadPressureEvents = data.receiverUnreadPressureEvents;
        saved.nextReceiverFallbackGameTime = data.nextReceiverFallbackGameTime;
        saved.receiverPhaseCooldownUntilGameTime = data.receiverPhaseCooldownUntilGameTime;
        saved.receiverFallbackPhaseNumber = data.receiverFallbackPhaseNumber;
        saved.receiverFallbacksInPhase = data.receiverFallbacksInPhase;
        saved.avoidanceScore = data.avoidanceScore;
        saved.individualDread = data.individualDread;
        saved.groupDread = data.groupDread;
        saved.unreadSignalPressure = data.unreadSignalPressure;
        saved.airborneTicks = data.airborneTicks;
        saved.mountedTicks = data.mountedTicks;
        saved.groupedTicks = data.groupedTicks;
        saved.idleTicks = data.idleTicks;
        saved.fastTravelTicks = data.fastTravelTicks;
        saved.stableAreaTicks = data.stableAreaTicks;
        saved.safeBaseTicks = data.safeBaseTicks;
        saved.firstNetherEntryGameTime = data.firstNetherEntryGameTime;
        saved.lastDimensionChangeGameTime = data.lastDimensionChangeGameTime;
        saved.skyborneEvents = data.skyborneEvents;
        saved.avoidanceTriggeredEvents = data.avoidanceTriggeredEvents;
        saved.groupDreadEvents = data.groupDreadEvents;
        saved.baseIntrusionEvents = data.baseIntrusionEvents;
        saved.afkReleasedEvents = data.afkReleasedEvents;
        saved.netherRushEvents = data.netherRushEvents;
        saved.jumpscareEvents = data.jumpscareEvents;
        saved.phaseFiveInterferenceEvents = data.phaseFiveInterferenceEvents;
        saved.chaseEvents = data.chaseEvents;
        saved.chaseEscapes = data.chaseEscapes;
        saved.chaseCaughtEvents = data.chaseCaughtEvents;
        saved.lastChaseGameTime = data.lastChaseGameTime;
        saved.caveStalkerEvents = data.caveStalkerEvents;
        saved.caveStalkerEscapes = data.caveStalkerEscapes;
        saved.caveStalkerCaughtEvents = data.caveStalkerCaughtEvents;
        saved.caveStalkerTrapTriggers = data.caveStalkerTrapTriggers;
        saved.caveStalkerEasterEggs = data.caveStalkerEasterEggs;
        saved.lastCaveStalkerGameTime = data.lastCaveStalkerGameTime;
        saved.undergroundTicks = data.undergroundTicks;
        saved.darkAloneTicks = data.darkAloneTicks;
        saved.nightAloneTicks = data.nightAloneTicks;
        saved.hasReceivedReceiver = data.hasReceivedReceiver;
        saved.hasSeenWatcher = data.hasSeenWatcher;
        saved.hasTriggeredManifestation = data.hasTriggeredManifestation;
        return saved;
    }

    private ReceiverMessage toReceiverMessage(SavedReceiverMessage saved) {
        ReceiverMessageType type = parseEnum(saved.type, ReceiverMessageType.class, ReceiverMessageType.OBSERVATION);
        HorrorPhase phase = parsePhase(saved.phase, type.getMinimumPhase());
        String text = saved.text == null ? "" : saved.text;
        return new ReceiverMessage(type, phase, saved.receivedEpochSecond, text);
    }

    private SavedReceiverMessage fromReceiverMessage(ReceiverMessage message) {
        SavedReceiverMessage saved = new SavedReceiverMessage();
        saved.type = message.type().name();
        saved.phase = message.phase().name();
        saved.receivedEpochSecond = message.receivedEpochSecond();
        saved.text = message.text();
        return saved;
    }

    private UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private HorrorPhase parsePhase(String value, HorrorPhase fallback) {
        return parseEnum(value, HorrorPhase.class, fallback);
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass, T fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private static final class PlayerDataFile {
        private int version;
        private List<SavedPlayerData> players = new ArrayList<>();
    }

    private static final class SavedPlayerData {
        private String playerId;
        private int watchingLevel;
        private String currentPhase;
        private long phaseEnteredTime;
        private long lastPhaseAdvanceTime;
        private long totalPlayTicks;
        private int caveFootstepEvents;
        private int miningEchoEvents;
        private int fakeChatEvents;
        private int handDropAnomalies;
        private int inventoryOpenAnomalies;
        private int mysteriousContactMessages;
        private int eerieSoundEvents;
        private int animalStareEvents;
        private int familiarSoundEvents;
        private int separationWarnings;
        private int fakeTeammateFootstepEvents;
        private int teamGazeEvents;
        private int fakeTabEvents;
        private int fakeAdvancementEvents;
        private int fakeRescueMessages;
        private int animalDisguiseEvents;
        private int animalDisguiseKilledEvents;
        private int animalDisguiseAnimalAttacks;
        private int animalDisguiseVictims;
        private int animalDisguiseRetaliations;
        private int watcherSightings;
        private int receiverMessagesReceived;
        private int receiverOpenedCount;
        private long receiverFirstReceivedGameTime;
        private long lastReceiverMessageGameTime;
        private long lastReceiverOpenedGameTime;
        private int lastReceiverOpenedMessageCount;
        private int receiverUnreadPressureEvents;
        private long nextReceiverFallbackGameTime;
        private long receiverPhaseCooldownUntilGameTime;
        private int receiverFallbackPhaseNumber;
        private int receiverFallbacksInPhase;
        private double avoidanceScore;
        private double individualDread;
        private double groupDread;
        private double unreadSignalPressure;
        private long airborneTicks;
        private long mountedTicks;
        private long groupedTicks;
        private long idleTicks;
        private long fastTravelTicks;
        private long stableAreaTicks;
        private long safeBaseTicks;
        private long firstNetherEntryGameTime;
        private long lastDimensionChangeGameTime;
        private int skyborneEvents;
        private int avoidanceTriggeredEvents;
        private int groupDreadEvents;
        private int baseIntrusionEvents;
        private int afkReleasedEvents;
        private int netherRushEvents;
        private int jumpscareEvents;
        private int phaseFiveInterferenceEvents;
        private int chaseEvents;
        private int chaseEscapes;
        private int chaseCaughtEvents;
        private long lastChaseGameTime;
        private int caveStalkerEvents;
        private int caveStalkerEscapes;
        private int caveStalkerCaughtEvents;
        private int caveStalkerTrapTriggers;
        private int caveStalkerEasterEggs;
        private long lastCaveStalkerGameTime;
        private int undergroundTicks;
        private int darkAloneTicks;
        private int nightAloneTicks;
        private boolean hasReceivedReceiver;
        private boolean hasSeenWatcher;
        private boolean hasTriggeredManifestation;
    }

    private static final class ReceiverMessagesFile {
        private int version;
        private List<SavedReceiverMessages> players = new ArrayList<>();
    }

    private static final class SavedReceiverMessages {
        private String playerId;
        private List<SavedReceiverMessage> messages = new ArrayList<>();
    }

    private static final class SavedReceiverMessage {
        private String type;
        private String phase;
        private long receivedEpochSecond;
        private String text;
    }
}
