package com.xm6680.it.analog;

import com.xm6680.it.ItMod;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MinorAnomalyAccumulator {
    private static final int TICKS_PER_SECOND = 20;
    private static final String[] SUMMARY_MESSAGES = {
            "附近出现多次未登记声音。",
            "声音记录无法匹配玩家行为。",
            "环境异常累计超过阈值。",
            "多个微弱信号来自同一区域。",
            "周围行为记录不完整。",
            "附近事件无法逐条确认。",
            "接收器合并了多条微弱记录。"
    };

    private final HorrorProgressionManager progressionManager;
    private final ReceiverManager receiverManager;
    private final Map<UUID, MinorAnomalyState> states = new HashMap<>();
    private final Random random = new Random();

    public MinorAnomalyAccumulator(HorrorProgressionManager progressionManager, ReceiverManager receiverManager) {
        this.progressionManager = progressionManager;
        this.receiverManager = receiverManager;
    }

    public void recordMinorSound(ServerPlayerEntity player, String source) {
        MinorAnomalyState state = states.computeIfAbsent(player.getUuid(), uuid -> new MinorAnomalyState());
        state.minorSoundEvents++;
        switch (source) {
            case "fake_footstep" -> state.fakeFootstepCount++;
            case "fake_eating" -> state.fakeEatingCount++;
            case "fake_block_place" -> state.fakeBlockPlaceCount++;
            case "mining_echo" -> state.miningEchoCount++;
            case "cave_sound" -> state.caveSoundCount++;
            default -> {
            }
        }
        state.lastMinorEventGameTime = currentTick(player);
        tryFlush(player, state, false);
    }

    public void recordMinorWorld(ServerPlayerEntity player) {
        MinorAnomalyState state = states.computeIfAbsent(player.getUuid(), uuid -> new MinorAnomalyState());
        state.minorWorldEvents++;
        state.lastMinorEventGameTime = currentTick(player);
        tryFlush(player, state, false);
    }

    public void tick(MinecraftServer server, long currentTick) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            MinorAnomalyState state = states.get(player.getUuid());
            if (state != null) {
                tryFlush(player, state, true);
            }
        }
    }

    public void remove(ServerPlayerEntity player) {
        states.remove(player.getUuid());
    }

    private void tryFlush(ServerPlayerEntity player, MinorAnomalyState state, boolean cooldownOnly) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableMinorAnomalyAggregation || player.isSpectator() || !player.isAlive()) {
            return;
        }

        HorrorPhase phase = progressionManager.getPhase(player);
        if (!phase.isAtLeast(phaseFromNumber(config.minorAnomalyAggregationMinPhase)) || isStrongEventActive(player, currentTick(player))) {
            return;
        }

        int total = state.minorSoundEvents + state.minorWorldEvents;
        if (total <= 0) {
            return;
        }

        long currentTick = currentTick(player);
        long cooldownTicks = secondsToTicks(Math.max(0, config.minorAnomalyAggregationCooldownSeconds));
        boolean cooldownReady = state.lastSummaryMessageGameTime <= 0L || currentTick - state.lastSummaryMessageGameTime >= cooldownTicks;
        boolean thresholdReady = total >= Math.max(1, config.minorAnomalyAggregationThreshold);
        if (!cooldownReady || (!thresholdReady && (!cooldownOnly || currentTick - state.lastMinorEventGameTime < cooldownTicks))) {
            return;
        }

        int delay = config.enableDelayedReceiverMessages
                ? randomBetween(Math.max(20, config.receiverEventMessageMinDelayTicks), Math.max(Math.max(20, config.receiverEventMessageMinDelayTicks), config.receiverEventMessageMaxDelayTicks))
                : 0;
        receiverManager.scheduleMessage(player, ReceiverMessageType.OBSERVATION, phase, pickSummaryMessage(phase), delay);
        state.lastSummaryMessageGameTime = currentTick;
        state.clearCounts();
    }

    private String pickSummaryMessage(HorrorPhase phase) {
        if (phase.isAtLeast(HorrorPhase.INTRUSION) && random.nextDouble() < 0.35D) {
            return random.nextBoolean() ? "微弱记录被合并，来源仍在附近。" : "接收器拒绝逐条展开异常。";
        }
        return SUMMARY_MESSAGES[random.nextInt(SUMMARY_MESSAGES.length)];
    }

    private boolean isStrongEventActive(ServerPlayerEntity player, long currentTick) {
        return ItMod.getChaseManager().isChasing(player)
                || ItMod.getCaveStalkerManager().isActive(player)
                || ItNetwork.isJumpscareActive(player, currentTick)
                || ItNetwork.isFaceScareActive(player, currentTick)
                || ItNetwork.isManifestationOverlayActive(player, currentTick)
                || ItNetwork.isAnimalDisguiseRetaliationActive(player, currentTick);
    }

    private HorrorPhase phaseFromNumber(int phaseNumber) {
        return switch (Math.max(1, Math.min(5, phaseNumber))) {
            case 2 -> HorrorPhase.WATCHING;
            case 3 -> HorrorPhase.IMITATING;
            case 4 -> HorrorPhase.INTRUSION;
            case 5 -> HorrorPhase.MANIFESTATION;
            default -> HorrorPhase.DORMANT;
        };
    }

    private long currentTick(ServerPlayerEntity player) {
        return player.getEntityWorld().getServer().getTicks();
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }

    private static final class MinorAnomalyState {
        private int minorSoundEvents;
        private int minorWorldEvents;
        private long lastSummaryMessageGameTime;
        private long lastMinorEventGameTime;
        private int fakeFootstepCount;
        private int fakeEatingCount;
        private int fakeBlockPlaceCount;
        private int miningEchoCount;
        private int caveSoundCount;

        private void clearCounts() {
            minorSoundEvents = 0;
            minorWorldEvents = 0;
            fakeFootstepCount = 0;
            fakeEatingCount = 0;
            fakeBlockPlaceCount = 0;
            miningEchoCount = 0;
            caveSoundCount = 0;
        }
    }
}
