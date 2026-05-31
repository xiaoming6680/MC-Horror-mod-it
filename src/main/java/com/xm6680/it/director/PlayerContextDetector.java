package com.xm6680.it.director;

import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.progression.PlayerHorrorData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerContextDetector {
    private static final int UPDATE_INTERVAL_TICKS = 100;
    private static final int AFK_THRESHOLD_TICKS = 3 * 60 * 20;
    private static final double ISOLATED_DISTANCE = 48.0D;
    private static final double STABLE_AREA_DISTANCE_SQUARED = 12.0D * 12.0D;

    private final Map<UUID, Vec3d> lastPositions = new HashMap<>();
    private final Map<UUID, Float> lastYaw = new HashMap<>();
    private final Map<UUID, Float> lastPitch = new HashMap<>();
    private final Map<UUID, Long> lastSampleTicks = new HashMap<>();
    private final Map<UUID, Boolean> wasAfk = new HashMap<>();
    private final Map<UUID, BlockPos> stableCenters = new HashMap<>();
    private final Map<UUID, PlayerContextSnapshot> snapshots = new HashMap<>();

    public void tick(MinecraftServer server, long currentTick, ReceiverManager receiverManager, HorrorProgressionManager progressionManager) {
        if (currentTick % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) {
                continue;
            }

            update(player, currentTick, receiverManager, progressionManager);
        }
    }

    public PlayerContextSnapshot getSnapshot(ServerPlayerEntity player) {
        return snapshots.getOrDefault(player.getUuid(), emptySnapshot(player));
    }

    public void remove(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        lastPositions.remove(uuid);
        lastYaw.remove(uuid);
        lastPitch.remove(uuid);
        lastSampleTicks.remove(uuid);
        wasAfk.remove(uuid);
        stableCenters.remove(uuid);
        snapshots.remove(uuid);
    }

    private void update(ServerPlayerEntity player, long currentTick, ReceiverManager receiverManager, HorrorProgressionManager progressionManager) {
        UUID uuid = player.getUuid();
        ItConfig config = ItConfigManager.getConfig();
        PlayerHorrorData data = progressionManager.getData(player);
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d previousPos = lastPositions.getOrDefault(uuid, currentPos);
        long previousTick = lastSampleTicks.getOrDefault(uuid, currentTick);
        long deltaTicks = Math.max(1L, currentTick - previousTick);
        double movedSquared = currentPos.squaredDistanceTo(previousPos);
        float yawDelta = Math.abs(player.getYaw() - lastYaw.getOrDefault(uuid, player.getYaw()));
        float pitchDelta = Math.abs(player.getPitch() - lastPitch.getOrDefault(uuid, player.getPitch()));
        boolean movedOrLooked = movedSquared > 0.01D || yawDelta > 2.0F || pitchDelta > 2.0F;

        if (movedOrLooked) {
            data.idleTicks = 0L;
        } else {
            data.idleTicks += deltaTicks;
        }

        if (!player.isOnGround()) {
            data.airborneTicks += deltaTicks;
        } else {
            data.airborneTicks = 0L;
        }

        if (player.hasVehicle()) {
            data.mountedTicks += deltaTicks;
        } else {
            data.mountedTicks = 0L;
        }

        updateStableArea(uuid, data, pos, deltaTicks);

        int nearbyPlayers = countNearbyPlayers(player, config.groupRadius);
        int lightLevel = world.getLightLevel(pos);
        boolean skyVisible = world.isSkyVisible(pos);
        boolean underground = !world.getRegistryKey().equals(World.NETHER)
                && !world.getRegistryKey().equals(World.END)
                && !skyVisible
                && player.getY() < world.getSeaLevel() + 8;
        boolean skyborne = player.getY() >= config.skyborneMinY
                || player.isGliding()
                || (data.airborneTicks >= secondsToTicks(config.skyborneMinAirborneSeconds) && player.getY() >= world.getSeaLevel() + 16)
                || (player.hasVehicle() && player.getY() >= config.skyborneMinY - 16);
        boolean fastTraveling = movedSquared >= 400.0D || player.isGliding() || (player.hasVehicle() && movedSquared >= 144.0D);
        boolean baseLike = data.stableAreaTicks >= secondsToTicks(240) || looksLikeBase(world, pos);
        boolean afk = data.idleTicks >= AFK_THRESHOLD_TICKS;
        boolean activeAfterAfk = wasAfk.getOrDefault(uuid, false) && !afk && movedOrLooked;
        int unreadMessages = Math.max(0, receiverManager.getMessageCount(player) - data.lastReceiverOpenedMessageCount);

        EnumSet<PlayerContext> contexts = EnumSet.noneOf(PlayerContext.class);
        if (world.getRegistryKey().equals(World.NETHER)) {
            contexts.add(PlayerContext.NETHER);
        } else if (world.getRegistryKey().equals(World.END)) {
            contexts.add(PlayerContext.END);
        } else if (underground) {
            contexts.add(PlayerContext.UNDERGROUND);
        }

        if (skyborne) {
            contexts.add(PlayerContext.SKYBORNE);
        }

        if (baseLike) {
            contexts.add(PlayerContext.HOME_OR_BASE);
        }

        if (nearbyPlayers > 0) {
            contexts.add(PlayerContext.GROUPED);
        }

        if (countNearbyPlayers(player, ISOLATED_DISTANCE) == 0) {
            contexts.add(PlayerContext.ISOLATED);
        }

        if (afk) {
            contexts.add(PlayerContext.AFK_OR_IDLE);
        }

        if (fastTraveling) {
            contexts.add(PlayerContext.FAST_TRAVELING);
        }

        if (lightLevel <= 7) {
            contexts.add(PlayerContext.IN_DARKNESS);
        }

        if (currentTick - data.lastReceiverOpenedGameTime <= secondsToTicks(60)) {
            contexts.add(PlayerContext.IN_RECEIVER_INTERACTION);
        }

        if (contexts.stream().noneMatch(context -> context == PlayerContext.UNDERGROUND
                || context == PlayerContext.NETHER
                || context == PlayerContext.END
                || context == PlayerContext.SKYBORNE
                || context == PlayerContext.HOME_OR_BASE)) {
            contexts.add(PlayerContext.GROUND_EXPLORING);
        }

        data.groupedTicks = contexts.contains(PlayerContext.GROUPED) ? data.groupedTicks + deltaTicks : 0L;
        data.fastTravelTicks = contexts.contains(PlayerContext.FAST_TRAVELING) ? data.fastTravelTicks + deltaTicks : 0L;
        data.safeBaseTicks = contexts.contains(PlayerContext.HOME_OR_BASE) ? data.safeBaseTicks + deltaTicks : 0L;

        snapshots.put(uuid, new PlayerContextSnapshot(
                uuid,
                contexts,
                lightLevel,
                nearbyPlayers,
                data.airborneTicks,
                data.mountedTicks,
                data.idleTicks,
                data.stableAreaTicks,
                unreadMessages,
                activeAfterAfk
        ));

        lastPositions.put(uuid, currentPos);
        lastYaw.put(uuid, player.getYaw());
        lastPitch.put(uuid, player.getPitch());
        lastSampleTicks.put(uuid, currentTick);
        wasAfk.put(uuid, afk);
    }

    private void updateStableArea(UUID uuid, PlayerHorrorData data, BlockPos pos, long deltaTicks) {
        BlockPos stableCenter = stableCenters.get(uuid);
        if (stableCenter == null || squaredDistance(stableCenter, pos) > STABLE_AREA_DISTANCE_SQUARED) {
            stableCenters.put(uuid, pos.toImmutable());
            data.stableAreaTicks = 0L;
            return;
        }

        data.stableAreaTicks += deltaTicks;
    }

    private int countNearbyPlayers(ServerPlayerEntity player, double radius) {
        double radiusSquared = radius * radius;
        int count = 0;
        for (ServerPlayerEntity other : player.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            if (other == player || other.isSpectator() || other.getEntityWorld() != player.getEntityWorld()) {
                continue;
            }

            if (other.squaredDistanceTo(player) <= radiusSquared) {
                count++;
            }
        }
        return count;
    }

    private boolean looksLikeBase(ServerWorld world, BlockPos center) {
        int score = 0;
        for (int x = -5; x <= 5; x += 2) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -5; z <= 5; z += 2) {
                    BlockState state = world.getBlockState(center.add(x, y, z));
                    if (state.isOf(Blocks.CHEST)
                            || state.isOf(Blocks.BARREL)
                            || state.isOf(Blocks.CRAFTING_TABLE)
                            || state.isOf(Blocks.FURNACE)
                            || state.isOf(Blocks.TORCH)
                            || state.isOf(Blocks.WALL_TORCH)
                            || state.isIn(BlockTags.BEDS)
                            || state.getBlock() instanceof DoorBlock) {
                        score++;
                        if (score >= 3) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private double squaredDistance(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dy = first.getY() - second.getY();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private PlayerContextSnapshot emptySnapshot(ServerPlayerEntity player) {
        return new PlayerContextSnapshot(
                player.getUuid(),
                EnumSet.of(PlayerContext.GROUND_EXPLORING),
                player.getEntityWorld().getLightLevel(player.getBlockPos()),
                0,
                0L,
                0L,
                0L,
                0L,
                0,
                false
        );
    }

    private long secondsToTicks(int seconds) {
        return (long) Math.max(0, seconds) * 20L;
    }
}
