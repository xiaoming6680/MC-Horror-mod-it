package com.xm6680.it.watching;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores and updates each player's hidden watching level on the server.
 */
public class WatchingLevelManager {
    private static final double NEAR_PLAYER_DISTANCE = 24.0;
    private static final double FAR_PLAYER_DISTANCE = 64.0;

    private final Map<UUID, Double> watchingLevels = new HashMap<>();

    public double getWatchingLevel(ServerPlayerEntity player) {
        return watchingLevels.getOrDefault(player.getUuid(), 0.0);
    }

    public void setWatchingLevel(ServerPlayerEntity player, double value) {
        ItConfig config = ItConfigManager.getConfig();
        watchingLevels.put(player.getUuid(), clamp(value, 0.0, config.maxWatchingLevel));
    }

    public boolean hasWatchingLevel(UUID playerId) {
        return watchingLevels.containsKey(playerId);
    }

    public void remove(ServerPlayerEntity player) {
        watchingLevels.remove(player.getUuid());
    }

    public void updatePlayer(ServerPlayerEntity player) {
        if (player.isSpectator()) {
            return;
        }

        if (!ItConfigManager.getConfig().enableWatchingLevel) {
            return;
        }

        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();

        int lightLevel = world.getLightLevel(pos);
        boolean skyVisible = world.isSkyVisible(pos);
        boolean night = isNight(world);
        boolean underground = isUnderground(world, player, skyVisible);
        boolean farFromOthers = isFarFromOtherPlayers(player, FAR_PLAYER_DISTANCE);
        boolean nearAnotherPlayer = !isFarFromOtherPlayers(player, NEAR_PLAYER_DISTANCE);

        double change = 0.0;

        if (night && skyVisible) {
            change += 0.8;
        }

        if (underground) {
            change += 0.9;
        }

        if (lightLevel <= 7) {
            change += 0.5;
        }

        if (farFromOthers) {
            change += 0.4;
        }

        if (!night && skyVisible && player.getY() >= world.getSeaLevel() - 5) {
            change -= 0.8;
        }

        if (nearAnotherPlayer) {
            change -= 0.8;
        }

        if (lightLevel >= 12) {
            change -= 0.5;
        }

        if (change == 0.0) {
            change -= 0.1;
        }

        setWatchingLevel(player, getWatchingLevel(player) + change);
    }

    public boolean isNight(ServerWorld world) {
        long time = world.getTimeOfDay() % 24000L;
        return time >= 13000L && time <= 23000L;
    }

    public boolean isUnderground(ServerWorld world, ServerPlayerEntity player, boolean skyVisible) {
        return !skyVisible && player.getY() < world.getSeaLevel();
    }

    private boolean isFarFromOtherPlayers(ServerPlayerEntity player, double distance) {
        double maxDistanceSquared = distance * distance;

        MinecraftServer server = player.getEntityWorld().getServer();

        for (ServerPlayerEntity otherPlayer : server.getPlayerManager().getPlayerList()) {
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

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
