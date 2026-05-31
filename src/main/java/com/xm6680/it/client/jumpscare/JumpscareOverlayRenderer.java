package com.xm6680.it.client.jumpscare;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.sound.SoundEvents;

public final class JumpscareOverlayRenderer {
    private static int remainingTicks;
    private static int totalTicks;
    private static float intensity;

    private JumpscareOverlayRenderer() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(JumpscareOverlayRenderer::render);
    }

    public static void start(int durationTicks, float overlayIntensity, boolean playSound, float soundVolume) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (playSound && client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, Math.min(1.0F, Math.max(0.0F, soundVolume)), 0.38F);
        }

        intensity = Math.max(0.0F, Math.min(1.0F, overlayIntensity));
        if (intensity <= 0.0F) {
            remainingTicks = 0;
            totalTicks = 0;
            return;
        }

        remainingTicks = Math.max(1, durationTicks);
        totalTicks = remainingTicks;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (remainingTicks <= 0) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float progress = totalTicks <= 0 ? 1.0F : remainingTicks / (float) totalTicks;
        float effectiveIntensity = config.reduceFlashingEffects || config.disableRapidFlashes ? intensity * 0.55F : intensity;
        int alpha = (int) (255.0F * Math.min(config.maxOverlayOpacity, effectiveIntensity) * progress);

        context.fill(0, 0, width, height, (alpha << 24));

        int centerX = width / 2;
        int centerY = height / 2;
        int faceWidth = Math.max(48, (int) (width * 0.24F * progress));
        int faceHeight = Math.max(90, (int) (height * 0.48F * progress));
        int shadow = ((int) (220 * progress) << 24) | 0x00020202;
        context.fill(centerX - faceWidth / 2, centerY - faceHeight / 2, centerX + faceWidth / 2, centerY + faceHeight / 2, shadow);
        context.fill(centerX - faceWidth / 3, centerY - faceHeight / 3, centerX - faceWidth / 5, centerY - faceHeight / 3 + 6, 0xAA0B0B0B);
        context.fill(centerX + faceWidth / 5, centerY - faceHeight / 3, centerX + faceWidth / 3, centerY - faceHeight / 3 + 6, 0xAA0B0B0B);

        if (!config.disableRapidFlashes) {
            int staticAlpha = (int) (70 * effectiveIntensity * progress);
            for (int y = remainingTicks % 5; y < height; y += 5) {
                context.fill(0, y, width, y + 1, (staticAlpha << 24) | 0x002F3A2F);
            }
        }

        remainingTicks--;
    }
}
