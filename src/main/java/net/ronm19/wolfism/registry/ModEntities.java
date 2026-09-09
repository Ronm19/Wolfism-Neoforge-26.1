package net.ronm19.wolfism.registry;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.*;

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
            // Lunar is a night-only celestial spawn. Using the ambient spawn channel keeps
            // her independent from the very small CREATURE cap filled by farm animals,
            // while the LunarWolf spawn predicate still enforces ground/night/sky rules.
            MobCategory.AMBIENT,
            builder -> builder
                    .sized(0.64F, 0.90F)
                    .eyeHeight(0.72F)
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<SpiritWolf>> SPIRIT_WOLF = ENTITY_TYPES.registerEntityType(
            "spirit_wolf", SpiritWolf::new, MobCategory.CREATURE,
            builder -> builder.sized(0.62F, 0.88F).eyeHeight(0.70F).clientTrackingRange(12));

    public static final Supplier<EntityType<ShadowWolf>> SHADOW_WOLF = ENTITY_TYPES.registerEntityType(
            "shadow_wolf",
            ShadowWolf::new,
            // Shadow is a night-only supernatural spawn. AMBIENT prevents daylight
            // CREATURE-cap saturation from starving the entity before night arrives.
            MobCategory.AMBIENT,
            builder -> builder
                    .sized(0.62F, 0.88F)
                    .eyeHeight(0.70F)
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<GoldenWolf>> GOLDEN_WOLF = ENTITY_TYPES.registerEntityType(
            "golden_wolf",
            GoldenWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.62F, 0.88F)
                    .eyeHeight(0.70F)
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<CherryWolf>> CHERRY_WOLF = ENTITY_TYPES.registerEntityType(
            "cherry_wolf",
            CherryWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.62F, 0.88F)
                    .eyeHeight(0.70F)
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<VioletWolf>> VIOLET_WOLF = ENTITY_TYPES.registerEntityType(
            "violet_wolf",
            VioletWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.62F, 0.88F)
                    .eyeHeight(0.70F)
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<GemWolf>> GEM_WOLF = ENTITY_TYPES.registerEntityType(
            "gem_wolf",
            GemWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.62F, 0.88F)
                    .eyeHeight(0.70F)
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<MushroomWolf>> MUSHROOM_WOLF = ENTITY_TYPES.registerEntityType(
            "mushroom_wolf",
            MushroomWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.62F, 0.88F)
                    .eyeHeight(0.70F)
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<BeeWolf>> BEE_WOLF = ENTITY_TYPES.registerEntityType(
            "bee_wolf",
            BeeWolf::new,
            MobCategory.CREATURE,
            builder -> builder
                    .sized(0.62F, 0.88F)
                    .eyeHeight(0.70F)
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<ZombieWolf>> ZOMBIE_WOLF = ENTITY_TYPES.registerEntityType(
            "zombie_wolf",
            ZombieWolf::new,
            // Zombie Wolf is nocturnal, but MONSTER makes him compete with the
            // entire hostile-mob cap (including cave mobs). AMBIENT gives him a
            // separate spawn pool; the spawn predicate below still makes him
            // night/dark-outside only.
            MobCategory.AMBIENT,
            builder -> builder
                    .sized(0.62F, 0.88F)
                    .eyeHeight(0.70F)
                    .clientTrackingRange(12));


    public static final Supplier<EntityType<SkeletonWolf>> SKELETON_WOLF = ENTITY_TYPES.registerEntityType(
            "skeleton_wolf",
            SkeletonWolf::new,
            MobCategory.AMBIENT,
            builder -> builder
                    .sized(0.62F, 0.88F)
                    .eyeHeight(0.70F)
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<BoneShardProjectile>> BONE_SHARD = ENTITY_TYPES.registerEntityType(
            "bone_shard",
            BoneShardProjectile::new,
            MobCategory.MISC,
            builder -> builder
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(10));


    public static final Supplier<EntityType<HuskWolf>> HUSK_WOLF = ENTITY_TYPES.registerEntityType(
            "husk_wolf",
            HuskWolf::new,
            // Husk Wolf is sun-resistant and may roam desert habitats during
            // both day and night. AMBIENT keeps this rare spawn independent
            // from the saturated MONSTER and CREATURE caps.
            MobCategory.AMBIENT,
            builder -> builder
                    .sized(0.62F, 0.88F)
                    .eyeHeight(0.70F)
                    .clientTrackingRange(12));


    public static final Supplier<EntityType<DrownedWolf>> DROWNED_WOLF = ENTITY_TYPES.registerEntityType(
            "drowned_wolf",
            DrownedWolf::new,
            MobCategory.AMBIENT,
            builder -> builder
                    .sized(0.62F, 0.88F)
                    .eyeHeight(0.70F)
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<PhantomWolf>> PHANTOM_WOLF = ENTITY_TYPES.registerEntityType(
            "phantom_wolf",
            PhantomWolf::new,
            // Phantom Wolf is nocturnal and VERY RARE. AMBIENT avoids the
            // daylight CREATURE-cap starvation already seen with Lunar/Shadow,
            // while the placement predicate still enforces dark wolf-ground spawns.
            MobCategory.AMBIENT,
            builder -> builder
                    .sized(0.60F, 0.85F)
                    .eyeHeight(0.68F)
                    .clientTrackingRange(12));

    public static final Supplier<EntityType<BloodWolf>> BLOOD_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "blood_wolf",
                    BloodWolf ::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.6F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(10));

    public static final Supplier<EntityType<EndWolf>> END_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "end_wolf",
                    EndWolf::new,
                    MobCategory.MONSTER,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<SculkWolf>> SCULK_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "sculk_wolf",
                    SculkWolf::new,
                    MobCategory.MONSTER,
                    builder -> builder
                            .sized(0.68F, 0.94F)
                            .eyeHeight(0.75F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<InfernalWolf>> INFERNAL_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "infernal_wolf",
                    InfernalWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.68F, 0.94F)
                            .eyeHeight(0.75F)
                            .fireImmune()
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<OmenWolf>> OMEN_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "omen_wolf",
                    OmenWolf::new,
                    MobCategory.CREATURE,
                    builder -> builder
                            .sized(0.62F, 0.88F)
                            .eyeHeight(0.70F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<AstralWolf>> ASTRAL_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "astral_wolf",
                    AstralWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.62F, 0.88F)
                            .eyeHeight(0.70F)
                            .clientTrackingRange(12));


    public static final Supplier<EntityType<AngelWolf>> ANGEL_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "angel_wolf",
                    AngelWolf::new,
                    MobCategory.CREATURE,
                    builder -> builder
                            .sized(0.62F, 0.88F)
                            .eyeHeight(0.70F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<DemonWolf>> DEMON_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "demon_wolf",
                    DemonWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.62F, 0.88F)
                            .eyeHeight(0.70F)
                            .fireImmune()
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<GraveWolf>> GRAVE_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "grave_wolf",
                    GraveWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));


    public static final Supplier<EntityType<RiftWolf>> RIFT_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "rift_wolf",
                    RiftWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));


    public static final Supplier<EntityType<VoidWolf>> VOID_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "void_wolf",
                    VoidWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<VampireWolf>> VAMPIRE_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "vampire_wolf",
                    VampireWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<SpectralWolf>> SPECTRAL_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "spectral_wolf",
                    SpectralWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<SalvaWolf>> SALVA_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "salva_wolf",
                    SalvaWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<WolfKing>> WOLF_KING =
            ENTITY_TYPES.registerEntityType(
                    "wolf_king",
                    WolfKing::new,
                    // Spawn-budget category only: keeps the Alpha encounter from
                    // being starved by the passive CREATURE cap in loaded taigas.
                    // Wolf King's actual Wolf AI/temperament is unchanged.
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.68F, 0.94F)
                            .eyeHeight(0.75F)
                            .clientTrackingRange(12));


    public static final Supplier<EntityType<PrimordialWolf>> PRIMORDIAL_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "primordial_wolf",
                    PrimordialWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<ToxicWolf>> TOXIC_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "toxic_wolf",
                    ToxicWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));


    public static final Supplier<EntityType<MagmaWolf>> MAGMA_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "magma_wolf",
                    MagmaWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.68F, 0.94F)
                            .eyeHeight(0.75F)
                            .fireImmune()
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<WarWolf>> WAR_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "war_wolf",
                    WarWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<IllagerWolf>> ILLAGER_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "illager_wolf",
                    IllagerWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<AncientWolf>>
            ANCIENT_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "ancient_wolf",
                    AncientWolf::new,
                    MobCategory.CREATURE,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<BladeWolf>> BLADE_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "blade_wolf",
                    BladeWolf::new,
                    MobCategory.CREATURE,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<RavenWolf>> RAVEN_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "raven_wolf",
                    RavenWolf::new,
                    MobCategory.CREATURE,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<CommandWolf>> COMMAND_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "command_wolf",
                    CommandWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<AshWolf>> ASH_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "ash_wolf",
                    AshWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .fireImmune()
                            .clientTrackingRange(12));

    public static final Supplier<EntityType<WitherWolf>> WITHER_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "wither_wolf",
                    WitherWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(16));

    public static final Supplier<EntityType<BlazeWolf>> BLAZE_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "blaze_wolf",
                    BlazeWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .fireImmune()
                            .clientTrackingRange(16));



    public static final Supplier<EntityType<HalloweenWolf>> HALLOWEEN_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "halloween_wolf",
                    HalloweenWolf::new,
                    MobCategory.CREATURE,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(14));

    public static final Supplier<EntityType<CreatorWolf>> CREATOR_WOLF =
            ENTITY_TYPES.registerEntityType(
                    "creator_wolf",
                    CreatorWolf::new,
                    MobCategory.AMBIENT,
                    builder -> builder
                            .sized(0.60F, 0.85F)
                            .eyeHeight(0.68F)
                            .clientTrackingRange(16));


    public static final Supplier<EntityType<RiftPortalEntity>> RIFT_PORTAL =
            ENTITY_TYPES.registerEntityType(
                    "rift_portal",
                    RiftPortalEntity::new,
                    MobCategory.MISC,
                    builder -> builder
                            .sized(0.30F, 1.80F)
                            .noSave()
                            .noSummon()
                            .clientTrackingRange(8)
                            .updateInterval(1));





    private ModEntities() {
    }
}
