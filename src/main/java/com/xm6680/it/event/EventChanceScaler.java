package com.xm6680.it.event;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.progression.HorrorProgressionManager;
import net.minecraft.server.network.ServerPlayerEntity;

public final class EventChanceScaler {
    private static final double ORDINARY_EVENT_WATCHING_BONUS_AT_MAX = 0.25D;
    private static final double PHASE_FIVE_EVENT_WATCHING_BONUS_AT_MAX = 0.45D;

    private EventChanceScaler() {
    }

    public static double ordinaryEventMultiplier(ServerPlayerEntity player, HorrorProgressionManager progressionManager, ItConfig config) {
        return 1.0D + watchingPercent(player, progressionManager, config) * ORDINARY_EVENT_WATCHING_BONUS_AT_MAX;
    }

    public static double phaseFiveHighPressureEventMultiplier(ServerPlayerEntity player, HorrorProgressionManager progressionManager, ItConfig config) {
        return 1.0D + watchingPercent(player, progressionManager, config) * PHASE_FIVE_EVENT_WATCHING_BONUS_AT_MAX;
    }

    public static double watchingPercent(ServerPlayerEntity player, HorrorProgressionManager progressionManager, ItConfig config) {
        double maxWatchingLevel = Math.max(1.0D, config.maxWatchingLevel);
        double percent = progressionManager.getData(player).watchingLevel / maxWatchingLevel;
        return Math.max(0.0D, Math.min(1.0D, percent));
    }

    public static double clampChance(double chance) {
        return Math.max(0.0D, Math.min(1.0D, chance));
    }
}
