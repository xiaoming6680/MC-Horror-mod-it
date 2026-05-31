package com.xm6680.it.event;

import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.analog.MinorAnomalyAccumulator;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.cavestalker.CaveStalkerManager;
import com.xm6680.it.chase.ChaseManager;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.effect.ClientDistortionManager;
import com.xm6680.it.jumpscare.JumpscareManager;
import com.xm6680.it.manifestation.ManifestationManager;
import com.xm6680.it.persistence.ItPersistentDataManager;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.runtime.ItManagers;
import com.xm6680.it.watching.WatchingLevelManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

/**
 * Connects Fabric server events to the mod managers.
 */
public final class ItServerEvents {
    private static final int WATCHING_UPDATE_INTERVAL_TICKS = 100;
    private static final int HORROR_EVENT_INTERVAL_TICKS = 200;
    private static final int WATCHER_SPAWN_INTERVAL_TICKS = 400;

    private static long tickCounter = 0L;

    private ItServerEvents() {
    }

    public static void register(ItManagers managers) {
        ServerLifecycleEvents.SERVER_STARTED.register(managers.persistentDataManager()::load);
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, force) -> managers.persistentDataManager().save(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(managers.persistentDataManager()::save);

        ServerTickEvents.END_SERVER_TICK.register(server -> tickServer(server, managers));

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity serverPlayer) {
                managers.horrorEventManager().onPlayerStartedMining(serverPlayer);
            }

            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient()
                    && player instanceof ServerPlayerEntity serverPlayer
                    && world.getBlockState(hitResult.getBlockPos()).isIn(BlockTags.BEDS)) {
                managers.worldAnomalyManager().onSleepAttempt(serverPlayer, hitResult.getBlockPos(), serverPlayer.getEntityWorld().getServer().getTicks());
            }

            if (!world.isClient()
                    && player instanceof ServerPlayerEntity serverPlayer
                    && world.getBlockState(hitResult.getBlockPos()).isIn(BlockTags.BEDS)
                    && managers.manifestationManager().shouldBlockSleep(serverPlayer, serverPlayer.getEntityWorld().getServer().getTicks())) {
                return ActionResult.FAIL;
            }

            return ActionResult.PASS;
        });

        ServerPlayerEvents.JOIN.register(managers.receiverManager()::onPlayerJoin);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            managers.receiverManager().onPlayerRespawn(newPlayer);
            managers.playerContextDetector().remove(oldPlayer);
            managers.avoidanceManager().remove(oldPlayer);
            managers.groupDreadManager().remove(oldPlayer);
            managers.skyborneHorrorManager().remove(oldPlayer);
            managers.horrorDirectorManager().remove(oldPlayer);
            managers.multiplayerDreadManager().remove(oldPlayer);
            managers.animalDisguiseManager().remove(oldPlayer);
            managers.netherSignalManager().remove(oldPlayer);
            managers.minorAnomalyAccumulator().remove(oldPlayer);
            for (var extension : managers.extensions()) {
                extension.remove(oldPlayer);
            }
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            managers.avoidanceManager().onChangedWorld(player, origin, destination);
            managers.playerContextDetector().remove(player);
            managers.avoidanceManager().remove(player);
            managers.groupDreadManager().remove(player);
            managers.skyborneHorrorManager().remove(player);
            managers.horrorDirectorManager().remove(player);
            managers.multiplayerDreadManager().remove(player);
            managers.animalDisguiseManager().remove(player);
            managers.netherSignalManager().remove(player);
            managers.minorAnomalyAccumulator().remove(player);
            for (var extension : managers.extensions()) {
                extension.remove(player);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register(managers.animalDisguiseManager()::onDeath);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            managers.progressionManager().syncWatchingLevel(handler.player);
            managers.persistentDataManager().save(server);
            managers.watchingLevelManager().remove(handler.player);
            managers.playerContextDetector().remove(handler.player);
            managers.avoidanceManager().remove(handler.player);
            managers.groupDreadManager().remove(handler.player);
            managers.skyborneHorrorManager().remove(handler.player);
            managers.horrorDirectorManager().remove(handler.player);
            managers.horrorEventManager().remove(handler.player);
            managers.watcherSpawnManager().remove(handler.player);
            managers.multiplayerDreadManager().remove(handler.player);
            managers.animalDisguiseManager().remove(handler.player);
            managers.netherSignalManager().remove(handler.player);
            managers.minorAnomalyAccumulator().remove(handler.player);
            managers.chaseManager().remove(handler.player);
            managers.caveStalkerManager().remove(handler.player);
            for (var extension : managers.extensions()) {
                extension.remove(handler.player);
            }
        });
    }

    private static void tickServer(MinecraftServer server, ItManagers managers) {
        WatchingLevelManager watchingLevelManager = managers.watchingLevelManager();
        HorrorProgressionManager progressionManager = managers.progressionManager();
        ReceiverManager receiverManager = managers.receiverManager();
        AnalogHorrorManager analogHorrorManager = managers.analogHorrorManager();
        MinorAnomalyAccumulator minorAnomalyAccumulator = managers.minorAnomalyAccumulator();
        HorrorEventManager horrorEventManager = managers.horrorEventManager();
        WatcherSpawnManager watcherSpawnManager = managers.watcherSpawnManager();
        WorldAnomalyManager worldAnomalyManager = managers.worldAnomalyManager();
        MultiplayerDreadManager multiplayerDreadManager = managers.multiplayerDreadManager();
        AnimalDisguiseManager animalDisguiseManager = managers.animalDisguiseManager();
        NetherSignalManager netherSignalManager = managers.netherSignalManager();
        ManifestationManager manifestationManager = managers.manifestationManager();
        JumpscareManager jumpscareManager = managers.jumpscareManager();
        ChaseManager chaseManager = managers.chaseManager();
        CaveStalkerManager caveStalkerManager = managers.caveStalkerManager();
        ClientDistortionManager clientDistortionManager = managers.clientDistortionManager();

        tickCounter++;
        managers.playerContextDetector().tick(server, tickCounter, receiverManager, progressionManager);
        if (tickCounter % WATCHING_UPDATE_INTERVAL_TICKS == 0) {
            managers.avoidanceManager().tick(server, tickCounter);
            managers.groupDreadManager().tick(server, tickCounter);
        }
        horrorEventManager.tickActiveSounds(server, tickCounter);
        worldAnomalyManager.tickActive(server, tickCounter);
        multiplayerDreadManager.tickActive(server, tickCounter);
        animalDisguiseManager.tickActive(server, tickCounter);
        netherSignalManager.tickActive(server, tickCounter);
        for (var extension : managers.extensions()) {
            extension.tickActive(server, tickCounter);
        }
        receiverManager.tickDelayedMessages(server, tickCounter);
        minorAnomalyAccumulator.tick(server, tickCounter);
        chaseManager.tick(server, tickCounter);
        caveStalkerManager.tick(server, tickCounter);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            progressionManager.tickPlayer(player);
        }

        if (tickCounter % WATCHING_UPDATE_INTERVAL_TICKS == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                watchingLevelManager.updatePlayer(player);
                progressionManager.syncWatchingLevel(player);

                if (ItConfigManager.getConfig().debugMode) {
                    int level = (int) Math.round(watchingLevelManager.getWatchingLevel(player));
                    player.sendMessage(Text.literal("被注视值=" + level + " 阶段=" + progressionManager.getPhase(player).getNumber()), true);
                }
            }
        }

        int progressionInterval = Math.max(20, ItConfigManager.getConfig().phaseProgressionCheckIntervalTicks);
        if (tickCounter % progressionInterval == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                var advancedPhase = progressionManager.tryAdvancePhase(player, tickCounter);
                if (advancedPhase != null) {
                    analogHorrorManager.onPhaseAdvanced(player, advancedPhase);
                }
            }
        }

        boolean horrorDirectorEnabled = ItConfigManager.getConfig().horrorDirectorEnabled;
        if (horrorDirectorEnabled) {
            managers.horrorDirectorManager().tick(server, tickCounter);
        }

        if (tickCounter % HORROR_EVENT_INTERVAL_TICKS == 0) {
            if (!horrorDirectorEnabled) {
                horrorEventManager.tryRandomEvents(server, tickCounter);
                analogHorrorManager.tick(server, tickCounter);
                worldAnomalyManager.tick(server, tickCounter);
                multiplayerDreadManager.tick(server, tickCounter);
                animalDisguiseManager.tick(server, tickCounter);
                netherSignalManager.tick(server, tickCounter);
                manifestationManager.tick(server, tickCounter);
                jumpscareManager.tick(server, tickCounter);
                clientDistortionManager.tick(server, tickCounter);
            }
            for (var extension : managers.extensions()) {
                extension.tick(server, tickCounter);
            }
        }

        if (tickCounter % WATCHER_SPAWN_INTERVAL_TICKS == 0) {
            watcherSpawnManager.tick(server);
        }
    }
}
