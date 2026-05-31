package com.xm6680.it.client.cavestalker;

import com.xm6680.it.ItMod;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

import java.util.Random;

public final class CaveStalkerFaceScareOverlayRenderer {
    private static final Identifier FACE_TEXTURE = Identifier.of(ItMod.MOD_ID, "textures/gui/cavestalker_facescare.png");
    private static final Random RANDOM = new Random();
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 512;

    private static int remainingTicks;
    private static int totalTicks;
    private static float intensity;
    private static boolean soundEnabled;
    private static boolean secondBurstPlayed;
    private static boolean finalBurstPlayed;

    private CaveStalkerFaceScareOverlayRenderer() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(CaveStalkerFaceScareOverlayRenderer::render);
    }

    public static void start(int durationTicks, float overlayIntensity, boolean playSound) {
        remainingTicks = Math.max(1, durationTicks);
        totalTicks = remainingTicks;
        intensity = Math.max(0.0F, Math.min(1.0F, overlayIntensity));
        soundEnabled = playSound;
        secondBurstPlayed = false;
        finalBurstPlayed = false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (playSound && client.player != null) {
            client.player.playSound(SoundEvents.ENTITY_GHAST_SCREAM, 0.90F, 1.18F);
            client.player.playSound(SoundEvents.ENTITY_ENDERMAN_SCREAM, 0.75F, 0.66F);
            client.player.playSound(SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.70F, 0.58F);
        }
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
        float effectiveIntensity = reduced ? intensity * 0.72F : intensity;
        int backgroundAlpha = (int) (255.0F * Math.min(config.maxOverlayOpacity, 0.84F) * effectiveIntensity);
        context.fill(0, 0, width, height, (backgroundAlpha << 24) | 0x00010000);

        int jitter = reduced ? 1 : Math.max(2, (int) (8.0F * effectiveIntensity));
        int xOffset = RANDOM.nextInt(jitter * 2 + 1) - jitter;
        int yOffset = RANDOM.nextInt(jitter * 2 + 1) - jitter;
        int drawWidth = width + Math.max(24, (int) (width * 0.09F * progress));
        int drawHeight = height + Math.max(24, (int) (height * 0.09F * progress));
        int x = (width - drawWidth) / 2 + xOffset;
        int y = (height - drawHeight) / 2 + yOffset;

        context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                FACE_TEXTURE,
                x,
                y,
                0.0F,
                0.0F,
                drawWidth,
                drawHeight,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );

        int redAlpha = (int) (118.0F * effectiveIntensity * progress);
        context.fill(0, 0, width, height, (redAlpha << 24) | 0x00250000);

        int scanAlpha = (int) (92.0F * effectiveIntensity * progress);
        for (int scanY = remainingTicks % 3; scanY < height; scanY += 3) {
            context.fill(0, scanY, width, scanY + 1, (scanAlpha << 24) | 0x00222222);
        }

        if (!reduced) {
            int tearColor = ((int) (100.0F * effectiveIntensity) << 24) | 0x00460000;
            for (int i = 0; i < 5; i++) {
                int tearY = RANDOM.nextInt(Math.max(1, height));
                context.fill(0, tearY, width, Math.min(height, tearY + 2 + RANDOM.nextInt(8)), tearColor);
            }
        }

        tickSoundBursts();
        remainingTicks--;
    }

    private static void tickSoundBursts() {
        if (!soundEnabled || remainingTicks <= 0) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        int elapsedTicks = totalTicks - remainingTicks;
        if (!secondBurstPlayed && elapsedTicks >= 12) {
            client.player.playSound(SoundEvents.ENTITY_GHAST_HURT, 0.70F, 1.45F);
            client.player.playSound(SoundEvents.ENTITY_GOAT_SCREAMING_HURT, 0.55F, 1.70F);
            secondBurstPlayed = true;
        }

        if (!finalBurstPlayed && elapsedTicks >= 28) {
            client.player.playSound(SoundEvents.ENTITY_ENDERMAN_SCREAM, 0.65F, 1.05F);
            client.player.playSound(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 0.42F, 0.74F);
            finalBurstPlayed = true;
        }
    }
}
