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
 * Client-only render distance distortion. The view distance is intentionally not restored.
 */
public final class ClientViewDistanceEffect {
    private static final Random RANDOM = new Random();
    private static int overlayTicks;
    private static int overlayTotalTicks;

    private ClientViewDistanceEffect() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientViewDistanceEffect::tick);
        HudRenderCallback.EVENT.register(ClientViewDistanceEffect::render);
    }

    public static void start(int chunks, int durationTicks) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) {
            return;
        }

        int target = Math.max(2, Math.min(32, chunks));
        client.options.getViewDistance().setValue(target);
        client.options.write();
        overlayTicks = Math.max(4, Math.min(12, durationTicks));
        overlayTotalTicks = overlayTicks;
    }

    private static void tick(MinecraftClient client) {
        if (overlayTicks <= 0) {
            return;
        }

        overlayTicks--;
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (overlayTicks <= 0) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        boolean reduced = config.reduceFlashingEffects || config.disableRapidFlashes;
        float progress = overlayTotalTicks <= 0 ? 0.0F : overlayTicks / (float) overlayTotalTicks;
        float intensity = reduced ? 0.34F : 0.68F;
        int alpha = (int) (255.0F * Math.min(config.maxOverlayOpacity, 0.12F + progress * 0.24F) * intensity);

        context.fill(0, 0, width, height, ((int) (alpha * 0.34F) << 24) | 0x00030304);

        int scanAlpha = reduced ? 28 : 58;
        for (int y = overlayTicks % 3; y < height; y += 3) {
            context.fill(0, y, width, y + 1, (scanAlpha << 24) | 0x002B352B);
        }

        if (reduced) {
            return;
        }

        int specks = Math.max(28, width * height / 1200);
        for (int i = 0; i < specks; i++) {
            int x = RANDOM.nextInt(Math.max(1, width));
            int y = RANDOM.nextInt(Math.max(1, height));
            int color = RANDOM.nextBoolean() ? 0x66404040 : 0x66101010;
            context.fill(x, y, Math.min(width, x + 2), Math.min(height, y + 2), color);
        }

        if (RANDOM.nextFloat() < 0.65F) {
            int y = RANDOM.nextInt(Math.max(1, height));
            context.fill(0, y, width, Math.min(height, y + 2 + RANDOM.nextInt(4)), 0x66373737);
        }
    }
}
