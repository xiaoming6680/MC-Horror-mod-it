package com.xm6680.it.client.cavestalker;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Random;

public final class CaveStalkerOverlayRenderer {
    private static final Random RANDOM = new Random();
    private static boolean active;
    private static int remainingTicks;
    private static int totalTicks;
    private static float intensity;

    private CaveStalkerOverlayRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CaveStalkerOverlayRenderer::tick);
        HudRenderCallback.EVENT.register(CaveStalkerOverlayRenderer::render);
    }

    public static void start(int durationTicks, float overlayIntensity) {
        active = true;
        remainingTicks = Math.max(1, durationTicks);
        totalTicks = remainingTicks;
        intensity = Math.max(0.15F, Math.min(1.0F, overlayIntensity));
    }

    public static void stop() {
        active = false;
        remainingTicks = 0;
        totalTicks = 0;
        intensity = 0.0F;
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || !client.player.isAlive()) {
            stop();
        }
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!active) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        boolean reduced = config.reduceFlashingEffects || config.disableRapidFlashes;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float progress = totalTicks <= 0 ? 1.0F : Math.max(0.35F, remainingTicks / (float) totalTicks);
        float effectiveIntensity = reduced ? intensity * 0.58F : intensity;
        int alpha = (int) (255.0F * Math.min(config.maxOverlayOpacity, 0.12F + effectiveIntensity * 0.20F));

        context.fill(0, 0, width, height, ((int) (alpha * 0.55F) << 24) | 0x00020302);

        int scanAlpha = (int) (86.0F * effectiveIntensity * progress);
        for (int y = remainingTicks % 5; y < height; y += 5) {
            context.fill(0, y, width, y + 1, (scanAlpha << 24) | 0x002B3F2B);
        }

        if (!reduced) {
            int tearColor = ((int) (58.0F * effectiveIntensity) << 24) | 0x001B260F;
            for (int i = 0; i < 3; i++) {
                int y = RANDOM.nextInt(Math.max(1, height));
                context.fill(0, y, width, Math.min(height, y + 2 + RANDOM.nextInt(6)), tearColor);
            }

            int specks = Math.max(16, (int) (width * height / 3200.0F * effectiveIntensity));
            for (int i = 0; i < specks; i++) {
                int x = RANDOM.nextInt(Math.max(1, width));
                int y = RANDOM.nextInt(Math.max(1, height));
                context.fill(x, y, Math.min(width, x + 2), y + 1, 0x44313F31);
            }
        }

        remainingTicks--;
        if (remainingTicks <= 0) {
            remainingTicks = Math.max(20, Math.min(80, totalTicks / 8));
        }
    }
}
