package com.xm6680.it.client.chase;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Random;

/**
 * Lightweight chase overlay: dark edge pressure, scanlines, and restrained static.
 */
public final class ChaseOverlayRenderer {
    private static final Random RANDOM = new Random();
    private static boolean active;
    private static int remainingTicks;
    private static int totalTicks;
    private static int pulseTicks;
    private static int receiverSignalTicks;
    private static int distanceHintTicks;
    private static int distanceHintColor = 0xFFFF5A4E;
    private static String distanceHint = "";
    private static float intensity;

    private ChaseOverlayRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ChaseOverlayRenderer::tick);
        HudRenderCallback.EVENT.register(ChaseOverlayRenderer::render);
    }

    public static void start(int durationTicks, float overlayIntensity) {
        active = true;
        remainingTicks = Math.max(1, durationTicks);
        totalTicks = remainingTicks;
        pulseTicks = 0;
        intensity = Math.max(0.35F, Math.min(1.0F, overlayIntensity));
    }

    public static void stop() {
        active = false;
        remainingTicks = 0;
        totalTicks = 0;
        pulseTicks = 0;
        distanceHintTicks = 0;
        distanceHint = "";
    }

    public static void startReceiverSignal(int durationTicks) {
        receiverSignalTicks = Math.max(20, durationTicks);
    }

    public static void stopReceiverSignal() {
        receiverSignalTicks = 0;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isReceiverSignalActive() {
        return receiverSignalTicks > 0 || active;
    }

    public static void showDistanceHint(String text, int color, int durationTicks) {
        distanceHint = text == null ? "" : text;
        distanceHintColor = color;
        distanceHintTicks = Math.max(1, durationTicks);
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || !client.player.isAlive()) {
            stop();
            stopReceiverSignal();
            return;
        }

        if (receiverSignalTicks > 0) {
            receiverSignalTicks--;
        }

        if (distanceHintTicks > 0) {
            distanceHintTicks--;
        }
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!active) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float progress = totalTicks <= 0 ? 1.0F : Math.max(0.42F, remainingTicks / (float) totalTicks);
        boolean reduced = config.chaseRespectReduceFlashingEffects && (config.reduceFlashingEffects || config.disableRapidFlashes);
        float effectiveIntensity = reduced ? intensity * 0.55F : intensity;
        int alpha = (int) (255.0F * Math.min(config.maxOverlayOpacity, 0.16F + effectiveIntensity * 0.22F));

        context.fill(0, 0, width, height, ((int) (alpha * 0.22F) << 24) | 0x00040404);

        int scanAlpha = (int) (80.0F * effectiveIntensity * progress);
        for (int y = remainingTicks % 5; y < height; y += 5) {
            context.fill(0, y, width, y + 1, (scanAlpha << 24) | 0x002C3F2D);
        }

        if (!reduced) {
            int tearColor = ((int) (75.0F * effectiveIntensity) << 24) | 0x00440000;
            for (int i = 0; i < 4; i++) {
                int y = RANDOM.nextInt(Math.max(1, height));
                int tearHeight = 2 + RANDOM.nextInt(7);
                context.fill(0, y, width, Math.min(height, y + tearHeight), tearColor);
            }

            int specks = (int) (width * height / 2500.0F * effectiveIntensity);
            for (int i = 0; i < specks; i++) {
                int x = RANDOM.nextInt(Math.max(1, width));
                int y = RANDOM.nextInt(Math.max(1, height));
                context.fill(x, y, Math.min(width, x + 2), y + 1, 0x55333333);
            }
        }

        renderDistanceHint(context);

        pulseTicks++;
        remainingTicks--;
        if (remainingTicks <= 0) {
            remainingTicks = Math.max(20, Math.min(80, totalTicks / 8));
        }
    }

    private static void renderDistanceHint(DrawContext context) {
        if (distanceHintTicks <= 0 || distanceHint.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        int textWidth = client.textRenderer.getWidth(distanceHint);
        int x = (width - textWidth) / 2;
        int y = height / 2 + 16;
        context.drawTextWithShadow(client.textRenderer, distanceHint, x, y, distanceHintColor);
    }
}
