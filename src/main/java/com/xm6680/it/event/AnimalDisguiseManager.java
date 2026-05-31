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
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class AnimalDisguiseManager {
    public static final String DISGUISE_TAG = "it_animal_disguise";
    private static final int TICKS_PER_SECOND = 20;
    private static final int MIN_AGGRESSION_ATTACKS = 3;

    private static final String[] RECEIVER_MESSAGES = {
            "它不是动物。",
            "伪装体已被破坏。",
            "你杀死了错误目标。",
            "外观确认失败。",
            "生物记录不存在。",
            "它故意让你看见。"
    };

    private static final String[] AGGRESSION_RECEIVER_MESSAGES = {
            "生物行为记录异常。",
            "该动物攻击了不该攻击的目标。",
            "伪装体正在模仿普通生物。",
            "生物关系记录不一致。",
            "它不是真的动物。"
    };

    private static final String[] DEPARTURE_RECEIVER_MESSAGES = {
            "伪装体已离开。",
            "生物记录恢复正常。",
            "目标没有接触伪装体。"
    };

    private final HorrorProgressionManager progressionManager;
    private final ReceiverManager receiverManager;
    private final Map<UUID, Long> nextAnimalDisguiseTicks = new HashMap<>();
    private final Map<UUID, ActiveDisguise> activeDisguises = new HashMap<>();
    private final List<ActiveRetaliationNoiseBurst> activeNoiseBursts = new ArrayList<>();
    private final Random random = new Random();

    public AnimalDisguiseManager(HorrorProgressionManager progressionManager, ReceiverManager receiverManager) {
        this.progressionManager = progressionManager;
        this.receiverManager = receiverManager;
    }

    public void tickActive(MinecraftServer server, long currentTick) {
        tickRetaliationNoiseBursts(server, currentTick);
        ItConfig config = ItConfigManager.getConfig();
        Iterator<Map.Entry<UUID, ActiveDisguise>> iterator = activeDisguises.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveDisguise> entry = iterator.next();
            ActiveDisguise disguise = entry.getValue();
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(disguise.targetUuid);
            Entity entity = findEntity(server, entry.getKey());
            if (!(entity instanceof AnimalEntity animal) || !entity.isAlive() || target == null || currentTick >= disguise.despawnTick) {
                if (target != null && currentTick >= disguise.despawnTick && random.nextDouble() < 0.30D) {
                    receiverManager.scheduleMessage(target, ReceiverMessageType.OBSERVATION, progressionManager.getPhase(target), pick(DEPARTURE_RECEIVER_MESSAGES), config.animalDisguiseReceiverDelayTicks);
                }
                if (entity != null && entity.isAlive()) {
                    entity.discard();
                }
                iterator.remove();
                continue;
            }

            if ((disguise.forcedAggressive || (config.enableAnimalDisguiseAggression && canAttackNearbyTargets(config)))
                    && canContinueAggression(disguise, config)) {
                tickAggression(animal, target, disguise, currentTick, config);
            } else if (random.nextInt(40) == 0 || animal.squaredDistanceTo(target) < 12.0D * 12.0D) {
                holdUnnaturalStillness(animal, target);
            }
        }
    }

    public void tick(MinecraftServer server, long currentTick) {
        ItConfig config = ItConfigManager.getConfig();
        if (!config.enableAnimalDisguiseEvent || !config.animalDisguiseEnabled) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }

            trySpawnDisguise(player, currentTick, config);
        }
    }

    public boolean triggerAnimalDisguise(ServerPlayerEntity player, boolean forced) {
        return triggerAnimalDisguise(player, forced, false);
    }

    public boolean triggerAnimalDisguise(ServerPlayerEntity player, boolean forced, boolean aggressive) {
        return spawnDisguise(player, player.getEntityWorld().getServer().getTicks(), ItConfigManager.getConfig(), forced, aggressive);
    }

    public int clearDisguises(MinecraftServer server) {
        int cleared = 0;
        for (ServerWorld world : server.getWorlds()) {
            List<AnimalEntity> animals = world.getEntitiesByType(
                    TypeFilter.instanceOf(AnimalEntity.class),
                    new Box(-3.0E7D, world.getBottomY(), -3.0E7D, 3.0E7D, world.getBottomY() + world.getHeight(), 3.0E7D),
                    animal -> animal.getCommandTags().contains(DISGUISE_TAG)
            );
            for (AnimalEntity animal : animals) {
                animal.discard();
                cleared++;
            }
        }

        cleared = Math.max(cleared, activeDisguises.size());
        activeDisguises.clear();
        return cleared;
    }

    public AnimalDisguiseStatus status(ServerPlayerEntity player) {
        long currentTick = player.getEntityWorld().getServer().getTicks();
        long nextTick = nextAnimalDisguiseTicks.getOrDefault(player.getUuid(), 0L);
        return new AnimalDisguiseStatus(activeDisguises.size(), Math.max(0L, nextTick - currentTick), statusDetails(player.getEntityWorld().getServer(), currentTick));
    }

    public String getStatusLine(ServerPlayerEntity player) {
        AnimalDisguiseStatus status = status(player);
        return "active=" + status.activeCount() + ", cooldown=" + status.cooldownRemainingTicks() + " ticks, " + status.details();
    }

    public void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof AnimalEntity animal) || !isDisguise(animal)) {
            return;
        }

        ActiveDisguise disguise = activeDisguises.remove(animal.getUuid());
        ServerPlayerEntity target = findRetaliationTarget(animal, source, disguise);
        if (target == null) {
            return;
        }

        triggerRetaliation(target);
    }

    public void remove(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        nextAnimalDisguiseTicks.remove(uuid);
        activeDisguises.entrySet().removeIf(entry -> {
            if (!entry.getValue().targetUuid.equals(uuid)) {
                return false;
            }
            Entity entity = findEntity(player.getEntityWorld().getServer(), entry.getKey());
            if (entity != null) {
                entity.discard();
            }
            return true;
        });
    }

    private void trySpawnDisguise(ServerPlayerEntity player, long currentTick, ItConfig config) {
        if (!config.animalDisguiseEnabled
                || !progressionManager.getPhase(player).isAtLeast(phaseFromNumber(config.animalDisguiseMinPhase))
                || isStrongEventActive(player, currentTick)
                || currentTick < nextAnimalDisguiseTicks.getOrDefault(player.getUuid(), 0L)
                || hasTargetDisguise(player.getUuid())) {
            return;
        }

        nextAnimalDisguiseTicks.put(player.getUuid(), currentTick + secondsToTicks(randomBetween(420, 900)));
        if (!isReasonableSpawnEnvironment(player, config)) {
            return;
        }

        double chance = EventChanceScaler.clampChance(0.035D
                * config.animalDisguiseSpawnChanceMultiplier
                * config.eventChanceMultiplier
                * EventChanceScaler.ordinaryEventMultiplier(player, progressionManager, config));
        if (random.nextDouble() <= chance) {
            spawnDisguise(player, currentTick, config, false, false);
        }
    }

    private boolean spawnDisguise(ServerPlayerEntity player, long currentTick, ItConfig config, boolean forced, boolean aggressive) {
        if (!forced && (!config.enableAnimalDisguiseEvent || !config.animalDisguiseEnabled || isStrongEventActive(player, currentTick))) {
            return false;
        }

        EntityType<? extends AnimalEntity> type = chooseAnimalType(config);
        Optional<BlockPos> pos = findSpawnPosition(player, config);
        if (type == null || pos.isEmpty()) {
            return false;
        }

        ServerWorld world = player.getEntityWorld();
        AnimalEntity animal = type.create(world, entity -> {
        }, pos.get(), SpawnReason.TRIGGERED, false, false);
        if (animal == null) {
            return false;
        }

        animal.refreshPositionAndAngles(pos.get().getX() + 0.5D, pos.get().getY(), pos.get().getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        animal.addCommandTag(DISGUISE_TAG);
        animal.setPersistent();
        animal.setSilent(true);
        holdUnnaturalStillness(animal, player);
        if (!world.spawnEntity(animal)) {
            return false;
        }

        activeDisguises.put(animal.getUuid(), new ActiveDisguise(player.getUuid(), currentTick + secondsToTicks(config.animalDisguiseDespawnSeconds), aggressive));
        playSpawnHeartbeat(player, animal);
        progressionManager.recordAnimalDisguiseEvent(player);
        nextAnimalDisguiseTicks.put(player.getUuid(), currentTick + secondsToTicks(Math.max(60, config.animalDisguiseCooldownSeconds)));
        return true;
    }

    private void tickAggression(AnimalEntity animal, ServerPlayerEntity targetPlayer, ActiveDisguise disguise, long currentTick, ItConfig config) {
        LivingEntity victim = findCurrentVictim(animal, disguise, config);
        if (victim == null) {
            victim = findAggressionVictim(animal, disguise, config);
            disguise.attackTargetUuid = victim == null ? null : victim.getUuid();
        }

        if (victim == null) {
            if (random.nextInt(36) == 0 || animal.squaredDistanceTo(targetPlayer) < 12.0D * 12.0D) {
                holdUnnaturalStillness(animal, targetPlayer);
            }
            return;
        }

        animal.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, victim.getEyePos());
        animal.setBodyYaw(animal.getYaw());
        animal.setHeadYaw(animal.getYaw());

        double attackDistance = Math.max(2.2D, animal.getWidth() + victim.getWidth() + 0.9D);
        if (animal.squaredDistanceTo(victim) > attackDistance * attackDistance) {
            animal.getNavigation().startMovingTo(victim, 1.15D);
            if (random.nextInt(52) == 0) {
                animal.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, targetPlayer.getEyePos());
            }
            return;
        }

        animal.getNavigation().stop();
        if (currentTick < disguise.nextAttackTick) {
            return;
        }

        ServerWorld world = (ServerWorld) animal.getEntityWorld();
        boolean damaged = victim.damage(world, animal.getDamageSources().mobAttackNoAggro(animal), config.animalDisguiseAttackDamage);
        disguise.attacks++;
        disguise.nextAttackTick = currentTick + Math.max(10, config.animalDisguiseAttackCooldownTicks);
        progressionManager.recordAnimalDisguiseAnimalAttack(targetPlayer);
        sendSoundToPlayer(targetPlayer, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.NEUTRAL, new Vec3d(animal.getX(), animal.getY(), animal.getZ()), 0.65F, 0.72F + random.nextFloat() * 0.12F);
        if (random.nextDouble() < 0.35D) {
            sendSoundToPlayer(targetPlayer, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, new Vec3d(animal.getX(), animal.getY(), animal.getZ()), 0.38F, 1.45F + random.nextFloat() * 0.25F);
        }

        if (!disguise.aggressionMessageQueued) {
            disguise.aggressionMessageQueued = true;
            ItMod.getMinorAnomalyAccumulator().recordMinorWorld(targetPlayer);
            if (AnalogHorrorManager.shouldRecord(random, progressionManager.getPhase(targetPlayer), ReceiverRecordPolicy.SAMPLED, config)) {
                receiverManager.scheduleMessage(targetPlayer, ReceiverMessageType.OBSERVATION, progressionManager.getPhase(targetPlayer), pick(AGGRESSION_RECEIVER_MESSAGES), config.animalDisguiseAggressionReceiverDelayTicks);
            }
        }

        if (damaged && (!victim.isAlive() || victim.getHealth() <= 0.0F) && disguise.victimUuids.add(victim.getUuid())) {
            disguise.victims++;
            progressionManager.recordAnimalDisguiseVictim(targetPlayer);
            disguise.attackTargetUuid = null;
        } else if (!isEligibleAggressionVictim(animal, victim, disguise, config)) {
            disguise.attackTargetUuid = null;
        }
    }

    private boolean canAttackNearbyTargets(ItConfig config) {
        return config.animalDisguiseAttackNearbyAnimals || config.animalDisguiseAttackNearbyMonsters;
    }

    private boolean canContinueAggression(ActiveDisguise disguise, ItConfig config) {
        return disguise.attacks < config.animalDisguiseMaxAttacks
                && (disguise.attacks < MIN_AGGRESSION_ATTACKS || disguise.victims < config.animalDisguiseMaxVictims);
    }

    private void playSpawnHeartbeat(ServerPlayerEntity player, AnimalEntity animal) {
        Vec3d pos = new Vec3d(animal.getX(), animal.getY() + 0.5D, animal.getZ());
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, pos, 0.82F, 0.62F + random.nextFloat() * 0.08F);
    }

    private void triggerRetaliation(ServerPlayerEntity player) {
        ItConfig config = ItConfigManager.getConfig();
        int duration = Math.max(40, config.animalDisguiseRetaliationDurationTicks);
        ItNetwork.sendAnimalDisguiseRetaliation(player, duration, config.animalDisguiseStaticIntensity, config.animalDisguiseNoiseVolume, config.reduceFlashingEffects || config.disableRapidFlashes);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, Math.max(20, config.animalDisguiseBlindnessSeconds * TICKS_PER_SECOND), 0, false, false, true));
        playRetaliationNoise(player, config.animalDisguiseNoiseVolume);
        activeNoiseBursts.add(new ActiveRetaliationNoiseBurst(player.getUuid(), player.getEntityWorld().getServer().getTicks(), 10, config.animalDisguiseNoiseVolume));
        receiverManager.scheduleMessage(player, ReceiverMessageType.SYSTEM_ERROR, progressionManager.getPhase(player), pick(RECEIVER_MESSAGES), config.animalDisguiseReceiverDelayTicks);
        progressionManager.recordAnimalDisguiseKilledEvent(player);
        progressionManager.recordAnimalDisguiseRetaliation(player);
        player.sendMessage(Text.literal("那不是动物。").formatted(Formatting.DARK_GRAY), true);
    }

    private void tickRetaliationNoiseBursts(MinecraftServer server, long currentTick) {
        Iterator<ActiveRetaliationNoiseBurst> iterator = activeNoiseBursts.iterator();
        while (iterator.hasNext()) {
            ActiveRetaliationNoiseBurst burst = iterator.next();
            if (currentTick < burst.nextPulseTick) {
                continue;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(burst.targetUuid);
            if (player == null || !player.isAlive()) {
                iterator.remove();
                continue;
            }

            playRetaliationNoisePulse(player, burst.volume, burst.remainingPulses);
            burst.remainingPulses--;
            burst.nextPulseTick = currentTick + 1L;
            if (burst.remainingPulses <= 0) {
                iterator.remove();
            }
        }
    }

    private void playRetaliationNoise(ServerPlayerEntity player, float configuredVolume) {
        float volume = Math.max(0.0F, Math.min(2.0F, configuredVolume));
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.AMBIENT, pos, volume * 0.75F, 1.35F + random.nextFloat() * 0.25F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.AMBIENT, pos, volume * 0.55F, 1.20F + random.nextFloat() * 0.30F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.AMBIENT, pos, volume * 0.35F, 1.45F + random.nextFloat() * 0.28F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, pos, volume * 0.42F, 1.85F + random.nextFloat() * 0.25F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_NOTE_BLOCK_BELL, SoundCategory.AMBIENT, pos, volume * 0.35F, 1.70F + random.nextFloat() * 0.24F);
        sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, pos, volume * 0.85F, 0.55F + random.nextFloat() * 0.10F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_TENDRIL_CLICKS, SoundCategory.AMBIENT, pos, volume * 0.46F, 0.70F + random.nextFloat() * 0.14F);
        sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, pos, volume * 0.50F, 1.60F + random.nextFloat() * 0.22F);
        sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE, SoundCategory.AMBIENT, pos, volume * 0.32F, 0.62F + random.nextFloat() * 0.08F);
    }

    private void playRetaliationNoisePulse(ServerPlayerEntity player, float configuredVolume, int pulseIndex) {
        float volume = Math.max(0.0F, Math.min(2.0F, configuredVolume)) * (pulseIndex % 2 == 0 ? 1.15F : 0.95F);
        Vec3d pos = new Vec3d(player.getX(), player.getY(), player.getZ());
        switch (random.nextInt(5)) {
            case 0 -> {
                sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.AMBIENT, pos, volume * 0.70F, 1.55F + random.nextFloat() * 0.22F);
                sendSoundToPlayer(player, SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.AMBIENT, pos, volume * 0.68F, 1.45F + random.nextFloat() * 0.28F);
            }
            case 1 -> {
                sendSoundToPlayer(player, SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.AMBIENT, pos, volume * 0.55F, 1.70F + random.nextFloat() * 0.18F);
                sendSoundToPlayer(player, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, pos, volume * 0.62F, 1.92F + random.nextFloat() * 0.12F);
            }
            case 2 -> {
                sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.AMBIENT, pos, volume * 0.58F, 0.78F + random.nextFloat() * 0.10F);
                sendSoundToPlayer(player, SoundEvents.ENTITY_WARDEN_TENDRIL_CLICKS, SoundCategory.AMBIENT, pos, volume * 0.62F, 1.18F + random.nextFloat() * 0.16F);
            }
            case 3 -> {
                sendSoundToPlayer(player, SoundEvents.ENTITY_GOAT_SCREAMING_DEATH, SoundCategory.AMBIENT, pos, volume * 0.52F, 1.65F + random.nextFloat() * 0.20F);
                sendSoundToPlayer(player, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.AMBIENT, pos, volume * 0.54F, 1.32F + random.nextFloat() * 0.18F);
            }
            default -> {
                sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, pos, volume * 0.90F, 0.48F + random.nextFloat() * 0.08F);
                sendSoundToPlayer(player, SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, pos, volume * 0.66F, 1.82F + random.nextFloat() * 0.16F);
            }
        }
    }

    private ServerPlayerEntity findRetaliationTarget(AnimalEntity animal, DamageSource source, ActiveDisguise disguise) {
        Entity attacker = source.getAttacker();
        if (attacker instanceof ServerPlayerEntity player) {
            return player;
        }

        MinecraftServer server = animal.getEntityWorld().getServer();
        if (server != null && disguise != null) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(disguise.targetUuid);
            if (target != null) {
                return target;
            }
        }

        ServerPlayerEntity nearest = null;
        double nearestDistance = 32.0D * 32.0D;
        for (ServerPlayerEntity player : animal.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() == animal.getEntityWorld() && player.squaredDistanceTo(animal) < nearestDistance) {
                nearest = player;
                nearestDistance = player.squaredDistanceTo(animal);
            }
        }
        return nearest;
    }

    private LivingEntity findCurrentVictim(AnimalEntity animal, ActiveDisguise disguise, ItConfig config) {
        if (disguise.attackTargetUuid == null) {
            return null;
        }

        Entity entity = findEntity(animal.getEntityWorld().getServer(), disguise.attackTargetUuid);
        if (!(entity instanceof LivingEntity victim) || !isEligibleAggressionVictim(animal, victim, disguise, config)) {
            disguise.attackTargetUuid = null;
            return null;
        }
        return victim;
    }

    private LivingEntity findAggressionVictim(AnimalEntity animal, ActiveDisguise disguise, ItConfig config) {
        if (!canContinueAggression(disguise, config)) {
            return null;
        }

        double radius = Math.max(2.0D, config.animalDisguiseAttackRadius);
        Box box = animal.getBoundingBox().expand(radius);
        List<LivingEntity> candidates = new ArrayList<>();
        if (config.animalDisguiseAttackNearbyMonsters) {
            candidates.addAll(animal.getEntityWorld().getEntitiesByType(
                    TypeFilter.instanceOf(HostileEntity.class),
                    box,
                    candidate -> isEligibleMonsterVictim(animal, candidate, disguise, config)
            ));
        }
        if (config.animalDisguiseAttackNearbyAnimals) {
            candidates.addAll(animal.getEntityWorld().getEntitiesByType(
                    TypeFilter.instanceOf(AnimalEntity.class),
                    box,
                    candidate -> isEligibleAnimalVictim(animal, candidate, disguise, config)
            ));
        }

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = candidate.squaredDistanceTo(animal);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean isEligibleAggressionVictim(AnimalEntity disguiseAnimal, LivingEntity candidate, ActiveDisguise disguise, ItConfig config) {
        if (candidate instanceof HostileEntity hostile) {
            return isEligibleMonsterVictim(disguiseAnimal, hostile, disguise, config);
        }
        if (candidate instanceof AnimalEntity animal) {
            return isEligibleAnimalVictim(disguiseAnimal, animal, disguise, config);
        }
        return false;
    }

    private boolean isEligibleMonsterVictim(AnimalEntity disguiseAnimal, HostileEntity candidate, ActiveDisguise disguise, ItConfig config) {
        return candidate.isAlive()
                && !candidate.hasCustomName()
                && !isDisguise(candidate)
                && !disguise.victimUuids.contains(candidate.getUuid())
                && candidate.squaredDistanceTo(disguiseAnimal) <= config.animalDisguiseAttackRadius * config.animalDisguiseAttackRadius;
    }

    private boolean isEligibleAnimalVictim(AnimalEntity disguiseAnimal, AnimalEntity candidate, ActiveDisguise disguise, ItConfig config) {
        if (candidate == disguiseAnimal
                || !candidate.isAlive()
                || isDisguise(candidate)
                || disguise.victimUuids.contains(candidate.getUuid())
                || candidate.squaredDistanceTo(disguiseAnimal) > config.animalDisguiseAttackRadius * config.animalDisguiseAttackRadius) {
            return false;
        }
        if (config.animalDisguiseAvoidNamedAnimals && candidate.hasCustomName()) {
            return false;
        }
        if (config.animalDisguiseAvoidTamedAnimals && candidate instanceof TameableEntity tameable && tameable.isTamed()) {
            return false;
        }
        if (config.animalDisguiseAvoidMounts && candidate instanceof AbstractHorseEntity) {
            return false;
        }
        return isAllowedVictimType(candidate);
    }

    private boolean isAllowedVictimType(AnimalEntity animal) {
        EntityType<?> type = animal.getType();
        return type == EntityType.COW
                || type == EntityType.SHEEP
                || type == EntityType.PIG
                || type == EntityType.CHICKEN
                || type == EntityType.RABBIT;
    }

    private Entity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private boolean isDisguise(Entity entity) {
        return activeDisguises.containsKey(entity.getUuid()) || entity.getCommandTags().contains(DISGUISE_TAG);
    }

    private boolean hasTargetDisguise(UUID targetUuid) {
        for (ActiveDisguise disguise : activeDisguises.values()) {
            if (disguise.targetUuid.equals(targetUuid)) {
                return true;
            }
        }
        return false;
    }

    private boolean isReasonableSpawnEnvironment(ServerPlayerEntity player, ItConfig config) {
        HorrorPhase phase = progressionManager.getPhase(player);
        ServerWorld world = player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        if (phase == HorrorPhase.MANIFESTATION) {
            return true;
        }
        if (world.isSkyVisible(pos) || player.getBlockY() >= world.getSeaLevel() - 12) {
            return true;
        }
        return nearbyLeaves(world, pos) >= 5 || nearbyVillagers(world, player) > 0;
    }

    private Optional<BlockPos> findSpawnPosition(ServerPlayerEntity player, ItConfig config) {
        ServerWorld world = player.getEntityWorld();
        double min = Math.max(4.0D, config.animalDisguiseSpawnMinDistance);
        double max = Math.max(min + 2.0D, config.animalDisguiseSpawnMaxDistance);
        for (int attempts = 0; attempts < 48; attempts++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = min + random.nextDouble() * (max - min);
            BlockPos candidate = BlockPos.ofFloored(
                    player.getX() + Math.cos(angle) * distance,
                    player.getY() + randomBetween(-4, 4),
                    player.getZ() + Math.sin(angle) * distance
            );

            Optional<BlockPos> standable = findNearbyStandable(world, candidate);
            if (standable.isPresent() && player.squaredDistanceTo(standable.get().getX() + 0.5D, standable.get().getY(), standable.get().getZ() + 0.5D) >= min * min) {
                return standable;
            }
        }
        return Optional.empty();
    }

    private Optional<BlockPos> findNearbyStandable(ServerWorld world, BlockPos center) {
        for (int y = 4; y >= -6; y--) {
            BlockPos pos = center.add(0, y, 0);
            if (isSafeStandable(world, pos)) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    private boolean isSafeStandable(ServerWorld world, BlockPos pos) {
        if (pos.getY() <= world.getBottomY() + 2) {
            return false;
        }
        BlockState state = world.getBlockState(pos);
        BlockState above = world.getBlockState(pos.up());
        BlockState floor = world.getBlockState(pos.down());
        return state.isAir()
                && above.isAir()
                && floor.isSolidBlock(world, pos.down())
                && !world.getFluidState(pos).isIn(FluidTags.LAVA)
                && !world.getFluidState(pos.down()).isIn(FluidTags.LAVA)
                && !floor.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)
                && !floor.isOf(net.minecraft.block.Blocks.CACTUS)
                && !floor.isOf(net.minecraft.block.Blocks.FIRE);
    }

    private EntityType<? extends AnimalEntity> chooseAnimalType(ItConfig config) {
        List<EntityType<? extends AnimalEntity>> types = new ArrayList<>();
        String allowed = config.animalDisguiseAllowedAnimals.toLowerCase(Locale.ROOT);
        if (allowed.contains("cow")) {
            types.add(EntityType.COW);
        }
        if (allowed.contains("sheep")) {
            types.add(EntityType.SHEEP);
        }
        if (allowed.contains("pig")) {
            types.add(EntityType.PIG);
        }
        if (allowed.contains("chicken")) {
            types.add(EntityType.CHICKEN);
        }

        if (types.isEmpty()) {
            types.add(EntityType.COW);
        }
        return types.get(random.nextInt(types.size()));
    }

    private void holdUnnaturalStillness(AnimalEntity animal, ServerPlayerEntity target) {
        animal.getNavigation().stop();
        animal.setVelocity(Vec3d.ZERO);
        animal.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.getEyePos());
        animal.setBodyYaw(animal.getYaw());
        animal.setHeadYaw(animal.getYaw());
    }

    private int nearbyLeaves(ServerWorld world, BlockPos center) {
        int leaves = 0;
        for (BlockPos pos : BlockPos.iterate(center.add(-8, -3, -8), center.add(8, 6, 8))) {
            if (world.getBlockState(pos).isIn(BlockTags.LEAVES)) {
                leaves++;
                if (leaves >= 5) {
                    return leaves;
                }
            }
        }
        return leaves;
    }

    private int nearbyVillagers(ServerWorld world, ServerPlayerEntity player) {
        return world.getEntitiesByType(
                TypeFilter.instanceOf(net.minecraft.entity.passive.VillagerEntity.class),
                player.getBoundingBox().expand(32.0D),
                villager -> villager.isAlive()
        ).size();
    }

    private String statusDetails(MinecraftServer server, long currentTick) {
        if (activeDisguises.isEmpty()) {
            return "details=none";
        }

        StringBuilder builder = new StringBuilder("details=");
        int shown = 0;
        for (Map.Entry<UUID, ActiveDisguise> entry : activeDisguises.entrySet()) {
            if (shown > 0) {
                builder.append("; ");
            }
            Entity entity = findEntity(server, entry.getKey());
            ActiveDisguise disguise = entry.getValue();
            builder.append(shortId(entry.getKey()))
                    .append(":type=").append(entity == null ? "missing" : Registries.ENTITY_TYPE.getId(entity.getType()).getPath())
                    .append(",target=").append(disguise.attackTargetUuid == null ? "none" : shortId(disguise.attackTargetUuid))
                    .append(",attacks=").append(disguise.attacks)
                    .append(",victims=").append(disguise.victims)
                    .append(",nextAttack=").append(Math.max(0L, disguise.nextAttackTick - currentTick));
            shown++;
            if (shown >= 3) {
                break;
            }
        }
        if (activeDisguises.size() > shown) {
            builder.append("; ...");
        }
        return builder.toString();
    }

    private String shortId(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private boolean isStrongEventActive(ServerPlayerEntity player, long currentTick) {
        return ItMod.getChaseManager().isChasing(player)
                || ItMod.getCaveStalkerManager().isActive(player)
                || ItNetwork.isJumpscareActive(player, currentTick)
                || ItNetwork.isFaceScareActive(player, currentTick)
                || ItNetwork.isManifestationOverlayActive(player, currentTick)
                || ItNetwork.isAnimalDisguiseRetaliationActive(player, currentTick);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, pos, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(sound, category, pos.x, pos.y, pos.z, volume, pitch, random.nextLong());
        player.networkHandler.sendPacket(packet);
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

    private String pick(String... values) {
        return values[random.nextInt(values.length)];
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private long secondsToTicks(int seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }

    public record AnimalDisguiseStatus(int activeCount, long cooldownRemainingTicks, String details) {
    }

    private static class ActiveDisguise {
        private final UUID targetUuid;
        private final long despawnTick;
        private final boolean forcedAggressive;
        private final Set<UUID> victimUuids = new HashSet<>();
        private UUID attackTargetUuid;
        private long nextAttackTick;
        private int attacks;
        private int victims;
        private boolean aggressionMessageQueued;

        private ActiveDisguise(UUID targetUuid, long despawnTick, boolean forcedAggressive) {
            this.targetUuid = targetUuid;
            this.despawnTick = despawnTick;
            this.forcedAggressive = forcedAggressive;
        }
    }

    private static class ActiveRetaliationNoiseBurst {
        private final UUID targetUuid;
        private long nextPulseTick;
        private int remainingPulses;
        private final float volume;

        private ActiveRetaliationNoiseBurst(UUID targetUuid, long nextPulseTick, int remainingPulses, float volume) {
            this.targetUuid = targetUuid;
            this.nextPulseTick = nextPulseTick;
            this.remainingPulses = remainingPulses;
            this.volume = volume;
        }
    }
}
