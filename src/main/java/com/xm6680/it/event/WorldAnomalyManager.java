package com.xm6680.it.event;

import com.xm6680.it.ItMod;
import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.analog.ReceiverMessageType;
import com.xm6680.it.analog.ReceiverRecordPolicy;
import com.xm6680.it.config.ItConfig;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.watching.HorrorPhase;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public class WorldAnomalyManager {
    private static final int TICKS_PER_SECOND = 20;

    private static final String[] SIGN_LINES = {
            "它是谁？",
            "别数人数。",
            "门不是你开的。",
            "它知道这里。",
            "接收器在听。",
            "不要回答。",
            "少了什么？",
            "你回来了。",
            "它在外面。",
            "这里不是家。"
    };

    private static final String[] ANIMAL_STARE_RECEIVER_MESSAGES = {
            "附近生物出现同步行为。",
            "多个目标同时转向你。",
            "动物反应记录完成。",
            "它们好像同时看见了什么。"
    };

    private static final String[] WORLD_ANOMALY_RECEIVER_MESSAGES = {
            "附近环境记录发生变更。",
            "物体位置校验失败。",
            "生活区异常已经完成记录。",
            "接收器延迟读取到环境变化。",
            "异常发生时间早于记录时间。"
    };

    private final HorrorProgressionManager progressionManager;
    private final ReceiverManager receiverManager;
    private final Map<UUID, Long> nextAnimalStareTicks = new HashMap<>();
    private final Map<UUID, Long> nextBaseAnomalyTicks = new HashMap<>();
    private final Map<UUID, Long> nextSignAnomalyTicks = new HashMap<>();
    private final Map<UUID, Long> nextCrossAnomalyTicks = new HashMap<>();
    private final List<ActiveAnimalStare> activeAnimalStares = new ArrayList<>();
    private final Random random = new Random();

    public WorldAnomalyManager(HorrorProgressionManager progressionManager, ReceiverManager receiverManager) {
        this.progressionManager = progressionManager;
        this.receiverManager = receiverManager;
    }

    public void tickActive(MinecraftServer server, long currentTick) {
        Iterator<ActiveAnimalStare> iterator = activeAnimalStares.iterator();
        while (iterator.hasNext()) {
            ActiveAnimalStare stare = iterator.next();
            if (currentTick >= stare.untilTick) {
                iterator.remove();
                continue;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(stare.playerUuid);
            if (player == null || player.isSpectator()) {
                iterator.remove();
                continue;
            }

            Entity entity = player.getEntityWorld().getEntityById(stare.animalEntityId);
            if (!(entity instanceof AnimalEntity animal) || !entity.isAlive()) {
                iterator.remove();
                continue;
            }

            holdAnimalStare(animal, player);
        }
    }

    public void tick(MinecraftServer server, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableWorldAnomalyEvents) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator()) {
                continue;
            }

            HorrorPhase phase = progressionManager.getPhase(player);
            if (phase.isAtLeast(HorrorPhase.WATCHING)) {
                tryAnimalStare(player, currentTick, config);
            }

            if (phase.isAtLeast(HorrorPhase.IMITATING)) {
                trySignAnomaly(player, player.getBlockPos(), currentTick, config, phase);
                tryNetherrackCross(player, currentTick, config, phase);
            }

            if (phase.isAtLeast(HorrorPhase.INTRUSION)) {
                tryBaseAnomaly(player, player.getBlockPos(), currentTick, config, false);
            }
        }
    }

    public void onSleepAttempt(ServerPlayerEntity player, BlockPos bedPos, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableWorldAnomalyEvents || !config.enableBaseAnomalies) {
            return;
        }

        if (progressionManager.getPhase(player).isAtLeast(HorrorPhase.INTRUSION)) {
            tryBaseAnomaly(player, bedPos, currentTick, config, true);
        }
    }

    public boolean forceAnimalStare(ServerPlayerEntity player, long currentTick) {
        return startAnimalStares(player, currentTick, true) > 0;
    }

    public int getEligibleAnimalCount(ServerPlayerEntity player) {
        return countEligibleAnimals(player, ItConfigManager.getConfig());
    }

    public boolean forceOpenDoor(ServerPlayerEntity player) {
        if (openNearbyDoor(player.getEntityWorld(), player.getBlockPos())) {
            scheduleWorldAnomalyRecord(player, ReceiverRecordPolicy.SAMPLED);
            return true;
        }
        return false;
    }

    public boolean forceRemoveSmallObject(ServerPlayerEntity player) {
        if (removeSmallBaseObject(player.getEntityWorld(), player.getBlockPos())) {
            scheduleWorldAnomalyRecord(player, ReceiverRecordPolicy.RARE);
            return true;
        }
        return false;
    }

    public boolean forcePottedFlower(ServerPlayerEntity player) {
        if (placePottedFlower(player.getEntityWorld(), player.getBlockPos())) {
            scheduleWorldAnomalyRecord(player, ReceiverRecordPolicy.RARE);
            return true;
        }
        return false;
    }

    public boolean forceSign(ServerPlayerEntity player) {
        if (placeSign(player.getEntityWorld(), player.getBlockPos(), 22)) {
            scheduleWorldAnomalyRecord(player, ReceiverRecordPolicy.IMPORTANT);
            return true;
        }
        return false;
    }

    public boolean forceBaseAnomaly(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableBaseAnomalies || !config.baseIntrusionEnabled) {
            return false;
        }

        List<BaseAnomalyAttempt> attempts = new ArrayList<>();
        addBaseAnomalyAttempts(attempts, player.getEntityWorld(), player.getBlockPos(), 12, config);

        for (int i = attempts.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            BaseAnomalyAttempt temp = attempts.get(i);
            attempts.set(i, attempts.get(j));
            attempts.set(j, temp);
        }

        for (BaseAnomalyAttempt attempt : attempts) {
            if (attempt.action().getAsBoolean()) {
                scheduleWorldAnomalyRecord(player, attempt.policy());
                return true;
            }
        }

        return false;
    }

    public boolean forceNetherrackCross(ServerPlayerEntity player) {
        ServerWorld world = player.getEntityWorld();
        for (int attempts = 0; attempts < 24; attempts++) {
            BlockPos base = randomGroundNear(player, 8, 24);
            if (base != null && placeNetherrackCross(world, base)) {
                scheduleWorldAnomalyRecord(player, ReceiverRecordPolicy.IMPORTANT);
                return true;
            }
        }

        return false;
    }

    private void tryAnimalStare(ServerPlayerEntity player, long currentTick, ItConfig config) {
        if (!config.enableAnimalStareEvents
                || !config.enableAnimalStareEvent
                || currentTick < nextAnimalStareTicks.getOrDefault(player.getUuid(), 0L)) {
            return;
        }

        nextAnimalStareTicks.put(player.getUuid(), currentTick + secondsToTicks(randomBetween(120, 260)));
        int nearbyAnimals = countEligibleAnimals(player, config);
        if (nearbyAnimals < Math.max(1, config.animalStareMinAnimals)) {
            return;
        }

        double chance = EventChanceScaler.clampChance(config.animalStareHighChance
                * config.worldAnomalyChanceMultiplier
                * config.eventChanceMultiplier
                * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() > chance) {
            return;
        }

        startAnimalStares(player, currentTick, false);
    }

    private int startAnimalStares(ServerPlayerEntity player, long currentTick, boolean forced) {
        ItConfig config = ItConfigManager.getConfig();
        ServerWorld world = player.getEntityWorld();
        List<? extends AnimalEntity> animals = world.getEntitiesByType(
                TypeFilter.instanceOf(AnimalEntity.class),
                player.getBoundingBox().expand(Math.max(2.0D, config.animalStareRadius)),
                animal -> isEligibleStaringAnimal(animal, player, config)
        );

        if (animals.size() < Math.max(1, config.animalStareMinAnimals)) {
            return 0;
        }

        List<AnimalEntity> selected = new ArrayList<>(animals);
        shuffleAnimals(selected);
        int maxAnimals = forced ? Math.max(config.animalStareMinAnimals, config.animalStareMaxAnimals) : config.animalStareMaxAnimals;
        int count = Math.min(selected.size(), Math.max(1, maxAnimals));
        long untilTick = currentTick + Math.max(20, config.animalStareDurationTicks);

        activeAnimalStares.removeIf(stare -> stare.playerUuid.equals(player.getUuid()));
        for (int i = 0; i < count; i++) {
            activeAnimalStares.add(new ActiveAnimalStare(selected.get(i).getId(), player.getUuid(), untilTick));
            holdAnimalStare(selected.get(i), player);
        }

        progressionManager.recordAnimalStareEvent(player);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, player.getX(), player.getY() + 0.8D, player.getZ(), 0.28F, 0.72F + random.nextFloat() * 0.08F);
        if (AnalogHorrorManager.shouldRecord(random, progressionManager.getPhase(player), ReceiverRecordPolicy.SAMPLED, config)) {
            receiverManager.scheduleMessage(player, ReceiverMessageType.OBSERVATION, progressionManager.getPhase(player), pick(ANIMAL_STARE_RECEIVER_MESSAGES), config.animalStareReceiverDelayTicks);
        }
        return count;
    }

    private int countEligibleAnimals(ServerPlayerEntity player, ItConfig config) {
        return player.getEntityWorld().getEntitiesByType(
                TypeFilter.instanceOf(AnimalEntity.class),
                player.getBoundingBox().expand(Math.max(2.0D, config.animalStareRadius)),
                animal -> isEligibleStaringAnimal(animal, player, config)
        ).size();
    }

    private boolean isEligibleStaringAnimal(AnimalEntity animal, ServerPlayerEntity player, ItConfig config) {
        return animal.isAlive()
                && !animal.isBaby()
                && animal.squaredDistanceTo(player) > 4.0D
                && (!config.animalStareIgnoreNamedAnimals || !animal.hasCustomName());
    }

    private void shuffleAnimals(List<AnimalEntity> animals) {
        for (int i = animals.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            AnimalEntity temp = animals.get(i);
            animals.set(i, animals.get(j));
            animals.set(j, temp);
        }
    }

    private void holdAnimalStare(AnimalEntity animal, ServerPlayerEntity player) {
        animal.getNavigation().stop();
        animal.setVelocity(Vec3d.ZERO);
        animal.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, player.getEyePos());
        animal.setBodyYaw(animal.getYaw());
        animal.setHeadYaw(animal.getYaw());
    }

    private void tryBaseAnomaly(ServerPlayerEntity player, BlockPos center, long currentTick, ItConfig config, boolean sleepTriggered) {
        if (!config.enableBaseAnomalies || !config.baseIntrusionEnabled) {
            return;
        }

        if (!sleepTriggered && currentTick < nextBaseAnomalyTicks.getOrDefault(player.getUuid(), 0L)) {
            return;
        }

        boolean night = isNight(player.getEntityWorld());
        if (!sleepTriggered && !night) {
            return;
        }

        nextBaseAnomalyTicks.put(player.getUuid(), currentTick + secondsToTicks(config.baseEventCooldownSeconds + randomBetween(0, 180)));
        double chance = EventChanceScaler.clampChance((sleepTriggered ? 0.42 : 0.16)
                * config.worldAnomalyChanceMultiplier
                * config.eventChanceMultiplier
                * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() > chance) {
            return;
        }

        List<BaseAnomalyAttempt> actions = new ArrayList<>();
        addBaseAnomalyAttempts(actions, player.getEntityWorld(), center, 10, config);
        if (actions.isEmpty()) {
            return;
        }

        BaseAnomalyAttempt action = actions.get(random.nextInt(actions.size()));
        if (action.action().getAsBoolean()) {
            scheduleWorldAnomalyRecord(player, action.policy());
        }
    }

    private void addBaseAnomalyAttempts(List<BaseAnomalyAttempt> actions, ServerWorld world, BlockPos center, int signRadius, ItConfig config) {
        if (config.allowNonDestructiveBaseChanges) {
            actions.add(new BaseAnomalyAttempt(() -> openNearbyDoor(world, center), ReceiverRecordPolicy.SAMPLED));
            actions.add(new BaseAnomalyAttempt(() -> placePottedFlower(world, center), ReceiverRecordPolicy.RARE));
            actions.add(new BaseAnomalyAttempt(() -> placeSign(world, center, signRadius), ReceiverRecordPolicy.IMPORTANT));
        }

        if (config.allowItemManipulation) {
            actions.add(new BaseAnomalyAttempt(() -> removeSmallBaseObject(world, center), ReceiverRecordPolicy.RARE));
        }
    }

    private void trySignAnomaly(ServerPlayerEntity player, BlockPos center, long currentTick, ItConfig config, HorrorPhase phase) {
        if (!config.enableRandomSignAnomalies || currentTick < nextSignAnomalyTicks.getOrDefault(player.getUuid(), 0L)) {
            return;
        }

        nextSignAnomalyTicks.put(player.getUuid(), currentTick + secondsToTicks(randomBetween(260, 620)));
        double phaseChance = switch (phase) {
            case IMITATING -> 0.065;
            case INTRUSION -> 0.105;
            case MANIFESTATION -> 0.135;
            default -> 0.0;
        };
        double chance = EventChanceScaler.clampChance(phaseChance
                * config.worldAnomalyChanceMultiplier
                * config.eventChanceMultiplier
                * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() > chance) {
            return;
        }

        if (placeSign(player.getEntityWorld(), center, 22)) {
            scheduleWorldAnomalyRecord(player, ReceiverRecordPolicy.IMPORTANT);
        }
    }

    private void tryNetherrackCross(ServerPlayerEntity player, long currentTick, ItConfig config, HorrorPhase phase) {
        if (!config.enableNetherrackCrossAnomalies || currentTick < nextCrossAnomalyTicks.getOrDefault(player.getUuid(), 0L)) {
            return;
        }

        int minCooldown = phase == HorrorPhase.IMITATING ? 900 : 520;
        int maxCooldown = phase == HorrorPhase.IMITATING ? 1600 : 980;
        nextCrossAnomalyTicks.put(player.getUuid(), currentTick + secondsToTicks(randomBetween(minCooldown, maxCooldown)));
        double phaseChance = switch (phase) {
            case IMITATING -> 0.028;
            case INTRUSION -> 0.075;
            case MANIFESTATION -> 0.10;
            default -> 0.0;
        };
        double chance = EventChanceScaler.clampChance(phaseChance
                * config.worldAnomalyChanceMultiplier
                * config.eventChanceMultiplier
                * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() > chance) {
            return;
        }

        ServerWorld world = player.getEntityWorld();
        for (int attempts = 0; attempts < 20; attempts++) {
            BlockPos base = randomGroundNear(player, 14, 34);
            if (base != null && placeNetherrackCross(world, base)) {
                scheduleWorldAnomalyRecord(player, ReceiverRecordPolicy.IMPORTANT);
                return;
            }
        }
    }

    private boolean openNearbyDoor(ServerWorld world, BlockPos center) {
        for (BlockPos pos : shuffledPositions(center, 10, 4)) {
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock && state.contains(DoorBlock.OPEN) && !state.get(DoorBlock.OPEN)) {
                world.setBlockState(pos, state.with(DoorBlock.OPEN, true), Block.NOTIFY_ALL);
                return true;
            }
        }

        return false;
    }

    private boolean removeSmallBaseObject(ServerWorld world, BlockPos center) {
        for (BlockPos pos : shuffledPositions(center, 9, 4)) {
            BlockState state = world.getBlockState(pos);
            if (isSmallRemovable(state)) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                return true;
            }
        }

        return false;
    }

    private boolean isSmallRemovable(BlockState state) {
        return state.isOf(Blocks.TORCH)
                || state.isOf(Blocks.WALL_TORCH)
                || state.isOf(Blocks.SOUL_TORCH)
                || state.isOf(Blocks.SOUL_WALL_TORCH)
                || state.isOf(Blocks.FLOWER_POT)
                || state.isOf(Blocks.POTTED_DANDELION)
                || state.isOf(Blocks.POTTED_POPPY)
                || state.isOf(Blocks.POTTED_AZURE_BLUET)
                || state.isOf(Blocks.POTTED_RED_TULIP);
    }

    private boolean placePottedFlower(ServerWorld world, BlockPos center) {
        Block[] pots = {
                Blocks.POTTED_POPPY,
                Blocks.POTTED_DANDELION,
                Blocks.POTTED_AZURE_BLUET,
                Blocks.POTTED_RED_TULIP
        };

        for (BlockPos pos : shuffledPositions(center, 8, 3)) {
            if (world.getBlockState(pos).isAir() && hasSupport(world, pos)) {
                world.setBlockState(pos, pots[random.nextInt(pots.length)].getDefaultState(), Block.NOTIFY_ALL);
                return true;
            }
        }

        return false;
    }

    private boolean placeSign(ServerWorld world, BlockPos center, int radius) {
        for (BlockPos pos : shuffledPositions(center, radius, 4)) {
            if (!world.getBlockState(pos).isAir() || !hasSupport(world, pos)) {
                continue;
            }

            BlockState state = Blocks.OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, random.nextInt(16));
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            if (world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                SignText text = new SignText()
                        .withMessage(0, Text.literal(SIGN_LINES[random.nextInt(SIGN_LINES.length)]))
                        .withMessage(1, Text.literal(SIGN_LINES[random.nextInt(SIGN_LINES.length)]))
                        .withMessage(2, Text.literal(""))
                        .withMessage(3, Text.literal("不要回应。"));
                sign.setText(text, true);
                sign.setWaxed(true);
            }
            return true;
        }

        return false;
    }

    private boolean placeNetherrackCross(ServerWorld world, BlockPos base) {
        Direction side = Direction.fromHorizontalQuarterTurns(random.nextInt(4));
        Direction signFacing = side.rotateYClockwise();
        BlockPos[] netherrackPositions = {
                base,
                base.up(),
                base.up(2),
                base.up(3),
                base.up(2).offset(side),
                base.up(2).offset(side.getOpposite())
        };
        BlockPos[] firePositions = {
                base.up(4),
                base.up(3).offset(side),
                base.up(3).offset(side.getOpposite())
        };
        BlockPos signPos = base.up().offset(signFacing);

        for (BlockPos pos : netherrackPositions) {
            if (!world.getBlockState(pos).isAir()) {
                return false;
            }
        }

        for (BlockPos pos : firePositions) {
            if (!world.getBlockState(pos).isAir()) {
                return false;
            }
        }

        if (!world.getBlockState(signPos).isAir()) {
            return false;
        }

        if (!hasSupport(world, base)) {
            return false;
        }

        for (BlockPos pos : netherrackPositions) {
            world.setBlockState(pos, Blocks.NETHERRACK.getDefaultState(), Block.NOTIFY_ALL);
        }

        for (BlockPos pos : firePositions) {
            world.setBlockState(pos, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
        }

        world.setBlockState(signPos, Blocks.OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, signFacing), Block.NOTIFY_ALL);
        if (world.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            SignText text = new SignText()
                    .withMessage(0, Text.literal("献给它"))
                    .withMessage(1, Text.literal("不要熄灭"))
                    .withMessage(2, Text.literal(""))
                    .withMessage(3, Text.literal("它会看见"));
            sign.setText(text, true);
            sign.setWaxed(true);
        }

        return true;
    }

    private BlockPos randomGroundNear(ServerPlayerEntity player, int minRadius, int maxRadius) {
        ServerWorld world = player.getEntityWorld();
        double angle = random.nextDouble() * Math.PI * 2.0;
        int distance = randomBetween(minRadius, maxRadius);
        int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        int y = player.getBlockY() + randomBetween(-8, 5);

        for (int dy = 0; dy < 12; dy++) {
            BlockPos pos = new BlockPos(x, y - dy, z);
            if (world.getBlockState(pos).isAir() && hasSupport(world, pos)) {
                return pos;
            }
        }

        return null;
    }

    private List<BlockPos> shuffledPositions(BlockPos center, int horizontalRadius, int verticalRadius) {
        List<BlockPos> positions = new ArrayList<>();
        for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
            for (int y = -verticalRadius; y <= verticalRadius; y++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    if (Math.abs(x) + Math.abs(z) <= horizontalRadius + random.nextInt(3)) {
                        positions.add(center.add(x, y, z));
                    }
                }
            }
        }

        for (int i = positions.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            BlockPos temp = positions.get(i);
            positions.set(i, positions.get(j));
            positions.set(j, temp);
        }

        return positions;
    }

    private boolean hasSupport(ServerWorld world, BlockPos pos) {
        return !world.getBlockState(pos.down()).isAir();
    }

    private boolean isNight(ServerWorld world) {
        long time = world.getTimeOfDay() % 24000L;
        return time >= 13000L && time <= 23000L;
    }

    private void scheduleWorldAnomalyRecord(ServerPlayerEntity player, ReceiverRecordPolicy policy) {
        ItConfig config = ItConfigManager.getConfig();
        if (policy == ReceiverRecordPolicy.RARE || policy == ReceiverRecordPolicy.SAMPLED) {
            ItMod.getMinorAnomalyAccumulator().recordMinorWorld(player);
        }
        if (!AnalogHorrorManager.shouldRecord(random, progressionManager.getPhase(player), policy, config)) {
            return;
        }
        int minDelay = config.enableDelayedReceiverMessages ? Math.max(60, config.receiverEventMessageMinDelayTicks) : 0;
        int maxDelay = config.enableDelayedReceiverMessages ? Math.max(minDelay, Math.max(160, config.receiverEventMessageMaxDelayTicks)) : 0;
        int delay = maxDelay <= 0 ? 0 : randomBetween(minDelay, maxDelay);
        receiverManager.scheduleMessage(player, ReceiverMessageType.SYSTEM_ERROR, progressionManager.getPhase(player), pick(WORLD_ANOMALY_RECEIVER_MESSAGES), delay);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, x, y, z, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(sound, category, x, y, z, volume, pitch, random.nextLong());
        player.networkHandler.sendPacket(packet);
    }

    private String pick(String... values) {
        return values[random.nextInt(values.length)];
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }

    private record ActiveAnimalStare(int animalEntityId, UUID playerUuid, long untilTick) {
    }

    private record BaseAnomalyAttempt(BooleanSupplier action, ReceiverRecordPolicy policy) {
    }
}
