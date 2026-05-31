package com.xm6680.it.client.render;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Random;

public final class ManifestationOverlayRenderer {
    private static final Random RANDOM = new Random();
    private static int remainingTicks;
    private static int totalTicks;
    private static float intensity;
    private static boolean reduceFlashes;

    private ManifestationOverlayRenderer() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(ManifestationOverlayRenderer::render);
    }

    public static void start(int durationTicks, float overlayIntensity, boolean reduced) {
        remainingTicks = Math.max(1, durationTicks);
        totalTicks = remainingTicks;
        intensity = Math.max(0.0F, Math.min(1.0F, overlayIntensity));
        reduceFlashes = reduced;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (remainingTicks <= 0) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float progress = totalTicks <= 0 ? 1.0F : remainingTicks / (float) totalTicks;
        float effectiveIntensity = reduceFlashes || config.reduceFlashingEffects || config.disableRapidFlashes
                ? intensity * 0.45F
                : intensity;
        int alpha = (int) (255.0F * Math.min(config.maxOverlayOpacity, 0.28F + effectiveIntensity * 0.55F) * progress);
        context.fill(0, 0, width, height, (alpha << 24) | 0x00101010);

        int lineColor = ((int) (120 * effectiveIntensity) << 24) | 0x002A5132;
        for (int y = remainingTicks % 5; y < height; y += 5) {
            context.fill(0, y, width, y + 1, lineColor);
        }

        int bandColor = ((int) (115 * effectiveIntensity * progress) << 24) | 0x00040704;
        int tearCount = reduceFlashes || config.disableRapidFlashes ? 2 : 4 + (int) (effectiveIntensity * 8.0F);
        for (int i = 0; i < tearCount; i++) {
            int y = RANDOM.nextInt(Math.max(1, height));
            int bandHeight = 2 + RANDOM.nextInt(reduceFlashes || config.disableRapidFlashes ? 5 : 14);
            int xOffset = reduceFlashes || config.disableRapidFlashes ? 0 : RANDOM.nextInt(33) - 16;
            context.fill(Math.min(0, xOffset), y, Math.max(width, width + xOffset), Math.min(height, y + bandHeight), bandColor);
        }

        if (!reduceFlashes && !config.disableRapidFlashes) {
            int blockCount = 5 + (int) (effectiveIntensity * 12.0F);
            for (int i = 0; i < blockCount; i++) {
                int x = RANDOM.nextInt(Math.max(1, width));
                int y = RANDOM.nextInt(Math.max(1, height));
                int blockWidth = 18 + RANDOM.nextInt(92);
                int blockHeight = 3 + RANDOM.nextInt(18);
                int color = switch (RANDOM.nextInt(4)) {
                    case 0 -> 0x66101010;
                    case 1 -> 0x552B5A38;
                    case 2 -> 0x443A2A1A;
                    default -> 0x66200000;
                };
                context.fill(x, y, Math.min(width, x + blockWidth), Math.min(height, y + blockHeight), color);
            }

            int specks = (int) (width * height / 1700.0F * effectiveIntensity);
            for (int i = 0; i < specks; i++) {
                int x = RANDOM.nextInt(Math.max(1, width));
                int y = RANDOM.nextInt(Math.max(1, height));
                int color = (RANDOM.nextBoolean() ? 0x66333333 : 0x552B5A38);
                context.fill(x, y, x + RANDOM.nextInt(14) + 1, y + 1, color);
            }
        }

        remainingTicks--;
    }
}
