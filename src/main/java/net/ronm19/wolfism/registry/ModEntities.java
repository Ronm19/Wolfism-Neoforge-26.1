package net.ronm19.wolfism.registry;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;
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

public final class ModEntities {
    public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(Wolfism.MOD_ID);

    public static final Supplier<EntityType<TimberWolf>> TIMBER_WOLF = ENTITY_TYPES.registerEntityType(
            "timber_wolf",
            TimberWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.6F, 0.85F)
                    .eyeHeight(0.68F)
                    .clientTrackingRange(10));

    public static final Supplier<EntityType<ArcticWolf>> ARCTIC_WOLF = ENTITY_TYPES.registerEntityType(
            "arctic_wolf",
            ArcticWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.6F, 0.85F)
                    .eyeHeight(0.68F)
                    .clientTrackingRange(10));

    public static final Supplier<EntityType<BlackWolf>> BLACK_WOLF = ENTITY_TYPES.registerEntityType(
            "black_wolf",
            BlackWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.6F, 0.85F)
                    .eyeHeight(0.68F)
                    .clientTrackingRange(10));

    public static final Supplier<EntityType<SandWolf>> SAND_WOLF = ENTITY_TYPES.registerEntityType(
            "sand_wolf",
            SandWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.6F, 0.85F)
                    .eyeHeight(0.68F)
                    .clientTrackingRange(10));


    public static final Supplier<EntityType<DireWolf>> DIRE_WOLF = ENTITY_TYPES.registerEntityType(
            "dire_wolf",
            DireWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(1.0F, 1.40F)
                    .eyeHeight(1.12F)
                    .clientTrackingRange(12));


    public static final Supplier<EntityType<FireWolf>> FIRE_WOLF = ENTITY_TYPES.registerEntityType(
            "fire_wolf",
            FireWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.6F, 0.85F)
                    .eyeHeight(0.68F)
                    .fireImmune()
                    .clientTrackingRange(10));

    public static final Supplier<EntityType<FrostWolf>> FROST_WOLF = ENTITY_TYPES.registerEntityType(
            "frost_wolf",
            FrostWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.6F, 0.85F)
                    .eyeHeight(0.68F)
                    .clientTrackingRange(10));

    public static final Supplier<EntityType<StormWolf>> STORM_WOLF = ENTITY_TYPES.registerEntityType(
            "storm_wolf",
            StormWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.6F, 0.85F)
                    .eyeHeight(0.68F)
                    .clientTrackingRange(10));

    public static final Supplier<EntityType<WaterWolf>> WATER_WOLF = ENTITY_TYPES.registerEntityType(
            "water_wolf",
            WaterWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.6F, 0.85F)
                    .eyeHeight(0.68F)
                    .clientTrackingRange(10));

    public static final Supplier<EntityType<EarthWolf>> EARTH_WOLF = ENTITY_TYPES.registerEntityType(
            "earth_wolf",
            EarthWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.68F, 0.92F)
                    .eyeHeight(0.73F)
                    .clientTrackingRange(10));

    public static final Supplier<EntityType<SolarWolf>> SOLAR_WOLF = ENTITY_TYPES.registerEntityType(
            "solar_wolf",
            SolarWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.64F, 0.90F)
                    .eyeHeight(0.72F)
                    .fireImmune()
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<LunarWolf>> LUNAR_WOLF = ENTITY_TYPES.registerEntityType(
            "lunar_wolf",
            LunarWolf::new,
            MobCategory.AMBIENT,
            builder -> builder
                    .sized(0.64F, 0.90F)
                    .eyeHeight(0.72F)
                    .clientTrackingRange(12));

    private ModEntities() {
    }
}
