package com.xm6680.it.event;

import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.cavestalker.CaveStalkerManager;
import com.xm6680.it.chase.ChaseManager;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.entity.ModEntities;
import com.xm6680.it.entity.TargetOnlyEntityVisibility;
import com.xm6680.it.entity.WatcherEntity;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.watching.HorrorPhase;
import com.xm6680.it.watching.WatchingLevelManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Rarely spawns Watcher entities near high watching-level players.
 */
public class WatcherSpawnManager {
    private static final int MAX_WATCHERS_PER_WORLD = 6;
    private static final double PLAYER_WATCHER_LIMIT_RADIUS = 64.0;
    private static final double MIN_NORMAL_WATCHER_DISTANCE = 6.0D;
    private static final double MIN_TUNNEL_WATCHER_DISTANCE = 6.0D;
    private static final double PHASE3_NORMAL_WATCHER_MIN_DISTANCE = 30.0D;
    private static final double PHASE3_NORMAL_WATCHER_MAX_DISTANCE = 50.0D;
    private static final double DEFAULT_NORMAL_WATCHER_MIN_DISTANCE = 20.0D;
    private static final double DEFAULT_NORMAL_WATCHER_MAX_DISTANCE = 40.0D;
    private static final double MANIFESTATION_NORMAL_WATCHER_MIN_DISTANCE = 12.0D;
    private static final double MANIFESTATION_NORMAL_WATCHER_MAX_DISTANCE = 26.0D;
    private static final int[] NORMAL_WATCHER_Y_OFFSETS = {4, 3, 2, 1, 0, -1, -2, -3, -4, -5, -6, -7, -8};
    private static final int[] TUNNEL_Y_OFFSETS = {0, -1, 1, -2, 2, -3, 3, -4, 4, -5, 5};

    private final WatchingLevelManager watchingLevelManager;
    private final HorrorProgressionManager progressionManager;
    private final AnalogHorrorManager analogHorrorManager;
    private final ChaseManager chaseManager;
    private final CaveStalkerManager caveStalkerManager;
    private final Map<UUID, Long> nextTunnelWatcherTicks = new HashMap<>();
    private final Map<UUID, String> lastTunnelWatcherFailure = new HashMap<>();
    private final Random random = new Random();

    public WatcherSpawnManager(
            WatchingLevelManager watchingLevelManager,
            HorrorProgressionManager progressionManager,
            AnalogHorrorManager analogHorrorManager,
            ChaseManager chaseManager,
            CaveStalkerManager caveStalkerManager
    ) {
        this.watchingLevelManager = watchingLevelManager;
        this.progressionManager = progressionManager;
        this.analogHorrorManager = analogHorrorManager;
        this.chaseManager = chaseManager;
        this.caveStalkerManager = caveStalkerManager;
    }

    public void tick(MinecraftServer server) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableWatcher) {
            return;
        }

        long currentTick = server.getTicks();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isSpectator()) {
                trySpawnWatcher(player, false);
                trySpawnTunnelWatcher(player, currentTick, false);
            }
        }
    }

    public boolean forceSpawnNear(ServerPlayerEntity player) {
        return trySpawnWatcher(player, true);
    }

    public boolean forceSpawnTunnelWatcher(ServerPlayerEntity player) {
        return trySpawnTunnelWatcher(player, player.getEntityWorld().getServer().getTicks(), true);
    }

    public void remove(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        nextTunnelWatcherTicks.remove(uuid);
        lastTunnelWatcherFailure.remove(uuid);
    }

    public long getNextTunnelWatcherTick(ServerPlayerEntity player) {
        return nextTunnelWatcherTicks.getOrDefault(player.getUuid(), 0L);
    }

    public TunnelWatcherDebugInfo getTunnelWatcherDebugInfo(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        long currentTick = player.getEntityWorld().getServer().getTicks();
        HorrorPhase phase = progressionManager.getPhase(player);
        int lightLevel = player.getEntityWorld().getLightLevel(player.getBlockPos());
        boolean enabled = config.enableWatcher && config.enableTunnelWatcherSpawns;
        boolean phaseAllowed = phase.isAtLeast(HorrorPhase.IMITATING) && phase.getNumber() >= config.tunnelWatcherMinPhase;
        boolean caveLike = isCaveLike(player);
        boolean lowLight = !config.tunnelWatcherRequiresLowLight || lightLevel <= config.tunnelWatcherMaxLightLevel;
        boolean strongEvent = isStrongEventActive(player, currentTick);
        long cooldownTicks = Math.max(0L, nextTunnelWatcherTicks.getOrDefault(player.getUuid(), 0L) - currentTick);
        boolean cooldownReady = cooldownTicks <= 0L;
        boolean canTry = enabled && phaseAllowed && caveLike && lowLight && !strongEvent && cooldownReady;
        String failure = lastTunnelWatcherFailure.getOrDefault(player.getUuid(), "尚未尝试 Tunnel Watcher 生成");

        return new TunnelWatcherDebugInfo(
                phase,
                lightLevel,
                config.tunnelWatcherMaxLightLevel,
                enabled,
                phaseAllowed,
                caveLike,
                lowLight,
                strongEvent,
                cooldownReady,
                cooldownTicks,
                canTry,
                failure
        );
    }

    public int clearWatchers(MinecraftServer server) {
        int removed = 0;

        for (ServerWorld world : server.getWorlds()) {
            List<? extends WatcherEntity> watchers = world.getEntitiesByType(TypeFilter.instanceOf(WatcherEntity.class), watcher -> true);
            removed += watchers.size();
            watchers.forEach(Entity::discard);
        }

        return removed;
    }

    private boolean trySpawnWatcher(ServerPlayerEntity player, boolean forced) {
        ServerWorld world = player.getEntityWorld();
        if (!forced && !canTryNaturalSpawn(player)) {
            return false;
        }

        if (!forced && countWatchers(world) >= MAX_WATCHERS_PER_WORLD) {
            return false;
        }

        if (!forced && hasWatcherNear(player, PLAYER_WATCHER_LIMIT_RADIUS)) {
            return false;
        }

        BlockPos spawnPos = findSpawnPos(player, forced);
        if (spawnPos == null) {
            return false;
        }

        if (spawnWatcherAt(player, spawnPos, false)) {
            if (!forced) {
                analogHorrorManager.onWatcherSighting(player);
            }
            return true;
        }

        return false;
    }

    private boolean trySpawnTunnelWatcher(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!forced && !canTryTunnelWatcher(player, currentTick, config)) {
            return false;
        }

        if (!forced && countWatchers(player.getEntityWorld()) >= MAX_WATCHERS_PER_WORLD) {
            rememberTunnelFailure(player, "世界内 Watcher 数量已达上限");
            return false;
        }

        if (!forced && hasWatcherNear(player, PLAYER_WATCHER_LIMIT_RADIUS)) {
            rememberTunnelFailure(player, "玩家附近已有 Watcher");
            return false;
        }

        HorrorPhase phase = progressionManager.getPhase(player);
        BlockPos spawnPos = findTunnelSpawnPos(player, phase, forced);
        if (spawnPos == null) {
            rememberTunnelFailure(player, "未找到安全且可见的矿道生成点");
            return false;
        }

        if (!spawnWatcherAt(player, spawnPos, true, phase)) {
            rememberTunnelFailure(player, "Watcher 实体创建失败");
            return false;
        }

        if (!forced) {
            nextTunnelWatcherTicks.put(player.getUuid(), currentTick + secondsToTicks(config.tunnelWatcherCooldownSeconds));
        }
        rememberTunnelFailure(player, "最近一次 Tunnel Watcher 生成成功");
        return true;
    }

    private boolean canTryTunnelWatcher(ServerPlayerEntity player, long currentTick, ItConfig config) {
        if (!config.enableWatcher || !config.enableTunnelWatcherSpawns) {
            rememberTunnelFailure(player, "Tunnel Watcher 或 Watcher 配置已关闭");
            return false;
        }

        HorrorPhase phase = progressionManager.getPhase(player);
        if (!phase.isAtLeast(HorrorPhase.IMITATING) || phase.getNumber() < config.tunnelWatcherMinPhase) {
            rememberTunnelFailure(player, "当前阶段未达到 Tunnel Watcher 门槛");
            return false;
        }

        if (!isCaveLike(player)) {
            rememberTunnelFailure(player, "当前位置不是地下矿洞/矿道环境");
            return false;
        }

        int lightLevel = player.getEntityWorld().getLightLevel(player.getBlockPos());
        if (config.tunnelWatcherRequiresLowLight && lightLevel > config.tunnelWatcherMaxLightLevel) {
            rememberTunnelFailure(player, "玩家附近光照过高");
            return false;
        }

        if (isStrongEventActive(player, currentTick)) {
            rememberTunnelFailure(player, "当前处于强事件中");
            return false;
        }

        if (currentTick < nextTunnelWatcherTicks.getOrDefault(player.getUuid(), 0L)) {
            rememberTunnelFailure(player, "Tunnel Watcher 冷却未结束");
            return false;
        }

        double chance = tunnelWatcherChance(player, phase, config);
        if (random.nextDouble() > chance) {
            nextTunnelWatcherTicks.put(
                    player.getUuid(),
                    currentTick + secondsToTicks(randomBetween(config.tunnelWatcherMissCooldownMinSeconds, config.tunnelWatcherMissCooldownMaxSeconds))
            );
            rememberTunnelFailure(player, "概率未命中");
            return false;
        }

        return true;
    }

    private double tunnelWatcherChance(ServerPlayerEntity player, HorrorPhase phase, ItConfig config) {
        double phaseChance = switch (phase) {
            case IMITATING -> 0.03D;
            case INTRUSION -> 0.10D;
            case MANIFESTATION -> 0.16D;
            default -> 0.0D;
        };
        return Math.min(0.85D, EventChanceScaler.clampChance(phaseChance
                * config.tunnelWatcherSpawnChanceMultiplier
                * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config)));
    }

    private boolean canTryNaturalSpawn(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        HorrorPhase phase = progressionManager.getPhase(player);
        if (!phase.isAtLeast(HorrorPhase.IMITATING)) {
            return false;
        }

        double watchingPercent = watchingLevelManager.getWatchingLevel(player) / Math.max(1.0, config.maxWatchingLevel);
        double phaseMultiplier = switch (phase) {
            case IMITATING -> 0.45;
            case INTRUSION -> 1.15;
            case MANIFESTATION -> 1.75;
            default -> 0.0;
        };
        double chance = EventChanceScaler.clampChance((0.003 + 0.02 * watchingPercent)
                * phaseMultiplier
                * config.watcherSpawnChanceMultiplier
                * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));

        return getEnvironmentScore(player) > 0 && random.nextDouble() <= chance;
    }

    private int getEnvironmentScore(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        int score = 0;

        if (watchingLevelManager.isNight(world) && world.isSkyVisible(pos)) {
            score += 2;
        }

        if (watchingLevelManager.isUnderground(world, player, world.isSkyVisible(pos))) {
            score += 2;
        }

        if (world.getLightLevel(pos) <= 7) {
            score += 1;
        }

        if (isForestLike(world, pos)) {
            score += 1;
        }

        return score;
    }

    private BlockPos findSpawnPos(ServerPlayerEntity player, boolean forced) {
        ServerWorld world = player.getEntityWorld();
        ItConfig config = ItConfigManager.getConfig();
        DistanceRange range = normalWatcherDistanceRange(progressionManager.getPhase(player));
        int attempts = forced ? 48 : (config.watcherPreferVisibleSpawnPositions ? 32 : 24);
        WatcherCandidate best = null;

        for (int attempt = 0; attempt < attempts; attempt++) {
            Vec3d direction = watcherSpawnDirection(player, config, attempt);
            double distance = range.min() + random.nextDouble() * Math.max(0.1D, range.max() - range.min());
            int x = player.getBlockX() + (int) Math.round(direction.x * distance);
            int z = player.getBlockZ() + (int) Math.round(direction.z * distance);
            BlockPos pos = findSafeWatcherPositionNear(player, world, x, z);
            if (pos == null) {
                continue;
            }

            if (!config.watcherPreferVisibleSpawnPositions) {
                return pos;
            }

            int score = scoreWatcherPosition(player, world, pos, config);
            if (score < 0) {
                continue;
            }

            if (best == null || score > best.score()) {
                best = new WatcherCandidate(pos, score);
            }
        }

        return best == null ? null : best.pos();
    }

    private Vec3d watcherSpawnDirection(ServerPlayerEntity player, ItConfig config, int attempt) {
        if (config.watcherPreferVisibleSpawnPositions && random.nextDouble() < config.watcherFrontArcPreference) {
            Vec3d forward = horizontalForward(player);
            Vec3d side = new Vec3d(-forward.z, 0.0D, forward.x);
            double angleOffset = attempt < 5
                    ? (attempt - 2) * 0.35D
                    : (random.nextDouble() * 2.0D - 1.0D) * 1.05D;
            return forward.multiply(Math.cos(angleOffset)).add(side.multiply(Math.sin(angleOffset))).normalize();
        }

        double angle = random.nextDouble() * Math.PI * 2.0D;
        return new Vec3d(Math.cos(angle), 0.0D, Math.sin(angle));
    }

    private BlockPos findSafeWatcherPositionNear(ServerPlayerEntity player, ServerWorld world, int x, int z) {
        if (world.isSkyVisible(player.getBlockPos())) {
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            return isSafeWatcherPosition(world, pos) ? pos : null;
        }

        int baseY = player.getBlockY() + random.nextInt(9) - 4;
        for (int offset : NORMAL_WATCHER_Y_OFFSETS) {
            BlockPos pos = new BlockPos(x, baseY + offset, z);
            if (isSafeWatcherPosition(world, pos)) {
                return pos;
            }
        }

        return null;
    }

    private int scoreWatcherPosition(ServerPlayerEntity player, ServerWorld world, BlockPos pos, ItConfig config) {
        if (!isSafeWatcherPosition(world, pos)) {
            return -1;
        }

        double dx = pos.getX() + 0.5D - player.getX();
        double dz = pos.getZ() + 0.5D - player.getZ();
        double distanceSquared = dx * dx + dz * dz;
        if (distanceSquared < MIN_NORMAL_WATCHER_DISTANCE * MIN_NORMAL_WATCHER_DISTANCE) {
            return -1;
        }

        Vec3d toCandidate = new Vec3d(dx, 0.0D, dz).normalize();
        double forwardDot = horizontalForward(player).dotProduct(toCandidate);
        boolean possibleLineOfSight = hasPossibleLineOfSight(player, pos);
        int light = world.getLightLevel(pos);
        int score = random.nextInt(4);

        if (possibleLineOfSight) {
            score += (int) Math.round(7.0D * config.watcherPossibleLineOfSightWeight);
        } else {
            score += 1;
        }

        if (forwardDot > -0.15D) {
            score += 2;
        }

        score += (int) Math.round(Math.max(0.0D, forwardDot) * 5.0D);

        if (forwardDot > 0.93D) {
            score -= 2;
        }

        if (light <= 7) {
            score += 2 + Math.max(0, 7 - light);
        }

        if (distanceSquared >= 12.0D * 12.0D) {
            score += 1;
        }

        return score;
    }

    private BlockPos findTunnelSpawnPos(ServerPlayerEntity player, HorrorPhase phase, boolean forced) {
        ServerWorld world = player.getEntityWorld();
        ItConfig config = ItConfigManager.getConfig();
        DistanceRange range = tunnelDistanceRange(config, phase);
        TunnelCandidate best = null;
        int attempts = forced ? 88 : 56;

        for (int attempt = 0; attempt < attempts; attempt++) {
            Vec3d direction = tunnelDirection(player, phase, attempt);
            double distance = range.min() + random.nextDouble() * Math.max(0.1D, range.max() - range.min());
            int x = player.getBlockX() + (int) Math.round(direction.x * distance);
            int z = player.getBlockZ() + (int) Math.round(direction.z * distance);
            int baseY = player.getBlockY() + random.nextInt(5) - 2;

            for (int yOffset : TUNNEL_Y_OFFSETS) {
                BlockPos pos = new BlockPos(x, baseY + yOffset, z);
                int score = scoreTunnelPosition(player, world, pos, config, forced);
                if (score < 0) {
                    continue;
                }

                if (best == null || score > best.score()) {
                    best = new TunnelCandidate(pos, score);
                }
            }
        }

        int requiredScore = forced ? 8 : 11;
        return best != null && best.score() >= requiredScore ? best.pos() : null;
    }

    private Vec3d tunnelDirection(ServerPlayerEntity player, HorrorPhase phase, int attempt) {
        if (phase == HorrorPhase.IMITATING) {
            return subtleTunnelDirection(player, attempt);
        }

        return directTunnelDirection(player, attempt);
    }

    private Vec3d directTunnelDirection(ServerPlayerEntity player, int attempt) {
        Vec3d forward = horizontalForward(player);
        Vec3d side = new Vec3d(-forward.z, 0.0D, forward.x);

        double angleOffset;
        if (attempt < 5) {
            angleOffset = (attempt - 2) * 0.22D;
        } else if (random.nextDouble() < 0.74D) {
            angleOffset = (random.nextDouble() * 2.0D - 1.0D) * 0.78D;
        } else {
            double sign = random.nextBoolean() ? 1.0D : -1.0D;
            angleOffset = sign * (0.78D + random.nextDouble() * 0.55D);
        }

        return forward.multiply(Math.cos(angleOffset)).add(side.multiply(Math.sin(angleOffset))).normalize();
    }

    private Vec3d subtleTunnelDirection(ServerPlayerEntity player, int attempt) {
        Vec3d forward = horizontalForward(player);
        Vec3d side = new Vec3d(-forward.z, 0.0D, forward.x);
        double sign = attempt % 2 == 0 ? 1.0D : -1.0D;

        double angleOffset;
        if (attempt < 6) {
            angleOffset = sign * (0.72D + (attempt / 2) * 0.20D);
        } else if (random.nextDouble() < 0.82D) {
            angleOffset = sign * (0.70D + random.nextDouble() * 0.62D);
        } else {
            angleOffset = sign * (0.40D + random.nextDouble() * 0.30D);
        }

        return forward.multiply(Math.cos(angleOffset)).add(side.multiply(Math.sin(angleOffset))).normalize();
    }

    private int scoreTunnelPosition(ServerPlayerEntity player, ServerWorld world, BlockPos pos, ItConfig config, boolean forced) {
        if (!isSafeWatcherPosition(world, pos)) {
            return -1;
        }

        double dx = pos.getX() + 0.5D - player.getX();
        double dy = pos.getY() - player.getY();
        double dz = pos.getZ() + 0.5D - player.getZ();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared < MIN_TUNNEL_WATCHER_DISTANCE * MIN_TUNNEL_WATCHER_DISTANCE) {
            return -1;
        }

        int light = world.getLightLevel(pos);
        if (!forced && config.tunnelWatcherRequiresLowLight && light > config.tunnelWatcherMaxLightLevel) {
            return -1;
        }

        boolean possibleLineOfSight = hasPossibleLineOfSight(player, pos);
        if (config.tunnelWatcherRequirePossibleLineOfSight && !possibleLineOfSight) {
            return -1;
        }

        Vec3d toCandidate = new Vec3d(
                pos.getX() + 0.5D - player.getX(),
                0.0D,
                pos.getZ() + 0.5D - player.getZ()
        ).normalize();
        double forwardDot = horizontalForward(player).dotProduct(toCandidate);
        if (forwardDot < 0.12D) {
            return -1;
        }

        int shapeScore = tunnelShapeScore(world, pos);
        if (shapeScore < 0) {
            return -1;
        }

        int score = shapeScore;
        score += possibleLineOfSight ? 5 : 1;
        score += (int) Math.round(Math.max(0.0D, forwardDot) * 4.0D);
        score += Math.max(0, config.tunnelWatcherMaxLightLevel - light);

        if (light <= config.tunnelWatcherMaxLightLevel) {
            score += 3;
        }

        if (distanceSquared >= 12.0D * 12.0D) {
            score += 1;
        }

        return score;
    }

    private boolean isSafeWatcherPosition(ServerWorld world, BlockPos pos) {
        if (!world.getWorldBorder().contains(pos)) {
            return false;
        }

        if (!world.getFluidState(pos).isEmpty()
                || !world.getFluidState(pos.up()).isEmpty()
                || !world.getFluidState(pos.down()).isEmpty()) {
            return false;
        }

        BlockState ground = world.getBlockState(pos.down());
        if (!ground.isSolidBlock(world, pos.down()) || isDangerousBlock(ground)) {
            return false;
        }

        for (int y = 0; y <= 2; y++) {
            BlockPos checkPos = pos.up(y);
            BlockState state = world.getBlockState(checkPos);
            if (!state.isAir() || isDangerousBlock(state) || !world.getFluidState(checkPos).isEmpty()) {
                return false;
            }
        }

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }

                for (int y = -1; y <= 1; y++) {
                    BlockPos checkPos = pos.add(x, y, z);
                    BlockState state = world.getBlockState(checkPos);
                    if (isDangerousBlock(state) || !world.getFluidState(checkPos).isEmpty()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean isDangerousBlock(BlockState state) {
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

    private int tunnelShapeScore(ServerWorld world, BlockPos pos) {
        boolean north = isOpenTunnelCell(world, pos.north());
        boolean south = isOpenTunnelCell(world, pos.south());
        boolean east = isOpenTunnelCell(world, pos.east());
        boolean west = isOpenTunnelCell(world, pos.west());
        int openings = 0;
        openings += north ? 1 : 0;
        openings += south ? 1 : 0;
        openings += east ? 1 : 0;
        openings += west ? 1 : 0;

        if (openings == 0) {
            return -1;
        }

        if (openings == 1) {
            return 5;
        }

        if (openings == 2) {
            boolean straight = (north && south) || (east && west);
            return straight ? 4 : 6;
        }

        return openings == 3 ? 3 : 1;
    }

    private boolean isOpenTunnelCell(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).isAir()
                && world.getBlockState(pos.up()).isAir()
                && world.getFluidState(pos).isEmpty()
                && world.getFluidState(pos.up()).isEmpty();
    }

    private boolean hasPossibleLineOfSight(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getEntityWorld();
        Vec3d start = player.getEyePos();
        Vec3d end = new Vec3d(pos.getX() + 0.5D, pos.getY() + 1.25D, pos.getZ() + 0.5D);
        int blocked = 0;
        int open = 0;
        int samples = 18;
        BlockPos last = null;

        for (int i = 1; i < samples; i++) {
            double t = (double) i / (double) samples;
            Vec3d point = start.lerp(end, t);
            BlockPos sampled = new BlockPos((int) Math.floor(point.x), (int) Math.floor(point.y), (int) Math.floor(point.z));
            if (sampled.equals(last)) {
                continue;
            }

            last = sampled;
            BlockState state = world.getBlockState(sampled);
            if (state.isAir() || !state.isSolidBlock(world, sampled)) {
                open++;
            } else {
                blocked++;
                if (blocked > 2) {
                    return false;
                }
            }
        }

        return blocked == 0 || open >= 6;
    }

    private boolean isCaveLike(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos center = player.getBlockPos();
        boolean skyVisible = world.isSkyVisible(center);
        if (!watchingLevelManager.isUnderground(world, player, skyVisible)) {
            return false;
        }

        int openCells = 0;
        int solidCells = 0;
        int stoneLikeCells = 0;

        for (int x = -3; x <= 3; x += 2) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -3; z <= 3; z += 2) {
                    BlockPos pos = center.add(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) {
                        openCells++;
                    } else if (state.isSolidBlock(world, pos)) {
                        solidCells++;
                        if (isStoneLike(state)) {
                            stoneLikeCells++;
                        }
                    }
                }
            }
        }

        boolean enclosed = solidCells >= 10;
        boolean hasWalkingSpace = openCells >= 8;
        boolean naturalOrDeep = stoneLikeCells >= 4 || player.getY() < world.getSeaLevel() - 18;
        return enclosed && hasWalkingSpace && naturalOrDeep;
    }

    private boolean isStoneLike(BlockState state) {
        return state.isOf(Blocks.STONE)
                || state.isOf(Blocks.DEEPSLATE)
                || state.isOf(Blocks.TUFF)
                || state.isOf(Blocks.GRANITE)
                || state.isOf(Blocks.DIORITE)
                || state.isOf(Blocks.ANDESITE)
                || state.isOf(Blocks.CALCITE)
                || state.isOf(Blocks.DRIPSTONE_BLOCK);
    }

    private boolean isStrongEventActive(ServerPlayerEntity player, long currentTick) {
        return chaseManager.isChasing(player)
                || caveStalkerManager.isActive(player)
                || ItNetwork.isJumpscareActive(player, currentTick)
                || ItNetwork.isFaceScareActive(player, currentTick)
                || ItNetwork.isManifestationOverlayActive(player, currentTick);
    }

    private boolean spawnWatcherAt(ServerPlayerEntity player, BlockPos spawnPos, boolean tunnelWatcher) {
        return spawnWatcherAt(player, spawnPos, tunnelWatcher, null);
    }

    private boolean spawnWatcherAt(ServerPlayerEntity player, BlockPos spawnPos, boolean tunnelWatcher, HorrorPhase tunnelWatcherPhase) {
        ServerWorld world = player.getEntityWorld();
        WatcherEntity watcher = ModEntities.WATCHER.create(world, spawnedWatcher -> {
        }, spawnPos, SpawnReason.TRIGGERED, false, false);
        if (watcher == null) {
            return false;
        }

        watcher.refreshPositionAndAngles(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, 0.0F, 0.0F);
        watcher.setVisibleTarget(player);
        watcher.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, player.getEyePos());
        world.spawnEntity(watcher);
        TargetOnlyEntityVisibility.hideFromNonTargetPlayers(watcher);

        if (tunnelWatcher) {
            maybePlayTunnelWatcherHint(player, spawnPos, tunnelWatcherPhase);
        }

        return true;
    }

    private void maybePlayTunnelWatcherHint(ServerPlayerEntity player, BlockPos spawnPos, HorrorPhase phase) {
        ItConfig config = ItConfigManager.getConfig();
        if (phase == HorrorPhase.IMITATING) {
            return;
        }

        if (!config.enableTunnelWatcherSpawnSound || random.nextDouble() > tunnelWatcherHintChance(phase)) {
            return;
        }

        Vec3d source = directionalSoundSource(player, new Vec3d(spawnPos.getX() + 0.5D, spawnPos.getY() + 0.5D, spawnPos.getZ() + 0.5D), 10.0D);
        SoundEvent sound = random.nextBoolean() ? SoundEvents.BLOCK_STONE_STEP : SoundEvents.BLOCK_GRAVEL_STEP;
        sendSoundToPlayer(player, sound, SoundCategory.AMBIENT, source, 0.26F, 0.52F + random.nextFloat() * 0.16F);
    }

    private double tunnelWatcherHintChance(HorrorPhase phase) {
        return phase == HorrorPhase.MANIFESTATION ? 0.28D : 0.16D;
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, pos, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(sound, category, pos.x, pos.y, pos.z, volume, pitch, random.nextLong());
        player.networkHandler.sendPacket(packet);
    }

    private Vec3d directionalSoundSource(ServerPlayerEntity player, Vec3d actualSource, double maxDistance) {
        Vec3d playerPos = new Vec3d(player.getX(), player.getY() + 0.8D, player.getZ());
        Vec3d direction = actualSource.subtract(playerPos);
        if (direction.lengthSquared() < 0.01D) {
            return playerPos;
        }

        return playerPos.add(direction.normalize().multiply(Math.min(maxDistance, direction.length())));
    }

    private Vec3d horizontalForward(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d horizontal = new Vec3d(look.x, 0.0D, look.z);
        if (horizontal.lengthSquared() < 0.01D) {
            return new Vec3d(0.0D, 0.0D, 1.0D);
        }

        return horizontal.normalize();
    }

    private DistanceRange tunnelDistanceRange(ItConfig config, HorrorPhase phase) {
        return switch (phase) {
            case INTRUSION -> new DistanceRange(config.phase4TunnelWatcherMinDistance, config.phase4TunnelWatcherMaxDistance);
            case MANIFESTATION -> new DistanceRange(config.phase5TunnelWatcherMinDistance, config.phase5TunnelWatcherMaxDistance);
            default -> new DistanceRange(config.phase3TunnelWatcherMinDistance, config.phase3TunnelWatcherMaxDistance);
        };
    }

    private DistanceRange normalWatcherDistanceRange(HorrorPhase phase) {
        return switch (phase) {
            case IMITATING -> new DistanceRange(PHASE3_NORMAL_WATCHER_MIN_DISTANCE, PHASE3_NORMAL_WATCHER_MAX_DISTANCE);
            case MANIFESTATION -> new DistanceRange(MANIFESTATION_NORMAL_WATCHER_MIN_DISTANCE, MANIFESTATION_NORMAL_WATCHER_MAX_DISTANCE);
            default -> new DistanceRange(DEFAULT_NORMAL_WATCHER_MIN_DISTANCE, DEFAULT_NORMAL_WATCHER_MAX_DISTANCE);
        };
    }

    private void rememberTunnelFailure(ServerPlayerEntity player, String reason) {
        lastTunnelWatcherFailure.put(player.getUuid(), reason);
    }

    private boolean hasWatcherNear(ServerPlayerEntity player, double radius) {
        Box box = player.getBoundingBox().expand(radius);
        return !player.getEntityWorld()
                .getEntitiesByType(TypeFilter.instanceOf(WatcherEntity.class), box, watcher -> true)
                .isEmpty();
    }

    private int countWatchers(ServerWorld world) {
        return world.getEntitiesByType(TypeFilter.instanceOf(WatcherEntity.class), watcher -> true).size();
    }

    private boolean isForestLike(ServerWorld world, BlockPos center) {
        int leaves = 0;

        for (int x = -5; x <= 5; x += 2) {
            for (int y = -2; y <= 5; y += 2) {
                for (int z = -5; z <= 5; z += 2) {
                    if (world.getBlockState(center.add(x, y, z)).isIn(BlockTags.LEAVES)) {
                        leaves++;
                        if (leaves >= 4) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private long secondsToTicks(int seconds) {
        return Math.max(0L, (long) seconds * 20L);
    }

    private int randomBetween(int min, int max) {
        if (max < min) {
            max = min;
        }
        return min + random.nextInt(max - min + 1);
    }

    private record DistanceRange(double min, double max) {
    }

    private record WatcherCandidate(BlockPos pos, int score) {
    }

    private record TunnelCandidate(BlockPos pos, int score) {
    }

    public static final class TunnelWatcherDebugInfo {
        public final HorrorPhase phase;
        public final int lightLevel;
        public final int maxLightLevel;
        public final boolean enabled;
        public final boolean phaseAllowed;
        public final boolean caveLike;
        public final boolean lowLight;
        public final boolean strongEventActive;
        public final boolean cooldownReady;
        public final long cooldownTicks;
        public final boolean canTry;
        public final String lastFailureReason;

        private TunnelWatcherDebugInfo(
                HorrorPhase phase,
                int lightLevel,
                int maxLightLevel,
                boolean enabled,
                boolean phaseAllowed,
                boolean caveLike,
                boolean lowLight,
                boolean strongEventActive,
                boolean cooldownReady,
                long cooldownTicks,
                boolean canTry,
                String lastFailureReason
        ) {
            this.phase = phase;
            this.lightLevel = lightLevel;
            this.maxLightLevel = maxLightLevel;
            this.enabled = enabled;
            this.phaseAllowed = phaseAllowed;
            this.caveLike = caveLike;
            this.lowLight = lowLight;
            this.strongEventActive = strongEventActive;
            this.cooldownReady = cooldownReady;
            this.cooldownTicks = cooldownTicks;
            this.canTry = canTry;
            this.lastFailureReason = lastFailureReason;
        }
    }
}
