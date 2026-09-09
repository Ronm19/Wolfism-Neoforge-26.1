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

        event.put(ModEntities.RIFT_WOLF.get(),
                RiftWolf.createAttributes().build());

        event.put(ModEntities.VOID_WOLF.get(),
                VoidWolf.createAttributes().build());

        event.put(ModEntities.SALVA_WOLF.get(),
                SalvaWolf.createAttributes().build());

        event.put(ModEntities.WOLF_KING.get(),
                WolfKing.createAttributes().build());

        event.put(ModEntities.PRIMORDIAL_WOLF.get(),
                PrimordialWolf.createAttributes().build());

        event.put(ModEntities.MAGMA_WOLF.get(),
                MagmaWolf.createAttributes().build());

        event.put(ModEntities.VAMPIRE_WOLF.get(),
                VampireWolf.createAttributes().build());

        event.put(ModEntities.SPECTRAL_WOLF.get(),
                SpectralWolf.createAttributes().build());

        event.put(ModEntities.TOXIC_WOLF.get(),
                ToxicWolf.createAttributes().build());

        event.put(ModEntities.WAR_WOLF.get(),
                WarWolf.createAttributes().build());

        event.put(ModEntities.ILLAGER_WOLF.get(),
                IllagerWolf.createAttributes().build());

        event.put(ModEntities.ANCIENT_WOLF.get(),
                AncientWolf.createAttributes().build());

        event.put(ModEntities.BLADE_WOLF.get(),
                BladeWolf.createAttributes().build());

        event.put(ModEntities.RAVEN_WOLF.get(),
                RavenWolf.createAttributes().build());

        event.put(ModEntities.COMMAND_WOLF.get(),
                CommandWolf.createAttributes().build());

        event.put(ModEntities.ASH_WOLF.get(),
                AshWolf.createAttributes().build());

        event.put(ModEntities.WITHER_WOLF.get(),
                WitherWolf.createAttributes().build());

        event.put(ModEntities.BLAZE_WOLF.get(),
                BlazeWolf.createAttributes().build());

        event.put(ModEntities.HALLOWEEN_WOLF.get(),
                HalloweenWolf.createAttributes().build());

        event.put(ModEntities.CREATOR_WOLF.get(),
                CreatorWolf.createAttributes().build());


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

        event.register(
                ModEntities.RIFT_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RiftWolf::checkRiftWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.VOID_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                VoidWolf::checkVoidWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.SALVA_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SalvaWolf::checkSalvaWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.WOLF_KING.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WolfKing::checkWolfKingSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.PRIMORDIAL_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                PrimordialWolf::checkPrimordialWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.MAGMA_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                MagmaWolf::checkMagmaWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.VAMPIRE_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                VampireWolf::checkVampireWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.SPECTRAL_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                SpectralWolf::checkSpectralWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.TOXIC_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ToxicWolf::checkToxicWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.WAR_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WarWolf::checkWarWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.ILLAGER_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                IllagerWolf::checkIllagerWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.ANCIENT_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AncientWolf::checkAncientWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.BLADE_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BladeWolf::checkBladeWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE
        );

        event.register(
                ModEntities.RAVEN_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RavenWolf::checkRavenWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.COMMAND_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CommandWolf::checkCommandWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.ASH_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AshWolf::checkAshWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);


        event.register(
                ModEntities.WITHER_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WitherWolf::checkWitherWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.BLAZE_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlazeWolf::checkBlazeWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);


        event.register(
                ModEntities.HALLOWEEN_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                HalloweenWolf::checkHalloweenWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);


    }
}
