package com.xm6680.it.event;

import com.xm6680.it.ItMod;
import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.analog.ReceiverMessageType;
import com.xm6680.it.analog.ReceiverRecordPolicy;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Low-frequency Nether-only anomalies. Portal events can shatter portal blocks, but never move players or spawn attackers.
 */
public class NetherSignalManager {
    private static final int TICKS_PER_SECOND = 20;
    private static final double RECEIVER_SIGNAL_BASE_CHANCE = 0.040D;
    private static final double PHANTOM_GHAST_BASE_CHANCE = 0.030D;
    private static final double SOUL_WHISPER_BASE_CHANCE = 0.045D;
    private static final double PORTAL_ANOMALY_BASE_CHANCE = 0.026D;
    private static final int MINOR_SOUL_SEARCH_RADIUS = 4;
    private static final int PORTAL_SHATTER_DELAY_TICKS = 45;
    private static final int PORTAL_SHATTER_MAX_BLOCKS = 6;
    private static final int PORTAL_SHATTER_SEARCH_RADIUS = 2;
    private static final String[] RECEIVER_SIGNAL_MESSAGES = {
            "下界无夜晚，但夜间记录仍在写入。",
            "下界维度时间无法与主世界夜晚同步。",
            "下界接收器收到主世界午夜回波。",
            "记录来源：下界；时间字段：夜间。",
            "返回路径未确认：下界坐标正在重复。"
    };
    private static final String[] PHANTOM_GHAST_MESSAGES = {
            "下界声源记录：恶魂哭声，附近无实体匹配。",
            "下界上方出现空白声源。",
            "恶魂样本来自下界空气层，实体列表为空。"
    };
    private static final String[] SOUL_WHISPER_MESSAGES = {
            "灵魂沙下方有低语声回传。",
            "下界灵魂材质附近存在低频语言样本。",
            "脚下灵魂土正在重复玩家呼吸声。"
    };
    private static final String[] PORTAL_MESSAGES = {
            "下界传送门返回路径出现碎裂回声。",
            "下界入口附近检测到第二个返回脚步。",
            "传送门另一侧传来主世界玩家行为记录。",
            "返回路径未确认：下界入口正在重复响应。",
            "下界传送门记录到未登记穿越尝试。"
    };

    private final HorrorProgressionManager progressionManager;
    private final ReceiverManager receiverManager;
    private final Random random = new Random();
    private final Map<UUID, Long> nextReceiverSignalTicks = new HashMap<>();
    private final Map<UUID, Long> nextPhantomGhastTicks = new HashMap<>();
    private final Map<UUID, Long> nextSoulWhisperTicks = new HashMap<>();
    private final Map<UUID, Long> nextPortalAnomalyTicks = new HashMap<>();
    private final Map<UUID, PortalShatterState> activePortalShatters = new HashMap<>();

    public NetherSignalManager(HorrorProgressionManager progressionManager, ReceiverManager receiverManager) {
        this.progressionManager = progressionManager;
        this.receiverManager = receiverManager;
    }

    public void tick(MinecraftServer server, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableNetherSignalEvents) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!isEligiblePlayer(player) || isStrongEventActive(player, currentTick)) {
                continue;
            }

            tryReceiverSignal(player, currentTick, config);
            tryPhantomGhastCry(player, currentTick, config);
            trySoulWhisper(player, currentTick, config);
            tryPortalAnomaly(player, currentTick, config);
        }
    }

    public void tickActive(MinecraftServer server, long currentTick) {
        Iterator<Map.Entry<UUID, PortalShatterState>> iterator = activePortalShatters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PortalShatterState> entry = iterator.next();
            PortalShatterState state = entry.getValue();
            if (currentTick < state.shatterTick()) {
                continue;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !isEligiblePlayer(player)) {
                iterator.remove();
                continue;
            }

            shatterPortalBlocks(player, state.origin());
            iterator.remove();
        }
    }

    public void remove(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        nextReceiverSignalTicks.remove(playerId);
        nextPhantomGhastTicks.remove(playerId);
        nextSoulWhisperTicks.remove(playerId);
        nextPortalAnomalyTicks.remove(playerId);
        activePortalShatters.remove(playerId);
    }

    public boolean triggerReceiverSignal(ServerPlayerEntity player, boolean forced) {
        return triggerReceiverSignal(player, currentServerTick(player), forced);
    }

    public boolean triggerPhantomGhastCry(ServerPlayerEntity player, boolean forced) {
        return triggerPhantomGhastCry(player, currentServerTick(player), forced);
    }

    public boolean triggerSoulSandWhisper(ServerPlayerEntity player, boolean forced) {
        return triggerSoulSandWhisper(player, currentServerTick(player), forced);
    }

    public boolean triggerPortalAnomaly(ServerPlayerEntity player, boolean forced) {
        return triggerPortalAnomaly(player, currentServerTick(player), forced);
    }

    public boolean hasNearbySoulMaterial(ServerPlayerEntity player) {
        return findSoulMaterial(player) != null;
    }

    public boolean hasNearbyPortal(ServerPlayerEntity player) {
        return findPortal(player, ItConfigManager.getConfig()) != null;
    }

    public static boolean isInNether(ServerPlayerEntity player) {
        return player.getEntityWorld().getRegistryKey().equals(World.NETHER);
    }

    private void tryReceiverSignal(ServerPlayerEntity player, long currentTick, ItConfig config) {
        UUID playerId = player.getUuid();
        if (!cooldownReady(nextReceiverSignalTicks, playerId, currentTick)) {
            return;
        }

        if (!phaseAllowed(player, phaseFromNumber(config.netherSignalMinPhase))) {
            return;
        }

        if (!rollNatural(player, RECEIVER_SIGNAL_BASE_CHANCE, config)) {
            setMissCooldown(nextReceiverSignalTicks, playerId, currentTick);
            return;
        }

        if (triggerReceiverSignal(player, currentTick, false)) {
            setCooldown(nextReceiverSignalTicks, playerId, currentTick, config.netherReceiverSignalCooldownSeconds);
        } else {
            setMissCooldown(nextReceiverSignalTicks, playerId, currentTick);
        }
    }

    private void tryPhantomGhastCry(ServerPlayerEntity player, long currentTick, ItConfig config) {
        UUID playerId = player.getUuid();
        if (!cooldownReady(nextPhantomGhastTicks, playerId, currentTick) || !phaseAllowed(player, HorrorPhase.WATCHING)) {
            return;
        }

        if (!rollNatural(player, PHANTOM_GHAST_BASE_CHANCE, config)) {
            setMissCooldown(nextPhantomGhastTicks, playerId, currentTick);
            return;
        }

        if (triggerPhantomGhastCry(player, currentTick, false)) {
            setCooldown(nextPhantomGhastTicks, playerId, currentTick, config.netherPhantomGhastCooldownSeconds);
        } else {
            setMissCooldown(nextPhantomGhastTicks, playerId, currentTick);
        }
    }

    private void trySoulWhisper(ServerPlayerEntity player, long currentTick, ItConfig config) {
        UUID playerId = player.getUuid();
        if (!cooldownReady(nextSoulWhisperTicks, playerId, currentTick) || !phaseAllowed(player, HorrorPhase.WATCHING)) {
            return;
        }

        if (findSoulMaterial(player) == null) {
            return;
        }

        if (!rollNatural(player, SOUL_WHISPER_BASE_CHANCE, config)) {
            setMissCooldown(nextSoulWhisperTicks, playerId, currentTick);
            return;
        }

        if (triggerSoulSandWhisper(player, currentTick, false)) {
            setCooldown(nextSoulWhisperTicks, playerId, currentTick, config.netherSoulWhisperCooldownSeconds);
        } else {
            setMissCooldown(nextSoulWhisperTicks, playerId, currentTick);
        }
    }

    private void tryPortalAnomaly(ServerPlayerEntity player, long currentTick, ItConfig config) {
        UUID playerId = player.getUuid();
        if (!cooldownReady(nextPortalAnomalyTicks, playerId, currentTick)
                || !phaseAllowed(player, phaseFromNumber(config.netherPortalAnomalyMinPhase))) {
            return;
        }

        if (findPortal(player, config) == null) {
            return;
        }

        if (!rollNatural(player, PORTAL_ANOMALY_BASE_CHANCE, config)) {
            setMissCooldown(nextPortalAnomalyTicks, playerId, currentTick);
            return;
        }

        if (triggerPortalAnomaly(player, currentTick, false)) {
            setCooldown(nextPortalAnomalyTicks, playerId, currentTick, config.netherPortalAnomalyCooldownSeconds);
        } else {
            setMissCooldown(nextPortalAnomalyTicks, playerId, currentTick);
        }
    }

    private boolean triggerReceiverSignal(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!canTrigger(player, phaseFromNumber(config.netherSignalMinPhase), forced, currentTick)) {
            return false;
        }

        HorrorPhase phase = progressionManager.getPhase(player);
        ReceiverRecordPolicy policy = phase.isAtLeast(HorrorPhase.INTRUSION)
                ? ReceiverRecordPolicy.IMPORTANT
                : ReceiverRecordPolicy.SAMPLED;
        boolean scheduled = scheduleReceiverMessage(
                player,
                ReceiverMessageType.LOCAL_ALERT,
                pick(RECEIVER_SIGNAL_MESSAGES),
                policy,
                config.netherSignalReceiverDelayTicks,
                forced
        );
        if (scheduled) {
            setCooldown(nextReceiverSignalTicks, player.getUuid(), currentTick, config.netherReceiverSignalCooldownSeconds);
        }
        return scheduled;
    }

    private boolean triggerPhantomGhastCry(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!canTrigger(player, HorrorPhase.WATCHING, forced, currentTick)) {
            return false;
        }

        if (!forced && config.netherPhantomGhastRequiresNoNearbyGhast && hasNearbyGhast(player, config)) {
            return false;
        }

        SoundEvent[] sounds = {
                SoundEvents.ENTITY_GHAST_AMBIENT,
                SoundEvents.ENTITY_GHAST_SCREAM,
                SoundEvents.ENTITY_GHAST_HURT
        };
        Vec3d source = rearSideAboveSource(player, forced);
        SoundEvent primarySound = forced ? SoundEvents.ENTITY_GHAST_SCREAM : sounds[random.nextInt(sounds.length)];
        sendSoundToPlayer(player, primarySound, SoundCategory.AMBIENT, source, forced ? 1.35F : 1.05F, forced ? 0.70F : 0.58F + random.nextFloat() * 0.28F);
        if (forced) {
            sendSoundToPlayer(player, SoundEvents.ENTITY_GHAST_AMBIENT, SoundCategory.HOSTILE, source.add(0.0D, 1.4D, 0.0D), 0.95F, 0.56F);
        }
        ItMod.getMinorAnomalyAccumulator().recordMinorSound(player, "nether_phantom_ghast");

        if (config.enableReceiverForNetherMinorSounds) {
            scheduleReceiverMessage(
                    player,
                    ReceiverMessageType.OBSERVATION,
                    pick(PHANTOM_GHAST_MESSAGES),
                    ReceiverRecordPolicy.RARE,
                    config.netherSignalReceiverDelayTicks,
                    forced
            );
        }
        setCooldown(nextPhantomGhastTicks, player.getUuid(), currentTick, config.netherPhantomGhastCooldownSeconds);
        return true;
    }

    private boolean triggerSoulSandWhisper(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!canTrigger(player, HorrorPhase.WATCHING, forced, currentTick)) {
            return false;
        }

        BlockPos soulPos = findSoulMaterial(player);
        if (soulPos == null) {
            return false;
        }

        Vec3d source = Vec3d.ofCenter(soulPos);
        sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, source, 0.42F, 0.38F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, source, 0.28F, 0.34F + random.nextFloat() * 0.12F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, source, 0.34F, 0.42F + random.nextFloat() * 0.10F);
        ItMod.getMinorAnomalyAccumulator().recordMinorSound(player, "nether_soul_whisper");

        if (config.enableReceiverForNetherMinorSounds) {
            scheduleReceiverMessage(
                    player,
                    ReceiverMessageType.OBSERVATION,
                    pick(SOUL_WHISPER_MESSAGES),
                    ReceiverRecordPolicy.RARE,
                    config.netherSignalReceiverDelayTicks,
                    forced
            );
        }
        setCooldown(nextSoulWhisperTicks, player.getUuid(), currentTick, config.netherSoulWhisperCooldownSeconds);
        return true;
    }

    private boolean triggerPortalAnomaly(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        if (!canTrigger(player, phaseFromNumber(config.netherPortalAnomalyMinPhase), forced, currentTick)) {
            return false;
        }

        BlockPos portalPos = findPortal(player, config);
        if (portalPos == null) {
            return false;
        }

        Vec3d source = Vec3d.ofCenter(portalPos);
        sendSoundToPlayer(player, SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.AMBIENT, source, 1.15F, 0.58F + random.nextFloat() * 0.16F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.AMBIENT, source, 0.95F, 0.52F + random.nextFloat() * 0.14F);
        activePortalShatters.put(player.getUuid(), new PortalShatterState(portalPos.toImmutable(), currentTick + PORTAL_SHATTER_DELAY_TICKS));

        Vec3d extraSource = offsetNear(source, 1.2D);
        int extraSound = random.nextInt(3);
        if (extraSound == 0) {
            sendSoundToPlayer(player, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.AMBIENT, extraSource, 0.52F, 0.62F + random.nextFloat() * 0.22F);
        } else if (extraSound == 1) {
            sendSoundToPlayer(player, SoundEvents.BLOCK_STONE_STEP, SoundCategory.AMBIENT, extraSource, 0.52F, 0.62F + random.nextFloat() * 0.22F);
        } else {
            sendSoundToPlayer(player, SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.AMBIENT, extraSource, 0.52F, 0.62F + random.nextFloat() * 0.22F);
        }

        if (config.enableNetherPortalDistortion
                && config.netherPortalDistortionDurationTicks > 0
                && config.netherPortalDistortionIntensity > 0.0F) {
            ItNetwork.sendReceiverDistortion(player, config.netherPortalDistortionDurationTicks, config.netherPortalDistortionIntensity);
        }

        HorrorPhase phase = progressionManager.getPhase(player);
        ReceiverRecordPolicy policy = phase.isAtLeast(HorrorPhase.INTRUSION)
                ? ReceiverRecordPolicy.IMPORTANT
                : ReceiverRecordPolicy.SAMPLED;
        scheduleReceiverMessage(
                player,
                ReceiverMessageType.SYSTEM_ERROR,
                pick(PORTAL_MESSAGES),
                policy,
                config.netherSignalReceiverDelayTicks,
                forced
        );
        setCooldown(nextPortalAnomalyTicks, player.getUuid(), currentTick, config.netherPortalAnomalyCooldownSeconds);
        return true;
    }

    private void shatterPortalBlocks(ServerPlayerEntity player, BlockPos origin) {
        ServerWorld world = player.getEntityWorld();
        List<BlockPos> portalBlocks = collectPortalBlocks(world, origin);
        if (portalBlocks.isEmpty()) {
            return;
        }

        Vec3d source = Vec3d.ofCenter(origin);
        sendSoundToPlayer(player, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, source, 1.15F, 0.38F + random.nextFloat() * 0.10F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, source, 0.95F, 0.40F + random.nextFloat() * 0.10F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, source, 0.82F, 0.36F + random.nextFloat() * 0.10F);

        for (BlockPos pos : portalBlocks) {
            if (world.getBlockState(pos).isOf(Blocks.NETHER_PORTAL)) {
                world.breakBlock(pos, false, player);
            }
        }
    }

    private List<BlockPos> collectPortalBlocks(ServerWorld world, BlockPos origin) {
        List<BlockPos> portalBlocks = new ArrayList<>();
        addPortalBlock(world, portalBlocks, origin);

        for (int y = -PORTAL_SHATTER_SEARCH_RADIUS; y <= PORTAL_SHATTER_SEARCH_RADIUS; y++) {
            for (int x = -PORTAL_SHATTER_SEARCH_RADIUS; x <= PORTAL_SHATTER_SEARCH_RADIUS; x++) {
                for (int z = -PORTAL_SHATTER_SEARCH_RADIUS; z <= PORTAL_SHATTER_SEARCH_RADIUS; z++) {
                    if (portalBlocks.size() >= PORTAL_SHATTER_MAX_BLOCKS) {
                        return portalBlocks;
                    }

                    addPortalBlock(world, portalBlocks, origin.add(x, y, z));
                }
            }
        }

        return portalBlocks;
    }

    private void addPortalBlock(ServerWorld world, List<BlockPos> portalBlocks, BlockPos pos) {
        if (!world.getBlockState(pos).isOf(Blocks.NETHER_PORTAL) || portalBlocks.contains(pos)) {
            return;
        }

        portalBlocks.add(pos.toImmutable());
    }

    private boolean scheduleReceiverMessage(ServerPlayerEntity player, ReceiverMessageType type, String text, ReceiverRecordPolicy policy, int delayTicks, boolean forced) {
        HorrorPhase phase = progressionManager.getPhase(player);
        ItConfig config = ItConfigManager.getConfig();
        if (!forced && !AnalogHorrorManager.shouldRecord(random, phase, policy, config)) {
            return false;
        }

        receiverManager.scheduleMessage(player, type, phase, text, Math.max(0, delayTicks));
        return true;
    }

    private boolean canTrigger(ServerPlayerEntity player, HorrorPhase minPhase, boolean forced, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        return config.enableNetherSignalEvents
                && isEligiblePlayer(player)
                && (forced || !isStrongEventActive(player, currentTick))
                && (forced || phaseAllowed(player, minPhase));
    }

    private boolean isEligiblePlayer(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && isInNether(player);
    }

    private boolean phaseAllowed(ServerPlayerEntity player, HorrorPhase minPhase) {
        return progressionManager.getPhase(player).isAtLeast(minPhase);
    }

    private boolean hasNearbyGhast(ServerPlayerEntity player, ItConfig config) {
        double radius = Math.max(1.0D, config.netherPhantomGhastCheckRadius);
        return !player.getEntityWorld().getEntitiesByType(
                TypeFilter.instanceOf(GhastEntity.class),
                player.getBoundingBox().expand(radius),
                ghast -> ghast.isAlive()
        ).isEmpty();
    }

    private BlockPos findSoulMaterial(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        BlockPos center = player.getBlockPos();
        for (int x = -MINOR_SOUL_SEARCH_RADIUS; x <= MINOR_SOUL_SEARCH_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -MINOR_SOUL_SEARCH_RADIUS; z <= MINOR_SOUL_SEARCH_RADIUS; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (isSoulMaterial(world.getBlockState(pos))) {
                        return pos;
                    }
                }
            }
        }

        return null;
    }

    private boolean isSoulMaterial(BlockState state) {
        return state.isOf(Blocks.SOUL_SAND)
                || state.isOf(Blocks.SOUL_SOIL)
                || state.isOf(Blocks.SOUL_FIRE)
                || state.isOf(Blocks.SOUL_TORCH)
                || state.isOf(Blocks.SOUL_WALL_TORCH)
                || state.isOf(Blocks.SOUL_CAMPFIRE);
    }

    private BlockPos findPortal(ServerPlayerEntity player, ItConfig config) {
        ServerWorld world = player.getEntityWorld();
        BlockPos center = player.getBlockPos();
        double radius = Math.max(1.0D, config.netherPortalSearchRadius);
        double radiusSquared = radius * radius;
        int blockRadius = (int) Math.ceil(radius);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -blockRadius; x <= blockRadius; x++) {
            for (int y = -blockRadius; y <= blockRadius; y++) {
                for (int z = -blockRadius; z <= blockRadius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (!world.getBlockState(pos).isOf(Blocks.NETHER_PORTAL)) {
                        continue;
                    }

                    double distanceSquared = squaredDistanceToCenter(player, pos);
                    if (distanceSquared <= radiusSquared && distanceSquared < bestDistance) {
                        best = pos;
                        bestDistance = distanceSquared;
                    }
                }
            }
        }

        return best;
    }

    private double squaredDistanceToCenter(ServerPlayerEntity player, BlockPos pos) {
        double dx = pos.getX() + 0.5D - player.getX();
        double dy = pos.getY() + 0.5D - player.getY();
        double dz = pos.getZ() + 0.5D - player.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private Vec3d rearSideAboveSource(ServerPlayerEntity player, boolean forced) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        if (look.lengthSquared() < 0.01D) {
            look = new Vec3d(0.0D, 0.0D, 1.0D);
        }

        Vec3d side = new Vec3d(-look.z, 0.0D, look.x).normalize();
        if (random.nextBoolean()) {
            side = side.multiply(-1.0D);
        }

        double distance = forced ? 7.0D + random.nextDouble() * 5.0D : 12.0D + random.nextDouble() * 14.0D;
        double sideOffset = forced ? 2.0D + random.nextDouble() * 4.0D : 3.0D + random.nextDouble() * 8.0D;
        double yOffset = forced ? 2.5D + random.nextDouble() * 4.0D : 5.0D + random.nextDouble() * 9.0D;
        return new Vec3d(player.getX(), player.getY() + yOffset, player.getZ())
                .subtract(look.multiply(distance))
                .add(side.multiply(sideOffset));
    }

    private Vec3d offsetNear(Vec3d base, double distance) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        return new Vec3d(
                base.x + Math.cos(angle) * distance,
                base.y + random.nextDouble() * 1.5D,
                base.z + Math.sin(angle) * distance
        );
    }

    private boolean rollNatural(ServerPlayerEntity player, double baseChance, ItConfig config) {
        double chance = EventChanceScaler.clampChance(baseChance
                * config.netherSignalChanceMultiplier
                * config.eventChanceMultiplier
                * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
        return random.nextDouble() <= chance;
    }

    private boolean isStrongEventActive(ServerPlayerEntity player, long currentTick) {
        return ItMod.getChaseManager().isChasing(player)
                || ItMod.getCaveStalkerManager().isActive(player)
                || ItNetwork.isJumpscareActive(player, currentTick)
                || ItNetwork.isFaceScareActive(player, currentTick)
                || ItNetwork.isManifestationOverlayActive(player, currentTick)
                || ItNetwork.isAnimalDisguiseRetaliationActive(player, currentTick);
    }

    private boolean cooldownReady(Map<UUID, Long> cooldowns, UUID playerId, long currentTick) {
        return currentTick >= cooldowns.getOrDefault(playerId, 0L);
    }

    private void setCooldown(Map<UUID, Long> cooldowns, UUID playerId, long currentTick, int seconds) {
        cooldowns.put(playerId, currentTick + secondsToTicks(Math.max(60, seconds)));
    }

    private void setMissCooldown(Map<UUID, Long> cooldowns, UUID playerId, long currentTick) {
        cooldowns.put(playerId, currentTick + secondsToTicks(35 + random.nextInt(46)));
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }

    private long currentServerTick(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        return server == null ? 0L : server.getTicks();
    }

    private HorrorPhase phaseFromNumber(int number) {
        return HorrorPhase.fromNumber(Math.max(1, Math.min(5, number)));
    }

    private String pick(String[] values) {
        return values[random.nextInt(values.length)];
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, pos, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(sound, category, pos.x, pos.y, pos.z, volume, pitch, random.nextLong());
        player.networkHandler.sendPacket(packet);
    }

    private record PortalShatterState(BlockPos origin, long shatterTick) {
    }
}
