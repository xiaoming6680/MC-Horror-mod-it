package com.xm6680.it.client.effect;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Random;

/**
 * Client-only black-and-white screen anomaly. Minecraft's HUD hook cannot
 * desaturate the world buffer directly, so this uses a grayscale wash plus
 * contrast bands and static to create a short monochrome camera failure.
 */
public final class MonochromeOverlayRenderer {
    private static final Random RANDOM = new Random();

    private static int remainingTicks;
    private static int totalTicks;
    private static float intensity;

    private MonochromeOverlayRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(MonochromeOverlayRenderer::tick);
        HudRenderCallback.EVENT.register(MonochromeOverlayRenderer::render);
    }

    public static void start(int durationTicks, float overlayIntensity) {
        remainingTicks = Math.max(1, durationTicks);
        totalTicks = remainingTicks;
        intensity = Math.max(0.0F, Math.min(1.0F, overlayIntensity));
    }

    private static void tick(MinecraftClient client) {
        if (remainingTicks <= 0) {
            return;
        }

        remainingTicks--;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (remainingTicks <= 0) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        boolean reduced = config.reduceFlashingEffects || config.disableRapidFlashes;
        float progress = totalTicks <= 0 ? 1.0F : remainingTicks / (float) totalTicks;
        float effectiveIntensity = reduced ? intensity * 0.62F : intensity;

        int grayAlpha = (int) (190.0F * Math.min(config.maxOverlayOpacity, 0.80F) * effectiveIntensity);
        context.fill(0, 0, width, height, (grayAlpha << 24) | 0x00B8B8B8);

        int darkAlpha = (int) (120.0F * effectiveIntensity * (0.55F + progress * 0.45F));
        context.fill(0, 0, width, height, (darkAlpha << 24) | 0x00000000);

        int scanAlpha = (int) ((reduced ? 28.0F : 62.0F) * effectiveIntensity);
        for (int y = remainingTicks % 4; y < height; y += 4) {
            context.fill(0, y, width, y + 1, (scanAlpha << 24) | 0x00D8D8D8);
        }

        if (reduced) {
            return;
        }

        int specks = (int) (width * height / 1800.0F * effectiveIntensity);
        for (int i = 0; i < specks; i++) {
            int x = RANDOM.nextInt(Math.max(1, width));
            int y = RANDOM.nextInt(Math.max(1, height));
            int size = RANDOM.nextBoolean() ? 1 : 2;
            int color = RANDOM.nextBoolean() ? 0x88E0E0E0 : 0x88141414;
            context.fill(x, y, Math.min(width, x + size), Math.min(height, y + size), color);
        }

        if (RANDOM.nextFloat() < 0.38F * effectiveIntensity) {
            int tearY = RANDOM.nextInt(Math.max(1, height));
            context.fill(0, tearY, width, Math.min(height, tearY + 2 + RANDOM.nextInt(6)), 0x883A3A3A);
        }
    }
}
