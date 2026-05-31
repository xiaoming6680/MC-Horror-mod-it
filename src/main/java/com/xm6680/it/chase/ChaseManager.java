package com.xm6680.it.chase;

import com.xm6680.it.ItMod;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.analog.ReceiverMessageType;
import com.xm6680.it.cavestalker.CaveStalkerManager;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.entity.HuntingWatcherEntity;
import com.xm6680.it.entity.ModEntities;
import com.xm6680.it.entity.TargetOnlyEntityVisibility;
import com.xm6680.it.event.EventChanceScaler;
import com.xm6680.it.item.ModItems;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.SpawnReason;
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
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Owns Phase 5 hunt events: warning, chase, escape, and catch resolution.
 */
public class ChaseManager {
    private static final int TICKS_PER_SECOND = 20;
    private static final int NATURAL_CHECK_INTERVAL_TICKS = 400;
    private static final int MIN_TIME_IN_PHASE_FIVE_TICKS = 5 * 60 * TICKS_PER_SECOND;
    private static final double CHASE_CATCH_DISTANCE = 1.65;

    private static final String[] ESCAPE_MESSAGES = {
            "信号中断。",
            "它停下了。",
            "它失去了你。",
            "距离恢复安全。",
            "追踪失败。"
    };

    private final HorrorProgressionManager progressionManager;
    private final ReceiverManager receiverManager;
    private final Map<UUID, ChaseState> chases = new HashMap<>();
    private final Map<UUID, Long> nextChaseTicks = new HashMap<>();
    private final Random random = new Random();

    public ChaseManager(HorrorProgressionManager progressionManager, ReceiverManager receiverManager) {
        this.progressionManager = progressionManager;
        this.receiverManager = receiverManager;
    }

    public void tick(MinecraftServer server, long currentTick) {
        tickActiveChases(server, currentTick);

        if (currentTick % NATURAL_CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isSpectator()) {
                triggerChase(player, currentTick, false);
            }
        }
    }

    public boolean triggerChase(ServerPlayerEntity player, long currentTick, boolean forced) {
        if (chases.containsKey(player.getUuid())) {
            return false;
        }

        ItConfig config = ItConfigManager.getConfig();
        if (!forced && !canTriggerNaturally(player, currentTick, config)) {
            return false;
        }

        startWarning(player, currentTick, config, forced);
        return true;
    }

    public boolean stopChase(ServerPlayerEntity player, long currentTick) {
        ChaseState state = chases.get(player.getUuid());
        if (state == null) {
            return false;
        }

        discardChaser(player, state);
        clearClientChaseState(player);
        receiverManager.setSignalBlocked(player, false);
        chases.remove(player.getUuid());
        progressionManager.recordChaseEnded(player, currentTick);
        return true;
    }

    public void remove(ServerPlayerEntity player) {
        ChaseState state = chases.remove(player.getUuid());
        if (state != null) {
            discardChaser(player, state);
            clearClientChaseState(player);
            receiverManager.setSignalBlocked(player, false);
        }
    }

    public void resetCooldown(ServerPlayerEntity player) {
        nextChaseTicks.remove(player.getUuid());
    }

    public long getNextChaseTick(ServerPlayerEntity player) {
        return nextChaseTicks.getOrDefault(player.getUuid(), 0L);
    }

    public ChaseState getState(ServerPlayerEntity player) {
        return chases.get(player.getUuid());
    }

    public boolean isChasing(ServerPlayerEntity player) {
        return chases.containsKey(player.getUuid());
    }

    public void handleCaughtByEntity(HuntingWatcherEntity hunter, ServerPlayerEntity target) {
        ChaseState state = chases.get(target.getUuid());
        if (state == null || state.caught || !state.active) {
            hunter.discard();
            return;
        }

        if (state.chaserEntityId != null && !state.chaserEntityId.equals(hunter.getUuid())) {
            hunter.discard();
            return;
        }

        handleCaught(target, state, target.getEntityWorld().getServer().getTicks(), hunter);
    }

    public String getStatusLine(ServerPlayerEntity player, long currentTick) {
        ChaseState state = getState(player);
        long cooldown = Math.max(0L, getNextChaseTick(player) - currentTick);
        if (state == null) {
            return "追猎：未触发，冷却剩余 " + formatTicks(cooldown);
        }

        String mode = state.warning ? "预警" : "追猎中";
        int remaining = state.warning ? state.warningTicksRemaining : state.chaseTicksRemaining;
        boolean hasChaser = findChaser(player, state) != null;
        return "追猎：" + mode + "，剩余 " + remaining + " ticks，追猎者=" + hasChaser + "，冷却剩余 " + formatTicks(cooldown);
    }

    private void tickActiveChases(MinecraftServer server, long currentTick) {
        List<UUID> ids = List.copyOf(chases.keySet());
        for (UUID playerId : ids) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            ChaseState state = chases.get(playerId);
            if (player == null || state == null || player.isSpectator() || !player.isAlive()) {
                if (state != null && player != null) {
                    discardChaser(player, state);
                    clearClientChaseState(player);
                    receiverManager.setSignalBlocked(player, false);
                } else {
                    receiverManager.clearSignalBlocked(playerId);
                }
                chases.remove(playerId);
                continue;
            }

            if (state.warning) {
                tickWarning(player, state, currentTick);
                continue;
            }

            tickActive(player, state, currentTick);
        }
    }

    private void tickWarning(ServerPlayerEntity player, ChaseState state, long currentTick) {
        if (state.warningTicksRemaining-- > 0) {
            if (state.warningTicksRemaining % 40 == 0) {
                playWarningSound(player);
            }
            return;
        }

        startActiveChase(player, state, currentTick);
    }

    private void tickActive(ServerPlayerEntity player, ChaseState state, long currentTick) {
        state.chaseTicksRemaining--;
        HuntingWatcherEntity chaser = findChaser(player, state);
        if (chaser == null || !chaser.isAlive()) {
            spawnChaser(player, state);
            chaser = findChaser(player, state);
        }

        if (chaser != null && chaser.squaredDistanceTo(player) <= CHASE_CATCH_DISTANCE * CHASE_CATCH_DISTANCE) {
            handleCaught(player, state, currentTick, chaser);
            return;
        }

        if (chaser != null) {
            tickMidChaseFakeRescue(player, state, currentTick);
            tickHuntingHeartbeat(player, state, chaser, currentTick);
            tickChaseAtmosphere(player, state, chaser, currentTick);
            tickHeldReceiverDistanceHint(player, state, chaser, currentTick);
        }

        tickEscapeChecks(player, state, currentTick);
        if (!chases.containsKey(player.getUuid())) {
            return;
        }

        if (state.chaseTicksRemaining <= 0) {
            finishEscape(player, state, currentTick, randomEscapeMessage());
            return;
        }

        if (ItConfigManager.getConfig().enableChaseFootsteps
                && currentTick - state.lastFootstepTime >= 7 + random.nextInt(5)) {
            state.lastFootstepTime = currentTick;
            playChaseFootstep(player, chaser);
        }
    }

    private void tickEscapeChecks(ServerPlayerEntity player, ChaseState state, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();

        if (config.chaseEndsInBrightLight && isNaturalBrightEscape(world, pos, config)) {
            state.brightLightTicks++;
        } else {
            state.brightLightTicks = 0;
        }

        if (state.brightLightTicks >= config.chaseBrightLightEscapeTicks) {
            finishEscape(player, state, currentTick, "信号被强光切断。");
            return;
        }

        if (config.chaseEndsNearOtherPlayers && !progressionManager.isAlone(player, config.chaseSafePlayerDistance)) {
            state.nearPlayerTicks++;
        } else {
            state.nearPlayerTicks = 0;
        }

        if (state.nearPlayerTicks >= config.chaseNearPlayerEscapeTicks) {
            finishEscape(player, state, currentTick, "多人信号重叠。它失去了你。");
            return;
        }

        if (config.chaseEndsOnSafeSurface && isSafeSurface(player)) {
            finishEscape(player, state, currentTick, "地表信号恢复稳定。");
        }
    }

    private boolean isNaturalBrightEscape(ServerWorld world, BlockPos pos, ItConfig config) {
        return world.isDay()
                && world.isSkyVisible(pos.up())
                && world.getLightLevel(pos) >= config.chaseLightEscapeLevel;
    }

    private void startWarning(ServerPlayerEntity player, long currentTick, ItConfig config, boolean forced) {
        int warningTicks = Math.max(20, config.chaseWarningTicks);
        int chaseTicks = Math.max(200, config.chaseDurationTicks);
        ChaseState state = new ChaseState(player.getUuid(), currentTick, warningTicks, chaseTicks);
        state.intensity = forced ? 2 : 1;
        chases.put(player.getUuid(), state);
        receiverManager.setSignalBlocked(player, true);
        progressionManager.recordChaseStarted(player);
        nextChaseTicks.put(player.getUuid(), currentTick + secondsToTicks(config.chaseCooldownSeconds));
        receiverManager.addMessageSilently(player, ReceiverMessageType.CHASE, HorrorPhase.MANIFESTATION, "追踪信号已建立。");

        playWarningSound(player);
        receiverManager.notifyStrongSignal(player);
        ItNetwork.sendChaseReceiverSignal(player, true, warningTicks + chaseTicks + 240);
        ItNetwork.sendManifestationOverlay(player, Math.min(90, warningTicks), 0.35F, config.reduceFlashingEffects || config.disableRapidFlashes);
    }

    private void startActiveChase(ServerPlayerEntity player, ChaseState state, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        state.warning = false;
        state.active = true;
        state.lastDistanceMessageTime = currentTick;
        state.lastFootstepTime = currentTick;
        state.nextAtmosphereSoundTime = currentTick + 45L + random.nextInt(36);
        spawnChaser(player, state);

        if (config.enableChaseOverlay) {
            float intensity = config.chaseRespectReduceFlashingEffects && (config.reduceFlashingEffects || config.disableRapidFlashes) ? 0.35F : 0.58F;
            ItNetwork.sendChaseOverlay(player, true, Math.max(80, state.chaseTicksRemaining), intensity);
        }
        ItNetwork.sendChaseReceiverSignal(player, true, Math.max(80, state.chaseTicksRemaining + 120));

        state.lastChaseWarningChatTime = currentTick;
        receiverManager.addMessageSilently(player, ReceiverMessageType.CHASE, HorrorPhase.MANIFESTATION, "追踪信号贴近。");
        sendChaseFootstepWarningOnce(player, state);
        playChaseStartSound(player);
    }

    private void tickMidChaseFakeRescue(ServerPlayerEntity player, ChaseState state, long currentTick) {
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
        if (lateChase || random.nextDouble() < 0.78D) {
            state.fakeRescueSent = ItMod.getMultiplayerDreadManager().triggerLinkedFakeRescue(player, false);
        }
    }

    private void spawnChaser(ServerPlayerEntity player, ChaseState state) {
        ServerWorld world = player.getEntityWorld();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = 24.0 + random.nextDouble() * 20.0;
        Vec3d spawnPos = new Vec3d(
                player.getX() + Math.cos(angle) * distance,
                player.getY() + 0.05,
                player.getZ() + Math.sin(angle) * distance
        );

        HuntingWatcherEntity hunter = ModEntities.HUNTING_WATCHER.create(world, entity -> {
        }, player.getBlockPos(), SpawnReason.TRIGGERED, false, false);
        if (hunter == null) {
            return;
        }

        hunter.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, player.getYaw(), 0.0F);
        hunter.beginHunt(player, Math.max(80, state.chaseTicksRemaining + 40), ItConfigManager.getConfig().chaseEntitySpeed);
        hunter.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, player.getEyePos());
        world.spawnEntity(hunter);
        TargetOnlyEntityVisibility.hideFromNonTargetPlayers(hunter);
        state.chaserEntityId = hunter.getUuid();
    }

    private void finishEscape(ServerPlayerEntity player, ChaseState state, long currentTick, String message) {
        discardChaser(player, state);
        clearClientChaseState(player);
        receiverManager.setSignalBlocked(player, false);
        chases.remove(player.getUuid());
        receiverManager.addMessageSilently(player, ReceiverMessageType.CHASE, HorrorPhase.MANIFESTATION, message);
        progressionManager.recordChaseEscape(player);
        progressionManager.recordChaseEnded(player, currentTick);
        playEscapeSound(player);
    }

    private void handleCaught(ServerPlayerEntity player, ChaseState state, long currentTick, HuntingWatcherEntity chaser) {
        if (state.caught) {
            return;
        }

        ItConfig config = ItConfigManager.getConfig();
        state.caught = true;
        float newHealth = player.getHealth() - config.chaseDamage;
        player.setHealth(config.chaseCanKillPlayer ? Math.max(0.0F, newHealth) : Math.max(1.0F, newHealth));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, false, false, true));

        if (config.enableChaseFaceScareOnCatch) {
            ItNetwork.sendHuntFaceScare(player, Math.max(35, config.chaseFaceScareDurationTicks), 1.0F, true);
        }

        ItNetwork.sendReceiverDistortion(player, 140, 0.92F);
        spawnCaughtParticles(player);
        receiverManager.addMessageSilently(player, ReceiverMessageType.CHASE, HorrorPhase.MANIFESTATION, "距离：0。");
        progressionManager.recordChaseCaught(player);
        progressionManager.recordChaseEnded(player, currentTick);
        playCaughtSound(player);
        chaser.discard();
        clearClientChaseState(player);
        receiverManager.setSignalBlocked(player, false);
        chases.remove(player.getUuid());
    }

    private boolean canTriggerNaturally(ServerPlayerEntity player, long currentTick, ItConfig config) {
        if (!config.enableChaseEvents) {
            return false;
        }

        if (config.chaseOnlyInPhaseFive && progressionManager.getPhase(player) != HorrorPhase.MANIFESTATION) {
            return false;
        }

        if (config.disableNormalChaseInCaves && CaveStalkerManager.isCaveLikeEnvironment(player)) {
            return false;
        }

        if (currentTick < nextChaseTicks.getOrDefault(player.getUuid(), 0L)) {
            return false;
        }

        PlayerHorrorData data = progressionManager.getData(player);
        if (data.getTimeInPhaseTicks(progressionManager.getProgressionTick(player)) < MIN_TIME_IN_PHASE_FIVE_TICKS) {
            return false;
        }

        if (data.watchingLevel < config.chaseRequiresWatchingLevel
                || data.receiverMessagesReceived < config.chaseRequiresReceiverMessages
                || data.watcherSightings < config.chaseRequiresWatcherSightings) {
            return false;
        }

        if (!progressionManager.isManifestationEnvironment(player)) {
            return false;
        }

        if (!progressionManager.isAlone(player, config.chaseSafePlayerDistance)) {
            return false;
        }

        double chance = EventChanceScaler.clampChance(0.060D
                * config.chaseChanceMultiplier
                * config.eventChanceMultiplier
                * EventChanceScaler.phaseFiveHighPressureEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() > chance) {
            nextChaseTicks.put(player.getUuid(), currentTick + secondsToTicks(90 + random.nextInt(121)));
            return false;
        }

        return true;
    }

    private boolean isSafeSurface(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        boolean skyVisible = world.isSkyVisible(pos);
        boolean brightEnough = world.getLightLevel(pos) >= 10;
        boolean aboveGround = player.getY() >= world.getSeaLevel() - 4;
        return skyVisible && aboveGround && brightEnough;
    }

    private HuntingWatcherEntity findChaser(ServerPlayerEntity player, ChaseState state) {
        if (state.chaserEntityId == null) {
            return null;
        }

        List<? extends HuntingWatcherEntity> hunters = player.getEntityWorld()
                .getEntitiesByType(TypeFilter.instanceOf(HuntingWatcherEntity.class), hunter -> hunter.getUuid().equals(state.chaserEntityId));
        return hunters.isEmpty() ? null : hunters.get(0);
    }

    private void discardChaser(ServerPlayerEntity player, ChaseState state) {
        HuntingWatcherEntity chaser = findChaser(player, state);
        if (chaser != null) {
            chaser.discard();
        }
    }

    private void clearClientChaseState(ServerPlayerEntity player) {
        ItNetwork.sendChaseOverlay(player, false, 0, 0.0F);
        ItNetwork.sendChaseReceiverSignal(player, false, 0);
    }

    private String randomEscapeMessage() {
        return ESCAPE_MESSAGES[random.nextInt(ESCAPE_MESSAGES.length)];
    }

    private void tickHeldReceiverDistanceHint(ServerPlayerEntity player, ChaseState state, HuntingWatcherEntity chaser, long currentTick) {
        if (currentTick - state.lastReceiverDistanceHintTime < 10) {
            return;
        }

        if (!player.getMainHandStack().isOf(ModItems.RECEIVER) && !player.getOffHandStack().isOf(ModItems.RECEIVER)) {
            return;
        }

        state.lastReceiverDistanceHintTime = currentTick;
        double distance = Math.sqrt(chaser.squaredDistanceTo(player));
        String hint = chaseDistanceHint(distance);
        int color = chaseDistanceHintColor(distance);
        ItNetwork.sendChaseDistanceHint(player, hint, color, 16);
    }

    private String chaseDistanceHint(double distance) {
        if (distance <= 1.25) {
            return "Game over";
        }

        if (distance <= 12.0) {
            return "它就要碰到你了....";
        }

        if (distance <= 22.0) {
            return "它离你很近...";
        }

        if (distance <= 36.0) {
            return "它离你较近...";
        }

        return "它离你较远...";
    }

    private int chaseDistanceHintColor(double distance) {
        if (distance <= 1.25) {
            return 0xFFFF2020;
        }

        if (distance <= 12.0) {
            return 0xFFFF3E30;
        }

        if (distance <= 22.0) {
            return 0xFFFF8A4A;
        }

        if (distance <= 36.0) {
            return 0xFFE0C86C;
        }

        return 0xFFB8B8B8;
    }

    private void tickHuntingHeartbeat(ServerPlayerEntity player, ChaseState state, HuntingWatcherEntity chaser, long currentTick) {
        double distance = Math.sqrt(chaser.squaredDistanceTo(player));
        int interval = distance <= 8.0 ? 6 : distance <= 16.0 ? 8 : distance <= 24.0 ? 10 : 12;
        if (currentTick - state.lastHeartbeatTime < interval) {
            return;
        }

        state.lastHeartbeatTime = currentTick;
        Vec3d source = new Vec3d(chaser.getX(), chaser.getY() + 0.9, chaser.getZ());
        float volume = distance <= 12.0 ? 1.35F : 1.75F;
        float pitch = 0.78F + random.nextFloat() * 0.10F;
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, source, volume, pitch);
    }

    private void tickChaseAtmosphere(ServerPlayerEntity player, ChaseState state, HuntingWatcherEntity chaser, long currentTick) {
        if (currentTick < state.nextAtmosphereSoundTime) {
            return;
        }

        state.nextAtmosphereSoundTime = currentTick + 70L + random.nextInt(71);
        Vec3d chaserPos = new Vec3d(chaser.getX(), chaser.getY() + 0.8, chaser.getZ());
        Vec3d source = directionalSourceNearPlayer(player, chaserPos, 11.0);
        float distance = (float) Math.sqrt(chaser.squaredDistanceTo(player));
        float volume = distance <= 16.0F ? 0.72F : 0.58F;
        sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, source, volume, 0.54F + random.nextFloat() * 0.08F);

        if (random.nextBoolean()) {
            sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSEST, SoundCategory.AMBIENT, source, 0.42F, 0.62F + random.nextFloat() * 0.10F);
        } else {
            sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, source, 0.45F, 0.82F + random.nextFloat() * 0.18F);
        }
    }

    private void playWarningSound(ServerPlayerEntity player) {
        Vec3d source = behindPlayer(player, 5.0);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, source, 0.72F, 0.55F + random.nextFloat() * 0.08F);
    }

    private void playChaseStartSound(ServerPlayerEntity player) {
        Vec3d source = behindPlayer(player, 6.0);
        sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, source, 2.0F, 0.42F + random.nextFloat() * 0.08F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_ENDERMAN_STARE, SoundCategory.AMBIENT, source, 0.95F, 0.36F + random.nextFloat() * 0.10F);
    }

    private void playChaseFootstep(ServerPlayerEntity player, HuntingWatcherEntity chaser) {
        Vec3d source = chaser == null
                ? behindPlayer(player, 5.0)
                : directionalSourceNearPlayer(player, new Vec3d(chaser.getX(), chaser.getY(), chaser.getZ()), 8.0);
        float volume = Math.max(0.0F, ItConfigManager.getConfig().chaseFootstepVolume);
        sendSoundToPlayer(player, random.nextBoolean() ? SoundEvents.BLOCK_STONE_STEP : SoundEvents.BLOCK_GRAVEL_STEP, SoundCategory.HOSTILE, source, volume, 0.62F + random.nextFloat() * 0.18F);
    }

    private void playEscapeSound(ServerPlayerEntity player) {
        sendSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, new Vec3d(player.getX(), player.getY(), player.getZ()), 0.55F, 0.85F);
    }

    private void playCaughtSound(ServerPlayerEntity player) {
        Vec3d source = behindPlayer(player, 1.5);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.HOSTILE, source, 1.0F, 0.55F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, source, 0.8F, 0.45F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, source, 0.75F, 1.18F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.HOSTILE, source, 0.9F, 1.95F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.HOSTILE, source, 0.8F, 1.85F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.HOSTILE, source, 0.85F, 1.75F);
    }

    private void sendChaseFootstepWarningOnce(ServerPlayerEntity player, ChaseState state) {
        if (state.chaseWarningChatSent) {
            return;
        }

        state.chaseWarningChatSent = true;
        player.sendMessage(Text.literal("你听见了不属于自己的脚步声").formatted(Formatting.RED, Formatting.BOLD), false);
    }

    private void spawnCaughtParticles(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        world.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.04, player.getZ(), 42, 0.85, 0.02, 0.85, 0.01);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.05, player.getZ(), 18, 0.65, 0.01, 0.65, 0.0);
    }

    private Vec3d behindPlayer(ServerPlayerEntity player, double distance) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        if (look.lengthSquared() < 0.01) {
            look = new Vec3d(0.0, 0.0, 1.0);
        }
        return new Vec3d(player.getX(), player.getY() + 0.8, player.getZ()).subtract(look.multiply(distance));
    }

    private Vec3d directionalSourceNearPlayer(ServerPlayerEntity player, Vec3d actualSource, double maxDistance) {
        Vec3d playerPos = new Vec3d(player.getX(), player.getY() + 0.8, player.getZ());
        Vec3d direction = actualSource.subtract(playerPos);
        if (direction.lengthSquared() < 0.01) {
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
