package com.xm6680.it.entity;

import com.xm6680.it.ItMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

/**
 * Registers custom entities used by the It mod.
 */
public final class ModEntities {
    public static final EntityType<WatcherEntity> WATCHER = registerWatcher();
    public static final EntityType<HuntingWatcherEntity> HUNTING_WATCHER = registerHuntingWatcher();
    public static final EntityType<CaveStalkerEntity> CAVE_STALKER = registerCaveStalker();

    private ModEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(WATCHER, MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.FOLLOW_RANGE, 48.0));
        FabricDefaultAttributeRegistry.register(HUNTING_WATCHER, MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 40.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.FOLLOW_RANGE, 96.0));
        FabricDefaultAttributeRegistry.register(CAVE_STALKER, MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 40.0)
                .add(EntityAttributes.MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.STEP_HEIGHT, 1.15));
    }

    private static EntityType<WatcherEntity> registerWatcher() {
        Identifier id = Identifier.of(ItMod.MOD_ID, "watcher");
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);

        EntityType<WatcherEntity> entityType = EntityType.Builder
                .create(WatcherEntity::new, SpawnGroup.MISC)
                .dimensions(0.55F, 2.9F)
                .eyeHeight(2.55F)
                .maxTrackingRange(64)
                .trackingTickInterval(2)
                .dropsNothing()
                .disableSaving()
                .build(key);

        return Registry.register(Registries.ENTITY_TYPE, key, entityType);
    }

    private static EntityType<HuntingWatcherEntity> registerHuntingWatcher() {
        Identifier id = Identifier.of(ItMod.MOD_ID, "hunting_watcher");
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);

        EntityType<HuntingWatcherEntity> entityType = EntityType.Builder
                .create(HuntingWatcherEntity::new, SpawnGroup.MISC)
                .dimensions(0.62F, 3.05F)
                .eyeHeight(2.65F)
                .maxTrackingRange(96)
                .trackingTickInterval(1)
                .dropsNothing()
                .disableSaving()
                .build(key);

        return Registry.register(Registries.ENTITY_TYPE, key, entityType);
    }

    private static EntityType<CaveStalkerEntity> registerCaveStalker() {
        Identifier id = Identifier.of(ItMod.MOD_ID, "cave_stalker");
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id);

        EntityType<CaveStalkerEntity> entityType = EntityType.Builder
                .create(CaveStalkerEntity::new, SpawnGroup.MISC)
                .dimensions(0.58F, 1.95F)
                .eyeHeight(1.74F)
                .maxTrackingRange(80)
                .trackingTickInterval(2)
                .dropsNothing()
                .disableSaving()
                .build(key);

        return Registry.register(Registries.ENTITY_TYPE, key, entityType);
    }
}
