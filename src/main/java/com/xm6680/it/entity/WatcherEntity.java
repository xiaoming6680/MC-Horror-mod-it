package com.xm6680.it.entity;

import com.xm6680.it.ItMod;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.network.ItNetwork;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * A passive black observer that disappears when approached or stared at.
 */
public class WatcherEntity extends MobEntity implements TargetOnlyVisibleEntity {
    private static final double VANISH_DISTANCE = 10.0;
    private static final double WATCH_DISTANCE = 56.0;
    private static final double WATCH_DOT_THRESHOLD = 0.985;
    private static final int WATCHED_TICKS_TO_VANISH = 10;
    private static final int MAX_AGE_TICKS = 45 * 20;
    private static final int JUMPSCARE_FACE_SCARE_MIN_TICKS = 120;
    private static final int IMPACT_PIG_DEATH_SOUNDS = 12;

    private int watchedTicks;
    private boolean sightingRecorded;
    private UUID visibleTargetUuid;
    private UUID jumpscareTargetUuid;
    private int jumpscareLungeTicks;
    private int jumpscareLungeDelayTicks;
    private boolean stareJumpscareTried;

    public WatcherEntity(EntityType<? extends WatcherEntity> entityType, World world) {
        super(entityType, world);
        setAiDisabled(true);
        setSilent(true);
        this.experiencePoints = 0;
    }

    @Override
    protected void initGoals() {
    }

    @Override
    public void tick() {
        super.tick();
        setVelocity(Vec3d.ZERO);

        if (getEntityWorld().isClient()) {
            return;
        }

        TargetOnlyEntityVisibility.hideFromNonTargetPlayers(this);

        if (jumpscareTargetUuid != null) {
            tickJumpscareLunge();
            return;
        }

        if (age > MAX_AGE_TICKS) {
            discard();
            return;
        }

        Optional<ServerPlayerEntity> nearestPlayer = findTargetOrNearestPlayer();
        if (nearestPlayer.isEmpty()) {
            discard();
            return;
        }

        ServerPlayerEntity player = nearestPlayer.get();
        facePlayer(player);

        if (squaredDistanceTo(player) <= VANISH_DISTANCE * VANISH_DISTANCE) {
            recordSighting(player);
            playWatcherVanishSound(player);
            discard();
            return;
        }

        boolean playerWatching = isPlayerWatching(player);
        if (playerWatching) {
            recordSighting(player);
            if (!stareJumpscareTried && watchedTicks >= 8) {
                stareJumpscareTried = true;
                if (ItMod.getJumpscareManager().tryWatcherStareJumpscare(player, player.getEntityWorld().getServer().getTicks())) {
                    discard();
                    return;
                }
            }

            watchedTicks++;
            if (watchedTicks >= WATCHED_TICKS_TO_VANISH) {
                playWatcherVanishSound(player);
                discard();
            }
        } else {
            watchedTicks = Math.max(0, watchedTicks - 2);
        }
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isCollidable(Entity entity) {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    public void setVisibleTarget(ServerPlayerEntity target) {
        this.visibleTargetUuid = target.getUuid();
    }

    @Override
    public UUID getVisibleTargetUuid() {
        return visibleTargetUuid;
    }

    private Optional<ServerPlayerEntity> findTargetOrNearestPlayer() {
        if (!(getEntityWorld() instanceof ServerWorld world)) {
            return Optional.empty();
        }

        if (visibleTargetUuid != null) {
            ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(visibleTargetUuid);
            if (target == null || target.isSpectator() || !target.isAlive() || target.getEntityWorld() != world) {
                return Optional.empty();
            }

            return Optional.of(target);
        }

        Box searchBox = getBoundingBox().expand(64.0);
        return world.getPlayers(player -> !player.isSpectator() && searchBox.contains(player.getX(), player.getY(), player.getZ()))
                .stream()
                .min(Comparator.comparingDouble(this::squaredDistanceTo));
    }

    private void facePlayer(PlayerEntity player) {
        lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, player.getEyePos());
        setBodyYaw(getYaw());
        setHeadYaw(getYaw());
    }

    private boolean isPlayerWatching(ServerPlayerEntity player) {
        if (squaredDistanceTo(player) > WATCH_DISTANCE * WATCH_DISTANCE) {
            return false;
        }

        if (!player.canSee(this)) {
            return false;
        }

        Vec3d toWatcher = getEyePos().subtract(player.getEyePos()).normalize();
        double dot = player.getRotationVec(1.0F).normalize().dotProduct(toWatcher);
        return dot >= WATCH_DOT_THRESHOLD;
    }

    private void recordSighting(ServerPlayerEntity player) {
        if (sightingRecorded) {
            return;
        }

        sightingRecorded = true;
        ItMod.getHorrorProgressionManager().recordWatcherSighting(player);
        ItMod.getAnalogHorrorManager().onWatcherSighting(player);
    }

    public void beginJumpscareLunge(ServerPlayerEntity target, int lifetimeTicks, int delayTicks) {
        setVisibleTarget(target);
        this.jumpscareTargetUuid = target.getUuid();
        this.jumpscareLungeDelayTicks = Math.max(0, delayTicks);
        this.jumpscareLungeTicks = Math.max(20, lifetimeTicks + this.jumpscareLungeDelayTicks);
        this.sightingRecorded = true;
        this.stareJumpscareTried = true;
        setNoGravity(true);
        setSilent(false);
    }

    private void tickJumpscareLunge() {
        if (!(getEntityWorld() instanceof ServerWorld world)) {
            return;
        }

        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(jumpscareTargetUuid);
        if (target == null || target.isSpectator()) {
            discard();
            return;
        }

        if (jumpscareLungeTicks-- <= 0) {
            discard();
            return;
        }

        facePlayer(target);
        if (jumpscareLungeDelayTicks > 0) {
            jumpscareLungeDelayTicks--;
            return;
        }

        Vec3d targetPos = target.getEyePos().add(0.0, -0.45, 0.0);
        Vec3d toTarget = targetPos.subtract(new Vec3d(getX(), getY(), getZ()));
        double distance = toTarget.length();
        if (distance <= 1.25 || getBoundingBox().expand(0.45).intersects(target.getBoundingBox())) {
            impactJumpscareTarget(target);
            return;
        }

        Vec3d direction = toTarget.normalize();
        double speed = jumpscareLungeTicks < 20 ? 1.15 : 0.85;
        refreshPositionAndAngles(
                getX() + direction.x * speed,
                getY() + direction.y * speed,
                getZ() + direction.z * speed,
                getYaw(),
                getPitch()
        );
    }

    private void impactJumpscareTarget(ServerPlayerEntity target) {
        setVelocity(Vec3d.ZERO);

        float newHealth = Math.max(1.0F, target.getHealth() - 8.0F);
        target.setHealth(newHealth);
        int faceScareTicks = Math.max(JUMPSCARE_FACE_SCARE_MIN_TICKS, ItConfigManager.getConfig().faceScareDurationTicks * 2);
        ItNetwork.sendFaceScare(target, faceScareTicks, 1.0F, true);

        for (int i = 0; i < IMPACT_PIG_DEATH_SOUNDS; i++) {
            sendSoundToPlayer(
                    target,
                    SoundEvents.ENTITY_PIG_DEATH,
                    SoundCategory.HOSTILE,
                    1.0F,
                    1.65F + target.getRandom().nextFloat() * 0.35F
            );
        }

        discard();
    }

    private void playWatcherVanishSound(ServerPlayerEntity player) {
        Vec3d source = directionalSoundSource(player, new Vec3d(getX(), getY(), getZ()), 8.0);
        sendSoundToPlayer(player, SoundEvents.AMBIENT_CAVE, SoundCategory.AMBIENT, source, 2.4F, 0.45F + player.getRandom().nextFloat() * 0.18F);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(sound, category, player.getX(), player.getY(), player.getZ(), volume, pitch, getRandom().nextLong());
        player.networkHandler.sendPacket(packet);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, SoundEvent sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        sendSoundToPlayer(player, Registries.SOUND_EVENT.getEntry(sound), category, pos, volume, pitch);
    }

    private void sendSoundToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, SoundCategory category, Vec3d pos, float volume, float pitch) {
        PlaySoundS2CPacket packet = new PlaySoundS2CPacket(sound, category, pos.x, pos.y, pos.z, volume, pitch, getRandom().nextLong());
        player.networkHandler.sendPacket(packet);
    }

    private Vec3d directionalSoundSource(ServerPlayerEntity player, Vec3d actualSource, double maxDistance) {
        Vec3d playerPos = new Vec3d(player.getX(), player.getY() + 0.8, player.getZ());
        Vec3d direction = actualSource.subtract(playerPos);
        if (direction.lengthSquared() < 0.01) {
            return playerPos;
        }

        return playerPos.add(direction.normalize().multiply(Math.min(maxDistance, direction.length())));
    }
}
