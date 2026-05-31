package com.xm6680.it.director;

import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.analog.ReceiverMessageType;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.event.MultiplayerDreadManager;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class SkyborneHorrorManager {
    private static final int TICKS_PER_SECOND = 20;

    private final HorrorProgressionManager progressionManager;
    private final ReceiverManager receiverManager;
    private final MultiplayerDreadManager multiplayerDreadManager;
    private final Map<UUID, Long> nextSkyborneEventTicks = new HashMap<>();
    private final Random random = new Random();

    public SkyborneHorrorManager(HorrorProgressionManager progressionManager, ReceiverManager receiverManager, MultiplayerDreadManager multiplayerDreadManager) {
        this.progressionManager = progressionManager;
        this.receiverManager = receiverManager;
        this.multiplayerDreadManager = multiplayerDreadManager;
    }

    public boolean triggerSkyborneEvent(ServerPlayerEntity player, PlayerContextSnapshot snapshot, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.skyborneEventsEnabled || (!forced && currentTick < nextSkyborneEventTicks.getOrDefault(player.getUuid(), 0L))) {
            return false;
        }

        PlayerHorrorData data = progressionManager.getData(player);
        HorrorPhase phase = progressionManager.getPhase(player);
        int event = chooseEvent(player, phase, config);
        boolean triggered = switch (event) {
            case 0 -> triggerSkySound(player, config);
            case 1 -> triggerHeightSignal(player, phase, config);
            case 2 -> triggerMountAnomaly(player, config);
            default -> triggerDistantIdentityLeak(player, phase, config, forced);
        };

        if (!triggered) {
            return false;
        }

        data.skyborneEvents++;
        nextSkyborneEventTicks.put(player.getUuid(), currentTick + secondsToTicks(config.skyborneEventCooldownSeconds + random.nextInt(121)));
        return true;
    }

    public long getNextSkyborneEventTick(ServerPlayerEntity player) {
        return nextSkyborneEventTicks.getOrDefault(player.getUuid(), 0L);
    }

    public void remove(ServerPlayerEntity player) {
        nextSkyborneEventTicks.remove(player.getUuid());
    }

    private int chooseEvent(ServerPlayerEntity player, HorrorPhase phase, ItConfig config) {
        if (player.getY() >= config.skyborneExtremeY) {
            return random.nextInt(2) == 0 ? 1 : 3;
        }

        if (player.hasVehicle() && config.skyborneMountAnomalyEnabled) {
            return 2;
        }

        if (phase.isAtLeast(HorrorPhase.INTRUSION) && config.skyborneFakePlayerEnabled && random.nextDouble() < 0.25D) {
            return 3;
        }

        return random.nextInt(2);
    }

    private boolean triggerSkySound(ServerPlayerEntity player, ItConfig config) {
        if (!config.skyborneSoundEventsEnabled) {
            return false;
        }

        Vec3d source = sourceAboveAndBehind(player, 10.0D + random.nextDouble() * 10.0D);
        sendSoundToPlayer(player, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, source, 0.62F, 0.55F + random.nextFloat() * 0.18F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.AMBIENT, source.add(0.0D, 3.0D, 0.0D), 0.34F, 0.45F + random.nextFloat() * 0.12F);
        return true;
    }

    private boolean triggerHeightSignal(ServerPlayerEntity player, HorrorPhase phase, ItConfig config) {
        if (!config.receiverUnreadPressureEnabled && !config.skyborneShadowEnabled) {
            return false;
        }

        ReceiverMessageType type = phase.isAtLeast(HorrorPhase.INTRUSION)
                ? ReceiverMessageType.SYSTEM_ERROR
                : ReceiverMessageType.OBSERVATION;
        String text;
        if (player.getY() >= config.skyborneExtremeY) {
            text = "高度变化记录完成。信号源仍在上方。";
        } else if (player.getY() >= config.skyborneHighY) {
            text = "声源高度高于玩家，但未检测到实体。";
        } else {
            text = "空中移动记录异常。下方阴影未与地形匹配。";
        }
        receiverManager.scheduleMessage(player, type, phase, text, 40 + random.nextInt(80));
        receiverManager.notifyStrongSignal(player);
        return true;
    }

    private boolean triggerMountAnomaly(ServerPlayerEntity player, ItConfig config) {
        if (!config.skyborneMountAnomalyEnabled || !player.hasVehicle()) {
            return false;
        }

        double x = player.getVehicle().getX();
        double y = player.getVehicle().getY() + 0.8D;
        double z = player.getVehicle().getZ();
        Vec3d source = new Vec3d(x, y, z);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, source, 0.72F, 0.55F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, source, 0.42F, 0.70F);
        player.sendMessage(Text.literal("当前坐骑心跳频率与实体类型不匹配。"), true);
        receiverManager.scheduleMessage(player, ReceiverMessageType.OBSERVATION, progressionManager.getPhase(player), "当前坐骑心跳频率与实体类型不匹配。", 60);
        return true;
    }

    private boolean triggerDistantIdentityLeak(ServerPlayerEntity player, HorrorPhase phase, ItConfig config, boolean forced) {
        if ((forced || phase.isAtLeast(HorrorPhase.INTRUSION)) && config.skyborneFakePlayerEnabled && multiplayerDreadManager.triggerFakeTab(player, forced)) {
            receiverManager.scheduleMessage(player, ReceiverMessageType.IDENTITY_WARNING, phase, "空中玩家列表出现短暂额外签名。", 80);
            return true;
        }

        return triggerHeightSignal(player, phase, config);
    }

    private Vec3d sourceAboveAndBehind(ServerPlayerEntity player, double distance) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d behind = look.multiply(-distance);
        double side = random.nextBoolean() ? 4.0D : -4.0D;
        return new Vec3d(player.getX(), player.getY() + 1.0D, player.getZ())
                .add(behind)
                .add(-look.z * side, 8.0D + random.nextDouble() * 6.0D, look.x * side);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, Vec3d source, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, source, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, Vec3d source, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(
                sound,
                category,
                source.x,
                source.y,
                source.z,
                volume,
                pitch,
                random.nextLong()
        );
        player.networkHandler.sendPacket(packet);
    }

    private long secondsToTicks(int seconds) {
        return (long) Math.max(0, seconds) * TICKS_PER_SECOND;
    }
}
