package com.xm6680.it.effect;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.event.EventChanceScaler;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Server-side scheduler for client-only visual distortions.
 */
public class ClientDistortionManager {
    private static final int TICKS_PER_SECOND = 20;

    private final HorrorProgressionManager progressionManager;
    private final Map<UUID, Long> nextViewDistanceTicks = new HashMap<>();
    private final Map<UUID, Long> nextLegacyTextureTicks = new HashMap<>();
    private final Map<UUID, Long> nextMonochromeTicks = new HashMap<>();
    private final Random random = new Random();

    public ClientDistortionManager(HorrorProgressionManager progressionManager) {
        this.progressionManager = progressionManager;
    }

    public void tick(MinecraftServer server, long currentTick) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) {
                continue;
            }

            tryTriggerViewDistanceDrop(player, currentTick, false);
            tryTriggerLegacyTexture(player, currentTick, false);
            tryTriggerMonochrome(player, currentTick, false);
        }
    }

    public boolean forceViewDistanceDrop(ServerPlayerEntity player, long currentTick) {
        return tryTriggerViewDistanceDrop(player, currentTick, true);
    }

    public boolean forceLegacyTexture(ServerPlayerEntity player, long currentTick) {
        return tryTriggerLegacyTexture(player, currentTick, true);
    }

    public boolean forceMonochrome(ServerPlayerEntity player, long currentTick) {
        return tryTriggerMonochrome(player, currentTick, true);
    }

    private boolean tryTriggerViewDistanceDrop(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!forced) {
            if (!config.enableViewDistanceAnomalies || !progressionManager.getPhase(player).isAtLeast(HorrorPhase.WATCHING)) {
                return false;
            }

            if (currentTick < nextViewDistanceTicks.getOrDefault(player.getUuid(), 0L)) {
                return false;
            }

            double chance = EventChanceScaler.clampChance(0.018
                    * config.viewDistanceAnomalyChanceMultiplier
                    * config.eventChanceMultiplier
                    * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
            if (random.nextDouble() > chance) {
                nextViewDistanceTicks.put(player.getUuid(), currentTick + secondsToTicks(60 + random.nextInt(121)));
                return false;
            }
        }

        ItNetwork.sendViewDistanceAnomaly(player, config.viewDistanceAnomalyChunks, config.viewDistanceAnomalyDurationTicks);
        playViewDistanceSound(player);
        nextViewDistanceTicks.put(player.getUuid(), currentTick + secondsToTicks(config.viewDistanceAnomalyCooldownSeconds + random.nextInt(181)));
        return true;
    }

    private boolean tryTriggerLegacyTexture(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!forced) {
            if (!config.enableLegacyTextureAnomalies || !progressionManager.getPhase(player).isAtLeast(HorrorPhase.IMITATING)) {
                return false;
            }

            if (currentTick < nextLegacyTextureTicks.getOrDefault(player.getUuid(), 0L)) {
                return false;
            }

            double chance = EventChanceScaler.clampChance(0.014
                    * config.legacyTextureAnomalyChanceMultiplier
                    * config.eventChanceMultiplier
                    * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
            if (random.nextDouble() > chance) {
                nextLegacyTextureTicks.put(player.getUuid(), currentTick + secondsToTicks(80 + random.nextInt(161)));
                return false;
            }
        }

        ItNetwork.sendLegacyTextureAnomaly(player, config.legacyTextureAnomalyDurationTicks, (float) config.legacyTextureSnowIntensity);
        nextLegacyTextureTicks.put(player.getUuid(), currentTick + secondsToTicks(config.legacyTextureAnomalyCooldownSeconds + random.nextInt(241)));
        return true;
    }

    private boolean tryTriggerMonochrome(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!forced) {
            if (!config.enableMonochromeAnomalies || !progressionManager.getPhase(player).isAtLeast(HorrorPhase.WATCHING)) {
                return false;
            }

            if (currentTick < nextMonochromeTicks.getOrDefault(player.getUuid(), 0L)) {
                return false;
            }

            double chance = EventChanceScaler.clampChance(0.012
                    * config.monochromeAnomalyChanceMultiplier
                    * config.eventChanceMultiplier
                    * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
            if (random.nextDouble() > chance) {
                nextMonochromeTicks.put(player.getUuid(), currentTick + secondsToTicks(70 + random.nextInt(151)));
                return false;
            }
        }

        ItNetwork.sendMonochromeAnomaly(player, config.monochromeAnomalyDurationTicks, (float) config.monochromeAnomalyIntensity);
        nextMonochromeTicks.put(player.getUuid(), currentTick + secondsToTicks(config.monochromeAnomalyCooldownSeconds + random.nextInt(181)));
        return true;
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }

    private void playViewDistanceSound(ServerPlayerEntity player) {
        sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, 0.75F, 0.50F + random.nextFloat() * 0.08F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.42F, 0.62F + random.nextFloat() * 0.08F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, 0.40F, 0.78F + random.nextFloat() * 0.14F);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(
                sound,
                SoundCategory.AMBIENT,
                player.getX(),
                player.getY() + 0.8,
                player.getZ(),
                volume,
                pitch,
                random.nextLong()
        );
        player.networkHandler.sendPacket(packet);
    }
}
