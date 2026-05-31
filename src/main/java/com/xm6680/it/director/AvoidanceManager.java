package com.xm6680.it.director;

import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AvoidanceManager {
    private static final int TICKS_PER_MINUTE = 20 * 60;

    private final HorrorProgressionManager progressionManager;
    private final PlayerContextDetector contextDetector;
    private final ReceiverManager receiverManager;
    private final Map<UUID, Long> lastUpdateTicks = new HashMap<>();

    public AvoidanceManager(HorrorProgressionManager progressionManager, PlayerContextDetector contextDetector, ReceiverManager receiverManager) {
        this.progressionManager = progressionManager;
        this.contextDetector = contextDetector;
        this.receiverManager = receiverManager;
    }

    public void tick(MinecraftServer server, long currentTick) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) {
                continue;
            }

            update(player, currentTick);
        }
    }

    public void onChangedWorld(ServerPlayerEntity player, World origin, World destination) {
        PlayerHorrorData data = progressionManager.getData(player);
        long currentTick = progressionManager.getProgressionTick(player);
        data.lastDimensionChangeGameTime = currentTick;

        if (destination.getRegistryKey().equals(World.NETHER)) {
            if (data.firstNetherEntryGameTime <= 0L) {
                data.firstNetherEntryGameTime = currentTick;
            }

            if (data.totalPlayTicks <= 12L * 60L * 20L) {
                data.netherRushEvents++;
                addAvoidance(player, 18.0D);
            } else {
                addAvoidance(player, 8.0D);
            }
        } else if (origin.getRegistryKey().equals(World.NETHER)) {
            addAvoidance(player, 8.0D);
        }
    }

    public void addAvoidance(ServerPlayerEntity player, double amount) {
        PlayerHorrorData data = progressionManager.getData(player);
        data.avoidanceScore = clamp(data.avoidanceScore + Math.max(0.0D, amount), 0.0D, 100.0D);
    }

    public void setAvoidance(ServerPlayerEntity player, double value) {
        progressionManager.getData(player).avoidanceScore = clamp(value, 0.0D, 100.0D);
    }

    public double getAvoidance(ServerPlayerEntity player) {
        return progressionManager.getData(player).avoidanceScore;
    }

    public void remove(ServerPlayerEntity player) {
        lastUpdateTicks.remove(player.getUuid());
    }

    private void update(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        PlayerHorrorData data = progressionManager.getData(player);
        long lastTick = lastUpdateTicks.getOrDefault(player.getUuid(), currentTick);
        long deltaTicks = Math.max(1L, currentTick - lastTick);
        lastUpdateTicks.put(player.getUuid(), currentTick);

        if (!config.avoidanceEnabled) {
            data.avoidanceScore = Math.max(0.0D, data.avoidanceScore - perTick(config.avoidanceDecayPerMinute, deltaTicks));
            return;
        }

        PlayerContextSnapshot snapshot = contextDetector.getSnapshot(player);
        double change = -perTick(config.avoidanceDecayPerMinute, deltaTicks);

        if (snapshot.has(PlayerContext.SKYBORNE)) {
            change += perTick(config.skyborneAvoidancePerMinute, deltaTicks);
        }

        if (snapshot.mountedTicks() >= 60L * 20L) {
            change += perTick(config.mountedAvoidancePerMinute, deltaTicks);
        }

        if (snapshot.has(PlayerContext.GROUPED) && !config.groupedPlayersAreFullySafe) {
            change += perTick(config.groupedAvoidancePerMinute, deltaTicks);
        }

        if (snapshot.has(PlayerContext.FAST_TRAVELING)) {
            change += perTick(config.fastTravelAvoidancePerMinute, deltaTicks);
        }

        if (snapshot.has(PlayerContext.HOME_OR_BASE)) {
            change += perTick(config.safeBaseAvoidancePerMinute, deltaTicks);
        }

        int unreadMessages = Math.max(0, receiverManager.getMessageCount(player) - data.lastReceiverOpenedMessageCount);
        if (config.receiverUnreadPressureEnabled && unreadMessages >= config.receiverUnreadSoftThreshold) {
            data.unreadSignalPressure = Math.min(100.0D, unreadMessages * 10.0D);
            change += perTick(config.ignoredReceiverAvoidancePerMinute, deltaTicks)
                    * Math.min(3.0D, Math.max(1.0D, unreadMessages / 2.0D));
        } else {
            data.unreadSignalPressure = Math.max(0.0D, data.unreadSignalPressure - perTick(12.0D, deltaTicks));
        }

        data.avoidanceScore = clamp(data.avoidanceScore + change, 0.0D, 100.0D);
    }

    private double perTick(double perMinute, long ticks) {
        return perMinute * ((double) ticks / (double) TICKS_PER_MINUTE);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
