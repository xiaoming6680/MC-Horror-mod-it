package com.xm6680.it.event;

import com.mojang.authlib.GameProfile;
import com.xm6680.it.ItMod;
import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.analog.ReceiverMessageType;
import com.xm6680.it.analog.ReceiverRecordPolicy;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.entity.TargetOnlyEntityVisibility;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import com.xm6680.it.watching.HorrorPhase;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MultiplayerDreadManager {
    private static final int TICKS_PER_SECOND = 20;
    private static final Field PLAYER_LIST_ENTRIES_FIELD = resolvePlayerListEntriesField();
    private static final double MIMIC_WALK_SPEED = 0.105D;
    private static final double MIMIC_LOOK_AT_PLAYER_DISTANCE = 9.0D;
    private static final int MIMIC_LOOK_AT_PLAYER_MIN_TICKS = 18;
    private static final int MIMIC_LOOK_AT_PLAYER_MAX_TICKS = 46;

    private static final String[] SEPARATION_MESSAGES = {
            "你离他们太远了。",
            "单独目标已确认。",
            "队伍完整性下降。",
            "其中一个人已经离队。",
            "它更容易找到独处的人。",
            "群体信号已断开。"
    };

    private static final String[] TEAMMATE_FOOTSTEP_MESSAGES = {
            "脚步数量与玩家数量不一致。",
            "检测到重复的玩家脚步。",
            "它学会了一个人的脚步。",
            "声源不是队友。"
    };

    private static final String[] TEAM_GAZE_TARGET_MESSAGES = {
            "它们认出了你。",
            "多个目标同时确认了你的位置。",
            "不是所有人都被看见了。",
            "你被选中了。"
    };

    private static final String[] FAKE_TAB_MESSAGES = {
            "玩家数量不一致。",
            "未登记玩家出现在列表中。",
            "列表记录已被污染。",
            "该玩家没有加入记录。"
    };

    private static final String[] FAKE_ADVANCEMENT_MESSAGES = {
            "进度记录不存在。",
            "未登记进度被显示。",
            "成就来源无法确认。"
    };

    private static final String[] FAKE_RESCUE_HELPFUL = {
            "去找别人。",
            "它不喜欢你们站在一起。",
            "接收器响的时候，不要马上回头。",
            "你可以选择不回头。",
            "如果它追你，别跑进死路。",
            "它靠近时，声音会先出现。"
    };

    private static final String[] FAKE_RESCUE_MISLEADING = {
            "往左。",
            "继续往下。",
            "你已经安全了。",
            "别去找他们。",
            "光会让它看见你。",
            "站着别动。"
    };

    private static final FakeAdvancementLine[] FAKE_ADVANCEMENTS = {
            new FakeAdvancementLine("It 达成了进度", "找到你了"),
            new FakeAdvancementLine("Unknown 达成了进度", "深入地底"),
            new FakeAdvancementLine("未知信号 达成了进度", "保持连接"),
            new FakeAdvancementLine("陌生人 达成了进度", "回到队伍"),
            new FakeAdvancementLine("It 达成了进度", "别回头"),
            new FakeAdvancementLine("missing_player 达成了进度", "甜蜜的梦")
    };

    private final HorrorProgressionManager progressionManager;
    private final ReceiverManager receiverManager;
    private final Map<UUID, Long> separatedSinceTicks = new HashMap<>();
    private final Map<UUID, Long> nextSeparationWarningTicks = new HashMap<>();
    private final Map<UUID, Long> nextFakeTeammateFootstepTicks = new HashMap<>();
    private final Map<UUID, Long> nextTeamGazeTicks = new HashMap<>();
    private final Map<UUID, Long> nextFakeTabTicks = new HashMap<>();
    private final Map<UUID, Long> nextFakeAdvancementTicks = new HashMap<>();
    private final Map<UUID, Long> nextFakeRescueTicks = new HashMap<>();
    private final Map<UUID, Long> nextMimicPlayerTicks = new HashMap<>();
    private final List<ActiveFootstepSequence> activeFootsteps = new ArrayList<>();
    private final List<ActiveTeamGaze> activeTeamGazes = new ArrayList<>();
    private final List<ActiveFakeTab> activeFakeTabs = new ArrayList<>();
    private final List<ActiveMimicPlayer> activeMimicPlayers = new ArrayList<>();
    private final Random random = new Random();

    public MultiplayerDreadManager(HorrorProgressionManager progressionManager, ReceiverManager receiverManager) {
        this.progressionManager = progressionManager;
        this.receiverManager = receiverManager;
    }

    public void tickActive(MinecraftServer server, long currentTick) {
        tickFootsteps(server, currentTick);
        tickTeamGazes(server, currentTick);
        tickFakeTabs(server, currentTick);
        tickMimicPlayers(server, currentTick);
    }

    public void tick(MinecraftServer server, long currentTick) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) {
                continue;
            }

            trySeparationWarning(player, currentTick);
            tryFakeTeammateFootsteps(player, currentTick);
            tryTeamGaze(player, currentTick);
            tryFakeTab(player, currentTick);
            tryFakeAdvancement(player, currentTick);
            tryMimicPlayer(player, currentTick);
            tryFakeRescue(player, currentTick);
        }
    }

    public boolean triggerSeparationWarning(ServerPlayerEntity player, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!forced && !canRunNatural(player, config.enableSeparationWarning, config.separationWarningMinPhase, 0L)) {
            return false;
        }

        sendReceiver(player, ReceiverMessageType.OBSERVATION, pick(SEPARATION_MESSAGES), 40 + random.nextInt(80), ReceiverRecordPolicy.IMPORTANT);
        progressionManager.recordSeparationWarning(player);
        nextSeparationWarningTicks.put(player.getUuid(), player.getEntityWorld().getServer().getTicks() + secondsToTicks(Math.max(60, config.separationWarningCooldownSeconds)));
        return true;
    }

    public boolean triggerFakeTeammateFootsteps(ServerPlayerEntity player, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        if (!forced && !canRunNatural(player, config.enableFakeTeammateFootsteps, config.fakeTeammateFootstepsMinPhase, currentTick)) {
            return false;
        }

        int steps = randomBetween(config.fakeTeammateFootstepsStepCountMin, config.fakeTeammateFootstepsStepCountMax);
        activeFootsteps.removeIf(sequence -> sequence.playerUuid.equals(player.getUuid()));
        activeFootsteps.add(new ActiveFootstepSequence(
                player.getUuid(),
                currentTick + randomBetween(4, 12),
                steps,
                rearAngle(player),
                4.0D + random.nextDouble() * 5.0D,
                stepSoundFor(player)
        ));
        ItMod.getMinorAnomalyAccumulator().recordMinorSound(player, "fake_footstep");
        sendReceiver(player, ReceiverMessageType.OBSERVATION, pick(TEAMMATE_FOOTSTEP_MESSAGES), config.fakeTeammateFootstepsReceiverDelayTicks, ReceiverRecordPolicy.SAMPLED);
        progressionManager.recordFakeTeammateFootstepEvent(player);
        nextFakeTeammateFootstepTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(60, config.fakeTeammateFootstepsCooldownSeconds)));
        return true;
    }

    public boolean triggerTeamGaze(ServerPlayerEntity player, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        if (!forced && !canRunNatural(player, config.enableTeamSynchronizedGaze, config.teamGazeMinPhase, currentTick)) {
            return false;
        }

        List<ServerPlayerEntity> group = nearbyPlayers(player, config.teamGazePlayerGroupRadius);
        if (!forced && group.size() < 2) {
            return false;
        }

        ServerPlayerEntity target = forced || group.isEmpty() ? player : group.get(random.nextInt(group.size()));
        List<LivingEntity> entities = eligibleGazeEntities(target, config);
        if (entities.size() < Math.max(1, config.teamGazeMinEntities)) {
            return false;
        }

        shuffle(entities);
        int count = Math.min(entities.size(), Math.max(1, config.teamGazeMaxEntities));
        long untilTick = currentTick + Math.max(20, config.teamGazeDurationTicks);
        activeTeamGazes.removeIf(gaze -> gaze.targetUuid.equals(target.getUuid()));
        for (int i = 0; i < count; i++) {
            LivingEntity entity = entities.get(i);
            activeTeamGazes.add(new ActiveTeamGaze(entity.getId(), target.getUuid(), untilTick));
            holdGaze(entity, target);
        }

        sendReceiver(target, ReceiverMessageType.OBSERVATION, pick(TEAM_GAZE_TARGET_MESSAGES), config.teamGazeReceiverDelayTicks, ReceiverRecordPolicy.SAMPLED);
        progressionManager.recordTeamGazeEvent(target);
        nextTeamGazeTicks.put(target.getUuid(), currentTick + secondsToTicks(Math.max(60, config.teamGazeCooldownSeconds)));
        return true;
    }

    public boolean triggerFakeTab(ServerPlayerEntity player, boolean forced) {
        return showFakeTabEntry(player, randomFakeTabName(ItConfigManager.getConfig()), ItConfigManager.getConfig().fakeTabListDurationTicks, false, forced);
    }

    public boolean showFakeTabEntry(ServerPlayerEntity player, String name, int durationTicks, boolean fromFakeJoin) {
        return showFakeTabEntry(player, name, durationTicks, fromFakeJoin, true);
    }

    public boolean triggerFakeAdvancement(ServerPlayerEntity player, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        if (!forced && !canRunNatural(player, config.enableFakeAdvancementEvent, config.fakeAdvancementMinPhase, currentTick)) {
            return false;
        }

        FakeAdvancementLine line = FAKE_ADVANCEMENTS[random.nextInt(FAKE_ADVANCEMENTS.length)];
        ItNetwork.sendFakeAdvancement(player, line.title(), line.description(), config.fakeAdvancementUseToastIfPossible);
        sendReceiver(player, ReceiverMessageType.SYSTEM_ERROR, pick(FAKE_ADVANCEMENT_MESSAGES), 80, ReceiverRecordPolicy.IMPORTANT);
        progressionManager.recordFakeAdvancementEvent(player);
        nextFakeAdvancementTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(60, config.fakeAdvancementCooldownSeconds)));
        return true;
    }

    public boolean triggerMimicPlayer(ServerPlayerEntity player, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        MinecraftServer server = player.getEntityWorld().getServer();
        long currentTick = server.getTicks();
        if (!forced && !canRunNatural(player, config.enableMimicPlayerEvent, config.mimicPlayerMinPhase, currentTick)) {
            return false;
        }

        if (activePlayerCount(server) < 2 || hasActiveMimicPlayer(player.getUuid())) {
            return false;
        }

        ServerPlayerEntity copiedPlayer = chooseCopiedPlayer(player);
        if (copiedPlayer == null) {
            return false;
        }

        Vec3d spawnPos = findMimicSpawnPos(player, config);
        if (spawnPos == null) {
            return false;
        }

        GameProfile mimicProfile = copiedProfile(copiedPlayer);
        FakePlayer mimic = FakePlayer.get(player.getEntityWorld(), mimicProfile);
        mimic.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, yawToward(spawnPos, player.getEyePos()), 0.0F);
        mimic.setInvulnerable(true);
        mimic.setSilent(true);
        mimic.setCustomName(Text.literal(mimicProfile.name()));
        mimic.setCustomNameVisible(true);
        if (!sendMimicPlayerInfoAdd(player, mimicProfile)) {
            return false;
        }
        TargetOnlyEntityVisibility.registerExternalTarget(mimic, player);
        if (!player.getEntityWorld().spawnEntity(mimic)) {
            TargetOnlyEntityVisibility.unregisterExternalTarget(mimic);
            sendFakeTabRemove(player, mimic.getUuid());
            return false;
        }
        TargetOnlyEntityVisibility.hideFromNonTargetPlayers(mimic);
        activeMimicPlayers.add(new ActiveMimicPlayer(
                player.getUuid(),
                mimic,
                currentTick + Math.max(60, config.mimicPlayerDurationTicks),
                findMimicWalkTarget(mimic, player),
                currentTick + randomBetween(35, 70),
                currentTick + randomBetween(25, 60)
        ));
        nextMimicPlayerTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(60, config.mimicPlayerCooldownSeconds)));
        return true;
    }

    public int clearMimicPlayers() {
        int removed = 0;
        Iterator<ActiveMimicPlayer> iterator = activeMimicPlayers.iterator();
        while (iterator.hasNext()) {
            removeMimicPlayer(iterator.next());
            iterator.remove();
            removed++;
        }
        return removed;
    }

    public Vec3d getActiveMimicPlayerPosition(ServerPlayerEntity target) {
        for (ActiveMimicPlayer mimic : activeMimicPlayers) {
            if (mimic.targetUuid.equals(target.getUuid())) {
                return new Vec3d(mimic.fakePlayer.getX(), mimic.fakePlayer.getY(), mimic.fakePlayer.getZ());
            }
        }
        return null;
    }

    public boolean triggerFakeRescue(ServerPlayerEntity player, Boolean helpfulOverride, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        if (!forced && !canRunFakeRescue(player, currentTick, config)) {
            return false;
        }

        boolean helpful = helpfulOverride != null ? helpfulOverride : shouldSendHelpfulRescue(player, config);
        String text = helpful ? pick(FAKE_RESCUE_HELPFUL) : pick(FAKE_RESCUE_MISLEADING);
        sendMysteriousContact(player, text);
        progressionManager.recordFakeRescueMessage(player);
        nextFakeRescueTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(60, config.fakeRescueCooldownSeconds)));
        return true;
    }

    public boolean triggerLinkedFakeRescue(ServerPlayerEntity player, boolean caveStalker) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableFakeRescueMessages) {
            return false;
        }

        if (caveStalker && !config.fakeRescueAllowDuringCaveStalker) {
            return false;
        }

        if (!caveStalker && !config.fakeRescueAllowDuringChase) {
            return false;
        }

        Boolean helpful = player.getHealth() <= 6.0F ? Boolean.TRUE : null;
        return triggerFakeRescue(player, helpful, true);
    }

    public void remove(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        separatedSinceTicks.remove(uuid);
        nextSeparationWarningTicks.remove(uuid);
        nextFakeTeammateFootstepTicks.remove(uuid);
        nextTeamGazeTicks.remove(uuid);
        nextFakeTabTicks.remove(uuid);
        nextFakeAdvancementTicks.remove(uuid);
        nextFakeRescueTicks.remove(uuid);
        nextMimicPlayerTicks.remove(uuid);
        activeFootsteps.removeIf(sequence -> sequence.playerUuid.equals(uuid));
        activeTeamGazes.removeIf(gaze -> gaze.targetUuid.equals(uuid));
        Iterator<ActiveMimicPlayer> mimicIterator = activeMimicPlayers.iterator();
        while (mimicIterator.hasNext()) {
            ActiveMimicPlayer mimic = mimicIterator.next();
            if (mimic.targetUuid.equals(uuid)) {
                removeMimicPlayer(mimic);
                mimicIterator.remove();
            }
        }
        Iterator<ActiveFakeTab> iterator = activeFakeTabs.iterator();
        while (iterator.hasNext()) {
            ActiveFakeTab tab = iterator.next();
            if (tab.targetUuid.equals(uuid)) {
                sendFakeTabRemove(player, tab.fakeUuid);
                iterator.remove();
            }
        }
    }

    private void trySeparationWarning(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableSeparationWarning
                || activePlayerCount(player.getEntityWorld().getServer()) < 2
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.separationWarningMinPhase))
                || isStrongEventActive(player, currentTick)) {
            separatedSinceTicks.remove(player.getUuid());
            return;
        }

        if (!progressionManager.isAlone(player, config.separationWarningDistance)) {
            separatedSinceTicks.remove(player.getUuid());
            return;
        }

        UUID uuid = player.getUuid();
        separatedSinceTicks.putIfAbsent(uuid, currentTick);
        if (currentTick - separatedSinceTicks.get(uuid) < config.separationWarningMinTicks
                || currentTick < nextSeparationWarningTicks.getOrDefault(uuid, 0L)) {
            return;
        }

        double chance = EventChanceScaler.clampChance(0.22D * ItConfigManager.getConfig().eventChanceMultiplier);
        if (random.nextDouble() <= chance) {
            triggerSeparationWarning(player, false);
        }
    }

    private void tryFakeTeammateFootsteps(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableFakeTeammateFootsteps
                || (config.fakeTeammateFootstepsRequireMultiplayer && activePlayerCount(player.getEntityWorld().getServer()) < 2)
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.fakeTeammateFootstepsMinPhase))
                || !progressionManager.isAlone(player, Math.min(32.0D, config.separationWarningDistance))
                || isStrongEventActive(player, currentTick)
                || currentTick < nextFakeTeammateFootstepTicks.getOrDefault(player.getUuid(), 0L)) {
            return;
        }

        double chance = EventChanceScaler.clampChance(0.055D * config.eventChanceMultiplier * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() <= chance) {
            triggerFakeTeammateFootsteps(player, false);
        }
    }

    private void tryTeamGaze(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableTeamSynchronizedGaze
                || activePlayerCount(player.getEntityWorld().getServer()) < 2
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.teamGazeMinPhase))
                || isStrongEventActive(player, currentTick)
                || currentTick < nextTeamGazeTicks.getOrDefault(player.getUuid(), 0L)
                || nearbyPlayers(player, config.teamGazePlayerGroupRadius).size() < 2) {
            return;
        }

        double chance = EventChanceScaler.clampChance(0.045D * config.eventChanceMultiplier * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() <= chance) {
            triggerTeamGaze(player, false);
        }
    }

    private void tryFakeTab(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableFakeTabListEvent
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.fakeTabListMinPhase))
                || isStrongEventActive(player, currentTick)
                || currentTick < nextFakeTabTicks.getOrDefault(player.getUuid(), 0L)) {
            return;
        }

        double chance = EventChanceScaler.clampChance(0.025D * config.eventChanceMultiplier * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() <= chance) {
            triggerFakeTab(player, false);
        }
    }

    private void tryFakeAdvancement(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableFakeAdvancementEvent
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.fakeAdvancementMinPhase))
                || isStrongEventActive(player, currentTick)
                || currentTick < nextFakeAdvancementTicks.getOrDefault(player.getUuid(), 0L)) {
            return;
        }

        double chance = EventChanceScaler.clampChance(0.028D * config.eventChanceMultiplier * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() <= chance) {
            triggerFakeAdvancement(player, false);
        }
    }

    private void tryMimicPlayer(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableMimicPlayerEvent
                || activePlayerCount(player.getEntityWorld().getServer()) < 2
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.mimicPlayerMinPhase))
                || isStrongEventActive(player, currentTick)
                || hasActiveMimicPlayer(player.getUuid())
                || currentTick < nextMimicPlayerTicks.getOrDefault(player.getUuid(), 0L)) {
            return;
        }

        double chance = EventChanceScaler.clampChance(0.018D
                * config.eventChanceMultiplier
                * config.mimicPlayerChanceMultiplier
                * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() <= chance) {
            triggerMimicPlayer(player, false);
        }
    }

    private void tryFakeRescue(ServerPlayerEntity player, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        boolean chaseActive = ItMod.getChaseManager().isChasing(player);
        boolean caveActive = ItMod.getCaveStalkerManager().isActive(player);
        if (chaseActive || caveActive) {
            return;
        }

        if (!config.enableFakeRescueMessages
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.fakeRescueMinPhase))
                || currentTick < nextFakeRescueTicks.getOrDefault(player.getUuid(), 0L)
                || !isRescueContext(player, config)) {
            return;
        }

        double baseChance = 0.22D;
        double chance = EventChanceScaler.clampChance(baseChance * config.eventChanceMultiplier);
        if (random.nextDouble() <= chance) {
            triggerFakeRescue(player, null, false);
        }
    }

    private boolean showFakeTabEntry(ServerPlayerEntity player, String name, int durationTicks, boolean fromFakeJoin, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        if (!forced && !canRunNatural(player, config.enableFakeTabListEvent, config.fakeTabListMinPhase, currentTick)) {
            return false;
        }

        if (fromFakeJoin && !config.fakeTabListIntegrateWithFakeJoin) {
            return false;
        }

        UUID fakeUuid = UUID.randomUUID();
        if (!sendFakeTabAdd(player, fakeUuid, name, fromFakeJoin)) {
            return false;
        }
        activeFakeTabs.add(new ActiveFakeTab(player.getUuid(), fakeUuid, currentTick + Math.max(20, durationTicks)));
        sendReceiver(player, ReceiverMessageType.SYSTEM_ERROR, pick(FAKE_TAB_MESSAGES), 80, ReceiverRecordPolicy.IMPORTANT);
        progressionManager.recordFakeTabEvent(player);
        nextFakeTabTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(60, config.fakeTabListCooldownSeconds)));
        return true;
    }

    private void tickFootsteps(MinecraftServer server, long currentTick) {
        Iterator<ActiveFootstepSequence> iterator = activeFootsteps.iterator();
        while (iterator.hasNext()) {
            ActiveFootstepSequence sequence = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(sequence.playerUuid);
            if (player == null || player.isSpectator() || !player.isAlive()) {
                iterator.remove();
                continue;
            }

            if (currentTick < sequence.nextStepTick) {
                continue;
            }

            Vec3d pos = footstepPosition(player, sequence);
            sendSoundToPlayer(player, sequence.sound, SoundCategory.PLAYERS, pos, 0.62F, 0.83F + random.nextFloat() * 0.14F);
            sequence.playedSteps++;
            if (sequence.playedSteps >= sequence.totalSteps) {
                iterator.remove();
                continue;
            }

            sequence.nextStepTick = currentTick + randomBetween(7, 14);
        }
    }

    private void tickTeamGazes(MinecraftServer server, long currentTick) {
        Iterator<ActiveTeamGaze> iterator = activeTeamGazes.iterator();
        while (iterator.hasNext()) {
            ActiveTeamGaze gaze = iterator.next();
            if (currentTick >= gaze.untilTick) {
                iterator.remove();
                continue;
            }

            ServerPlayerEntity target = server.getPlayerManager().getPlayer(gaze.targetUuid);
            if (target == null || target.isSpectator()) {
                iterator.remove();
                continue;
            }

            Entity entity = target.getEntityWorld().getEntityById(gaze.entityId);
            if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
                iterator.remove();
                continue;
            }

            holdGaze(living, target);
        }
    }

    private void tickFakeTabs(MinecraftServer server, long currentTick) {
        Iterator<ActiveFakeTab> iterator = activeFakeTabs.iterator();
        while (iterator.hasNext()) {
            ActiveFakeTab tab = iterator.next();
            if (currentTick < tab.removeTick) {
                continue;
            }

            ServerPlayerEntity target = server.getPlayerManager().getPlayer(tab.targetUuid);
            if (target != null) {
                sendFakeTabRemove(target, tab.fakeUuid);
            }
            iterator.remove();
        }
    }

    private void tickMimicPlayers(MinecraftServer server, long currentTick) {
        Iterator<ActiveMimicPlayer> iterator = activeMimicPlayers.iterator();
        while (iterator.hasNext()) {
            ActiveMimicPlayer mimic = iterator.next();
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(mimic.targetUuid);
            if (target == null || target.isSpectator() || !target.isAlive() || mimic.fakePlayer.getEntityWorld() != target.getEntityWorld()) {
                removeMimicPlayer(mimic);
                iterator.remove();
                continue;
            }

            TargetOnlyEntityVisibility.hideFromNonTargetPlayers(mimic.fakePlayer);
            tickMimicMovement(mimic, target, currentTick);

            ItConfig config = ItConfigManager.getConfig();
            if (mimic.fakePlayer.squaredDistanceTo(target) <= config.mimicPlayerTriggerDistance * config.mimicPlayerTriggerDistance) {
                resolveMimicApproach(target, mimic, config);
                iterator.remove();
                continue;
            }

            if (currentTick >= mimic.removeTick || !mimic.fakePlayer.isAlive()) {
                removeMimicPlayer(mimic);
                iterator.remove();
            }
        }
    }

    private void tickMimicMovement(ActiveMimicPlayer mimic, ServerPlayerEntity target, long currentTick) {
        snapMimicToGround(mimic.fakePlayer, target.getEntityWorld());

        double lookDistanceSquared = MIMIC_LOOK_AT_PLAYER_DISTANCE * MIMIC_LOOK_AT_PLAYER_DISTANCE;
        if (mimic.fakePlayer.squaredDistanceTo(target) <= lookDistanceSquared
                && currentTick >= mimic.nextLookAtTargetTick) {
            mimic.lookAtTargetUntilTick = currentTick + randomBetween(MIMIC_LOOK_AT_PLAYER_MIN_TICKS, MIMIC_LOOK_AT_PLAYER_MAX_TICKS);
            mimic.nextLookAtTargetTick = mimic.lookAtTargetUntilTick + randomBetween(45, 115);
        }

        if (currentTick < mimic.lookAtTargetUntilTick) {
            mimic.fakePlayer.setVelocity(Vec3d.ZERO);
            mimic.fakePlayer.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.getEyePos());
            mimic.fakePlayer.setBodyYaw(mimic.fakePlayer.getYaw());
            mimic.fakePlayer.setHeadYaw(mimic.fakePlayer.getYaw());
            return;
        }

        if (mimic.walkTarget == null
                || currentTick >= mimic.nextWalkTargetTick
                || mimic.fakePlayer.squaredDistanceTo(mimic.walkTarget) < 0.9D) {
            mimic.walkTarget = findMimicWalkTarget(mimic.fakePlayer, target);
            mimic.nextWalkTargetTick = currentTick + randomBetween(45, 90);
        }

        if (mimic.walkTarget == null) {
            mimic.fakePlayer.setVelocity(Vec3d.ZERO);
            mimic.fakePlayer.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.getEyePos());
            mimic.fakePlayer.setBodyYaw(mimic.fakePlayer.getYaw());
            mimic.fakePlayer.setHeadYaw(mimic.fakePlayer.getYaw());
            return;
        }

        Vec3d current = new Vec3d(mimic.fakePlayer.getX(), mimic.fakePlayer.getY(), mimic.fakePlayer.getZ());
        Vec3d delta = mimic.walkTarget.subtract(current);
        double horizontalLength = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalLength < 0.05D) {
            mimic.fakePlayer.setVelocity(Vec3d.ZERO);
            return;
        }

        double speed = Math.min(MIMIC_WALK_SPEED, horizontalLength);
        Vec3d step = new Vec3d(delta.x / horizontalLength * speed, 0.0D, delta.z / horizontalLength * speed);
        Vec3d grounded = findGroundedMimicPosition(
                target.getEntityWorld(),
                current.add(step),
                mimic.fakePlayer.getBlockY(),
                4
        );
        if (grounded == null) {
            mimic.fakePlayer.setVelocity(Vec3d.ZERO);
            mimic.walkTarget = findMimicWalkTarget(mimic.fakePlayer, target);
            mimic.nextWalkTargetTick = currentTick + randomBetween(20, 45);
            return;
        }

        float yaw = yawToward(current, grounded);
        mimic.fakePlayer.refreshPositionAndAngles(grounded.x, grounded.y, grounded.z, yaw, 0.0F);
        mimic.fakePlayer.setVelocity(new Vec3d(grounded.x - current.x, grounded.y - current.y, grounded.z - current.z));
        mimic.fakePlayer.setYaw(yaw);
        mimic.fakePlayer.setBodyYaw(yaw);
        mimic.fakePlayer.setHeadYaw(yaw);
    }

    private void resolveMimicApproach(ServerPlayerEntity target, ActiveMimicPlayer mimic, ItConfig config) {
        if (random.nextDouble() < config.mimicPlayerWatcherTransformChance) {
            triggerMimicFaceScare(target, mimic, config);
        } else {
            vanishMimicPlayer(target, mimic);
        }
    }

    private void triggerMimicFaceScare(ServerPlayerEntity target, ActiveMimicPlayer mimic, ItConfig config) {
        Vec3d pos = new Vec3d(mimic.fakePlayer.getX(), mimic.fakePlayer.getY(), mimic.fakePlayer.getZ());
        removeMimicPlayer(mimic);
        int durationTicks = Math.max(
                70,
                Math.max(config.faceScareDurationTicks * 2, config.mimicPlayerWatcherLungeDelayTicks + config.mimicPlayerWatcherLungeTicks + 20)
        );
        ItNetwork.sendMimicFaceScare(target, durationTicks, 1.0F, true);
        sendSoundToPlayer(target, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, pos, 1.0F, 0.62F + random.nextFloat() * 0.12F);
        sendSoundToPlayer(target, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE, pos, 0.75F, 0.72F + random.nextFloat() * 0.12F);
    }

    private void vanishMimicPlayer(ServerPlayerEntity target, ActiveMimicPlayer mimic) {
        Vec3d pos = new Vec3d(mimic.fakePlayer.getX(), mimic.fakePlayer.getY(), mimic.fakePlayer.getZ());
        removeMimicPlayer(mimic);
        sendSoundToPlayer(target, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, pos, 0.68F, 1.55F + random.nextFloat() * 0.18F);
        sendSoundToPlayer(target, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, pos, 0.35F, 0.55F + random.nextFloat() * 0.12F);
    }

    private void removeMimicPlayer(ActiveMimicPlayer mimic) {
        MinecraftServer server = mimic.fakePlayer.getEntityWorld().getServer();
        ServerPlayerEntity target = server.getPlayerManager().getPlayer(mimic.targetUuid);
        if (target != null) {
            sendFakeTabRemove(target, mimic.fakePlayer.getUuid());
        }
        TargetOnlyEntityVisibility.unregisterExternalTarget(mimic.fakePlayer);
        mimic.fakePlayer.discard();
    }

    private boolean sendMimicPlayerInfoAdd(ServerPlayerEntity target, GameProfile profile) {
        PlayerListS2CPacket.Entry entry = new PlayerListS2CPacket.Entry(
                profile.id(),
                profile,
                false,
                0,
                GameMode.SURVIVAL,
                Text.literal(profile.name()),
                true,
                0,
                null
        );
        PlayerListS2CPacket packet = new PlayerListS2CPacket(
                EnumSet.of(
                        PlayerListS2CPacket.Action.ADD_PLAYER,
                        PlayerListS2CPacket.Action.UPDATE_GAME_MODE,
                        PlayerListS2CPacket.Action.UPDATE_LISTED,
                        PlayerListS2CPacket.Action.UPDATE_LATENCY,
                        PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME
                ),
                List.<ServerPlayerEntity>of()
        );
        if (!replacePlayerListEntries(packet, List.of(entry))) {
            return false;
        }
        target.networkHandler.sendPacket(packet);
        return true;
    }

    private boolean sendFakeTabAdd(ServerPlayerEntity player, UUID fakeUuid, String name, boolean fromFakeJoin) {
        Text displayName = fromFakeJoin
                ? Text.literal(name).formatted(Formatting.DARK_GRAY, Formatting.OBFUSCATED)
                : Text.literal(name).formatted(Formatting.DARK_GRAY);
        PlayerListS2CPacket.Entry entry = new PlayerListS2CPacket.Entry(
                fakeUuid,
                new GameProfile(fakeUuid, safeProfileName(name)),
                true,
                0,
                GameMode.SPECTATOR,
                displayName,
                false,
                0,
                null
        );
        PlayerListS2CPacket packet = new PlayerListS2CPacket(
                EnumSet.of(
                        PlayerListS2CPacket.Action.ADD_PLAYER,
                        PlayerListS2CPacket.Action.UPDATE_GAME_MODE,
                        PlayerListS2CPacket.Action.UPDATE_LISTED,
                        PlayerListS2CPacket.Action.UPDATE_LATENCY,
                        PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME
                ),
                List.<ServerPlayerEntity>of()
        );
        if (!replacePlayerListEntries(packet, List.of(entry))) {
            return false;
        }
        player.networkHandler.sendPacket(packet);
        return true;
    }

    private static Field resolvePlayerListEntriesField() {
        try {
            Field field = PlayerListS2CPacket.class.getDeclaredField("entries");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            for (Field field : PlayerListS2CPacket.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field;
                }
            }
            ItMod.LOGGER.warn("Unable to access PlayerListS2CPacket entries field for fake Tab entries.", exception);
            return null;
        }
    }

    private static boolean replacePlayerListEntries(PlayerListS2CPacket packet, List<PlayerListS2CPacket.Entry> entries) {
        if (PLAYER_LIST_ENTRIES_FIELD == null) {
            return false;
        }

        try {
            PLAYER_LIST_ENTRIES_FIELD.set(packet, entries);
            return true;
        } catch (IllegalAccessException exception) {
            ItMod.LOGGER.warn("Unable to write fake Tab entries into PlayerListS2CPacket.", exception);
            return false;
        }
    }

    private void sendFakeTabRemove(ServerPlayerEntity player, UUID fakeUuid) {
        player.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(fakeUuid)));
    }

    private String safeProfileName(String displayName) {
        String filtered = displayName.replaceAll("[^A-Za-z0-9_]", "");
        if (filtered.isBlank()) {
            return "Unknown";
        }
        return filtered.length() > 16 ? filtered.substring(0, 16) : filtered;
    }

    private String randomFakeTabName(ItConfig config) {
        String[] names = {"Unknown", "missing_player", "It", "ERR_000", "lost_player", "NULL"};
        String name = names[random.nextInt(names.length)];
        if (random.nextDouble() < config.fakeTabListUseObfuscatedNameChance) {
            return "ERR_" + random.nextInt(10) + random.nextInt(10) + random.nextInt(10);
        }
        return name;
    }

    private ServerPlayerEntity chooseCopiedPlayer(ServerPlayerEntity target) {
        List<ServerPlayerEntity> candidates = new ArrayList<>();
        for (ServerPlayerEntity player : target.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            if (player == target || player.isSpectator() || !player.isAlive() || player.getEntityWorld() != target.getEntityWorld()) {
                continue;
            }
            candidates.add(player);
        }

        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }

    private GameProfile copiedProfile(ServerPlayerEntity copiedPlayer) {
        GameProfile original = copiedPlayer.getGameProfile();
        return new GameProfile(UUID.randomUUID(), original.name(), original.properties());
    }

    private void snapMimicToGround(FakePlayer mimic, ServerWorld world) {
        Vec3d grounded = findGroundedMimicPosition(
                world,
                new Vec3d(mimic.getX(), mimic.getY(), mimic.getZ()),
                mimic.getBlockY(),
                4
        );
        if (grounded == null || Math.abs(grounded.y - mimic.getY()) < 0.001D) {
            return;
        }

        mimic.refreshPositionAndAngles(grounded.x, grounded.y, grounded.z, mimic.getYaw(), mimic.getPitch());
        mimic.setVelocity(Vec3d.ZERO);
    }

    private Vec3d findGroundedMimicPosition(ServerWorld world, Vec3d position, int baseY, int verticalSearch) {
        int x = (int) Math.floor(position.x);
        int z = (int) Math.floor(position.z);
        int limit = Math.max(1, verticalSearch);

        BlockPos level = new BlockPos(x, baseY, z);
        if (isSafeMimicPosition(world, level)) {
            return new Vec3d(position.x, level.getY(), position.z);
        }

        for (int dy = 1; dy <= limit; dy++) {
            BlockPos down = new BlockPos(x, baseY - dy, z);
            if (isSafeMimicPosition(world, down)) {
                return new Vec3d(position.x, down.getY(), position.z);
            }

            BlockPos up = new BlockPos(x, baseY + dy, z);
            if (isSafeMimicPosition(world, up)) {
                return new Vec3d(position.x, up.getY(), position.z);
            }
        }

        return null;
    }

    private Vec3d findMimicSpawnPos(ServerPlayerEntity target, ItConfig config) {
        ServerWorld world = target.getEntityWorld();
        double minDistance = Math.max(4.0D, config.mimicPlayerSpawnMinDistance);
        double maxDistance = Math.max(minDistance + 2.0D, config.mimicPlayerSpawnMaxDistance);
        Vec3d look = target.getRotationVec(1.0F).normalize();
        double baseAngle = Math.atan2(look.z, look.x);

        for (int attempt = 0; attempt < 36; attempt++) {
            double angle = baseAngle + Math.PI + (random.nextDouble() - 0.5D) * Math.PI * 1.4D;
            if (attempt % 4 == 0) {
                angle = random.nextDouble() * Math.PI * 2.0D;
            }
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            int x = (int) Math.floor(target.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(target.getZ() + Math.sin(angle) * distance);
            int baseY = target.getBlockY();

            for (int dy = -4; dy <= 4; dy++) {
                BlockPos pos = new BlockPos(x, baseY + dy, z);
                if (isSafeMimicPosition(world, pos)) {
                    return new Vec3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                }
            }
        }

        return null;
    }

    private Vec3d findMimicWalkTarget(FakePlayer mimic, ServerPlayerEntity target) {
        ServerWorld world = target.getEntityWorld();
        ItConfig config = ItConfigManager.getConfig();
        double minTargetDistance = Math.max(1.5D, config.mimicPlayerTriggerDistance + 1.25D);
        double maxTargetDistance = Math.max(minTargetDistance + 2.0D, config.mimicPlayerSpawnMaxDistance + 4.0D);
        BlockPos origin = mimic.getBlockPos();

        for (int attempt = 0; attempt < 18; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 2.0D + random.nextDouble() * 5.0D;
            int x = (int) Math.floor(mimic.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(mimic.getZ() + Math.sin(angle) * distance);

            for (int dy = -4; dy <= 4; dy++) {
                BlockPos pos = new BlockPos(x, origin.getY() + dy, z);
                Vec3d candidate = new Vec3d(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                double targetDistanceSquared = target.squaredDistanceTo(candidate);
                if (targetDistanceSquared < minTargetDistance * minTargetDistance
                        || targetDistanceSquared > maxTargetDistance * maxTargetDistance) {
                    continue;
                }
                if (isSafeMimicPosition(world, pos)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private boolean isSafeMimicPosition(ServerWorld world, BlockPos pos) {
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }

        BlockState ground = world.getBlockState(pos.down());
        if (!ground.isSolidBlock(world, pos.down()) || isDangerousMimicBlock(ground)) {
            return false;
        }

        for (int y = 0; y <= 1; y++) {
            BlockPos checkPos = pos.up(y);
            BlockState state = world.getBlockState(checkPos);
            if (!state.isAir() || isDangerousMimicBlock(state) || !world.getFluidState(checkPos).isEmpty()) {
                return false;
            }
        }

        return world.getFluidState(pos.down()).isEmpty();
    }

    private boolean isDangerousMimicBlock(BlockState state) {
        return state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.WATER)
                || state.isOf(Blocks.MAGMA_BLOCK)
                || state.isOf(Blocks.FIRE)
                || state.isOf(Blocks.SOUL_FIRE)
                || state.isOf(Blocks.CAMPFIRE)
                || state.isOf(Blocks.SOUL_CAMPFIRE)
                || state.isOf(Blocks.CACTUS)
                || state.isOf(Blocks.POWDER_SNOW)
                || state.isOf(Blocks.POINTED_DRIPSTONE)
                || state.isOf(Blocks.SWEET_BERRY_BUSH);
    }

    private float yawToward(Vec3d from, Vec3d to) {
        Vec3d direction = to.subtract(from);
        return (float) (Math.atan2(direction.z, direction.x) * 180.0D / Math.PI) - 90.0F;
    }

    private boolean hasActiveMimicPlayer(UUID targetUuid) {
        for (ActiveMimicPlayer mimic : activeMimicPlayers) {
            if (mimic.targetUuid.equals(targetUuid)) {
                return true;
            }
        }

        return false;
    }

    private List<ServerPlayerEntity> nearbyPlayers(ServerPlayerEntity player, double radius) {
        double maxDistanceSquared = radius * radius;
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity other : player.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            if (other.isSpectator() || other.getEntityWorld() != player.getEntityWorld()) {
                continue;
            }
            if (other.squaredDistanceTo(player) <= maxDistanceSquared) {
                players.add(other);
            }
        }
        return players;
    }

    private List<LivingEntity> eligibleGazeEntities(ServerPlayerEntity player, ItConfig config) {
        double radius = Math.max(4.0D, config.teamGazeEntityRadius);
        return player.getEntityWorld().getEntitiesByType(
                TypeFilter.instanceOf(LivingEntity.class),
                player.getBoundingBox().expand(radius),
                entity -> entity.isAlive()
                        && entity.squaredDistanceTo(player) > 4.0D
                        && (!config.teamGazeIgnoreNamedEntities || !entity.hasCustomName())
                        && (entity instanceof AnimalEntity || (config.teamGazeIncludeVillagers && entity instanceof VillagerEntity))
        );
    }

    private void holdGaze(LivingEntity entity, ServerPlayerEntity target) {
        if (entity instanceof AnimalEntity animal) {
            animal.getNavigation().stop();
            animal.setVelocity(Vec3d.ZERO);
        } else if (entity instanceof VillagerEntity villager) {
            villager.getNavigation().stop();
            villager.setVelocity(Vec3d.ZERO);
        }
        entity.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.getEyePos());
        entity.setBodyYaw(entity.getYaw());
        entity.setHeadYaw(entity.getYaw());
    }

    private boolean canRunNatural(ServerPlayerEntity player, boolean enabled, int minPhase, long currentTick) {
        return enabled
                && !player.isSpectator()
                && player.isAlive()
                && progressionManager.getPhase(player).isAtLeast(phaseFromNumber(minPhase))
                && !isStrongEventActive(player, currentTick);
    }

    private boolean isStrongEventActive(ServerPlayerEntity player, long currentTick) {
        return ItMod.getChaseManager().isChasing(player)
                || ItMod.getCaveStalkerManager().isActive(player)
                || ItNetwork.isJumpscareActive(player, currentTick)
                || ItNetwork.isFaceScareActive(player, currentTick)
                || ItNetwork.isManifestationOverlayActive(player, currentTick)
                || ItNetwork.isAnimalDisguiseRetaliationActive(player, currentTick);
    }

    private boolean isRescueContext(ServerPlayerEntity player, ItConfig config) {
        PlayerHorrorData data = progressionManager.getData(player);
        return ItMod.getChaseManager().isChasing(player)
                || ItMod.getCaveStalkerManager().isActive(player)
                || progressionManager.isAlone(player, config.separationWarningDistance)
                || player.getHealth() <= 8.0F
                || data.undergroundTicks >= 6 * 60 * TICKS_PER_SECOND;
    }

    private boolean shouldSendHelpfulRescue(ServerPlayerEntity player, ItConfig config) {
        if (player.getHealth() <= 4.0F) {
            return true;
        }
        return random.nextDouble() < config.fakeRescueHelpfulChance;
    }

    private void sendMysteriousContact(ServerPlayerEntity player, String line) {
        player.sendMessage(Text.literal("")
                .append(Text.literal("???").formatted(Formatting.GRAY, Formatting.ITALIC, Formatting.OBFUSCATED))
                .append(Text.literal("悄悄地对你说：" + line).formatted(Formatting.GRAY, Formatting.ITALIC)), false);
    }

    private boolean canRunFakeRescue(ServerPlayerEntity player, long currentTick, ItConfig config) {
        if (!config.enableFakeRescueMessages
                || player.isSpectator()
                || !player.isAlive()
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.fakeRescueMinPhase))) {
            return false;
        }

        boolean chaseActive = ItMod.getChaseManager().isChasing(player);
        boolean caveActive = ItMod.getCaveStalkerManager().isActive(player);
        if (chaseActive) {
            return config.fakeRescueAllowDuringChase;
        }

        if (caveActive) {
            return config.fakeRescueAllowDuringCaveStalker;
        }

        return !isStrongEventActive(player, currentTick);
    }

    private void sendReceiver(ServerPlayerEntity player, ReceiverMessageType type, String text, int delayTicks, ReceiverRecordPolicy policy) {
        if (AnalogHorrorManager.shouldRecord(random, progressionManager.getPhase(player), policy, ItConfigManager.getConfig())) {
            receiverManager.scheduleMessage(player, type, progressionManager.getPhase(player), text, Math.max(0, delayTicks));
        }
    }

    private Vec3d footstepPosition(ServerPlayerEntity player, ActiveFootstepSequence sequence) {
        double sideAngle = sequence.angle + Math.PI / 2.0D;
        double movement = sequence.playedSteps * 0.62D;
        double side = Math.sin(sequence.playedSteps * 1.1D) * 0.45D;
        return new Vec3d(
                player.getX() + Math.cos(sequence.angle) * Math.max(1.8D, sequence.distance - movement) + Math.cos(sideAngle) * side,
                player.getY(),
                player.getZ() + Math.sin(sequence.angle) * Math.max(1.8D, sequence.distance - movement) + Math.sin(sideAngle) * side
        );
    }

    private double rearAngle(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        double base = Math.atan2(-look.z, -look.x);
        return base + (random.nextDouble() - 0.5D) * 0.9D;
    }

    private SoundEvent stepSoundFor(ServerPlayerEntity player) {
        BlockState state = player.getEntityWorld().getBlockState(player.getBlockPos().down());
        if (state.isIn(BlockTags.WOODEN_STAIRS) || state.isIn(BlockTags.WOODEN_SLABS)
                || state.isOf(Blocks.OAK_PLANKS) || state.isOf(Blocks.SPRUCE_PLANKS)
                || state.isOf(Blocks.BIRCH_PLANKS) || state.isOf(Blocks.JUNGLE_PLANKS)
                || state.isOf(Blocks.ACACIA_PLANKS) || state.isOf(Blocks.DARK_OAK_PLANKS)
                || state.isOf(Blocks.MANGROVE_PLANKS) || state.isOf(Blocks.CHERRY_PLANKS)
                || state.isOf(Blocks.BAMBOO_PLANKS) || state.isOf(Blocks.CRIMSON_PLANKS)
                || state.isOf(Blocks.WARPED_PLANKS)) {
            return SoundEvents.BLOCK_WOOD_STEP;
        }
        if (state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.COARSE_DIRT) || state.isOf(Blocks.ROOTED_DIRT)) {
            return SoundEvents.BLOCK_GRASS_STEP;
        }
        return SoundEvents.BLOCK_STONE_STEP;
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, pos, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(sound, category, pos.x, pos.y, pos.z, volume, pitch, random.nextLong());
        player.networkHandler.sendPacket(packet);
    }

    private int activePlayerCount(MinecraftServer server) {
        int count = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isSpectator()) {
                count++;
            }
        }
        return count;
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

    private <T> void shuffle(List<T> values) {
        for (int i = values.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T temp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, temp);
        }
    }

    private String pick(String... values) {
        return values[random.nextInt(values.length)];
    }

    private int randomBetween(int min, int max) {
        if (max < min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }

    private record FakeAdvancementLine(String title, String description) {
    }

    private static final class ActiveFootstepSequence {
        private final UUID playerUuid;
        private long nextStepTick;
        private final int totalSteps;
        private final double angle;
        private final double distance;
        private final SoundEvent sound;
        private int playedSteps;

        private ActiveFootstepSequence(UUID playerUuid, long nextStepTick, int totalSteps, double angle, double distance, SoundEvent sound) {
            this.playerUuid = playerUuid;
            this.nextStepTick = nextStepTick;
            this.totalSteps = totalSteps;
            this.angle = angle;
            this.distance = distance;
            this.sound = sound;
        }
    }

    private record ActiveTeamGaze(int entityId, UUID targetUuid, long untilTick) {
    }

    private record ActiveFakeTab(UUID targetUuid, UUID fakeUuid, long removeTick) {
    }

    private static final class ActiveMimicPlayer {
        private final UUID targetUuid;
        private final FakePlayer fakePlayer;
        private final long removeTick;
        private Vec3d walkTarget;
        private long nextWalkTargetTick;
        private long lookAtTargetUntilTick;
        private long nextLookAtTargetTick;

        private ActiveMimicPlayer(UUID targetUuid, FakePlayer fakePlayer, long removeTick, Vec3d walkTarget, long nextWalkTargetTick, long nextLookAtTargetTick) {
            this.targetUuid = targetUuid;
            this.fakePlayer = fakePlayer;
            this.removeTick = removeTick;
            this.walkTarget = walkTarget;
            this.nextWalkTargetTick = nextWalkTargetTick;
            this.lookAtTargetUntilTick = 0L;
            this.nextLookAtTargetTick = nextLookAtTargetTick;
        }
    }
}
