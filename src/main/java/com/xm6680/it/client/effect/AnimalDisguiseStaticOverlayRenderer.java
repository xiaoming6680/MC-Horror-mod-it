package com.xm6680.it.client.effect;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Random;

public final class AnimalDisguiseStaticOverlayRenderer {
    private static final Random RANDOM = new Random();
    private static final String[] RED_TEXT_FRAGMENTS = {
            "它不是动物",
            "外观确认失败",
            "生物记录不存在",
            "你杀错了",
            "模仿中断",
            "错误目标",
            "██ 它 ██",
            "不是它",
            "不要看",
            "记录裂开"
    };

    private static int remainingTicks;
    private static int totalTicks;
    private static float intensity;
    private static boolean reduceFlashes;

    private AnimalDisguiseStaticOverlayRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AnimalDisguiseStaticOverlayRenderer::tick);
        HudRenderCallback.EVENT.register(AnimalDisguiseStaticOverlayRenderer::render);
    }

    public static void start(int durationTicks, float overlayIntensity, boolean reduced) {
        remainingTicks = Math.max(20, durationTicks);
        totalTicks = remainingTicks;
        intensity = Math.max(0.0F, Math.min(1.0F, overlayIntensity));
        reduceFlashes = reduced;
    }

    private static void tick(MinecraftClient client) {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (remainingTicks <= 0) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        MinecraftClient client = MinecraftClient.getInstance();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        boolean reduced = reduceFlashes || config.reduceFlashingEffects || config.disableRapidFlashes;
        float progress = totalTicks <= 0 ? 1.0F : remainingTicks / (float) totalTicks;
        float effectiveIntensity = reduced ? intensity * 0.58F : intensity;
        int alpha = (int) (210.0F * Math.min(config.maxOverlayOpacity, 0.82F) * effectiveIntensity);

        context.fill(0, 0, width, height, ((int) (alpha * 0.35F) << 24) | 0x00000000);

        int scanAlpha = (int) ((reduced ? 34.0F : 84.0F) * effectiveIntensity);
        for (int y = remainingTicks % 3; y < height; y += 3) {
            context.fill(0, y, width, y + 1, (scanAlpha << 24) | 0x00E4E4E4);
        }

        int snowCount = Math.max(12, (int) (width * height / (reduced ? 2600.0F : 850.0F) * effectiveIntensity));
        for (int i = 0; i < snowCount; i++) {
            int x = RANDOM.nextInt(Math.max(1, width));
            int y = RANDOM.nextInt(Math.max(1, height));
            int size = reduced ? 1 : 1 + RANDOM.nextInt(3);
            int shade = RANDOM.nextBoolean() ? 0x00F2F2F2 : 0x00101010;
            int speckAlpha = reduced ? 0x55 : 0x88;
            context.fill(x, y, Math.min(width, x + size), Math.min(height, y + size), (speckAlpha << 24) | shade);
        }

        if (!reduced && RANDOM.nextFloat() < 0.42F * effectiveIntensity) {
            int y = RANDOM.nextInt(Math.max(1, height));
            int bandHeight = 2 + RANDOM.nextInt(8);
            context.fill(0, y, width, Math.min(height, y + bandHeight), 0xAAE8E8E8);
        }

        drawRedTextCorruption(context, client, width, height, effectiveIntensity, reduced);

        int vignetteAlpha = (int) (120.0F * effectiveIntensity * (0.4F + progress * 0.6F));
        context.fill(0, 0, width, Math.max(1, height / 12), (vignetteAlpha << 24) | 0x00000000);
        context.fill(0, height - Math.max(1, height / 12), width, height, (vignetteAlpha << 24) | 0x00000000);
    }

    private static void drawRedTextCorruption(DrawContext context, MinecraftClient client, int width, int height, float effectiveIntensity, boolean reduced) {
        if (client.textRenderer == null) {
            return;
        }

        int stepX = reduced ? 78 : 54;
        int stepY = reduced ? 22 : 15;
        int alpha = (int) ((reduced ? 86.0F : 156.0F) * effectiveIntensity);
        int strongAlpha = (int) ((reduced ? 118.0F : 210.0F) * effectiveIntensity);
        int tickOffset = remainingTicks % Math.max(1, stepY);

        for (int y = -tickOffset; y < height + stepY; y += stepY) {
            int rowOffset = ((y / Math.max(1, stepY)) & 1) == 0 ? 0 : stepX / 2;
            for (int x = -rowOffset; x < width + stepX; x += stepX) {
                String text = corruptedText();
                int jitterX = reduced ? 0 : RANDOM.nextInt(9) - 4;
                int jitterY = reduced ? 0 : RANDOM.nextInt(5) - 2;
                int colorAlpha = RANDOM.nextFloat() < 0.16F ? strongAlpha : alpha;
                int color = (Math.max(0, Math.min(255, colorAlpha)) << 24) | 0x00FF1010;
                context.drawText(client.textRenderer, text, x + jitterX, y + jitterY, color, false);
            }
        }
    }

    private static String corruptedText() {
        String base = RED_TEXT_FRAGMENTS[RANDOM.nextInt(RED_TEXT_FRAGMENTS.length)];
        if (RANDOM.nextFloat() < 0.45F) {
            int split = RANDOM.nextInt(Math.max(1, base.length()));
            return base.substring(0, split) + randomBreak() + base.substring(split);
        }

        return base;
    }

    private static String randomBreak() {
        return switch (RANDOM.nextInt(6)) {
            case 0 -> "///";
            case 1 -> "▌▌";
            case 2 -> "##";
            case 3 -> "??";
            case 4 -> "断";
            default -> "█";
        };
    }
}
