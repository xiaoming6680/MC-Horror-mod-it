package com.xm6680.it.entity;

import com.xm6680.it.ItMod;
import com.xm6680.it.config.ItConfigManager;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Temporary manifestation used only by the hunt event. It is not a natural mob.
 */
public class HuntingWatcherEntity extends MobEntity implements TargetOnlyVisibleEntity {
    private UUID targetPlayerUuid;
    private int lifetimeTicks;
    private double speedMultiplier = 1.0;

    public HuntingWatcherEntity(EntityType<? extends HuntingWatcherEntity> entityType, World world) {
        super(entityType, world);
        setSilent(true);
        setNoGravity(true);
        this.experiencePoints = 0;
    }

    @Override
    protected void initGoals() {
    }

    public void beginHunt(ServerPlayerEntity target, int lifetimeTicks, double speedMultiplier) {
        this.targetPlayerUuid = target.getUuid();
        this.lifetimeTicks = Math.max(20, lifetimeTicks);
        this.speedMultiplier = Math.max(0.2, speedMultiplier);
        setNoGravity(true);
        setSilent(false);
    }

    public boolean isTarget(ServerPlayerEntity player) {
        return targetPlayerUuid != null && targetPlayerUuid.equals(player.getUuid());
    }

    @Override
    public UUID getVisibleTargetUuid() {
        return targetPlayerUuid;
    }

    @Override
    public void tick() {
        super.tick();

        if (getEntityWorld().isClient()) {
            return;
        }

        TargetOnlyEntityVisibility.hideFromNonTargetPlayers(this);

        if (!(getEntityWorld() instanceof ServerWorld world) || targetPlayerUuid == null) {
            discard();
            return;
        }

        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetPlayerUuid);
        if (target == null || target.isSpectator() || !target.isAlive() || target.getEntityWorld() != world) {
            discard();
            return;
        }

        if (lifetimeTicks-- <= 0) {
            discard();
            return;
        }

        faceTarget(target);
        double distanceSquared = squaredDistanceTo(target);
        if (distanceSquared <= 1.55 * 1.55 || getBoundingBox().expand(0.35).intersects(target.getBoundingBox())) {
            ItMod.getChaseManager().handleCaughtByEntity(this, target);
            return;
        }

        moveToward(target, distanceSquared);
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

    private void moveToward(ServerPlayerEntity target, double distanceSquared) {
        Vec3d current = new Vec3d(getX(), getY(), getZ());
        Vec3d targetPos = target.getEyePos().add(0.0, -0.85, 0.0);
        Vec3d toTarget = targetPos.subtract(current);
        double distance = toTarget.length();
        if (distance < 0.01) {
            return;
        }

        double baseStep = distanceSquared > 32.0 * 32.0 ? 0.255 : 0.235;
        double stepSize = Math.min(0.335, baseStep * speedMultiplier);
        if (isTargetWatching(target)) {
            stepSize *= 0.72;
        }
        if (distance < 4.0) {
            stepSize *= 0.90;
        }

        Vec3d step = toTarget.normalize().multiply(Math.min(distance, stepSize));
        refreshPositionAndAngles(current.x + step.x, current.y + step.y, current.z + step.z, getYaw(), getPitch());
    }

    private boolean isTargetWatching(ServerPlayerEntity target) {
        if (!target.canSee(this)) {
            return false;
        }

        Vec3d toHunter = getEyePos().subtract(target.getEyePos());
        if (toHunter.lengthSquared() < 0.01) {
            return false;
        }

        double dot = target.getRotationVec(1.0F).normalize().dotProduct(toHunter.normalize());
        return dot >= 0.94;
    }

    private void faceTarget(ServerPlayerEntity target) {
        lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.getEyePos());
        setBodyYaw(getYaw());
        setHeadYaw(getYaw());
    }
}
