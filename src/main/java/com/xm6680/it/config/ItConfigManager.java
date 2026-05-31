package com.xm6680.it.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xm6680.it.ItMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and saves the small JSON config file used by the mod.
 */
public final class ItConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(ItMod.MOD_ID + ".json");

    private static ItConfig config = new ItConfig();

    private ItConfigManager() {
    }

    public static void load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());

            if (Files.notExists(CONFIG_PATH)) {
                config = new ItConfig();
                save();
                return;
            }

            String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
            ItConfig loadedConfig = GSON.fromJson(json, ItConfig.class);
            config = loadedConfig == null ? new ItConfig() : loadedConfig;
            validate();
            save();
        } catch (IOException exception) {
            ItMod.LOGGER.error("Failed to load config. Using defaults.", exception);
            config = new ItConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            ItMod.LOGGER.error("Failed to save config.", exception);
        }
    }

    public static ItConfig getConfig() {
        return config;
    }

    private static void validate() {
        if (config.maxWatchingLevel < 1) {
            config.maxWatchingLevel = 100;
        }

        if (config.phaseProgressionCheckIntervalTicks < 20) {
            config.phaseProgressionCheckIntervalTicks = 600;
        }

        if (config.horrorDirectorIntervalTicks < 20) {
            config.horrorDirectorIntervalTicks = 200;
        }

        if (config.minorEventFailsafeSeconds < 30) {
            config.minorEventFailsafeSeconds = 240;
        }

        if (config.noticeableEventFailsafeSeconds < config.minorEventFailsafeSeconds) {
            config.noticeableEventFailsafeSeconds = Math.max(420, config.minorEventFailsafeSeconds);
        }

        if (config.majorEventFailsafeSeconds < config.noticeableEventFailsafeSeconds) {
            config.majorEventFailsafeSeconds = Math.max(780, config.noticeableEventFailsafeSeconds);
        }

        config.noticeableEventChanceMultiplier = nonNegative(config.noticeableEventChanceMultiplier, 1.0D);
        config.majorEventChanceMultiplier = nonNegative(config.majorEventChanceMultiplier, 1.0D);

        if (config.horrorDirectorTestMode || config.horrorTestModeAcceleratedProgression) {
            config.passivePhase2Seconds = Math.min(config.passivePhase2Seconds, 120);
            config.passivePhase3Seconds = Math.min(config.passivePhase3Seconds, 300);
            config.passivePhase4Seconds = Math.min(config.passivePhase4Seconds, 600);
            config.passivePhase5Seconds = Math.min(config.passivePhase5Seconds, 960);
            config.minorEventFailsafeSeconds = Math.min(config.minorEventFailsafeSeconds, 150);
            config.noticeableEventFailsafeSeconds = Math.min(config.noticeableEventFailsafeSeconds, 300);
            config.majorEventFailsafeSeconds = Math.min(config.majorEventFailsafeSeconds, 600);
        }

        if (config.passivePhase2Seconds < 60) {
            config.passivePhase2Seconds = config.horrorTestModeAcceleratedProgression ? 120 : 600;
        }

        if (config.passivePhase3Seconds < config.passivePhase2Seconds) {
            config.passivePhase3Seconds = Math.max(1500, config.passivePhase2Seconds);
        }

        if (config.passivePhase4Seconds < config.passivePhase3Seconds) {
            config.passivePhase4Seconds = Math.max(2700, config.passivePhase3Seconds);
        }

        if (config.passivePhase5Seconds < config.passivePhase4Seconds) {
            config.passivePhase5Seconds = Math.max(4800, config.passivePhase4Seconds);
        }

        if (config.phase2MinPlayTimeSeconds == 720 || config.phase2MinPlayTimeSeconds == 600) {
            config.phase2MinPlayTimeSeconds = 420;
        }

        if (config.phase3MinTimeInPhaseSeconds == 900
                || config.phase3MinTimeInPhaseSeconds == 720
                || config.phase3MinTimeInPhaseSeconds == 600) {
            config.phase3MinTimeInPhaseSeconds = 480;
        }

        if (config.phase4MinTimeInPhaseSeconds == 1200) {
            config.phase4MinTimeInPhaseSeconds = 900;
        }

        if (config.phase4MinTimeInPhaseSeconds == 900) {
            config.phase4MinTimeInPhaseSeconds = 840;
        }

        if (config.phase5MinTimeInPhaseSeconds == 1800) {
            config.phase5MinTimeInPhaseSeconds = 1200;
        }

        if (config.phase5MinTimeInPhaseSeconds == 1200) {
            config.phase5MinTimeInPhaseSeconds = 1080;
        }

        if (config.phase2MinPlayTimeSeconds < 240) {
            config.phase2MinPlayTimeSeconds = 420;
        }

        if (config.phase3MinTimeInPhaseSeconds < 300) {
            config.phase3MinTimeInPhaseSeconds = 480;
        }

        if (config.phase4MinTimeInPhaseSeconds < 480) {
            config.phase4MinTimeInPhaseSeconds = 840;
        }

        if (config.phase5MinTimeInPhaseSeconds < 600) {
            config.phase5MinTimeInPhaseSeconds = 1080;
        }

        if (config.phase2RequiredReceiverMessages == 2) {
            config.phase2RequiredReceiverMessages = 1;
        }

        if (config.phase2RequiredReceiverMessages < 1) {
            config.phase2RequiredReceiverMessages = 1;
        }

        if (config.phase2RequiredReceiverOpens < 1) {
            config.phase2RequiredReceiverOpens = 1;
        }

        if (config.phase3RequiredReceiverMessages == 4) {
            config.phase3RequiredReceiverMessages = 3;
        }

        if (config.phase3RequiredReceiverOpens == 3 || config.phase3RequiredReceiverOpens == 6) {
            config.phase3RequiredReceiverOpens = 2;
        }

        if (config.phase4RequiredReceiverMessages == 5 || config.phase4RequiredReceiverMessages == 6) {
            config.phase4RequiredReceiverMessages = 8;
        }

        if (config.phase4RequiredReceiverOpens == 4 || config.phase4RequiredReceiverOpens == 10) {
            config.phase4RequiredReceiverOpens = 6;
        }

        if (config.phase4RequiredFakeChatsOrWatcherSightings < 2) {
            config.phase4RequiredFakeChatsOrWatcherSightings = 2;
        }

        if (config.phase5RequiredReceiverMessages == 8 || config.phase5RequiredReceiverMessages == 10 || config.phase5RequiredReceiverMessages == 14) {
            config.phase5RequiredReceiverMessages = 12;
        }

        if (config.phase5RequiredReceiverOpens == 6 || config.phase5RequiredReceiverOpens == 10 || config.phase5RequiredReceiverOpens == 18) {
            config.phase5RequiredReceiverOpens = 9;
        }

        if (config.phase5RequiredWatcherSightings == 3) {
            config.phase5RequiredWatcherSightings = 2;
        }

        if (config.phase5RequiredWatcherSightings == 2) {
            config.phase5RequiredWatcherSightings = 1;
        }

        if (config.phase3RequiredReceiverMessages < 1) {
            config.phase3RequiredReceiverMessages = 3;
        }

        if (config.phase3RequiredReceiverOpens < 1) {
            config.phase3RequiredReceiverOpens = 2;
        }

        if (config.phase3RequiredSmallEventScore == 2) {
            config.phase3RequiredSmallEventScore = 1;
        }

        if (config.phase3RequiredSmallEventScore < 1) {
            config.phase3RequiredSmallEventScore = 1;
        }

        if (config.phase4RequiredReceiverMessages < 3) {
            config.phase4RequiredReceiverMessages = 8;
        }

        if (config.phase4RequiredReceiverOpens < 2) {
            config.phase4RequiredReceiverOpens = 6;
        }

        if (config.phase4RequiredEventScore < 1) {
            config.phase4RequiredEventScore = 5;
        }

        if (config.phase5RequiredReceiverMessages < 5) {
            config.phase5RequiredReceiverMessages = 12;
        }

        if (config.phase5RequiredReceiverOpens < 3) {
            config.phase5RequiredReceiverOpens = 9;
        }

        if (config.phase5RequiredWatcherSightings < 1) {
            config.phase5RequiredWatcherSightings = 1;
        }

        if (config.phase5RequiredEventScore < 1) {
            config.phase5RequiredEventScore = 10;
        }

        if (config.receiverPhaseCooldownTicks < 0) {
            config.receiverPhaseCooldownTicks = 400;
        }

        if (config.receiverUnreadSoftThreshold < 1) {
            config.receiverUnreadSoftThreshold = 1;
        }

        if (config.receiverUnreadMediumThreshold < config.receiverUnreadSoftThreshold) {
            config.receiverUnreadMediumThreshold = Math.max(3, config.receiverUnreadSoftThreshold);
        }

        if (config.receiverUnreadHighThreshold < config.receiverUnreadMediumThreshold) {
            config.receiverUnreadHighThreshold = Math.max(5, config.receiverUnreadMediumThreshold);
        }

        if (config.maxReceiverMessages < 1) {
            config.maxReceiverMessages = 20;
        }

        if (config.receiverEventMessageMinDelayTicks < 0) {
            config.receiverEventMessageMinDelayTicks = 40;
        }

        if (config.receiverEventMessageMaxDelayTicks < config.receiverEventMessageMinDelayTicks) {
            config.receiverEventMessageMaxDelayTicks = Math.max(120, config.receiverEventMessageMinDelayTicks);
        }

        config.rareReceiverRecordChance = clamp01(config.rareReceiverRecordChance, 0.20D);
        config.sampledReceiverRecordChance = clamp01(config.sampledReceiverRecordChance, 0.40D);
        config.importantReceiverRecordChance = clamp01(config.importantReceiverRecordChance, 0.80D);
        config.receiverRecordPhaseBonus = clamp01(config.receiverRecordPhaseBonus, 0.05D);
        if (config.minorAnomalyAggregationThreshold < 1) {
            config.minorAnomalyAggregationThreshold = 3;
        }
        if (config.minorAnomalyAggregationCooldownSeconds < 0) {
            config.minorAnomalyAggregationCooldownSeconds = 360;
        }
        config.minorAnomalyAggregationMinPhase = clampPhase(config.minorAnomalyAggregationMinPhase, 2);

        if (config.mysteriousContactMinPhase < 1) {
            config.mysteriousContactMinPhase = 3;
        }

        if (config.mysteriousContactMinPhase > 5) {
            config.mysteriousContactMinPhase = 5;
        }

        config.mysteriousContactHelpfulChance = clamp01(config.mysteriousContactHelpfulChance, 0.25D);

        if (config.mysteriousContactCooldownSeconds < 60) {
            config.mysteriousContactCooldownSeconds = 360;
        }

        if (config.handDropMinPhase < 1) {
            config.handDropMinPhase = 2;
        }

        if (config.handDropMinPhase > 5) {
            config.handDropMinPhase = 5;
        }

        if (config.handDropChanceMultiplier < 0.0D) {
            config.handDropChanceMultiplier = 0.0D;
        }

        if (config.handDropCooldownSeconds < 60) {
            config.handDropCooldownSeconds = 420;
        }

        config.handDropDropWholeStackChance = clamp01(config.handDropDropWholeStackChance, 0.05D);

        if (config.handDropReceiverDelayTicks < 0) {
            config.handDropReceiverDelayTicks = 60;
        }

        if (config.inventoryOpenMinPhase < 1) {
            config.inventoryOpenMinPhase = 2;
        }

        if (config.inventoryOpenMinPhase > 5) {
            config.inventoryOpenMinPhase = 5;
        }

        if (config.inventoryOpenChanceMultiplier < 0.0D) {
            config.inventoryOpenChanceMultiplier = 0.0D;
        }

        if (config.inventoryOpenCooldownSeconds < 60) {
            config.inventoryOpenCooldownSeconds = 600;
        }

        if (config.inventoryOpenReceiverDelayTicks < 0) {
            config.inventoryOpenReceiverDelayTicks = 80;
        }

        if (config.eerieSoundMinPhase < 1) {
            config.eerieSoundMinPhase = 2;
        }

        if (config.eerieSoundMinPhase > 5) {
            config.eerieSoundMinPhase = 5;
        }

        if (config.eerieSoundChanceMultiplier < 0.0D) {
            config.eerieSoundChanceMultiplier = 0.0D;
        }

        if (config.eerieSoundCooldownSeconds < 60) {
            config.eerieSoundCooldownSeconds = 300;
        }

        if (config.eerieSoundVolume < 0.0F) {
            config.eerieSoundVolume = 0.0F;
        }

        if (config.eerieSoundVolume > 2.0F) {
            config.eerieSoundVolume = 2.0F;
        }

        if (config.eerieSoundReceiverDelayTicks < 0) {
            config.eerieSoundReceiverDelayTicks = 80;
        }

        if (config.eerieSoundLaughWeight < 0.0D) {
            config.eerieSoundLaughWeight = 0.0D;
        }

        config.familiarSoundMinPhase = clampPhase(config.familiarSoundMinPhase, 2);
        if (config.familiarSoundChanceMultiplier < 0.0D) {
            config.familiarSoundChanceMultiplier = 0.0D;
        }
        if (config.familiarSoundCooldownSeconds < 60) {
            config.familiarSoundCooldownSeconds = 260;
        }
        if (config.fakeChestSoundRadius < 2.0D) {
            config.fakeChestSoundRadius = 12.0D;
        }
        if (config.fakeChestSoundCooldownSeconds < 60) {
            config.fakeChestSoundCooldownSeconds = 420;
        }
        if (config.fakeBlockPlaceSoundCooldownSeconds < 60) {
            config.fakeBlockPlaceSoundCooldownSeconds = 300;
        }
        if (config.fakeEatingSoundCooldownSeconds < 60) {
            config.fakeEatingSoundCooldownSeconds = 300;
        }
        config.fakeEatingSoundBurpChance = clamp01(config.fakeEatingSoundBurpChance, 0.12D);
        if (config.familiarSoundReceiverDelayTicks < 0) {
            config.familiarSoundReceiverDelayTicks = 80;
        }

        config.separationWarningMinPhase = clampPhase(config.separationWarningMinPhase, 2);
        if (config.separationWarningDistance < 8.0D) {
            config.separationWarningDistance = 48.0D;
        }
        if (config.separationWarningMinTicks < 20) {
            config.separationWarningMinTicks = 600;
        }
        if (config.separationWarningCooldownSeconds < 60) {
            config.separationWarningCooldownSeconds = 420;
        }
        config.separationWarningNotifyTeammatesChance = clamp01(config.separationWarningNotifyTeammatesChance, 0.15D);

        config.fakeTeammateFootstepsMinPhase = clampPhase(config.fakeTeammateFootstepsMinPhase, 2);
        if (config.fakeTeammateFootstepsCooldownSeconds < 60) {
            config.fakeTeammateFootstepsCooldownSeconds = 360;
        }
        if (config.fakeTeammateFootstepsStepCountMin < 1) {
            config.fakeTeammateFootstepsStepCountMin = 3;
        }
        if (config.fakeTeammateFootstepsStepCountMax < config.fakeTeammateFootstepsStepCountMin) {
            config.fakeTeammateFootstepsStepCountMax = Math.max(6, config.fakeTeammateFootstepsStepCountMin);
        }
        if (config.fakeTeammateFootstepsReceiverDelayTicks < 0) {
            config.fakeTeammateFootstepsReceiverDelayTicks = 80;
        }

        config.teamGazeMinPhase = clampPhase(config.teamGazeMinPhase, 3);
        if (config.teamGazePlayerGroupRadius < 4.0D) {
            config.teamGazePlayerGroupRadius = 24.0D;
        }
        if (config.teamGazeEntityRadius < 4.0D) {
            config.teamGazeEntityRadius = 24.0D;
        }
        if (config.teamGazeMinEntities < 1) {
            config.teamGazeMinEntities = 4;
        }
        if (config.teamGazeMaxEntities < config.teamGazeMinEntities) {
            config.teamGazeMaxEntities = Math.max(12, config.teamGazeMinEntities);
        }
        if (config.teamGazeDurationTicks < 20) {
            config.teamGazeDurationTicks = 200;
        }
        if (config.teamGazeCooldownSeconds < 60) {
            config.teamGazeCooldownSeconds = 600;
        }
        if (config.teamGazeReceiverDelayTicks < 0) {
            config.teamGazeReceiverDelayTicks = 80;
        }

        config.fakeTabListMinPhase = clampPhase(config.fakeTabListMinPhase, 4);
        if (config.fakeTabListDurationTicks < 20) {
            config.fakeTabListDurationTicks = 80;
        }
        if (config.fakeTabListCooldownSeconds < 60) {
            config.fakeTabListCooldownSeconds = 900;
        }
        config.fakeTabListUseObfuscatedNameChance = clamp01(config.fakeTabListUseObfuscatedNameChance, 0.35D);

        config.fakeAdvancementMinPhase = clampPhase(config.fakeAdvancementMinPhase, 3);
        if (config.fakeAdvancementCooldownSeconds < 60) {
            config.fakeAdvancementCooldownSeconds = 720;
        }

        config.fakeRescueMinPhase = clampPhase(config.fakeRescueMinPhase, 3);
        config.fakeRescueHelpfulChance = clamp01(config.fakeRescueHelpfulChance, 0.35D);
        if (config.fakeRescueCooldownSeconds < 60) {
            config.fakeRescueCooldownSeconds = 360;
        }

        config.mimicPlayerMinPhase = clampPhase(config.mimicPlayerMinPhase, 4);
        if (config.mimicPlayerMinPhase < 4) {
            config.mimicPlayerMinPhase = 4;
        }
        if (config.mimicPlayerChanceMultiplier < 0.0D) {
            config.mimicPlayerChanceMultiplier = 0.0D;
        }
        if (config.mimicPlayerCooldownSeconds < 60) {
            config.mimicPlayerCooldownSeconds = 900;
        }
        if (config.mimicPlayerDurationTicks < 60) {
            config.mimicPlayerDurationTicks = 320;
        }
        if (config.mimicPlayerSpawnMinDistance < 4.0D) {
            config.mimicPlayerSpawnMinDistance = 10.0D;
        }
        if (config.mimicPlayerSpawnMaxDistance < config.mimicPlayerSpawnMinDistance + 2.0D) {
            config.mimicPlayerSpawnMaxDistance = config.mimicPlayerSpawnMinDistance + 12.0D;
        }
        if (config.mimicPlayerTriggerDistance < 1.0D) {
            config.mimicPlayerTriggerDistance = 4.5D;
        }
        config.mimicPlayerWatcherTransformChance = clamp01(config.mimicPlayerWatcherTransformChance, 0.70D);
        if (config.mimicPlayerWatcherLungeTicks < 20) {
            config.mimicPlayerWatcherLungeTicks = 70;
        }
        if (config.mimicPlayerWatcherLungeDelayTicks < 0) {
            config.mimicPlayerWatcherLungeDelayTicks = 4;
        }

        config.forcedChatMinPhase = clampPhase(config.forcedChatMinPhase, 3);
        if (config.forcedChatChanceMultiplier < 0.0D) {
            config.forcedChatChanceMultiplier = 0.0D;
        }
        if (config.forcedChatCooldownSeconds < 60) {
            config.forcedChatCooldownSeconds = 720;
        }
        if (config.forcedChatReceiverDelayTicks < 0) {
            config.forcedChatReceiverDelayTicks = 80;
        }

        config.animalDisguiseMinPhase = clampPhase(config.animalDisguiseMinPhase, 3);
        if (config.animalDisguiseCooldownSeconds < 60) {
            config.animalDisguiseCooldownSeconds = 900;
        }
        if (config.animalDisguiseSpawnChanceMultiplier < 0.0D) {
            config.animalDisguiseSpawnChanceMultiplier = 0.0D;
        }
        if (config.animalDisguiseSpawnMinDistance < 4.0D) {
            config.animalDisguiseSpawnMinDistance = 8.0D;
        }
        if (config.animalDisguiseSpawnMaxDistance < config.animalDisguiseSpawnMinDistance + 2.0D) {
            config.animalDisguiseSpawnMaxDistance = config.animalDisguiseSpawnMinDistance + 12.0D;
        }
        if (config.animalDisguiseAllowedAnimals == null || config.animalDisguiseAllowedAnimals.isBlank()) {
            config.animalDisguiseAllowedAnimals = "cow,sheep,pig,chicken";
        }
        if (config.animalDisguiseAttackRadius < 24.0D) {
            config.animalDisguiseAttackRadius = 24.0D;
        }
        if (config.animalDisguiseAttackDamage < 0.0F) {
            config.animalDisguiseAttackDamage = 0.0F;
        }
        if (config.animalDisguiseAttackDamage > 8.0F) {
            config.animalDisguiseAttackDamage = 8.0F;
        }
        if (config.animalDisguiseAttackCooldownTicks < 10) {
            config.animalDisguiseAttackCooldownTicks = 40;
        }
        if (config.animalDisguiseMaxVictims < 0) {
            config.animalDisguiseMaxVictims = 2;
        }
        if (config.animalDisguiseMaxVictims > 4) {
            config.animalDisguiseMaxVictims = 4;
        }
        if (config.animalDisguiseMaxAttacks < 3) {
            config.animalDisguiseMaxAttacks = 3;
        }
        if (config.animalDisguiseMaxAttacks > 12) {
            config.animalDisguiseMaxAttacks = 12;
        }
        if (config.animalDisguiseAggressionLimit < 1) {
            config.animalDisguiseAggressionLimit = config.animalDisguiseMaxAttacks;
        }
        if (config.animalDisguiseAggressionReceiverDelayTicks < 0) {
            config.animalDisguiseAggressionReceiverDelayTicks = 80;
        }
        if (config.animalDisguiseRetaliationDurationTicks < 40) {
            config.animalDisguiseRetaliationDurationTicks = 120;
        }
        if (config.animalDisguiseBlindnessSeconds < 1) {
            config.animalDisguiseBlindnessSeconds = 5;
        }
        if (config.animalDisguiseNoiseVolume < 0.0F) {
            config.animalDisguiseNoiseVolume = 0.0F;
        }
        if (config.animalDisguiseNoiseVolume > 2.0F) {
            config.animalDisguiseNoiseVolume = 2.0F;
        }
        if (config.animalDisguiseStaticIntensity < 0.0F) {
            config.animalDisguiseStaticIntensity = 0.0F;
        }
        if (config.animalDisguiseStaticIntensity > 1.0F) {
            config.animalDisguiseStaticIntensity = 1.0F;
        }
        if (config.animalDisguiseReceiverDelayTicks < 0) {
            config.animalDisguiseReceiverDelayTicks = 40;
        }
        if (config.animalDisguiseDespawnSeconds < 20) {
            config.animalDisguiseDespawnSeconds = 180;
        }

        if (config.caveFootstepChanceMultiplier < 0.0D) {
            config.caveFootstepChanceMultiplier = 0.0D;
        }

        if (config.miningEchoChanceMultiplier < 0.0D) {
            config.miningEchoChanceMultiplier = 0.0D;
        }

        if (config.phaseTwoFallbackFirstMinSeconds < 360) {
            config.phaseTwoFallbackFirstMinSeconds = 360;
        }

        if (config.phaseTwoFallbackFirstMaxSeconds < config.phaseTwoFallbackFirstMinSeconds) {
            config.phaseTwoFallbackFirstMaxSeconds = Math.max(600, config.phaseTwoFallbackFirstMinSeconds);
        }

        if (config.phaseTwoFallbackRepeatMinSeconds < 600) {
            config.phaseTwoFallbackRepeatMinSeconds = 600;
        }

        if (config.phaseTwoFallbackRepeatMaxSeconds < config.phaseTwoFallbackRepeatMinSeconds) {
            config.phaseTwoFallbackRepeatMaxSeconds = Math.max(1080, config.phaseTwoFallbackRepeatMinSeconds);
        }

        if (config.phaseFiveFlickerDurationTicks < 260) {
            config.phaseFiveFlickerDurationTicks = 260;
        }

        if (config.jumpscareOverlayDurationTicks < 180) {
            config.jumpscareOverlayDurationTicks = 180;
        }

        if (config.jumpscareDarknessDurationTicks < 180) {
            config.jumpscareDarknessDurationTicks = 180;
        }

        if (config.eventChanceMultiplier < 0.0) {
            config.eventChanceMultiplier = 0.0;
        }

        if (config.watcherSpawnChanceMultiplier < 0.0) {
            config.watcherSpawnChanceMultiplier = 0.0;
        }

        if (config.watcherPossibleLineOfSightWeight < 0.0D) {
            config.watcherPossibleLineOfSightWeight = 0.0D;
        }

        if (config.watcherPossibleLineOfSightWeight > 3.0D) {
            config.watcherPossibleLineOfSightWeight = 3.0D;
        }

        if (config.watcherFrontArcPreference < 0.0D) {
            config.watcherFrontArcPreference = 0.0D;
        }

        if (config.watcherFrontArcPreference > 1.0D) {
            config.watcherFrontArcPreference = 1.0D;
        }

        if (config.tunnelWatcherMinPhase < 3) {
            config.tunnelWatcherMinPhase = 3;
        }

        if (config.tunnelWatcherMinPhase > 5) {
            config.tunnelWatcherMinPhase = 5;
        }

        if (config.phase3TunnelWatcherMinDistance == 18.0D || config.phase3TunnelWatcherMinDistance == 28.0D) {
            config.phase3TunnelWatcherMinDistance = 36.0D;
        }

        if (config.phase3TunnelWatcherMaxDistance == 32.0D || config.phase3TunnelWatcherMaxDistance == 44.0D) {
            config.phase3TunnelWatcherMaxDistance = 56.0D;
        }

        if (config.phase4TunnelWatcherMinDistance == 12.0D) {
            config.phase4TunnelWatcherMinDistance = 16.0D;
        }

        if (config.phase4TunnelWatcherMaxDistance == 24.0D) {
            config.phase4TunnelWatcherMaxDistance = 30.0D;
        }

        config.phase3TunnelWatcherMinDistance = validatedWatcherDistance(config.phase3TunnelWatcherMinDistance, 36.0D);
        config.phase4TunnelWatcherMinDistance = validatedWatcherDistance(config.phase4TunnelWatcherMinDistance, 16.0D);
        config.phase5TunnelWatcherMinDistance = validatedWatcherDistance(config.phase5TunnelWatcherMinDistance, 8.0D);
        config.phase3TunnelWatcherMaxDistance = validatedWatcherMaxDistance(config.phase3TunnelWatcherMinDistance, config.phase3TunnelWatcherMaxDistance, 56.0D);
        config.phase4TunnelWatcherMaxDistance = validatedWatcherMaxDistance(config.phase4TunnelWatcherMinDistance, config.phase4TunnelWatcherMaxDistance, 30.0D);
        config.phase5TunnelWatcherMaxDistance = validatedWatcherMaxDistance(config.phase5TunnelWatcherMinDistance, config.phase5TunnelWatcherMaxDistance, 18.0D);

        if (config.tunnelWatcherSpawnChanceMultiplier < 0.0D) {
            config.tunnelWatcherSpawnChanceMultiplier = 0.0D;
        }

        if (config.tunnelWatcherMaxLightLevel < 0) {
            config.tunnelWatcherMaxLightLevel = 0;
        }

        if (config.tunnelWatcherMaxLightLevel > 15) {
            config.tunnelWatcherMaxLightLevel = 15;
        }

        if (config.tunnelWatcherCooldownSeconds < 0) {
            config.tunnelWatcherCooldownSeconds = 240;
        }

        if (config.tunnelWatcherMissCooldownMinSeconds < 0) {
            config.tunnelWatcherMissCooldownMinSeconds = 45;
        }

        if (config.tunnelWatcherMissCooldownMaxSeconds < config.tunnelWatcherMissCooldownMinSeconds) {
            config.tunnelWatcherMissCooldownMaxSeconds = Math.max(config.tunnelWatcherMissCooldownMinSeconds, 90);
        }

        if (config.receiverMessageChanceMultiplier < 0.0) {
            config.receiverMessageChanceMultiplier = 0.0;
        }

        if (config.jumpscareChanceMultiplier < 0.0) {
            config.jumpscareChanceMultiplier = 0.0;
        }

        if (config.faceScareChanceMultiplier < 0.0) {
            config.faceScareChanceMultiplier = 0.0;
        }

        if (config.faceScareCooldownSeconds < 60) {
            config.faceScareCooldownSeconds = 360;
        }

        if (config.faceScareDurationTicks < 20) {
            config.faceScareDurationTicks = 55;
        }

        if (config.chaseChanceMultiplier < 0.0) {
            config.chaseChanceMultiplier = 0.0;
        }

        if (config.chaseCooldownSeconds < 60) {
            config.chaseCooldownSeconds = 1200;
        }

        if (config.chaseWarningTicks < 20) {
            config.chaseWarningTicks = 160;
        }

        if (config.chaseDurationTicks < 200) {
            config.chaseDurationTicks = 1200;
        }

        if (config.chaseEntitySpeed < 0.2) {
            config.chaseEntitySpeed = 1.25;
        }

        if (config.chaseDamage < 0.0F) {
            config.chaseDamage = 0.0F;
        }

        if (config.chaseRequiresWatchingLevel < 0) {
            config.chaseRequiresWatchingLevel = 85;
        }

        if (config.chaseRequiresWatcherSightings == 3) {
            config.chaseRequiresWatcherSightings = 1;
        }

        if (config.chaseRequiresWatcherSightings < 0) {
            config.chaseRequiresWatcherSightings = 1;
        }

        if (config.chaseRequiresReceiverMessages < 0) {
            config.chaseRequiresReceiverMessages = 10;
        }

        if (config.chaseLightEscapeLevel < 0) {
            config.chaseLightEscapeLevel = 0;
        }

        if (config.chaseLightEscapeLevel > 15) {
            config.chaseLightEscapeLevel = 15;
        }

        if (config.chaseBrightLightEscapeTicks < 200) {
            config.chaseBrightLightEscapeTicks = 200;
        }

        if (config.chaseSafePlayerDistance < 1.0) {
            config.chaseSafePlayerDistance = 16.0;
        }

        if (config.chaseNearPlayerEscapeTicks < 260) {
            config.chaseNearPlayerEscapeTicks = 260;
        }

        if (config.chaseDistanceMessageIntervalTicks < 80) {
            config.chaseDistanceMessageIntervalTicks = 200;
        }

        if (config.chaseFootstepVolume < 0.0F) {
            config.chaseFootstepVolume = 0.0F;
        }

        if (config.chaseFaceScareDurationTicks < 20) {
            config.chaseFaceScareDurationTicks = 100;
        }

        if (config.caveStalkerMinPhase < 1) {
            config.caveStalkerMinPhase = 5;
        }

        if (config.caveStalkerMinPhase > 5) {
            config.caveStalkerMinPhase = 5;
        }

        if (config.caveStalkerCooldownSeconds < 60) {
            config.caveStalkerCooldownSeconds = 900;
        }

        if (config.caveStalkerChanceMultiplier < 0.0) {
            config.caveStalkerChanceMultiplier = 0.0;
        }

        if (config.caveStalkerWarningTicks < 20) {
            config.caveStalkerWarningTicks = 160;
        }

        if (config.caveStalkerWalkSpeed < 0.65F) {
            config.caveStalkerWalkSpeed = 0.72F;
        }

        if (config.caveStalkerMaxChaseTicks < 200) {
            config.caveStalkerMaxChaseTicks = 1200;
        }

        if (config.caveStalkerBlockedTicksToVanish < 20) {
            config.caveStalkerBlockedTicksToVanish = 60;
        }

        if (config.caveStalkerFreezeTicks < 20) {
            config.caveStalkerFreezeTicks = 100;
        }

        if (config.caveStalkerLookBackTimeoutTicks < 200) {
            config.caveStalkerLookBackTimeoutTicks = 200;
        }

        if (config.caveStalkerLookBackDelayTicks < 10) {
            config.caveStalkerLookBackDelayTicks = 30;
        }

        if (config.caveStalkerTurnBackTimeoutTicks < 40) {
            config.caveStalkerTurnBackTimeoutTicks = 160;
        }

        if (config.caveStalkerTriggerAngleDot < 0.1D) {
            config.caveStalkerTriggerAngleDot = 0.85D;
        }

        if (config.caveStalkerTriggerAngleDot > 0.99D) {
            config.caveStalkerTriggerAngleDot = 0.99D;
        }

        if (config.caveStalkerTriggerDistance < 1.0D) {
            config.caveStalkerTriggerDistance = 5.0D;
        }

        if (config.caveStalkerFaceScareDurationTicks < 20) {
            config.caveStalkerFaceScareDurationTicks = 100;
        }

        if (config.caveStalkerDamage < 0.0F) {
            config.caveStalkerDamage = 0.0F;
        }

        if (config.caveStalkerCatchDistance < 0.5D) {
            config.caveStalkerCatchDistance = 1.65D;
        }

        if (config.caveStalkerEscapeDistance < 8.0D) {
            config.caveStalkerEscapeDistance = 36.0D;
        }

        if (config.caveStalkerSafePlayerDistance < 1.0D) {
            config.caveStalkerSafePlayerDistance = 16.0D;
        }

        if (config.caveStalkerNearPlayerEscapeTicks < 40) {
            config.caveStalkerNearPlayerEscapeTicks = 160;
        }

        if (config.caveStalkerSpawnMinDistance < 8.0D) {
            config.caveStalkerSpawnMinDistance = 24.0D;
        }

        if (config.caveStalkerSpawnMaxDistance < config.caveStalkerSpawnMinDistance + 4.0D) {
            config.caveStalkerSpawnMaxDistance = config.caveStalkerSpawnMinDistance + 20.0D;
        }

        if (config.caveStalkerForcedSpawnFallbackMinDistance < 4.0D) {
            config.caveStalkerForcedSpawnFallbackMinDistance = 12.0D;
        }

        if (config.caveStalkerFootstepVolume < 0.0F) {
            config.caveStalkerFootstepVolume = 0.0F;
        }

        if (config.viewDistanceAnomalyChanceMultiplier < 0.0) {
            config.viewDistanceAnomalyChanceMultiplier = 0.0;
        }

        if (config.viewDistanceAnomalyCooldownSeconds < 60) {
            config.viewDistanceAnomalyCooldownSeconds = 480;
        }

        if (config.viewDistanceAnomalyDurationTicks < 40) {
            config.viewDistanceAnomalyDurationTicks = 220;
        }

        if (config.viewDistanceAnomalyChunks < 2) {
            config.viewDistanceAnomalyChunks = 2;
        }

        if (config.viewDistanceAnomalyChunks > 32) {
            config.viewDistanceAnomalyChunks = 32;
        }

        if (config.legacyTextureAnomalyChanceMultiplier < 0.0) {
            config.legacyTextureAnomalyChanceMultiplier = 0.0;
        }

        if (config.legacyTextureAnomalyCooldownSeconds < 60) {
            config.legacyTextureAnomalyCooldownSeconds = 600;
        }

        if (config.legacyTextureAnomalyDurationTicks < 40) {
            config.legacyTextureAnomalyDurationTicks = 240;
        }

        if (config.legacyTextureSnowIntensity < 0.0) {
            config.legacyTextureSnowIntensity = 0.0;
        }

        if (config.legacyTextureSnowIntensity > 1.0) {
            config.legacyTextureSnowIntensity = 1.0;
        }

        if (config.monochromeAnomalyChanceMultiplier < 0.0) {
            config.monochromeAnomalyChanceMultiplier = 0.0;
        }

        if (config.monochromeAnomalyCooldownSeconds < 60) {
            config.monochromeAnomalyCooldownSeconds = 540;
        }

        if (config.monochromeAnomalyDurationTicks < 40 || config.monochromeAnomalyDurationTicks > 400) {
            config.monochromeAnomalyDurationTicks = 200;
        }

        if (config.monochromeAnomalyIntensity < 0.0) {
            config.monochromeAnomalyIntensity = 0.0;
        }

        if (config.monochromeAnomalyIntensity > 1.0) {
            config.monochromeAnomalyIntensity = 1.0;
        }

        if (config.worldAnomalyChanceMultiplier < 0.0) {
            config.worldAnomalyChanceMultiplier = 0.0;
        }

        config.netherSignalMinPhase = clampPhase(config.netherSignalMinPhase, 2);
        if (config.netherSignalChanceMultiplier < 0.0D) {
            config.netherSignalChanceMultiplier = 0.0D;
        }
        if (config.netherReceiverSignalCooldownSeconds < 60) {
            config.netherReceiverSignalCooldownSeconds = 360;
        }
        if (config.netherPhantomGhastCooldownSeconds < 60) {
            config.netherPhantomGhastCooldownSeconds = 420;
        }
        if (config.netherSoulWhisperCooldownSeconds < 60) {
            config.netherSoulWhisperCooldownSeconds = 300;
        }
        if (config.netherPortalAnomalyCooldownSeconds < 60) {
            config.netherPortalAnomalyCooldownSeconds = 600;
        }
        if (config.netherSignalReceiverDelayTicks < 0) {
            config.netherSignalReceiverDelayTicks = 80;
        }
        if (Double.isNaN(config.netherPhantomGhastCheckRadius)
                || Double.isInfinite(config.netherPhantomGhastCheckRadius)
                || config.netherPhantomGhastCheckRadius < 1.0D) {
            config.netherPhantomGhastCheckRadius = 64.0D;
        }
        if (Double.isNaN(config.netherPortalSearchRadius)
                || Double.isInfinite(config.netherPortalSearchRadius)
                || config.netherPortalSearchRadius < 1.0D) {
            config.netherPortalSearchRadius = 10.0D;
        }
        config.netherPortalAnomalyMinPhase = clampPhase(config.netherPortalAnomalyMinPhase, 3);
        if (config.netherPortalDistortionDurationTicks < 0) {
            config.netherPortalDistortionDurationTicks = 60;
        }
        if (Float.isNaN(config.netherPortalDistortionIntensity)
                || Float.isInfinite(config.netherPortalDistortionIntensity)) {
            config.netherPortalDistortionIntensity = 0.35F;
        }
        if (config.netherPortalDistortionIntensity < 0.0F) {
            config.netherPortalDistortionIntensity = 0.0F;
        }
        if (config.netherPortalDistortionIntensity > 1.0F) {
            config.netherPortalDistortionIntensity = 1.0F;
        }

        config.skyborneAvoidancePerMinute = nonNegative(config.skyborneAvoidancePerMinute, 18.0D);
        config.mountedAvoidancePerMinute = nonNegative(config.mountedAvoidancePerMinute, 8.0D);
        config.groupedAvoidancePerMinute = nonNegative(config.groupedAvoidancePerMinute, 6.0D);
        config.ignoredReceiverAvoidancePerMinute = nonNegative(config.ignoredReceiverAvoidancePerMinute, 7.0D);
        config.fastTravelAvoidancePerMinute = nonNegative(config.fastTravelAvoidancePerMinute, 10.0D);
        config.safeBaseAvoidancePerMinute = nonNegative(config.safeBaseAvoidancePerMinute, 5.0D);
        config.avoidanceDecayPerMinute = nonNegative(config.avoidanceDecayPerMinute, 4.0D);
        config.avoidanceThresholdLight = nonNegative(config.avoidanceThresholdLight, 20.0D);
        if (config.avoidanceThresholdMedium < config.avoidanceThresholdLight) {
            config.avoidanceThresholdMedium = Math.max(50.0D, config.avoidanceThresholdLight);
        }
        if (config.avoidanceThresholdHigh < config.avoidanceThresholdMedium) {
            config.avoidanceThresholdHigh = Math.max(80.0D, config.avoidanceThresholdMedium);
        }

        if (config.groupRadius < 4.0D) {
            config.groupRadius = 24.0D;
        }
        config.groupDreadGainPerMinute = nonNegative(config.groupDreadGainPerMinute, 7.0D);
        if (config.groupEventCooldownSeconds < 60) {
            config.groupEventCooldownSeconds = 420;
        }

        if (config.skyborneMinY < 64.0D) {
            config.skyborneMinY = 120.0D;
        }
        if (config.skyborneHighY < config.skyborneMinY) {
            config.skyborneHighY = Math.max(180.0D, config.skyborneMinY);
        }
        if (config.skyborneExtremeY < config.skyborneHighY) {
            config.skyborneExtremeY = Math.max(260.0D, config.skyborneHighY);
        }
        if (config.skyborneMinAirborneSeconds < 5) {
            config.skyborneMinAirborneSeconds = 45;
        }
        if (config.skyborneEventCooldownSeconds < 60) {
            config.skyborneEventCooldownSeconds = 240;
        }
        config.skyborneSignalMultiplier = nonNegative(config.skyborneSignalMultiplier, 1.0D);

        if (config.baseEventCooldownSeconds < 60) {
            config.baseEventCooldownSeconds = 600;
        }

        if (config.animalStareMinAnimals < 1) {
            config.animalStareMinAnimals = 4;
        }

        if (config.animalStareRadius == 18.0D) {
            config.animalStareRadius = 48.0D;
        }

        if (config.animalStareRadius < 12.0D) {
            config.animalStareRadius = 48.0D;
        }

        if (config.animalStareHighChance == 0.75D) {
            config.animalStareHighChance = 0.35D;
        }

        config.animalStareHighChance = clamp01(config.animalStareHighChance, 0.35D);

        if (config.animalStareDurationTicks == 160) {
            config.animalStareDurationTicks = 320;
        }

        if (config.animalStareDurationTicks < 20) {
            config.animalStareDurationTicks = 320;
        }

        if (config.animalStareMaxAnimals == 8) {
            config.animalStareMaxAnimals = 10;
        }

        if (config.animalStareMaxAnimals < 1) {
            config.animalStareMaxAnimals = 10;
        }

        if (config.animalStareReceiverDelayTicks == 80) {
            config.animalStareReceiverDelayTicks = 40;
        }

        if (config.animalStareReceiverDelayTicks < 0) {
            config.animalStareReceiverDelayTicks = 40;
        }

        if (config.phaseFiveNoiseVolume < 0.0) {
            config.phaseFiveNoiseVolume = 0.0;
        }

        if (config.jumpscareSoundVolume < 0.0) {
            config.jumpscareSoundVolume = 0.0;
        }

        if (config.maxOverlayOpacity < 0.0) {
            config.maxOverlayOpacity = 0.0;
        }

        if (config.maxOverlayOpacity > 1.0) {
            config.maxOverlayOpacity = 1.0;
        }
    }

    private static double validatedWatcherDistance(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 6.0D) {
            return fallback;
        }

        return value;
    }

    private static double validatedWatcherMaxDistance(double minDistance, double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < minDistance) {
            return Math.max(fallback, minDistance + 2.0D);
        }

        return value;
    }

    private static double clamp01(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }

        if (value < 0.0D) {
            return 0.0D;
        }

        if (value > 1.0D) {
            return 1.0D;
        }

        return value;
    }

    private static double nonNegative(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }

        return Math.max(0.0D, value);
    }

    private static int clampPhase(int value, int fallback) {
        if (value < 1 || value > 5) {
            return fallback;
        }

        return value;
    }
}
