package com.xm6680.it.client.effect;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Random;

/**
 * Short client-only Receiver corruption burst used when hunt contact is confirmed.
 */
public final class ReceiverDistortionOverlayRenderer {
    private static final Random RANDOM = new Random();
    private static int remainingTicks;
    private static int totalTicks;
    private static float intensity;

    private ReceiverDistortionOverlayRenderer() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(ReceiverDistortionOverlayRenderer::render);
    }

    public static void start(int durationTicks, float overlayIntensity) {
        remainingTicks = Math.max(20, durationTicks);
        totalTicks = remainingTicks;
        intensity = Math.max(0.0F, Math.min(1.0F, overlayIntensity));
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (remainingTicks <= 0) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        boolean reduced = config.reduceFlashingEffects || config.disableRapidFlashes;
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float progress = totalTicks <= 0 ? 1.0F : remainingTicks / (float) totalTicks;
        float effectiveIntensity = reduced ? intensity * 0.58F : intensity;
        int alpha = (int) (255.0F * Math.min(config.maxOverlayOpacity, 0.30F + progress * 0.36F) * effectiveIntensity);

        context.fill(0, 0, width, height, ((int) (alpha * 0.46F) << 24) | 0x00050805);

        int scanAlpha = (int) (110.0F * effectiveIntensity);
        for (int y = remainingTicks % 3; y < height; y += 3) {
            context.fill(0, y, width, y + 1, (scanAlpha << 24) | 0x00294B2A);
        }

        int bandAlpha = (int) (92.0F * effectiveIntensity);
        for (int i = 0; i < 5; i++) {
            int y = RANDOM.nextInt(Math.max(1, height));
            context.fill(0, y, width, Math.min(height, y + 2 + RANDOM.nextInt(7)), (bandAlpha << 24) | 0x001B1B1B);
        }

        if (!reduced) {
            int specks = Math.max(24, (int) (width * height / 1200.0F * effectiveIntensity));
            for (int i = 0; i < specks; i++) {
                int x = RANDOM.nextInt(Math.max(1, width));
                int y = RANDOM.nextInt(Math.max(1, height));
                int color = RANDOM.nextBoolean() ? 0x884C6B4C : 0x88111111;
                context.fill(x, y, Math.min(width, x + 2), Math.min(height, y + 2), color);
            }
        }

        remainingTicks--;
    }
}
