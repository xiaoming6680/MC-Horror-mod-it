package com.xm6680.it.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Optional lifecycle hook for future server-side horror features.
 */
public interface HorrorExtension {
    default void onRegister(HorrorExtensionContext context) {
    }

    default void tickActive(MinecraftServer server, long currentTick) {
    }

    default void tick(MinecraftServer server, long currentTick) {
    }

    default void remove(ServerPlayerEntity player) {
    }
}
