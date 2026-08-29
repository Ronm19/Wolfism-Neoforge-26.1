package net.ronm19.wolfism.event;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.ronm19.wolfism.entity.custom.*;
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
                ModEntities.DIRE_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 36.0D)
                        .add(Attributes.ATTACK_DAMAGE, 8.0D)
                        .add(Attributes.ARMOR, 3.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.30D)
                        .add(Attributes.FOLLOW_RANGE, 32.0D)
                        .add(Attributes.STEP_HEIGHT, 1.0D)
                        .build());

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
                ModEntities.SPIRIT_WOLF.get(),
                Wolf.createAttributes().add(Attributes.MAX_HEALTH, 30.0D).add(Attributes.ATTACK_DAMAGE, 5.0D)
                        .add(Attributes.ARMOR, 5.0D).add(Attributes.MOVEMENT_SPEED, 0.31D).add(Attributes.FOLLOW_RANGE, 40.0D).build());
        event.put(
                ModEntities.SHADOW_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 26.0D)
                        .add(Attributes.ATTACK_DAMAGE, 6.0D)
                        .add(Attributes.ARMOR, 3.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.32D)
                        .add(Attributes.FOLLOW_RANGE, 40.0D)
                        .build());
        event.put(
                ModEntities.GOLDEN_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 26.0D)
                        .add(Attributes.ATTACK_DAMAGE, 4.5D)
                        .add(Attributes.ARMOR, 2.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.31D)
                        .add(Attributes.FOLLOW_RANGE, 40.0D)
                        .build());
        event.put(
                ModEntities.CHERRY_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 26.0D)
                        .add(Attributes.ATTACK_DAMAGE, 4.5D)
                        .add(Attributes.ARMOR, 2.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.32D)
                        .add(Attributes.FOLLOW_RANGE, 40.0D)
                        .build());
        event.put(
                ModEntities.VIOLET_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 28.0D)
                        .add(Attributes.ATTACK_DAMAGE, 6.0D)
                        .add(Attributes.ARMOR, 3.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.33D)
                        .add(Attributes.FOLLOW_RANGE, 40.0D)
                        .build());
        event.put(
                ModEntities.GEM_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 26.0D)
                        .add(Attributes.ATTACK_DAMAGE, 4.5D)
                        .add(Attributes.ARMOR, 2.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.31D)
                        .add(Attributes.FOLLOW_RANGE, 48.0D)
                        .build());
        event.put(
                ModEntities.MUSHROOM_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 26.0D)
                        .add(Attributes.ATTACK_DAMAGE, 4.5D)
                        .add(Attributes.ARMOR, 2.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.31D)
                        .add(Attributes.FOLLOW_RANGE, 40.0D)
                        .build());
        event.put(
                ModEntities.BEE_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 26.0D)
                        .add(Attributes.ATTACK_DAMAGE, 4.5D)
                        .add(Attributes.ARMOR, 2.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.32D)
                        .add(Attributes.FLYING_SPEED, 0.60D)
                        .add(Attributes.STEP_HEIGHT, 1.0D)
                        .add(Attributes.FOLLOW_RANGE, 40.0D)
                        .build());
        event.put(
                ModEntities.ZOMBIE_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 30.0D)
                        .add(Attributes.ATTACK_DAMAGE, 5.5D)
                        .add(Attributes.ARMOR, 3.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.29D)
                        .add(Attributes.FOLLOW_RANGE, 40.0D)
                        .build());

        event.put(
                ModEntities.SKELETON_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 24.0D)
                        .add(Attributes.ATTACK_DAMAGE, 4.5D)
                        .add(Attributes.ARMOR, 2.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.32D)
                        .add(Attributes.FOLLOW_RANGE, 48.0D)
                        .build());


        event.put(
                ModEntities.HUSK_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 28.0D)
                        .add(Attributes.ATTACK_DAMAGE, 5.0D)
                        .add(Attributes.ARMOR, 3.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.30D)
                        .add(Attributes.FOLLOW_RANGE, 40.0D)
                        .build());


        event.put(
                ModEntities.DROWNED_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 28.0D)
                        .add(Attributes.ATTACK_DAMAGE, 5.0D)
                        .add(Attributes.ARMOR, 2.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.30D)
                        .add(Attributes.WATER_MOVEMENT_EFFICIENCY, 1.0D)
                        .add(Attributes.FOLLOW_RANGE, 48.0D)
                        .add(Attributes.STEP_HEIGHT, 1.0D)
                        .build());

        event.put(ModEntities.PHANTOM_WOLF.get(), Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.5D)
                .add(Attributes.ARMOR, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FLYING_SPEED, 0.60D)
                .add(Attributes.STEP_HEIGHT, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D)
                .build());

        event.put(
                ModEntities.BLOOD_WOLF.get(), BloodWolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 40.0D)
                        .add(Attributes.ATTACK_DAMAGE, 7.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.32D)
                        .add(Attributes.ARMOR, 3.0D)
                        .add(Attributes.FOLLOW_RANGE, 36.0D)
                        .add(Attributes.KNOCKBACK_RESISTANCE, 0.10D)
                        .build());

        event.put(
                ModEntities.END_WOLF.get(),
                Wolf.createAttributes()
                        .add(Attributes.MAX_HEALTH, 38.0D)
                        .add(Attributes.ATTACK_DAMAGE, 5.0D)
                        .add(Attributes.ARMOR, 2.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.33D)
                        .add(Attributes.FOLLOW_RANGE, 40.0D)
                        .build());

        event.put(ModEntities.SCULK_WOLF.get(), SculkWolf.createAttributes()
                .add(Attributes.MAX_HEALTH, 42.0D)
                .add(Attributes.ATTACK_DAMAGE, 5.5D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.STEP_HEIGHT, 1.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.10D)
                .build());

        event.put(ModEntities.INFERNAL_WOLF.get(),
                InfernalWolf.createAttributes().build());

        event.put(ModEntities.OMEN_WOLF.get(),
                OmenWolf.createAttributes().build());

        event.put(ModEntities.ASTRAL_WOLF.get(),
                AstralWolf.createAttributes().build());

        event.put(ModEntities.ANGEL_WOLF.get(),
                AngelWolf.createAttributes().build());

        event.put(ModEntities.DEMON_WOLF.get(),
                DemonWolf.createAttributes().build());

        event.put(ModEntities.GRAVE_WOLF.get(),
                GraveWolf.createAttributes().build());



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

        event.register(
                ModEntities.SPIRIT_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SpiritWolf::checkSpiritWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.SHADOW_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ShadowWolf::checkShadowWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.GOLDEN_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                GoldenWolf::checkGoldenWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.CHERRY_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CherryWolf::checkCherryWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.VIOLET_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                VioletWolf::checkVioletWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.GEM_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                GemWolf::checkGemWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.MUSHROOM_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MushroomWolf::checkMushroomWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.BEE_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BeeWolf::checkBeeWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.ZOMBIE_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ZombieWolf::checkZombieWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);


        event.register(
                ModEntities.SKELETON_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SkeletonWolf::checkSkeletonWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);


        event.register(
                ModEntities.HUSK_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                HuskWolf::checkHuskWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);


        event.register(
                ModEntities.DROWNED_WOLF.get(),
                SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.OCEAN_FLOOR,
                DrownedWolf::checkDrownedWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.PHANTOM_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                PhantomWolf::checkPhantomWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.BLOOD_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BloodWolf::checkBloodWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.END_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                EndWolf::checkEndWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.SCULK_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SculkWolf::checkSculkWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.INFERNAL_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                InfernalWolf::checkInfernalWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);


        event.register(
                ModEntities.OMEN_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                OmenWolf::checkOmenWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.ASTRAL_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AstralWolf::checkAstralWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.ANGEL_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AngelWolf::checkAngelWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.DEMON_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                DemonWolf::checkDemonWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.GRAVE_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                GraveWolf::checkGraveWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);





    }
}
