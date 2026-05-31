package com.xm6680.it.jumpscare;

import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.entity.ModEntities;
import com.xm6680.it.entity.WatcherEntity;
import com.xm6680.it.entity.TargetOnlyEntityVisibility;
import com.xm6680.it.event.EventChanceScaler;
import com.xm6680.it.manifestation.ManifestationManager;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class JumpscareManager {
    private static final int TICKS_PER_SECOND = 20;

    private final HorrorProgressionManager progressionManager;
    private final AnalogHorrorManager analogHorrorManager;
    private final ManifestationManager manifestationManager;
    private final Map<UUID, Long> nextJumpscareTicks = new HashMap<>();
    private final Random random = new Random();

    public JumpscareManager(HorrorProgressionManager progressionManager, AnalogHorrorManager analogHorrorManager, ManifestationManager manifestationManager) {
        this.progressionManager = progressionManager;
        this.analogHorrorManager = analogHorrorManager;
        this.manifestationManager = manifestationManager;
    }

    public void tick(MinecraftServer server, long currentTick) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isSpectator()) {
                tryJumpscare(player, currentTick);
            }
        }
    }

    public boolean triggerJumpscare(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!forced && !canTriggerNaturally(player, currentTick, config)) {
            return false;
        }

        if (!forced && !config.enableJumpscares) {
            return false;
        }

        boolean reducedEffects = config.reduceFlashingEffects || config.disableRapidFlashes;
        int configuredDuration = Math.max(40, config.jumpscareOverlayDurationTicks);
        int duration = reducedEffects ? Math.max(50, configuredDuration * 3 / 4) : configuredDuration;
        float intensity = config.enableJumpscareOverlay ? (reducedEffects ? 0.45F : 0.78F) : 0.0F;
        if (config.enableJumpscareOverlay || config.enableJumpscareSound) {
            ItNetwork.sendJumpscare(
                    player,
                    duration,
                    intensity,
                    config.enableJumpscareSound,
                    (float) Math.min(1.0, Math.max(0.0, config.jumpscareSoundVolume))
            );
        }

        if (config.enableJumpscareSound) {
            playJumpscareSoundBurst(player, config);
        }

        if (config.enableJumpscareDarkness) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, Math.max(0, config.jumpscareDarknessDurationTicks), 0, false, false, false));
        }

        spawnLungingWatcher(player);
        analogHorrorManager.addManifestationMessage(player, "它离你很近了....");
        progressionManager.recordJumpscare(player);
        nextJumpscareTicks.put(player.getUuid(), currentTick + secondsToTicks(config.jumpscareCooldownSeconds));
        return true;
    }

    public boolean tryWatcherStareJumpscare(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableJumpscares || progressionManager.getPhase(player) != HorrorPhase.MANIFESTATION) {
            return false;
        }

        if (currentTick < nextJumpscareTicks.getOrDefault(player.getUuid(), 0L)) {
            return false;
        }

        if (random.nextDouble() > 0.35 * config.jumpscareChanceMultiplier) {
            return false;
        }

        return triggerJumpscare(player, currentTick, true);
    }

    public long getNextJumpscareTick(ServerPlayerEntity player) {
        return nextJumpscareTicks.getOrDefault(player.getUuid(), 0L);
    }

    private void tryJumpscare(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!canTriggerNaturally(player, currentTick, config)) {
            return;
        }

        double chance = EventChanceScaler.clampChance(0.008
                * config.jumpscareChanceMultiplier
                * config.eventChanceMultiplier
                * EventChanceScaler.phaseFiveHighPressureEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() <= chance) {
            triggerJumpscare(player, currentTick, false);
        }
    }

    private boolean canTriggerNaturally(ServerPlayerEntity player, long currentTick, ItConfig config) {
        if (!config.enableJumpscares) {
            return false;
        }

        if (config.jumpscareOnlyInPhaseFive && progressionManager.getPhase(player) != HorrorPhase.MANIFESTATION) {
            return false;
        }

        if (!progressionManager.isManifestationEnvironment(player)) {
            return false;
        }

        PlayerHorrorData data = progressionManager.getData(player);
        if (data.phaseFiveInterferenceEvents < config.jumpscareRequiresInterferenceEvents
                || data.watcherSightings < 2
                || data.receiverMessagesReceived < 8) {
            return false;
        }

        if (!manifestationManager.isSeparatedFromInterference(player, currentTick)) {
            return false;
        }

        return currentTick >= nextJumpscareTicks.getOrDefault(player.getUuid(), 0L);
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }

    private void spawnLungingWatcher(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d side = look.crossProduct(new Vec3d(0.0, 1.0, 0.0)).normalize();
        if (side.lengthSquared() < 0.01) {
            side = new Vec3d(1.0, 0.0, 0.0);
        }

        Vec3d spawnPos = player.getEyePos()
                .add(look.multiply(9.5))
                .add(side.multiply((random.nextDouble() - 0.5) * 2.0))
                .add(0.0, -0.9, 0.0);

        WatcherEntity watcher = ModEntities.WATCHER.create(world, spawnedWatcher -> {
        }, player.getBlockPos(), net.minecraft.entity.SpawnReason.TRIGGERED, false, false);
        if (watcher == null) {
            return;
        }

        watcher.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0.0F, 0.0F);
        watcher.beginJumpscareLunge(player, Math.max(80, ItConfigManager.getConfig().jumpscareOverlayDurationTicks), TICKS_PER_SECOND);
        watcher.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, player.getEyePos());
        world.spawnEntity(watcher);
        TargetOnlyEntityVisibility.hideFromNonTargetPlayers(watcher);
    }

    private void playJumpscareSoundBurst(ServerPlayerEntity player, ItConfig config) {
        float volume = (float) Math.min(1.0, Math.max(0.0, config.jumpscareSoundVolume));
        sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, Math.max(0.85F, volume), 0.34F + random.nextFloat() * 0.08F);
        sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, Math.max(0.70F, volume * 0.85F), 0.82F + random.nextFloat() * 0.16F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.AMBIENT, volume * 0.78F, 0.48F + random.nextFloat() * 0.10F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.AMBIENT, volume * 0.55F, 0.32F + random.nextFloat() * 0.08F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST, SoundCategory.AMBIENT, volume * 0.85F, 0.48F + random.nextFloat() * 0.08F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_ENDERMAN_STARE, SoundCategory.AMBIENT, volume * 0.72F, 0.36F + random.nextFloat() * 0.12F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.AMBIENT, volume * 0.46F, 0.26F + random.nextFloat() * 0.12F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.AMBIENT, volume * 0.52F, 0.42F + random.nextFloat() * 0.16F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, volume * 0.62F, 0.90F + random.nextFloat() * 0.55F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_PIG_DEATH, SoundCategory.HOSTILE, volume * 0.34F, 1.80F + random.nextFloat() * 0.35F);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(sound, category, player.getX(), player.getY(), player.getZ(), volume, pitch, random.nextLong());
        player.networkHandler.sendPacket(packet);
    }
}
