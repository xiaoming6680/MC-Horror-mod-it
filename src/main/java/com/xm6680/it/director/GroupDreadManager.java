package com.xm6680.it.director;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GroupDreadManager {
    private static final int TICKS_PER_MINUTE = 20 * 60;

    private final HorrorProgressionManager progressionManager;
    private final PlayerContextDetector contextDetector;
    private final Map<UUID, Long> lastUpdateTicks = new HashMap<>();

    public GroupDreadManager(HorrorProgressionManager progressionManager, PlayerContextDetector contextDetector) {
        this.progressionManager = progressionManager;
        this.contextDetector = contextDetector;
    }

    public void tick(MinecraftServer server, long currentTick) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isSpectator()) {
                update(player, currentTick);
            }
        }
    }

    public void recordGroupEvent(ServerPlayerEntity player) {
        PlayerHorrorData data = progressionManager.getData(player);
        data.groupDreadEvents++;
        data.groupDread = Math.max(0.0D, data.groupDread - 18.0D);
    }

    public double getGroupDread(ServerPlayerEntity player) {
        return progressionManager.getData(player).groupDread;
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

        PlayerContextSnapshot snapshot = contextDetector.getSnapshot(player);
        double decay = perTick(5.0D, deltaTicks);
        data.individualDread = Math.max(0.0D, data.individualDread - decay);
        data.groupDread = Math.max(0.0D, data.groupDread - decay);

        if (!config.groupDreadEnabled) {
            return;
        }

        if (snapshot.has(PlayerContext.GROUPED)) {
            double multiplier = 1.0D + Math.min(2.0D, snapshot.nearbyPlayers() * 0.35D);
            data.groupDread = Math.min(100.0D, data.groupDread + perTick(config.groupDreadGainPerMinute, deltaTicks) * multiplier);
        }

        if (snapshot.has(PlayerContext.ISOLATED) || snapshot.has(PlayerContext.IN_DARKNESS) || snapshot.has(PlayerContext.UNDERGROUND)) {
            data.individualDread = Math.min(100.0D, data.individualDread + perTick(6.0D, deltaTicks));
        }
    }

    private double perTick(double perMinute, long ticks) {
        return perMinute * ((double) ticks / (double) TICKS_PER_MINUTE);
    }
}
