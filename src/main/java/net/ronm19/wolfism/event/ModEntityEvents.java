package net.ronm19.wolfism.event;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.ronm19.wolfism.entity.custom.ArcticWolf;
import net.ronm19.wolfism.entity.custom.BlackWolf;
import net.ronm19.wolfism.entity.custom.SandWolf;
import net.ronm19.wolfism.entity.custom.DireWolf;
import net.ronm19.wolfism.entity.custom.FireWolf;
import net.ronm19.wolfism.entity.custom.FrostWolf;
import net.ronm19.wolfism.entity.custom.StormWolf;
import net.ronm19.wolfism.entity.custom.TimberWolf;
import net.ronm19.wolfism.entity.custom.WaterWolf;
import net.ronm19.wolfism.entity.custom.EarthWolf;
import net.ronm19.wolfism.entity.custom.SolarWolf;
import net.ronm19.wolfism.entity.custom.LunarWolf;
import net.ronm19.wolfism.registry.ModEntities;

public final class ModEntityEvents {
    private ModEntityEvents() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TIMBER_WOLF.get(), Wolf.createAttributes().build());
        event.put(ModEntities.ARCTIC_WOLF.get(), Wolf.createAttributes().build());
        event.put(ModEntities.BLACK_WOLF.get(), Wolf.createAttributes().build());
        event.put(ModEntities.SAND_WOLF.get(), Wolf.createAttributes().build());
        event.put(
                ModEntities.FIRE_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 24.0D)
                        .add(Attributes.ATTACK_DAMAGE, 5.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.32D)
                        .add(Attributes.FOLLOW_RANGE, 32.0D)
                        .build());
        event.put(
                ModEntities.FROST_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 24.0D)
                        .add(Attributes.ATTACK_DAMAGE, 5.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.30D)
                        .add(Attributes.FOLLOW_RANGE, 32.0D)
                        .build());
        event.put(
                ModEntities.STORM_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 24.0D)
                        .add(Attributes.ATTACK_DAMAGE, 5.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.31D)
                        .add(Attributes.FOLLOW_RANGE, 32.0D)
                        .build());
        event.put(
                ModEntities.WATER_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 24.0D)
                        .add(Attributes.ATTACK_DAMAGE, 5.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.30D)
                        .add(Attributes.FOLLOW_RANGE, 32.0D)
                        .build());
        event.put(
                ModEntities.EARTH_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 34.0D)
                        .add(Attributes.ATTACK_DAMAGE, 6.0D)
                        .add(Attributes.ARMOR, 8.0D)
                        .add(Attributes.KNOCKBACK_RESISTANCE, 0.45D)
                        .add(Attributes.MOVEMENT_SPEED, 0.28D)
                        .add(Attributes.FOLLOW_RANGE, 36.0D)
                        .add(Attributes.STEP_HEIGHT, 1.0D)
                        .build());
        event.put(
                ModEntities.SOLAR_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 30.0D)
                        .add(Attributes.ATTACK_DAMAGE, 6.0D)
                        .add(Attributes.ARMOR, 4.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.32D)
                        .add(Attributes.FOLLOW_RANGE, 40.0D)
                        .build());
        event.put(
                ModEntities.LUNAR_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 28.0D)
                        .add(Attributes.ATTACK_DAMAGE, 5.5D)
                        .add(Attributes.ARMOR, 4.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.33D)
                        .add(Attributes.FOLLOW_RANGE, 44.0D)
                        .build());
        event.put(
                ModEntities.DIRE_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 36.0D)
                        .add(Attributes.ATTACK_DAMAGE, 8.0D)
                        .add(Attributes.ARMOR, 3.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.30D)
                        .add(Attributes.FOLLOW_RANGE, 32.0D)
                        .add(Attributes.STEP_HEIGHT, 1.0D)
                        .build());
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                ModEntities.TIMBER_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                TimberWolf::checkTimberWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.ARCTIC_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ArcticWolf::checkArcticWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.BLACK_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlackWolf::checkBlackWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.SAND_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SandWolf::checkSandWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.DIRE_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DireWolf::checkDireWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.FIRE_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                FireWolf::checkFireWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.FROST_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                FrostWolf::checkFrostWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.STORM_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                StormWolf::checkStormWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.WATER_WOLF.get(),
                SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WaterWolf::checkWaterWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.EARTH_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                EarthWolf::checkEarthWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.SOLAR_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SolarWolf::checkSolarWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.LUNAR_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LunarWolf::checkLunarWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

    }
}
