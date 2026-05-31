package com.xm6680.it.client.jumpscare;

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

/**
 * A standalone fullscreen face scare. It is intentionally separate from the
 * gameplay jumpscare sequence and is currently test-command driven.
 */
public final class FaceScareOverlayRenderer {
    private static final Identifier FACE_TEXTURE = Identifier.of(ItMod.MOD_ID, "textures/gui/face_scare.png");
    private static final Random RANDOM = new Random();
    private static final int TEXTURE_WIDTH = 512;
    private static final int TEXTURE_HEIGHT = 512;

    private static int remainingTicks;
    private static int totalTicks;
    private static float intensity;
    private static boolean soundEnabled;
    private static boolean secondScreamPlayed;
    private static boolean thirdScreamPlayed;

    private FaceScareOverlayRenderer() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(FaceScareOverlayRenderer::render);
    }

    public static void start(int durationTicks, float overlayIntensity, boolean playSound) {
        intensity = Math.max(0.0F, Math.min(1.0F, overlayIntensity));
        remainingTicks = Math.max(1, durationTicks);
        totalTicks = remainingTicks;
        soundEnabled = playSound;
        secondScreamPlayed = false;
        thirdScreamPlayed = false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (playSound && client.player != null) {
            playInitialSoundBurst(client);
        }
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (remainingTicks <= 0) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float progress = totalTicks <= 0 ? 1.0F : remainingTicks / (float) totalTicks;
        float effectiveIntensity = config.reduceFlashingEffects || config.disableRapidFlashes ? intensity * 0.78F : intensity;
        int backgroundAlpha = (int) (255.0F * Math.min(config.maxOverlayOpacity, 0.82F) * effectiveIntensity);
        context.fill(0, 0, width, height, (backgroundAlpha << 24));

        int jitter = config.disableRapidFlashes ? 0 : Math.max(1, (int) (6.0F * effectiveIntensity));
        int xOffset = jitter <= 0 ? 0 : RANDOM.nextInt(jitter * 2 + 1) - jitter;
        int yOffset = jitter <= 0 ? 0 : RANDOM.nextInt(jitter * 2 + 1) - jitter;
        int drawWidth = width + Math.max(18, (int) (width * 0.08F * progress));
        int drawHeight = height + Math.max(18, (int) (height * 0.08F * progress));
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

        int vignetteAlpha = (int) (180.0F * effectiveIntensity * progress);
        context.fill(0, 0, width, Math.max(1, height / 7), (vignetteAlpha << 24));
        context.fill(0, height - Math.max(1, height / 6), width, height, (vignetteAlpha << 24));

        int lineAlpha = (int) (80.0F * effectiveIntensity * progress);
        for (int scanY = remainingTicks % 4; scanY < height; scanY += 4) {
            context.fill(0, scanY, width, scanY + 1, (lineAlpha << 24) | 0x00333333);
        }

        if (!config.reduceFlashingEffects && !config.disableRapidFlashes) {
            int tearColor = ((int) (90.0F * effectiveIntensity) << 24) | 0x00460000;
            for (int i = 0; i < 5; i++) {
                int tearY = RANDOM.nextInt(Math.max(1, height));
                context.fill(0, tearY, width, Math.min(height, tearY + 2 + RANDOM.nextInt(8)), tearColor);
            }
        }

        tickScreams();
        remainingTicks--;
    }

    private static void playInitialSoundBurst(MinecraftClient client) {
        client.player.playSound(SoundEvents.ENTITY_GHAST_SCREAM, 0.95F, 1.25F);
        client.player.playSound(SoundEvents.ENTITY_ENDERMAN_SCREAM, 0.85F, 0.72F);
        client.player.playSound(SoundEvents.ENTITY_GOAT_SCREAMING_DEATH, 0.55F, 1.45F);
        client.player.playSound(SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.55F, 0.70F);
        client.player.playSound(SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST, 0.62F, 0.50F);
    }

    private static void tickScreams() {
        if (!soundEnabled || remainingTicks <= 0) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        int elapsedTicks = totalTicks - remainingTicks;
        if (!secondScreamPlayed && elapsedTicks >= 10) {
            client.player.playSound(SoundEvents.ENTITY_GHAST_SCREAM, 0.80F, 1.55F);
            client.player.playSound(SoundEvents.ENTITY_GOAT_SCREAMING_HURT, 0.50F, 1.70F);
            secondScreamPlayed = true;
        }

        if (!thirdScreamPlayed && elapsedTicks >= 24) {
            client.player.playSound(SoundEvents.ENTITY_ENDERMAN_SCREAM, 0.55F, 1.10F);
            client.player.playSound(SoundEvents.ENTITY_GHAST_HURT, 0.65F, 1.35F);
            thirdScreamPlayed = true;
        }
    }
}
