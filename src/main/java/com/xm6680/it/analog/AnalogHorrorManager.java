package com.xm6680.it.analog;

import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class AnalogHorrorManager {
    private static final int TICKS_PER_SECOND = 20;
    private static final int PHASE_ONE_FALLBACK_FIRST_MIN_SECONDS = 5 * 60;
    private static final int PHASE_ONE_FALLBACK_FIRST_MAX_SECONDS = 8 * 60;
    private static final int PHASE_ONE_FALLBACK_REPEAT_MIN_SECONDS = 8 * 60;
    private static final int PHASE_ONE_FALLBACK_REPEAT_MAX_SECONDS = 15 * 60;

    private final HorrorProgressionManager progressionManager;
    private final ReceiverManager receiverManager;
    private final Map<UUID, Long> nextReceiverMessageTicks = new HashMap<>();
    private final Random random = new Random();

    public AnalogHorrorManager(HorrorProgressionManager progressionManager, ReceiverManager receiverManager) {
        this.progressionManager = progressionManager;
        this.receiverManager = receiverManager;
    }

    public void tick(MinecraftServer server, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableReceiver || !config.enableReceiverMessages || !config.enableBroadcastEvents) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isSpectator()) {
                if (tryPhaseReceiverFallback(player, currentTick)) {
                    continue;
                }
                tryRandomReceiverMessage(player, currentTick);
            }
        }
    }

    public boolean forceReceiverMessage(ServerPlayerEntity player) {
        return addRandomPhaseMessage(player, progressionManager.getPhase(player));
    }

    public boolean forceWeatherMessage(ServerPlayerEntity player) {
        return receiverManager.addMessage(player, ReceiverMessageType.WEATHER, progressionManager.getPhase(player), pickWeatherMessage(player.getEntityWorld()));
    }

    public void onPhaseAdvanced(ServerPlayerEntity player, HorrorPhase phase) {
        switch (phase) {
            case WATCHING -> schedulePhaseAdvanceMessage(player, ReceiverMessageType.LOCAL_ALERT, phase, pick(
                    "接收器开始记录周围环境。",
                    "有东西注意到了你的活动。",
                    "地下信号开始变得不稳定。",
                    "请留意身后的声音。"
            ), randomBetween(20, 40));
            case IMITATING -> schedulePhaseAdvanceMessage(player, ReceiverMessageType.IDENTITY_WARNING, phase, pick(
                    "信号中出现了不属于你的声音。",
                    "它开始学习你们的行为。",
                    "不要只通过名字判断一个人。",
                    "模仿记录已开始。"
            ), randomBetween(20, 40));
            case INTRUSION -> schedulePhaseAdvanceMessage(player, ReceiverMessageType.SYSTEM_ERROR, phase, pick(
                    "异常信号已经进入你的生活区。",
                    "它不只在洞里。",
                    "接收器检测到身份混淆。",
                    "请确认你身边的人。"
            ), randomBetween(20, 40));
            case MANIFESTATION -> schedulePhaseAdvanceMessage(player, ReceiverMessageType.MANIFESTATION, phase, pick(
                    "它已经离你很近了。",
                    "观察阶段结束。",
                    "它不再只是看着你。",
                    "接触风险：极高。"
            ), randomBetween(20, 40));
            default -> {
            }
        }
    }

    public void onCaveFootstep(ServerPlayerEntity player) {
        onCaveFootstep(player, false);
    }

    public void onCaveFootstep(ServerPlayerEntity player, boolean forced) {
        com.xm6680.it.ItMod.getMinorAnomalyAccumulator().recordMinorSound(player, "cave_sound");
        ItConfig config = ItConfigManager.getConfig();
        if (config.enableReceiverForCaveFootstep || config.enableReceiverForMinorSounds) {
            tryScheduleEventMessage(player, ReceiverMessageType.OBSERVATION, pick(
                    "附近记录到无法匹配的脚步。",
                    "脚步声停止后，目标仍停留在原地。",
                    "声源在你移动后仍保持距离。",
                    "脚步声没有对应实体。"
            ), randomEventDelayTicks(40, 100), ReceiverRecordPolicy.RARE);
        }
    }

    public void onMiningEcho(ServerPlayerEntity player) {
        onMiningEcho(player, false);
    }

    public void onMiningEcho(ServerPlayerEntity player, boolean forced) {
        com.xm6680.it.ItMod.getMinorAnomalyAccumulator().recordMinorSound(player, "mining_echo");
        ItConfig config = ItConfigManager.getConfig();
        if (config.enableReceiverForMiningEcho || config.enableReceiverForMinorSounds) {
            tryScheduleEventMessage(player, ReceiverMessageType.LOCAL_ALERT, pick(
                    "下方回声与挖掘节奏不一致。",
                    "矿道回声无法匹配。",
                    "挖掘声源位置记录异常。",
                    "回声在你停手后仍持续。"
            ), randomEventDelayTicks(40, 100), ReceiverRecordPolicy.RARE);
        }
    }

    public void onFakeChat(ServerPlayerEntity player) {
        if (progressionManager.getPhase(player).isAtLeast(HorrorPhase.IMITATING)) {
            tryScheduleEventMessage(player, ReceiverMessageType.IDENTITY_WARNING, pick(
                    "私聊来源未通过确认。",
                    "未知信号尝试直接联系目标。",
                    "接收器记录到非公开通讯。",
                    "目标收到来源不明的信息。"
            ), randomEventDelayTicks(40, 100), ReceiverRecordPolicy.SAMPLED);
        }
    }

    public void onWatcherSighting(ServerPlayerEntity player) {
        HorrorPhase phase = progressionManager.getPhase(player);
        if (phase.isAtLeast(HorrorPhase.IMITATING)) {
            tryScheduleEventMessage(player, ReceiverMessageType.OBSERVATION, "视觉异常进入安全距离以内。", randomEventDelayTicks(40, 120), ReceiverRecordPolicy.IMPORTANT);
        }
    }

    public void addManifestationMessage(ServerPlayerEntity player, String text) {
        receiverManager.addMessage(player, ReceiverMessageType.MANIFESTATION, HorrorPhase.MANIFESTATION, text);
    }

    public void scheduleDelayedMessage(ServerPlayerEntity player, ReceiverMessageType type, HorrorPhase phase, String text, long delayTicks) {
        receiverManager.scheduleMessage(player, type, phase, text, delayTicks);
    }

    private void schedulePhaseAdvanceMessage(ServerPlayerEntity player, ReceiverMessageType type, HorrorPhase phase, String text, long delayTicks) {
        receiverManager.scheduleMessageAfterPhaseCooldown(player, type, phase, text, delayTicks);
    }

    public void scheduleEventMessage(ServerPlayerEntity player, ReceiverMessageType type, String text, int preferredDelayTicks) {
        scheduleDelayedMessage(player, type, progressionManager.getPhase(player), text, preferredDelayTicks);
    }

    public boolean tryScheduleEventMessage(ServerPlayerEntity player, ReceiverMessageType type, String text, int preferredDelayTicks, ReceiverRecordPolicy policy) {
        HorrorPhase phase = progressionManager.getPhase(player);
        if (!shouldRecord(random, phase, policy, ItConfigManager.getConfig())) {
            return false;
        }

        scheduleDelayedMessage(player, type, phase, text, preferredDelayTicks);
        return true;
    }

    public static boolean shouldRecord(Random random, HorrorPhase phase, ReceiverRecordPolicy policy, ItConfig config) {
        if (policy == ReceiverRecordPolicy.NONE) {
            return false;
        }

        if (!config.enableReceiverRecordPolicy) {
            return policy != ReceiverRecordPolicy.NONE;
        }

        if (policy == ReceiverRecordPolicy.CRITICAL) {
            return true;
        }

        double baseChance = switch (policy) {
            case RARE -> config.rareReceiverRecordChance;
            case SAMPLED -> config.sampledReceiverRecordChance;
            case IMPORTANT -> config.importantReceiverRecordChance;
            default -> 0.0D;
        };
        double phaseBonus = Math.max(0, phase.getNumber() - 1) * config.receiverRecordPhaseBonus;
        double chance = Math.min(1.0D, Math.max(0.0D, baseChance + phaseBonus));
        return random.nextDouble() <= chance;
    }

    private void tryRandomReceiverMessage(ServerPlayerEntity player, long currentTick) {
        UUID uuid = player.getUuid();
        if (currentTick < nextReceiverMessageTicks.getOrDefault(uuid, 0L)) {
            return;
        }

        HorrorPhase phase = progressionManager.getPhase(player);
        double chance = switch (phase) {
            case DORMANT -> 0.010;
            case WATCHING -> 0.020;
            case IMITATING -> 0.030;
            case INTRUSION -> 0.038;
            case MANIFESTATION -> 0.052;
        } * multiplier();
        if (phase == HorrorPhase.DORMANT) {
            chance *= phaseOneWatchingLevelMultiplier(player);
        }

        if (random.nextDouble() <= chance && addRandomPhaseMessage(player, phase)) {
            nextReceiverMessageTicks.put(uuid, currentTick + secondsToTicks(randomBetween(90, 210)));
        } else {
            nextReceiverMessageTicks.put(uuid, currentTick + secondsToTicks(randomBetween(45, 90)));
        }
    }

    private boolean addRandomPhaseMessage(ServerPlayerEntity player, HorrorPhase phase) {
        ReceiverMessageType type = chooseMessageType(phase);
        if (type == null) {
            return false;
        }

        return receiverManager.addMessage(player, type, phase, chooseMessageText(player, phase, type));
    }

    private boolean tryPhaseReceiverFallback(ServerPlayerEntity player, long serverTick) {
        HorrorPhase phase = progressionManager.getPhase(player);
        if (!supportsReceiverFallback(phase) || !receiverManager.hasReceiver(player)) {
            return false;
        }

        PlayerHorrorData data = progressionManager.getData(player);
        if (!data.hasReceivedReceiver) {
            return false;
        }

        ItConfig config = ItConfigManager.getConfig();
        if (phase == HorrorPhase.WATCHING && !config.enablePhaseTwoReceiverFallback) {
            data.nextReceiverFallbackGameTime = 0L;
            return false;
        }

        long currentTick = progressionManager.getProgressionTick(player);
        ensurePhaseReceiverFallbackSchedule(player, data, phase, currentTick, config);
        if (data.nextReceiverFallbackGameTime <= 0L || currentTick < data.nextReceiverFallbackGameTime) {
            return false;
        }

        ReceiverMessageType type = chooseFallbackMessageType(phase);
        if (!receiverManager.addMessage(player, type, phase, chooseFallbackMessageText(player, phase, type))) {
            return false;
        }

        data.receiverFallbackPhaseNumber = phase.getNumber();
        data.receiverFallbacksInPhase++;
        data.nextReceiverFallbackGameTime = currentTick + secondsToTicks(randomBetween(fallbackRepeatMinSeconds(phase, config), fallbackRepeatMaxSeconds(phase, config)));
        nextReceiverMessageTicks.put(player.getUuid(), serverTick + secondsToTicks(randomBetween(45, 90)));
        return true;
    }

    private void ensurePhaseReceiverFallbackSchedule(ServerPlayerEntity player, PlayerHorrorData data, HorrorPhase phase, long currentTick, ItConfig config) {
        if (data.receiverFallbackPhaseNumber != phase.getNumber()) {
            data.receiverFallbackPhaseNumber = phase.getNumber();
            data.receiverFallbacksInPhase = 0;
            data.nextReceiverFallbackGameTime = 0L;
        }

        if (data.receiverFirstReceivedGameTime <= 0L || data.receiverFirstReceivedGameTime > currentTick) {
            data.receiverFirstReceivedGameTime = currentTick;
        }

        if (data.lastReceiverMessageGameTime > currentTick) {
            data.lastReceiverMessageGameTime = 0L;
            data.nextReceiverFallbackGameTime = 0L;
        }

        if (data.nextReceiverFallbackGameTime > currentTick + secondsToTicks(fallbackScheduleMaxSeconds(phase, config))) {
            data.nextReceiverFallbackGameTime = 0L;
        }

        if (data.nextReceiverFallbackGameTime > 0L) {
            return;
        }

        long baseTick = fallbackBaseTick(data, phase, currentTick);
        int minSeconds = fallbackFirstOrRepeatMinSeconds(player, data, phase, config);
        int maxSeconds = fallbackFirstOrRepeatMaxSeconds(player, data, phase, config);
        data.nextReceiverFallbackGameTime = baseTick + secondsToTicks(randomBetween(minSeconds, maxSeconds));
    }

    private boolean supportsReceiverFallback(HorrorPhase phase) {
        return phase == HorrorPhase.DORMANT || phase == HorrorPhase.WATCHING;
    }

    private long fallbackBaseTick(PlayerHorrorData data, HorrorPhase phase, long currentTick) {
        if (phase == HorrorPhase.DORMANT) {
            return data.lastReceiverMessageGameTime > 0L ? data.lastReceiverMessageGameTime : data.receiverFirstReceivedGameTime;
        }

        long phaseBaseTick = data.phaseEnteredTime > 0L ? data.phaseEnteredTime : currentTick;
        if (data.lastReceiverMessageGameTime >= phaseBaseTick && data.lastReceiverMessageGameTime <= currentTick) {
            return data.lastReceiverMessageGameTime;
        }
        return phaseBaseTick;
    }

    private int fallbackFirstOrRepeatMinSeconds(ServerPlayerEntity player, PlayerHorrorData data, HorrorPhase phase, ItConfig config) {
        if (phase == HorrorPhase.DORMANT) {
            return hasPreviousReceiverMessage(player, data) ? PHASE_ONE_FALLBACK_REPEAT_MIN_SECONDS : PHASE_ONE_FALLBACK_FIRST_MIN_SECONDS;
        }
        return data.receiverFallbacksInPhase <= 0 ? phaseTwoFirstMinSeconds(config) : phaseTwoRepeatMinSeconds(config);
    }

    private int fallbackFirstOrRepeatMaxSeconds(ServerPlayerEntity player, PlayerHorrorData data, HorrorPhase phase, ItConfig config) {
        if (phase == HorrorPhase.DORMANT) {
            return hasPreviousReceiverMessage(player, data) ? PHASE_ONE_FALLBACK_REPEAT_MAX_SECONDS : PHASE_ONE_FALLBACK_FIRST_MAX_SECONDS;
        }
        return data.receiverFallbacksInPhase <= 0 ? phaseTwoFirstMaxSeconds(config) : phaseTwoRepeatMaxSeconds(config);
    }

    private int fallbackRepeatMinSeconds(HorrorPhase phase, ItConfig config) {
        return phase == HorrorPhase.DORMANT ? PHASE_ONE_FALLBACK_REPEAT_MIN_SECONDS : phaseTwoRepeatMinSeconds(config);
    }

    private int fallbackRepeatMaxSeconds(HorrorPhase phase, ItConfig config) {
        return phase == HorrorPhase.DORMANT ? PHASE_ONE_FALLBACK_REPEAT_MAX_SECONDS : phaseTwoRepeatMaxSeconds(config);
    }

    private int fallbackScheduleMaxSeconds(HorrorPhase phase, ItConfig config) {
        return phase == HorrorPhase.DORMANT ? PHASE_ONE_FALLBACK_REPEAT_MAX_SECONDS : phaseTwoRepeatMaxSeconds(config);
    }

    private int phaseTwoFirstMinSeconds(ItConfig config) {
        return Math.max(360, config.phaseTwoFallbackFirstMinSeconds);
    }

    private int phaseTwoFirstMaxSeconds(ItConfig config) {
        return Math.max(phaseTwoFirstMinSeconds(config), config.phaseTwoFallbackFirstMaxSeconds);
    }

    private int phaseTwoRepeatMinSeconds(ItConfig config) {
        return Math.max(600, config.phaseTwoFallbackRepeatMinSeconds);
    }

    private int phaseTwoRepeatMaxSeconds(ItConfig config) {
        return Math.max(phaseTwoRepeatMinSeconds(config), config.phaseTwoFallbackRepeatMaxSeconds);
    }

    private boolean hasPreviousReceiverMessage(ServerPlayerEntity player, PlayerHorrorData data) {
        return data.lastReceiverMessageGameTime > 0L
                || data.receiverMessagesReceived > 0
                || receiverManager.getMessageCount(player) > 0;
    }

    private ReceiverMessageType chooseFallbackMessageType(HorrorPhase phase) {
        if (phase == HorrorPhase.DORMANT) {
            return random.nextDouble() < 0.75 ? ReceiverMessageType.WEATHER : ReceiverMessageType.OBSERVATION;
        }
        return random.nextBoolean() ? ReceiverMessageType.LOCAL_ALERT : ReceiverMessageType.OBSERVATION;
    }

    private String chooseFallbackMessageText(ServerPlayerEntity player, HorrorPhase phase, ReceiverMessageType type) {
        if (phase == HorrorPhase.DORMANT) {
            if (type == ReceiverMessageType.WEATHER) {
                return pickWeatherMessage(player.getEntityWorld());
            }
            return pick(
                    "接收器自检完成。",
                    "低频频道保持安静。",
                    "周边记录未发现异常。",
                    "今日观察记录已归档。"
            );
        }

        return pick(
                "地下移动信号仍在持续。",
                "低光照区域记录未完成。",
                "接收器检测到重复回声。",
                "目标已进入被注视阶段。",
                "附近声音来源无法确认。",
                "矿道回声正在重复你的动作。",
                "请不要长时间停留在矿洞入口。"
        );
    }

    private ReceiverMessageType chooseMessageType(HorrorPhase phase) {
        if (phase == HorrorPhase.DORMANT) {
            return ReceiverMessageType.WEATHER;
        }

        if (phase == HorrorPhase.WATCHING) {
            ReceiverMessageType[] types = {
                    ReceiverMessageType.WEATHER,
                    ReceiverMessageType.LOCAL_ALERT,
                    ReceiverMessageType.OBSERVATION
            };
            return types[random.nextInt(types.length)];
        }

        if (phase == HorrorPhase.IMITATING) {
            ReceiverMessageType[] types = {
                    ReceiverMessageType.LOCAL_ALERT,
                    ReceiverMessageType.OBSERVATION,
                    ReceiverMessageType.IDENTITY_WARNING
            };
            return types[random.nextInt(types.length)];
        }

        if (phase == HorrorPhase.INTRUSION) {
            ReceiverMessageType[] types = {
                    ReceiverMessageType.OBSERVATION,
                    ReceiverMessageType.IDENTITY_WARNING,
                    ItConfigManager.getConfig().enableFakeSystemErrors ? ReceiverMessageType.SYSTEM_ERROR : ReceiverMessageType.OBSERVATION
            };
            return types[random.nextInt(types.length)];
        }

        ReceiverMessageType[] types = {
                ReceiverMessageType.SYSTEM_ERROR,
                ReceiverMessageType.PERSONAL_SIGNAL,
                ReceiverMessageType.MANIFESTATION
        };
        return types[random.nextInt(types.length)];
    }

    private String chooseMessageText(ServerPlayerEntity player, HorrorPhase phase, ReceiverMessageType type) {
        String name = player.getGameProfile().name();
        ServerWorld world = player.getEntityWorld();
        boolean alone = progressionManager.isAlone(player, 48.0);
        int y = player.getBlockY();

        return switch (type) {
            case WEATHER -> pickWeatherMessage(world);
            case LOCAL_ALERT -> pick(
                    "日落后不建议进入洞穴。",
                    "地下有移动声，方向无法确认。",
                    "不要回应来自地下的声音。",
                    "矿道回声正在重复你的动作。",
                    "低光照区域出现未登记移动。",
                    "如果听见奔跑声，请留在原地。"
            );
            case OBSERVATION -> pick(
                    "目标进入低光照区域。",
                    "目标独处时间过长。",
                    "目标听见脚步声后停止移动。",
                    "目标附近的动物出现短暂凝视。",
                    "目标多次确认身后空间。",
                    "目标没有发现缺失项。"
            );
            case IDENTITY_WARNING -> pick(
                    "不要只凭外观确认身份。",
                    "名字正确不代表身份正确。",
                    "语音确认失败。建议沉默。",
                    "不要相信第一个回复你的人。",
                    "它会等你先开口。"
            );
            case SYSTEM_ERROR -> pick(
                    "玩家数量校验不一致。",
                    "未知实体未能卸载。",
                    "身份确认流程失败。",
                    "接收器信号源：内部。",
                    "房间边界校验失败。"
            );
            case PERSONAL_SIGNAL -> {
                if (!ItConfigManager.getConfig().enablePersonalizedSignals) {
                    yield "目标位置已被记录。";
                }

                yield pick(
                        name + " 当前位于地表以下。",
                        name + (alone ? " 当前处于独处状态。" : " 附近存在其他玩家。"),
                        name + " 当前 Y 坐标：" + y + "。",
                        "维度记录：" + world.getRegistryKey().getValue() + "。",
                        "接收器已经贴近 " + name + "。"
                );
            }
            case MANIFESTATION -> phase == HorrorPhase.MANIFESTATION ? pick(
                    "直接接触已确认。",
                    "它不再只是看着。",
                    "它离你很近了....",
                    "不要眨眼。",
                    "检测到信号干扰。",
                    "目标正被直接确认。"
            ) : "信号已衰减。";
            case CHASE -> pick(
                    "它开始移动了。",
                    "信号源正在接近。",
                    "距离正在缩短。",
                    "信号源：身后。",
                    "不要停下。"
            );
            case CAVE_STALKER -> pick(
                    "矿洞下方有移动信号。",
                    "矿洞信号中断。",
                    "看看你的后面...",
                    "它一直在后面。",
                    "接触已确认。"
            );
        };
    }

    private String pickWeatherMessage(ServerWorld world) {
        String weather;
        if (world.isThundering()) {
            weather = "雷暴";
        } else if (world.isRaining()) {
            weather = "降雨";
        } else {
            weather = "晴朗";
        }

        long dayTime = world.getTimeOfDay() % 24000L;
        String timeSegment = dayTime >= 13000L && dayTime <= 23000L ? "夜间" : "白日";
        return pick(
                "今日天气：" + weather + "。当前时段：" + timeSegment + "。",
                "天气频道已接通：" + weather + "。请保留接收器。",
                "地表能见度记录：" + weather + "。夜间数据另行核对。",
                "环境报告已更新：" + weather + "，" + timeSegment + "。"
        );
    }

    private String pick(String... values) {
        return values[random.nextInt(values.length)];
    }

    private double multiplier() {
        ItConfig config = ItConfigManager.getConfig();
        return config.receiverMessageChanceMultiplier * config.eventChanceMultiplier;
    }

    private double phaseOneWatchingLevelMultiplier(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        double maxWatchingLevel = Math.max(1.0, config.maxWatchingLevel);
        double watchingPercent = Math.max(0.0, Math.min(1.0, progressionManager.getData(player).watchingLevel / maxWatchingLevel));
        return 1.0 + watchingPercent * 0.35;
    }

    private int randomEventDelayTicks(int fallbackMinTicks, int fallbackMaxTicks) {
        ItConfig config = ItConfigManager.getConfig();
        int min = config.enableDelayedReceiverMessages ? Math.max(0, config.receiverEventMessageMinDelayTicks) : 0;
        int max = config.enableDelayedReceiverMessages ? Math.max(min, config.receiverEventMessageMaxDelayTicks) : 0;
        if (min == 0 && max == 0) {
            min = fallbackMinTicks;
            max = Math.max(min, fallbackMaxTicks);
        }
        return randomBetween(min, max);
    }

    private int randomBetween(int min, int max) {
        if (max < min) {
            max = min;
        }
        return min + random.nextInt(max - min + 1);
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }
}
