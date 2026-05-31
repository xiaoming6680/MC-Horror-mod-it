package com.xm6680.it.entity;

import com.xm6680.it.ItMod;
import com.xm6680.it.config.ItConfigManager;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Cave-only stalker that uses normal ground navigation and disappears when pathing fails.
 */
public class CaveStalkerEntity extends PathAwareEntity implements TargetOnlyVisibleEntity {
    private UUID targetPlayerUuid;
    private int lifetimeTicks;
    private boolean trapStill;
    private double walkSpeed = 0.72D;

    public CaveStalkerEntity(EntityType<? extends CaveStalkerEntity> entityType, World world) {
        super(entityType, world);
        setCanPickUpLoot(false);
        this.experiencePoints = 0;
    }

    @Override
    protected void initGoals() {
    }

    public void beginStalk(ServerPlayerEntity target, int lifetimeTicks, double speed) {
        this.targetPlayerUuid = target.getUuid();
        this.lifetimeTicks = Math.max(20, lifetimeTicks);
        this.walkSpeed = Math.max(0.05D, speed);
        this.trapStill = false;
        setTarget(target);
        setSilent(false);
    }

    public void beginTrapStill(ServerPlayerEntity target, int lifetimeTicks) {
        this.targetPlayerUuid = target.getUuid();
        this.lifetimeTicks = Math.max(20, lifetimeTicks);
        this.trapStill = true;
        setTarget(target);
        setSilent(true);
        getNavigation().stop();
    }

    public boolean isTarget(ServerPlayerEntity player) {
        return targetPlayerUuid != null && targetPlayerUuid.equals(player.getUuid());
    }

    @Override
    public UUID getVisibleTargetUuid() {
        return targetPlayerUuid;
    }

    public boolean tryMoveToTarget(ServerPlayerEntity target) {
        if (trapStill) {
            getNavigation().stop();
            return true;
        }

        boolean moving = getNavigation().startMovingTo(target, walkSpeed);
        if (!moving && canSee(target)) {
            getMoveControl().moveTo(target.getX(), target.getY(), target.getZ(), walkSpeed);
            moving = true;
        }
        return moving;
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

        lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, target.getEyePos());
        setBodyYaw(getYaw());
        setHeadYaw(getYaw());

        if (trapStill) {
            getNavigation().stop();
            return;
        }

        if (age % 4 == 0 || getNavigation().isIdle()) {
            tryMoveToTarget(target);
        }

        if (squaredDistanceTo(target) <= ItConfigManager.getConfig().caveStalkerCatchDistance * ItConfigManager.getConfig().caveStalkerCatchDistance
                || getBoundingBox().expand(0.25D).intersects(target.getBoundingBox())) {
            ItMod.getCaveStalkerManager().handleCaughtByEntity(this, target);
        }
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public boolean isCollidable(Entity entity) {
        return true;
    }

    @Override
    public float getStepHeight() {
        return Math.max(1.15F, super.getStepHeight());
    }

    public Vec3d currentPosition() {
        return new Vec3d(getX(), getY(), getZ());
    }
}
