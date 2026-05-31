package com.xm6680.it.client.effect;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.resource.ResourcePackManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Client-only old-texture distortion. It enables the built-in Programmer Art pack
 * and uses snow/static without opening Minecraft's resource reload screen.
 */
public final class LegacyTextureOverlayRenderer {
    private static final List<String> PROGRAMMER_ART_IDS = List.of(
            "programmer_art",
            "vanilla/programmer_art",
            "minecraft:programmer_art",
            "builtin/programmer_art"
    );
    private static final Random RANDOM = new Random();
    private static int remainingTicks;
    private static int totalTicks;
    private static int suppressReloadOverlayTicks;
    private static float intensity;

    private LegacyTextureOverlayRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(LegacyTextureOverlayRenderer::tick);
        HudRenderCallback.EVENT.register(LegacyTextureOverlayRenderer::render);
    }

    public static void start(int durationTicks, float overlayIntensity) {
        enableProgrammerArtPack();
        remainingTicks = Math.max(20, durationTicks);
        totalTicks = remainingTicks;
        intensity = Math.max(0.0F, Math.min(1.0F, overlayIntensity));
    }

    private static void enableProgrammerArtPack() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) {
            return;
        }

        ResourcePackManager manager = client.getResourcePackManager();
        manager.scanPacks();
        String packId = findProgrammerArtId(manager);
        if (packId == null) {
            return;
        }

        List<String> enabled = new ArrayList<>(client.options.resourcePacks);
        if (!enabled.contains(packId)) {
            enabled.add(packId);
        }

        client.options.resourcePacks.clear();
        client.options.resourcePacks.addAll(enabled);
        client.options.incompatibleResourcePacks.remove(packId);
        manager.setEnabledProfiles(enabled);
        client.options.refreshResourcePacks(manager);
        client.options.write();
        forceReloadWithoutLoadingOverlay(client);
    }

    private static void forceReloadWithoutLoadingOverlay(MinecraftClient client) {
        suppressReloadOverlayTicks = 100;
        client.reloadResources();
        client.setOverlay(null);
    }

    private static void tick(MinecraftClient client) {
        if (suppressReloadOverlayTicks <= 0) {
            return;
        }

        suppressReloadOverlayTicks--;
        client.setOverlay(null);
    }

    private static String findProgrammerArtId(ResourcePackManager manager) {
        for (String candidate : PROGRAMMER_ART_IDS) {
            if (manager.getProfile(candidate) != null) {
                return candidate;
            }
        }

        for (String id : manager.getIds()) {
            String lower = id.toLowerCase(Locale.ROOT);
            if (lower.contains("programmer") || lower.contains("classic")) {
                return id;
            }
        }

        return null;
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

        int sepiaAlpha = (int) (95.0F * effectiveIntensity * Math.min(1.0F, progress + 0.35F));
        context.fill(0, 0, width, height, (sepiaAlpha << 24) | 0x004C3B20);

        int blockSize = reduced ? 18 : 12;
        int blockAlpha = (int) (42.0F * effectiveIntensity);
        for (int y = (remainingTicks % blockSize) - blockSize; y < height; y += blockSize) {
            for (int x = 0; x < width; x += blockSize) {
                if (RANDOM.nextFloat() < 0.08F * effectiveIntensity) {
                    int shade = 0x00201810 + RANDOM.nextInt(0x00070707);
                    context.fill(x, y, Math.min(width, x + blockSize), Math.min(height, y + blockSize), (blockAlpha << 24) | shade);
                }
            }
        }

        int lineAlpha = (int) (90.0F * effectiveIntensity);
        for (int scanY = remainingTicks % 4; scanY < height; scanY += 4) {
            context.fill(0, scanY, width, scanY + 1, (lineAlpha << 24) | 0x00262626);
        }

        if (!reduced) {
            int specks = (int) (width * height / 1050.0F * effectiveIntensity);
            for (int i = 0; i < specks; i++) {
                int x = RANDOM.nextInt(Math.max(1, width));
                int y = RANDOM.nextInt(Math.max(1, height));
                int color = RANDOM.nextBoolean() ? 0x77D8D8D8 : 0x77303030;
                context.fill(x, y, Math.min(width, x + 2), Math.min(height, y + 2), color);
            }

            if (RANDOM.nextFloat() < 0.55F) {
                int tearY = RANDOM.nextInt(Math.max(1, height));
                context.fill(0, tearY, width, Math.min(height, tearY + 3 + RANDOM.nextInt(8)), 0x664F4F4F);
            }
        }

        remainingTicks--;
    }
}
