package com.xm6680.it.client.effect;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

public final class FirstPersonLockManager {
    private static int remainingTicks;

    private FirstPersonLockManager() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FirstPersonLockManager::tick);
    }

    public static void start(int durationTicks) {
        remainingTicks = Math.max(1, durationTicks);
    }

    public static void stop() {
        remainingTicks = 0;
    }

    private static void tick(MinecraftClient client) {
        if (remainingTicks <= 0) {
            return;
        }

        remainingTicks--;
        if (client.player == null || client.options == null) {
            return;
        }

        client.options.setPerspective(Perspective.FIRST_PERSON);
    }
}
