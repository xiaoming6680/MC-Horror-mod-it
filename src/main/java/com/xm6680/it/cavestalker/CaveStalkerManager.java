package com.xm6680.it.cavestalker;

import com.xm6680.it.ItMod;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.analog.ReceiverMessageType;
import com.xm6680.it.chase.ChaseManager;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.entity.CaveStalkerEntity;
import com.xm6680.it.entity.ModEntities;
import com.xm6680.it.entity.TargetOnlyEntityVisibility;
import com.xm6680.it.event.EventChanceScaler;
import com.xm6680.it.item.ModItems;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.Blocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class CaveStalkerManager {
    private static final int TICKS_PER_SECOND = 20;
    private static final int NATURAL_CHECK_INTERVAL_TICKS = 400;
    private static final String TRAP_MESSAGE = "看看你的后面...";
    private static final String TRAP_ABORT_MESSAGE = "后方信号丢失。";

    private final HorrorProgressionManager progressionManager;
    private final ReceiverManager receiverManager;
    private final ChaseManager chaseManager;
    private final Map<UUID, CaveStalkerState> states = new HashMap<>();
    private final Map<UUID, Long> nextCaveStalkerTicks = new HashMap<>();
    private final Random random = new Random();

    public CaveStalkerManager(HorrorProgressionManager progressionManager, ReceiverManager receiverManager, ChaseManager chaseManager) {
        this.progressionManager = progressionManager;
        this.receiverManager = receiverManager;
        this.chaseManager = chaseManager;
    }

    public void tick(MinecraftServer server, long currentTick) {
        tickActive(server, currentTick);

        if (currentTick % NATURAL_CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isSpectator()) {
                triggerCaveStalker(player, currentTick, false);
            }
        }
    }

    public boolean triggerCaveStalker(ServerPlayerEntity player, long currentTick, boolean forced) {
        if (states.containsKey(player.getUuid())) {
            return false;
        }

        ItConfig config = ItConfigManager.getConfig();
        if (!forced && !canTriggerNaturally(player, currentTick, config)) {
            return false;
        }

        int warningTicks = Math.max(20, config.caveStalkerWarningTicks);
        int chaseTicks = Math.max(200, config.caveStalkerMaxChaseTicks);
        CaveStalkerState state = new CaveStalkerState(player.getUuid(), currentTick, warningTicks, chaseTicks, forced);
        states.put(player.getUuid(), state);
        nextCaveStalkerTicks.put(player.getUuid(), currentTick + secondsToTicks(config.caveStalkerCooldownSeconds));
        progressionManager.recordCaveStalkerStarted(player);

        receiverManager.addMessageSilently(player, ReceiverMessageType.CAVE_STALKER, HorrorPhase.MANIFESTATION, "矿洞下方有移动信号。");
        sendReceiverWarning(player);
        playWarningSound(player);

        if (config.enableCaveStalkerOverlay) {
            ItNetwork.sendCaveStalkerOverlay(player, true, warningTicks + 80, config.reduceFlashingEffects || config.disableRapidFlashes ? 0.28F : 0.42F);
        }

        return true;
    }

    public boolean stopCaveStalker(ServerPlayerEntity player, long currentTick) {
        CaveStalkerState state = states.get(player.getUuid());
        if (state == null) {
            return false;
        }

        finish(player, state, currentTick, CaveStalkerPhase.END, false, null);
        return true;
    }

    public void remove(ServerPlayerEntity player) {
        CaveStalkerState state = states.remove(player.getUuid());
        if (state != null) {
            discardStalker(player, state);
            clearClientState(player);
        }
    }

    public void resetCooldown(ServerPlayerEntity player) {
        nextCaveStalkerTicks.remove(player.getUuid());
    }

    public long getNextCaveStalkerTick(ServerPlayerEntity player) {
        return nextCaveStalkerTicks.getOrDefault(player.getUuid(), 0L);
    }

    public CaveStalkerState getState(ServerPlayerEntity player) {
        return states.get(player.getUuid());
    }

    public boolean isActive(ServerPlayerEntity player) {
        return states.containsKey(player.getUuid());
    }

    public void onReceiverOpened(ServerPlayerEntity player, long currentTick) {
        CaveStalkerState state = states.get(player.getUuid());
        if (state == null || !state.isWaitingForReceiver()) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        state.phase = CaveStalkerPhase.RECEIVER_TRAP_MESSAGE;
        state.trapTriggered = true;
        progressionManager.recordCaveStalkerTrapTriggered(player);
        receiverManager.addMessageSilently(player, ReceiverMessageType.CAVE_STALKER, HorrorPhase.MANIFESTATION, TRAP_MESSAGE);
        captureTrapForward(player, state);

        if (config.caveStalkerForceLookBack) {
            player.setYaw(player.getYaw() + 180.0F);
        }

        state.phase = CaveStalkerPhase.WAITING_FOR_LOOK_BACK;
        state.lookBackTicksRemaining = Math.max(200, config.caveStalkerLookBackTimeoutTicks);
        state.phaseTicksRemaining = state.lookBackTicksRemaining;
        freezePlayer(player);
        ItNetwork.sendFirstPersonLock(player, true, state.lookBackTicksRemaining + config.caveStalkerLookBackDelayTicks + config.caveStalkerTurnBackTimeoutTicks + 120);
        ItNetwork.sendReceiverDistortion(player, Math.max(60, config.caveStalkerFreezeTicks), 0.55F);
        if (config.enableCaveStalkerOverlay) {
            ItNetwork.sendCaveStalkerOverlay(player, true, state.lookBackTicksRemaining + config.caveStalkerLookBackDelayTicks + 80, 0.45F);
        }
        playTrapSound(player);
    }

    public void handleCaughtByEntity(CaveStalkerEntity stalker, ServerPlayerEntity target) {
        CaveStalkerState state = states.get(target.getUuid());
        if (state == null || state.caught || state.phase != CaveStalkerPhase.CHASING) {
            stalker.discard();
            return;
        }

        if (state.stalkerEntityId != null && !state.stalkerEntityId.equals(stalker.getUuid())) {
            stalker.discard();
            return;
        }

        caught(target, state, target.getEntityWorld().getServer().getTicks(), stalker);
    }

    public String getStatusLine(ServerPlayerEntity player, long currentTick) {
        CaveStalkerState state = states.get(player.getUuid());
        long cooldown = Math.max(0L, getNextCaveStalkerTick(player) - currentTick);
        if (state == null) {
            return "Cave Stalker：未触发，冷却剩余 " + formatTicks(cooldown);
        }

        boolean hasEntity = findStalker(player, state) != null;
        int remaining = switch (state.phase) {
            case WARNING -> state.warningTicksRemaining;
            case CHASING -> state.chaseTicksRemaining;
            case WAITING_FOR_LOOK_BACK -> state.lookBackTicksRemaining;
            case LOOK_BACK_EMPTY -> state.lookBackDelayTicks;
            case WAITING_FOR_PLAYER_TURN_BACK, SPAWNED_BEHIND -> state.turnBackTicksRemaining;
            default -> state.phaseTicksRemaining;
        };
        return "Cave Stalker：" + state.phase + "，实体=" + hasEntity + "，剩余=" + remaining + " ticks，冷却剩余 " + formatTicks(cooldown);
    }

    public static boolean isCaveLikeEnvironment(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        boolean skyVisible = world.isSkyVisible(pos);
        boolean belowCaveLine = player.getY() < 50.0D;
        boolean belowSeaLevel = player.getY() < world.getSeaLevel();
        boolean lowLight = world.getLightLevel(pos) <= 7;
        boolean stoneNearby = hasStoneNearby(world, pos);
        return (!skyVisible && (belowCaveLine || belowSeaLevel)) || (!skyVisible && lowLight && stoneNearby);
    }

    private static boolean hasStoneNearby(ServerWorld world, BlockPos center) {
        int stoneBlocks = 0;
        for (int x = -3; x <= 3; x += 2) {
            for (int y = -2; y <= 2; y += 2) {
                for (int z = -3; z <= 3; z += 2) {
                    if (isCaveStone(world.getBlockState(center.add(x, y, z)).getBlock())) {
                        stoneBlocks++;
                        if (stoneBlocks >= 4) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean isCaveStone(net.minecraft.block.Block block) {
        return block == Blocks.STONE
                || block == Blocks.DEEPSLATE
                || block == Blocks.TUFF
                || block == Blocks.GRANITE
                || block == Blocks.DIORITE
                || block == Blocks.ANDESITE
                || block == Blocks.CALCITE
                || block == Blocks.DRIPSTONE_BLOCK;
    }

    private void tickActive(MinecraftServer server, long currentTick) {
        List<UUID> ids = List.copyOf(states.keySet());
        for (UUID playerId : ids) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            CaveStalkerState state = states.get(playerId);
            if (state == null) {
                continue;
            }

            if (player == null || player.isSpectator() || !player.isAlive()) {
                if (player != null) {
                    finish(player, state, currentTick, CaveStalkerPhase.END, false, null);
                } else {
                    states.remove(playerId);
                }
                continue;
            }

            switch (state.phase) {
                case WARNING -> tickWarning(player, state, currentTick);
                case CHASING -> tickChasing(player, state, currentTick);
                case RECEIVER_VIBRATION_WARNING -> tickReceiverWarning(player, state);
                case WAITING_FOR_RECEIVER_OPEN -> tickWaitingForReceiver(player, state);
                case WAITING_FOR_LOOK_BACK -> tickWaitingForLookBack(player, state, currentTick);
                case LOOK_BACK_EMPTY -> tickLookBackEmpty(player, state, currentTick);
                case SPAWNED_BEHIND, WAITING_FOR_PLAYER_TURN_BACK -> tickWaitingForTurnBack(player, state, currentTick);
                default -> {
                }
            }
        }
    }

    private void tickWarning(ServerPlayerEntity player, CaveStalkerState state, long currentTick) {
        state.warningTicksRemaining--;
        state.phaseTicksRemaining = state.warningTicksRemaining;
        if (state.warningTicksRemaining > 0) {
            if (state.warningTicksRemaining % 40 == 0) {
                playWarningSound(player);
            }
            return;
        }

        if (!spawnFarStalker(player, state, state.forced)) {
            finish(player, state, currentTick, CaveStalkerPhase.END, false, null);
            return;
        }

        state.phase = CaveStalkerPhase.CHASING;
        state.phaseTicksRemaining = state.chaseTicksRemaining;
        playChaseStartSound(player);
        if (ItConfigManager.getConfig().enableCaveStalkerOverlay) {
            ItNetwork.sendCaveStalkerOverlay(player, true, state.chaseTicksRemaining + 120, ItConfigManager.getConfig().reduceFlashingEffects ? 0.42F : 0.62F);
        }
    }

    private void tickChasing(ServerPlayerEntity player, CaveStalkerState state, long currentTick) {
        CaveStalkerEntity stalker = findStalker(player, state);
        if (stalker == null || !stalker.isAlive()) {
            enterReceiverWarning(player, state, currentTick);
            return;
        }

        state.chaseTicksRemaining--;
        state.phaseTicksRemaining = state.chaseTicksRemaining;
        double distance = Math.sqrt(stalker.squaredDistanceTo(player));

        if (distance <= ItConfigManager.getConfig().caveStalkerCatchDistance) {
            caught(player, state, currentTick, stalker);
            return;
        }

        stalker.tryMoveToTarget(player);
        tickBlockedChecks(player, state, stalker, distance, currentTick);
        if (!states.containsKey(player.getUuid()) || state.phase != CaveStalkerPhase.CHASING) {
            return;
        }

        tickEscapeChecks(player, state, distance, currentTick);
        if (!states.containsKey(player.getUuid()) || state.phase != CaveStalkerPhase.CHASING) {
            return;
        }

        tickMidChaseFakeRescue(player, state, currentTick);
        tickAtmosphere(player, state, stalker, currentTick);
        tickHeldReceiverHint(player, stalker, currentTick);

        if (state.chaseTicksRemaining <= 0) {
            escaped(player, state, currentTick, "你甩掉了矿洞里的脚步声。");
        }
    }

    private void tickMidChaseFakeRescue(ServerPlayerEntity player, CaveStalkerState state, long currentTick) {
        if (state.fakeRescueSent || state.initialChaseTicks <= 0 || currentTick < state.nextFakeRescueAttemptTick) {
            return;
        }

        int elapsed = state.initialChaseTicks - state.chaseTicksRemaining;
        if (elapsed < Math.max(60, state.initialChaseTicks / 3)) {
            state.nextFakeRescueAttemptTick = currentTick + 20L;
            return;
        }

        state.nextFakeRescueAttemptTick = currentTick + 30L;
        boolean lateChase = state.chaseTicksRemaining <= Math.max(80, state.initialChaseTicks / 4);
        if (lateChase || random.nextDouble() < 0.82D) {
            state.fakeRescueSent = ItMod.getMultiplayerDreadManager().triggerLinkedFakeRescue(player, true);
        }
    }

    private void tickBlockedChecks(ServerPlayerEntity player, CaveStalkerState state, CaveStalkerEntity stalker, double distance, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        double movedSq = stalker.squaredDistanceTo(state.lastStalkerX, state.lastStalkerY, state.lastStalkerZ);
        if (movedSq > 0.012D) {
            state.notMovingTicks = 0;
            state.lastStalkerX = stalker.getX();
            state.lastStalkerY = stalker.getY();
            state.lastStalkerZ = stalker.getZ();
        } else {
            state.notMovingTicks++;
        }

        boolean improved = distance < state.lastDistanceToPlayer - 0.12D;
        boolean blockedView = !stalker.canSee(player);
        boolean pathIdle = stalker.getNavigation().isIdle();
        if ((pathIdle || state.notMovingTicks >= 12) && currentTick % 5 == 0) {
            boolean restarted = stalker.tryMoveToTarget(player);
            pathIdle = stalker.getNavigation().isIdle();
            if (restarted && !pathIdle) {
                state.blockedTicks = Math.max(0, state.blockedTicks - 2);
            }
        }

        if (improved) {
            state.blockedTicks = 0;
            state.noLineOfSightTicks = 0;
            state.lastDistanceToPlayer = distance;
            return;
        }

        if (distance < state.lastDistanceToPlayer) {
            state.lastDistanceToPlayer = distance;
        }

        state.noLineOfSightTicks = blockedView ? state.noLineOfSightTicks + 1 : 0;

        if (pathIdle || state.notMovingTicks >= 20) {
            state.blockedTicks++;
        } else if (blockedView && state.noLineOfSightTicks >= config.caveStalkerBlockedTicksToVanish) {
            state.blockedTicks++;
        } else {
            state.blockedTicks = Math.max(0, state.blockedTicks - 1);
        }

        if (state.blockedTicks >= config.caveStalkerBlockedTicksToVanish) {
            enterReceiverWarning(player, state, currentTick);
        }
    }

    private void tickEscapeChecks(ServerPlayerEntity player, CaveStalkerState state, double distance, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!progressionManager.isAlone(player, config.caveStalkerSafePlayerDistance)) {
            state.nearPlayerTicks++;
        } else {
            state.nearPlayerTicks = 0;
        }

        if (state.nearPlayerTicks >= config.caveStalkerNearPlayerEscapeTicks) {
            escaped(player, state, currentTick, "多人信号重叠。它停下了。");
            return;
        }

        if (distance >= config.caveStalkerEscapeDistance) {
            state.escapeDistanceTicks++;
        } else {
            state.escapeDistanceTicks = 0;
        }

        if (state.escapeDistanceTicks >= Math.max(80, config.caveStalkerNearPlayerEscapeTicks)) {
            escaped(player, state, currentTick, "距离恢复安全。");
            return;
        }

        if (config.caveStalkerEndsOnSafeSurface && isSafeSurface(player)) {
            escaped(player, state, currentTick, "地表信号恢复稳定。");
        }
    }

    private void tickReceiverWarning(ServerPlayerEntity player, CaveStalkerState state) {
        state.receiverWarningTicks--;
        state.phaseTicksRemaining = state.receiverWarningTicks;
        if (state.receiverWarningTicks <= 0) {
            state.phase = CaveStalkerPhase.WAITING_FOR_RECEIVER_OPEN;
            state.phaseTicksRemaining = 0;
        }
    }

    private void tickWaitingForReceiver(ServerPlayerEntity player, CaveStalkerState state) {
        if (player.age % 80 == 0 && ItConfigManager.getConfig().enableCaveStalkerReceiverWarning) {
            sendReceiverWarning(player);
        }
        state.phaseTicksRemaining = 0;
    }

    private void tickWaitingForLookBack(ServerPlayerEntity player, CaveStalkerState state, long currentTick) {
        freezePlayer(player);
        state.lookBackTicksRemaining--;
        state.phaseTicksRemaining = state.lookBackTicksRemaining;

        if (hasLookedBehindTrapForward(player, state)) {
            state.lookedBackOnce = true;
            state.phase = CaveStalkerPhase.LOOK_BACK_EMPTY;
            state.lookBackDelayTicks = Math.max(10, ItConfigManager.getConfig().caveStalkerLookBackDelayTicks);
            state.phaseTicksRemaining = state.lookBackDelayTicks;
            ItNetwork.sendReceiverDistortion(player, state.lookBackDelayTicks + 30, 0.38F);
            return;
        }

        if (state.lookBackTicksRemaining <= 0) {
            state.phase = CaveStalkerPhase.EASTER_EGG_END;
            receiverManager.addMessageSilently(player, ReceiverMessageType.CAVE_STALKER, HorrorPhase.MANIFESTATION, "老弟不吃压啊");
            progressionManager.recordCaveStalkerEasterEgg(player);
            finish(player, state, currentTick, CaveStalkerPhase.EASTER_EGG_END, false, null);
        }
    }

    private void tickLookBackEmpty(ServerPlayerEntity player, CaveStalkerState state, long currentTick) {
        freezePlayer(player);
        state.lookBackDelayTicks--;
        state.phaseTicksRemaining = state.lookBackDelayTicks;
        if (state.lookBackDelayTicks > 0) {
            return;
        }

        if (!spawnBehindPlayer(player, state)) {
            receiverManager.addMessageSilently(player, ReceiverMessageType.CAVE_STALKER, HorrorPhase.MANIFESTATION, TRAP_ABORT_MESSAGE);
            finish(player, state, currentTick, CaveStalkerPhase.END, false, null);
            return;
        }

        state.phase = CaveStalkerPhase.WAITING_FOR_PLAYER_TURN_BACK;
        state.turnBackTicksRemaining = 0;
        state.phaseTicksRemaining = 0;
    }

    private void tickWaitingForTurnBack(ServerPlayerEntity player, CaveStalkerState state, long currentTick) {
        freezePlayer(player);
        CaveStalkerEntity stalker = findStalker(player, state);
        if (stalker == null || !stalker.isAlive()) {
            receiverManager.addMessageSilently(player, ReceiverMessageType.CAVE_STALKER, HorrorPhase.MANIFESTATION, TRAP_ABORT_MESSAGE);
            finish(player, state, currentTick, CaveStalkerPhase.END, false, null);
            return;
        }

        if (isLookingAt(player, stalker, ItConfigManager.getConfig().caveStalkerTriggerAngleDot, ItConfigManager.getConfig().caveStalkerTriggerDistance)) {
            state.phase = CaveStalkerPhase.FACE_SCARE;
            triggerTrapFaceScare(player, state, currentTick, stalker);
            return;
        }

        state.phaseTicksRemaining = 0;
        if (currentTick >= state.nextAtmosphereSoundTime) {
            state.nextAtmosphereSoundTime = currentTick + 55L + random.nextInt(55);
            Vec3d source = directionalSourceNearPlayer(player, stalker.currentPosition(), 4.0D);
            playCaveStalkerBreath(player, source, 0.42F);
        }
    }

    private boolean canTriggerNaturally(ServerPlayerEntity player, long currentTick, ItConfig config) {
        if (!config.enableCaveStalker) {
            return false;
        }

        if (progressionManager.getPhase(player).getNumber() < config.caveStalkerMinPhase) {
            return false;
        }

        if (chaseManager.isChasing(player)) {
            return false;
        }

        if (currentTick < nextCaveStalkerTicks.getOrDefault(player.getUuid(), 0L)) {
            return false;
        }

        if (config.caveStalkerOnlyUnderground && !isCaveLikeEnvironment(player)) {
            return false;
        }

        if (player.getEntityWorld().getLightLevel(player.getBlockPos()) > 7) {
            return false;
        }

        if (!progressionManager.isAlone(player, config.caveStalkerSafePlayerDistance)) {
            return false;
        }

        PlayerHorrorData data = progressionManager.getData(player);
        if (data.receiverMessagesReceived < 8
                || data.watcherSightings < 1) {
            return false;
        }

        double chance = EventChanceScaler.clampChance(0.045D
                * config.caveStalkerChanceMultiplier
                * config.eventChanceMultiplier
                * EventChanceScaler.phaseFiveHighPressureEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() > chance) {
            nextCaveStalkerTicks.put(player.getUuid(), currentTick + secondsToTicks(90 + random.nextInt(121)));
            return false;
        }

        return true;
    }

    private boolean spawnFarStalker(ServerPlayerEntity player, CaveStalkerState state, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        double min = Math.max(8.0D, config.caveStalkerSpawnMinDistance);
        double max = Math.max(min + 4.0D, config.caveStalkerSpawnMaxDistance);
        if (trySpawnFarStalkerInRange(player, state, min, max, 96, false)) {
            return true;
        }

        if (forced) {
            double fallbackMin = Math.max(4.0D, config.caveStalkerForcedSpawnFallbackMinDistance);
            return trySpawnFarStalkerInRange(player, state, fallbackMin, Math.max(fallbackMin + 8.0D, min), 96, true);
        }

        return false;
    }

    private boolean trySpawnFarStalkerInRange(ServerPlayerEntity player, CaveStalkerState state, double min, double max, int attempts, boolean allowNoPath) {
        ItConfig config = ItConfigManager.getConfig();
        ServerWorld world = player.getEntityWorld();
        for (BlockPos pos : findSpawnPositions(player, min, max, attempts)) {
            CaveStalkerEntity stalker = createStalker(player, pos);
            if (stalker == null) {
                continue;
            }

            stalker.beginStalk(player, state.chaseTicksRemaining + 80, config.caveStalkerWalkSpeed);
            if (!world.spawnEntity(stalker)) {
                continue;
            }
            TargetOnlyEntityVisibility.hideFromNonTargetPlayers(stalker);
            boolean pathStarted = stalker.tryMoveToTarget(player);
            if (!pathStarted && !allowNoPath) {
                stalker.discard();
                continue;
            }

            state.stalkerEntityId = stalker.getUuid();
            state.lastDistanceToPlayer = Math.sqrt(stalker.squaredDistanceTo(player));
            state.lastStalkerX = stalker.getX();
            state.lastStalkerY = stalker.getY();
            state.lastStalkerZ = stalker.getZ();
            return true;
        }

        return false;
    }

    private boolean spawnBehindPlayer(ServerPlayerEntity player, CaveStalkerState state) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        if (look.lengthSquared() < 0.01D) {
            look = new Vec3d(0.0D, 0.0D, 1.0D);
        }

        ServerWorld world = player.getEntityWorld();
        for (double distance : new double[]{2.1D, 1.8D, 2.5D, 3.0D}) {
            Vec3d target = new Vec3d(player.getX(), player.getY(), player.getZ()).subtract(look.multiply(distance));
            BlockPos pos = BlockPos.ofFloored(target.x, target.y, target.z);
            Optional<BlockPos> standable = findNearbyStandable(world, pos);
            if (standable.isEmpty()) {
                continue;
            }

            if (player.squaredDistanceTo(standable.get().getX() + 0.5D, standable.get().getY(), standable.get().getZ() + 0.5D) < 1.35D * 1.35D) {
                continue;
            }

            CaveStalkerEntity stalker = createStalker(player, standable.get());
            if (stalker == null) {
                continue;
            }

            stalker.beginTrapStill(player, Math.max(80, ItConfigManager.getConfig().caveStalkerTurnBackTimeoutTicks + 40));
            state.stalkerEntityId = stalker.getUuid();
            state.phase = CaveStalkerPhase.SPAWNED_BEHIND;
            world.spawnEntity(stalker);
            TargetOnlyEntityVisibility.hideFromNonTargetPlayers(stalker);
            playBehindSpawnSound(player);
            return true;
        }

        return false;
    }

    private CaveStalkerEntity createStalker(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getEntityWorld();
        CaveStalkerEntity stalker = ModEntities.CAVE_STALKER.create(world, entity -> {
        }, pos, SpawnReason.TRIGGERED, false, false);
        if (stalker == null) {
            return null;
        }

        stalker.refreshPositionAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYaw(), 0.0F);
        stalker.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, player.getEyePos());
        return stalker;
    }

    private List<BlockPos> findSpawnPositions(ServerPlayerEntity player, double minDistance, double maxDistance, int attempts) {
        ServerWorld world = player.getEntityWorld();
        List<BlockPos> positions = new java.util.ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = minDistance + random.nextDouble() * Math.max(1.0D, maxDistance - minDistance);
            int yOffset = random.nextInt(13) - 6;
            BlockPos candidate = BlockPos.ofFloored(
                    player.getX() + Math.cos(angle) * distance,
                    player.getY() + yOffset,
                    player.getZ() + Math.sin(angle) * distance
            );
            Optional<BlockPos> standable = findNearbyStandable(world, candidate);
            if (standable.isPresent()
                    && player.squaredDistanceTo(standable.get().getX() + 0.5D, standable.get().getY(), standable.get().getZ() + 0.5D) >= minDistance * minDistance) {
                positions.add(standable.get());
            }
        }

        return positions;
    }

    private Optional<BlockPos> findNearbyStandable(ServerWorld world, BlockPos center) {
        for (int y = 3; y >= -5; y--) {
            BlockPos pos = center.add(0, y, 0);
            if (isSafeStandable(world, pos)) {
                return Optional.of(pos);
            }
        }

        for (int radius = 1; radius <= 3; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) {
                        continue;
                    }
                    for (int y = 2; y >= -4; y--) {
                        BlockPos pos = center.add(x, y, z);
                        if (isSafeStandable(world, pos)) {
                            return Optional.of(pos);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private boolean isSafeStandable(ServerWorld world, BlockPos pos) {
        if (!world.getBlockState(pos).isAir() || !world.getBlockState(pos.up()).isAir()) {
            return false;
        }

        if (!world.getFluidState(pos).isEmpty() || !world.getFluidState(pos.up()).isEmpty()) {
            return false;
        }

        if (!world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
            return false;
        }

        if (world.getLightLevel(pos) > 11) {
            return false;
        }

        Box box = new Box(pos.getX() + 0.2D, pos.getY(), pos.getZ() + 0.2D, pos.getX() + 0.8D, pos.getY() + 1.95D, pos.getZ() + 0.8D);
        return world.isSpaceEmpty(box);
    }

    private void enterReceiverWarning(ServerPlayerEntity player, CaveStalkerState state, long currentTick) {
        state.phase = CaveStalkerPhase.BLOCKED_VANISH;
        CaveStalkerEntity stalker = findStalker(player, state);
        if (stalker != null) {
            player.getEntityWorld().spawnParticles(ParticleTypes.SMOKE, stalker.getX(), stalker.getY() + 0.3D, stalker.getZ(), 22, 0.35D, 0.4D, 0.35D, 0.01D);
            stalker.discard();
        }

        state.stalkerEntityId = null;
        state.phase = CaveStalkerPhase.RECEIVER_VIBRATION_WARNING;
        state.receiverWarningTicks = 60;
        state.phaseTicksRemaining = state.receiverWarningTicks;
        receiverManager.addMessageSilently(player, ReceiverMessageType.CAVE_STALKER, HorrorPhase.MANIFESTATION, "矿洞信号中断。");
        sendReceiverWarning(player);
        ItNetwork.sendReceiverDistortion(player, 100, 0.72F);
        if (ItConfigManager.getConfig().enableCaveStalkerOverlay) {
            ItNetwork.sendCaveStalkerOverlay(player, true, 220, 0.50F);
        }
        playBlockedVanishSound(player);
    }

    private void escaped(ServerPlayerEntity player, CaveStalkerState state, long currentTick, String message) {
        receiverManager.addMessageSilently(player, ReceiverMessageType.CAVE_STALKER, HorrorPhase.MANIFESTATION, message);
        progressionManager.recordCaveStalkerEscape(player);
        finish(player, state, currentTick, CaveStalkerPhase.ESCAPED, false, null);
    }

    private void caught(ServerPlayerEntity player, CaveStalkerState state, long currentTick, CaveStalkerEntity stalker) {
        if (state.caught) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        state.caught = true;
        state.phase = CaveStalkerPhase.CAUGHT;
        float newHealth = player.getHealth() - config.caveStalkerDamage;
        player.setHealth(config.caveStalkerCanKillPlayer ? Math.max(0.0F, newHealth) : Math.max(1.0F, newHealth));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0, false, false, true));
        receiverManager.addMessageSilently(player, ReceiverMessageType.CAVE_STALKER, HorrorPhase.MANIFESTATION, "距离：0。");
        progressionManager.recordCaveStalkerCaught(player);
        triggerFaceScare(player);
        playCaughtSound(player);
        finish(player, state, currentTick, CaveStalkerPhase.CAUGHT, false, stalker);
    }

    private void triggerTrapFaceScare(ServerPlayerEntity player, CaveStalkerState state, long currentTick, CaveStalkerEntity stalker) {
        receiverManager.addMessageSilently(player, ReceiverMessageType.CAVE_STALKER, HorrorPhase.MANIFESTATION, "它一直在后面。");
        triggerFaceScare(player);
        playCaughtSound(player);
        finish(player, state, currentTick, CaveStalkerPhase.FACE_SCARE, false, stalker);
    }

    private void triggerFaceScare(ServerPlayerEntity player) {
        int duration = Math.max(35, ItConfigManager.getConfig().caveStalkerFaceScareDurationTicks);
        ItNetwork.sendCaveStalkerFaceScare(player, duration, 1.0F, true);
        ItNetwork.sendReceiverDistortion(player, 90, 0.80F);
    }

    private void finish(ServerPlayerEntity player, CaveStalkerState state, long currentTick, CaveStalkerPhase finalPhase, boolean leaveOverlay, CaveStalkerEntity knownStalker) {
        if (knownStalker != null) {
            knownStalker.discard();
        } else {
            discardStalker(player, state);
        }

        if (!leaveOverlay) {
            clearClientState(player);
        }

        progressionManager.recordCaveStalkerEnded(player, currentTick);
        states.remove(player.getUuid());
    }

    private void discardStalker(ServerPlayerEntity player, CaveStalkerState state) {
        CaveStalkerEntity stalker = findStalker(player, state);
        if (stalker != null) {
            stalker.discard();
        }
    }

    private CaveStalkerEntity findStalker(ServerPlayerEntity player, CaveStalkerState state) {
        if (state.stalkerEntityId == null) {
            return null;
        }

        List<? extends CaveStalkerEntity> stalkers = player.getEntityWorld()
                .getEntitiesByType(TypeFilter.instanceOf(CaveStalkerEntity.class), entity -> entity.getUuid().equals(state.stalkerEntityId));
        return stalkers.isEmpty() ? null : stalkers.get(0);
    }

    private void clearClientState(ServerPlayerEntity player) {
        ItNetwork.sendCaveStalkerOverlay(player, false, 0, 0.0F);
        ItNetwork.sendFirstPersonLock(player, false, 0);
    }

    private void freezePlayer(ServerPlayerEntity player) {
        player.setVelocity(0.0D, Math.min(0.0D, player.getVelocity().y), 0.0D);
        player.setSprinting(false);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 6, 10, false, false, false));
    }

    private boolean isSafeSurface(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        return world.isSkyVisible(pos) && player.getY() >= world.getSeaLevel() - 4 && world.getLightLevel(pos) >= 10;
    }

    private void captureTrapForward(ServerPlayerEntity player, CaveStalkerState state) {
        Vec3d look = horizontal(player.getRotationVec(1.0F));
        state.trapForwardX = look.x;
        state.trapForwardZ = look.z;
    }

    private boolean hasLookedBehindTrapForward(ServerPlayerEntity player, CaveStalkerState state) {
        Vec3d current = horizontal(player.getRotationVec(1.0F));
        double dot = current.x * state.trapForwardX + current.z * state.trapForwardZ;
        return dot <= -0.55D;
    }

    private boolean isLookingAt(ServerPlayerEntity player, CaveStalkerEntity stalker, double dotThreshold, double maxDistance) {
        Vec3d toStalker = stalker.getEyePos().subtract(player.getEyePos());
        if (toStalker.lengthSquared() > maxDistance * maxDistance || toStalker.lengthSquared() < 0.01D) {
            return false;
        }

        double dot = player.getRotationVec(1.0F).normalize().dotProduct(toStalker.normalize());
        return dot >= dotThreshold && player.canSee(stalker);
    }

    private Vec3d horizontal(Vec3d vec) {
        Vec3d horizontal = new Vec3d(vec.x, 0.0D, vec.z);
        if (horizontal.lengthSquared() < 0.01D) {
            return new Vec3d(0.0D, 0.0D, 1.0D);
        }
        return horizontal.normalize();
    }

    private void tickAtmosphere(ServerPlayerEntity player, CaveStalkerState state, CaveStalkerEntity stalker, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (config.enableCaveStalkerFootsteps && currentTick - state.lastFootstepTime >= 12 + random.nextInt(9)) {
            state.lastFootstepTime = currentTick;
            Vec3d source = directionalSourceNearPlayer(player, stalker.currentPosition(), 9.0D);
            SoundEvent step = random.nextInt(3) == 0 ? SoundEvents.BLOCK_SCULK_STEP : random.nextBoolean() ? SoundEvents.BLOCK_STONE_STEP : SoundEvents.BLOCK_DEEPSLATE_STEP;
            sendSoundToPlayer(player, step, SoundCategory.HOSTILE, source, Math.max(0.0F, config.caveStalkerFootstepVolume), 0.48F + random.nextFloat() * 0.10F);
        }

        if (currentTick >= state.nextAtmosphereSoundTime) {
            state.nextAtmosphereSoundTime = currentTick + 80L + random.nextInt(80);
            Vec3d source = directionalSourceNearPlayer(player, stalker.currentPosition(), 10.0D);
            sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, source, 0.45F, 0.48F + random.nextFloat() * 0.08F);
            sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, source, 0.42F, 0.58F + random.nextFloat() * 0.08F);
            playCaveStalkerBreath(player, source, 0.34F);
        }
    }

    private void tickHeldReceiverHint(ServerPlayerEntity player, CaveStalkerEntity stalker, long currentTick) {
        if (currentTick % 16 != 0) {
            return;
        }

        if (!player.getMainHandStack().isOf(ModItems.RECEIVER) && !player.getOffHandStack().isOf(ModItems.RECEIVER)) {
            return;
        }

        double distance = Math.sqrt(stalker.squaredDistanceTo(player));
        String hint = distance <= 8.0D ? "它在矿洞里靠近。" : distance <= 20.0D ? "脚步声仍在后面。" : "矿洞信号不稳定。";
        int color = distance <= 8.0D ? 0xFFFF3D3D : 0xFFE0C86C;
        ItNetwork.sendChaseDistanceHint(player, hint, color, 18);
    }

    private void sendReceiverWarning(ServerPlayerEntity player) {
        if (ItConfigManager.getConfig().enableCaveStalkerReceiverWarning) {
            receiverManager.notifyStrongSignal(player);
        }
    }

    private void playWarningSound(ServerPlayerEntity player) {
        Vec3d source = behindPlayer(player, 4.0D);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, source, 0.58F, 0.52F + random.nextFloat() * 0.06F);
    }

    private void playChaseStartSound(ServerPlayerEntity player) {
        Vec3d source = behindPlayer(player, 8.0D);
        sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, source, 1.2F, 0.42F + random.nextFloat() * 0.08F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, source, 0.62F, 0.70F);
    }

    private void playBlockedVanishSound(ServerPlayerEntity player) {
        Vec3d source = behindPlayer(player, 3.0D);
        sendSoundToPlayer(player, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.AMBIENT, source, 0.42F, 0.35F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, source, 0.70F, 0.72F);
    }

    private void playTrapSound(ServerPlayerEntity player) {
        Vec3d source = behindPlayer(player, 2.0D);
        sendSoundToPlayer(player, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE, SoundCategory.AMBIENT, source, 0.45F, 0.45F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST, SoundCategory.AMBIENT, source, 0.50F, 0.50F);
    }

    private void playBehindSpawnSound(ServerPlayerEntity player) {
        Vec3d source = behindPlayer(player, 4.0D);
        sendSoundToPlayer(player, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.HOSTILE, source, 0.42F, 0.62F);
        playCaveStalkerBreath(player, source, 0.48F);
    }

    private void playCaughtSound(ServerPlayerEntity player) {
        Vec3d source = behindPlayer(player, 1.2D);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE, source, 0.85F, 0.55F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, source, 0.72F, 0.80F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, source, 0.85F, 1.85F);
    }

    private Vec3d behindPlayer(ServerPlayerEntity player, double distance) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        if (look.lengthSquared() < 0.01D) {
            look = new Vec3d(0.0D, 0.0D, 1.0D);
        }
        return new Vec3d(player.getX(), player.getY() + 0.8D, player.getZ()).subtract(look.multiply(distance));
    }

    private void playCaveStalkerBreath(ServerPlayerEntity player, Vec3d source, float volume) {
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_SNIFF, SoundCategory.HOSTILE, source, volume, 0.42F + random.nextFloat() * 0.06F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_TENDRIL_CLICKS, SoundCategory.HOSTILE, source, volume * 0.45F, 0.36F + random.nextFloat() * 0.05F);
    }

    private Vec3d directionalSourceNearPlayer(ServerPlayerEntity player, Vec3d actualSource, double maxDistance) {
        Vec3d playerPos = new Vec3d(player.getX(), player.getY() + 0.8D, player.getZ());
        Vec3d direction = actualSource.subtract(playerPos);
        if (direction.lengthSquared() < 0.01D) {
            return playerPos;
        }

        return playerPos.add(direction.normalize().multiply(Math.min(maxDistance, direction.length())));
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, pos, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(sound, category, pos.x, pos.y, pos.z, volume, pitch, random.nextLong());
        player.networkHandler.sendPacket(packet);
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }

    private String formatTicks(long ticks) {
        long seconds = Math.max(0L, ticks / TICKS_PER_SECOND);
        return (seconds / 60L) + "分" + (seconds % 60L) + "秒";
    }
}
