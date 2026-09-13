package net.ronm19.wolfism.registry;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.ai.sensor.*;

public final class ModSensorTypes {
    public static final DeferredRegister<SensorType<?>> SENSOR_TYPES =
            DeferredRegister.create(BuiltInRegistries.SENSOR_TYPE, Wolfism.MOD_ID);

    public static final Supplier<SensorType<TimberWolfPackSensor>> TIMBER_PACK =
            SENSOR_TYPES.register("timber_pack", () -> new SensorType<>(TimberWolfPackSensor::new));

    public static final Supplier<SensorType<TimberWolfPreySensor>> TIMBER_PREY =
            SENSOR_TYPES.register("timber_prey", () -> new SensorType<>(TimberWolfPreySensor::new));

    public static final Supplier<SensorType<ArcticWolfPackSensor>> ARCTIC_PACK =
            SENSOR_TYPES.register("arctic_pack", () -> new SensorType<>(ArcticWolfPackSensor::new));

    public static final Supplier<SensorType<ArcticWolfPreySensor>> ARCTIC_PREY =
            SENSOR_TYPES.register("arctic_prey", () -> new SensorType<>(ArcticWolfPreySensor::new));

    public static final Supplier<SensorType<BlackWolfPackSensor>> BLACK_PACK =
            SENSOR_TYPES.register("black_pack", () -> new SensorType<>(BlackWolfPackSensor::new));

    public static final Supplier<SensorType<BlackWolfNightPreySensor>> BLACK_NIGHT_PREY =
            SENSOR_TYPES.register("black_night_prey", () -> new SensorType<>(BlackWolfNightPreySensor::new));

    public static final Supplier<SensorType<SandWolfPackSensor>> SAND_PACK =
            SENSOR_TYPES.register("sand_pack", () -> new SensorType<>(SandWolfPackSensor::new));

    public static final Supplier<SensorType<SandWolfDesertSensor>> SAND_DESERT =
            SENSOR_TYPES.register("sand_desert", () -> new SensorType<>(SandWolfDesertSensor::new));


    public static final Supplier<SensorType<DireWolfPackSensor>> DIRE_PACK =
            SENSOR_TYPES.register("dire_pack", () -> new SensorType<>(DireWolfPackSensor::new));

    public static final Supplier<SensorType<DireWolfPreySensor>> DIRE_PREY =
            SENSOR_TYPES.register("dire_prey", () -> new SensorType<>(DireWolfPreySensor::new));


    public static final Supplier<SensorType<FireWolfPackSensor>> FIRE_PACK =
            SENSOR_TYPES.register("fire_pack", () -> new SensorType<>(FireWolfPackSensor::new));

    public static final Supplier<SensorType<FireWolfCombatSensor>> FIRE_COMBAT =
            SENSOR_TYPES.register("fire_combat", () -> new SensorType<>(FireWolfCombatSensor::new));


    public static final Supplier<SensorType<FrostWolfPackSensor>> FROST_PACK =
            SENSOR_TYPES.register("frost_pack", () -> new SensorType<>(FrostWolfPackSensor::new));

    public static final Supplier<SensorType<FrostWolfCombatSensor>> FROST_COMBAT =
            SENSOR_TYPES.register("frost_combat", () -> new SensorType<>(FrostWolfCombatSensor::new));

    public static final Supplier<SensorType<StormWolfPackSensor>> STORM_PACK =
            SENSOR_TYPES.register("storm_pack", () -> new SensorType<>(StormWolfPackSensor::new));

    public static final Supplier<SensorType<StormWolfCombatSensor>> STORM_COMBAT =
            SENSOR_TYPES.register("storm_combat", () -> new SensorType<>(StormWolfCombatSensor::new));

    public static final Supplier<SensorType<WaterWolfPackSensor>> WATER_PACK =
            SENSOR_TYPES.register("water_pack", () -> new SensorType<>(WaterWolfPackSensor::new));

    public static final Supplier<SensorType<WaterWolfAquaticSensor>> WATER_AQUATIC =
            SENSOR_TYPES.register("water_aquatic", () -> new SensorType<>(WaterWolfAquaticSensor::new));

    public static final Supplier<SensorType<EarthWolfPackSensor>> EARTH_PACK =
            SENSOR_TYPES.register("earth_pack", () -> new SensorType<>(EarthWolfPackSensor::new));

    public static final Supplier<SensorType<EarthWolfTerrainSensor>> EARTH_TERRAIN =
            SENSOR_TYPES.register("earth_terrain", () -> new SensorType<>(EarthWolfTerrainSensor::new));

    public static final Supplier<SensorType<SolarWolfPackSensor>> SOLAR_PACK =
            SENSOR_TYPES.register("solar_pack", () -> new SensorType<>(SolarWolfPackSensor::new));

    public static final Supplier<SensorType<LunarWolfPackSensor>> LUNAR_PACK =
            SENSOR_TYPES.register("lunar_pack", () -> new SensorType<>(LunarWolfPackSensor::new));

    public static final Supplier<SensorType<LunarWolfNightWatchSensor>> LUNAR_NIGHT_WATCH =
            SENSOR_TYPES.register("lunar_night_watch", () -> new SensorType<>(LunarWolfNightWatchSensor::new));

    public static final Supplier<SensorType<SpiritWolfPackSensor>> SPIRIT_PACK =
            SENSOR_TYPES.register("spirit_pack", () -> new SensorType<>(SpiritWolfPackSensor::new));

    public static final Supplier<SensorType<AngelWolfFamilySensor>> ANGEL_FAMILY =
            SENSOR_TYPES.register("angel_family", () -> new SensorType<>(AngelWolfFamilySensor::new));

    public static final Supplier<SensorType<AngelWolfThreatSensor>> ANGEL_THREAT =
            SENSOR_TYPES.register("angel_threat", () -> new SensorType<>(AngelWolfThreatSensor::new));

    public static final Supplier<SensorType<ShadowWolfPackSensor>> SHADOW_PACK =
            SENSOR_TYPES.register("shadow_pack", () -> new SensorType<>(ShadowWolfPackSensor::new));

    public static final Supplier<SensorType<GoldenWolfPackSensor>> GOLDEN_PACK =
            SENSOR_TYPES.register("golden_pack", () -> new SensorType<>(GoldenWolfPackSensor::new));

    public static final Supplier<SensorType<CherryWolfPackSensor>> CHERRY_PACK =
            SENSOR_TYPES.register("cherry_pack", () -> new SensorType<>(CherryWolfPackSensor::new));

    public static final Supplier<SensorType<VioletWolfPackSensor>> VIOLET_PACK =
            SENSOR_TYPES.register("violet_pack", () -> new SensorType<>(VioletWolfPackSensor::new));

    public static final Supplier<SensorType<GemWolfPackSensor>> GEM_PACK =
            SENSOR_TYPES.register("gem_pack", () -> new SensorType<>(GemWolfPackSensor::new));

    public static final Supplier<SensorType<MushroomWolfPackSensor>> MUSHROOM_PACK =
            SENSOR_TYPES.register("mushroom_pack", () -> new SensorType<>(MushroomWolfPackSensor::new));

    public static final Supplier<SensorType<BeeWolfPackSensor>> BEE_PACK =
            SENSOR_TYPES.register("bee_pack", () -> new SensorType<>(BeeWolfPackSensor::new));

    public static final Supplier<SensorType<ZombieWolfPackSensor>> ZOMBIE_PACK =
            SENSOR_TYPES.register("zombie_pack", () -> new SensorType<>(ZombieWolfPackSensor::new));


    public static final Supplier<SensorType<SkeletonWolfPackSensor>> SKELETON_PACK =
            SENSOR_TYPES.register("skeleton_pack", () -> new SensorType<>(SkeletonWolfPackSensor::new));


    public static final Supplier<SensorType<HuskWolfPackSensor>> HUSK_PACK =
            SENSOR_TYPES.register("husk_pack", () -> new SensorType<>(HuskWolfPackSensor::new));


    public static final Supplier<SensorType<DrownedWolfPackSensor>> DROWNED_PACK =
            SENSOR_TYPES.register("drowned_pack", () -> new SensorType<>(DrownedWolfPackSensor::new));


    public static final Supplier<SensorType<PhantomWolfPackSensor>> PHANTOM_PACK =
            SENSOR_TYPES.register("phantom_pack", () -> new SensorType<>(PhantomWolfPackSensor::new));

    public static final Supplier<SensorType<BloodWolfPackSensor>> BLOOD_PACK =
            SENSOR_TYPES.register("blood_pack", () -> new SensorType<>(BloodWolfPackSensor::new));

    public static final Supplier<SensorType<EndWolfPackSensor>> END_PACK =
            SENSOR_TYPES.register("end_pack", () -> new SensorType<>(EndWolfPackSensor::new));

    public static final Supplier<SensorType<SculkWolfPackSensor>> SCULK_PACK =
            SENSOR_TYPES.register("sculk_pack", () -> new SensorType<>(SculkWolfPackSensor::new));

    public static final Supplier<SensorType<InfernalWolfPackSensor>> INFERNAL_PACK =
            SENSOR_TYPES.register("infernal_pack", () -> new SensorType<>(InfernalWolfPackSensor::new));

    public static final Supplier<SensorType<InfernalWolfAwarenessSensor>> INFERNAL_AWARENESS =
            SENSOR_TYPES.register("infernal_awareness", () -> new SensorType<>(InfernalWolfAwarenessSensor::new));

    public static final Supplier<SensorType<OmenWolfPackSensor>> OMEN_PACK =
            SENSOR_TYPES.register(
                    "omen_pack",
                    () -> new SensorType<>(OmenWolfPackSensor::new));

    public static final Supplier<SensorType<OmenWolfThreatSensor>> OMEN_THREAT =
            SENSOR_TYPES.register(
                    "omen_threat",
                    () -> new SensorType<>(OmenWolfThreatSensor::new));

    public static final Supplier<SensorType<AstralWolfPackSensor>> ASTRAL_PACK =
            SENSOR_TYPES.register(
                    "astral_pack",
                    () -> new SensorType<>(AstralWolfPackSensor::new));

    public static final Supplier<SensorType<AstralWolfNightSensor>> ASTRAL_NIGHT =
            SENSOR_TYPES.register(
                    "astral_night",
                    () -> new SensorType<>(AstralWolfNightSensor::new));

    public static final Supplier<SensorType<DemonWolfBattlefieldSensor>> DEMON_BATTLEFIELD =
            SENSOR_TYPES.register("demon_battlefield", () -> new SensorType<>(DemonWolfBattlefieldSensor::new));

    public static final Supplier<SensorType<DemonWolfCharmSensor>> DEMON_CHARM =
            SENSOR_TYPES.register("demon_charm", () -> new SensorType<>(DemonWolfCharmSensor::new));

    public static final Supplier<SensorType<WolfKingPackSensor>> WOLF_KING_PACK =
            SENSOR_TYPES.register(
                    "wolf_king_pack",
                    () -> new SensorType<>(WolfKingPackSensor::new));

    public static final Supplier<SensorType<WolfKingThreatSensor>> WOLF_KING_THREAT =
            SENSOR_TYPES.register(
                    "wolf_king_threat",
                    () -> new SensorType<>(WolfKingThreatSensor::new));


    public static final Supplier<SensorType<PrimordialPackSensor>> PRIMORDIAL_PACK =
            SENSOR_TYPES.register("primordial_pack", () -> new SensorType<>(PrimordialPackSensor::new));

    public static final Supplier<SensorType<PrimordialAwarenessSensor>> PRIMORDIAL_AWARENESS =
            SENSOR_TYPES.register("primordial_awareness", () -> new SensorType<>(PrimordialAwarenessSensor::new));

    public static final Supplier<SensorType<CreatorAwarenessSensor>> CREATOR_AWARENESS =
            SENSOR_TYPES.register("creator_awareness", () -> new SensorType<>(CreatorAwarenessSensor::new));

    public static final Supplier<SensorType<CreatorFamilySensor>> CREATOR_FAMILY =
            SENSOR_TYPES.register("creator_family", () -> new SensorType<>(CreatorFamilySensor::new));

    public static final Supplier<SensorType<MagmaWolfPackSensor>> MAGMA_PACK =
            SENSOR_TYPES.register("magma_pack", () -> new SensorType<>(MagmaWolfPackSensor::new));

    public static final Supplier<SensorType<MagmaWolfTacticalSensor>> MAGMA_TACTICAL =
            SENSOR_TYPES.register("magma_tactical", () -> new SensorType<>(MagmaWolfTacticalSensor::new));

    public static final Supplier<SensorType<VampireWolfTacticalSensor>> VAMPIRE_TACTICAL =
            SENSOR_TYPES.register("vampire_tactical", () -> new SensorType<>(VampireWolfTacticalSensor::new));

    public static final Supplier<SensorType<SpectralWolfTacticalSensor>> SPECTRAL_TACTICAL =
            SENSOR_TYPES.register("spectral_tactical", () -> new SensorType<>(SpectralWolfTacticalSensor::new));

    public static final Supplier<SensorType<ToxicWolfTacticalSensor>> TOXIC_TACTICAL =
            SENSOR_TYPES.register("toxic_tactical", () -> new SensorType<>(ToxicWolfTacticalSensor::new));

    public static final Supplier<SensorType<WarWolfTacticalSensor>> WAR_TACTICAL =
            SENSOR_TYPES.register("war_tactical", () -> new SensorType<>(WarWolfTacticalSensor::new));

    public static final Supplier<SensorType<IllagerWolfTacticalSensor>> ILLAGER_TACTICAL =
            SENSOR_TYPES.register("illager_tactical", () -> new SensorType<>(IllagerWolfTacticalSensor::new));

    public static final Supplier<SensorType<AncientWolfTacticalSensor>>
            ANCIENT_TACTICAL = SENSOR_TYPES.register("ancient_tactical", () -> new SensorType<>(AncientWolfTacticalSensor::new));

    public static final Supplier<SensorType<BladeWolfTacticalSensor>> BLADE_TACTICAL =
            SENSOR_TYPES.register("blade_tactical", () -> new SensorType<>(BladeWolfTacticalSensor::new));

    public static final Supplier<SensorType<RavenWolfTacticalSensor>> RAVEN_TACTICAL =
            SENSOR_TYPES.register("raven_tactical", () -> new SensorType<>(RavenWolfTacticalSensor::new));

    public static final Supplier<SensorType<CommandWolfAwarenessSensor>> COMMAND_AWARENESS =
            SENSOR_TYPES.register("command_awareness", () -> new SensorType<>(CommandWolfAwarenessSensor::new));

    public static final Supplier<SensorType<AshWolfAwarenessSensor>> ASH_AWARENESS =
            SENSOR_TYPES.register("ash_awareness", () -> new SensorType<>(AshWolfAwarenessSensor::new));

    public static final Supplier<SensorType<WitherWolfAwarenessSensor>> WITHER_AWARENESS =
            SENSOR_TYPES.register("wither_awareness", () -> new SensorType<>(WitherWolfAwarenessSensor::new));

    public static final Supplier<SensorType<BlazeWolfAwarenessSensor>> BLAZE_AWARENESS =
            SENSOR_TYPES.register("blaze_awareness", () -> new SensorType<>(BlazeWolfAwarenessSensor::new));



    public static final Supplier<SensorType<HalloweenWolfAwarenessSensor>> HALLOWEEN_AWARENESS =
            SENSOR_TYPES.register(
                    "halloween_awareness",
                    () -> new SensorType<>(HalloweenWolfAwarenessSensor::new));


    public static final Supplier<SensorType<ChristmasWolfAwarenessSensor>> CHRISTMAS_AWARENESS =
            SENSOR_TYPES.register("christmas_awareness",
                    () -> new SensorType<>(ChristmasWolfAwarenessSensor::new));

    public static final Supplier<SensorType<SaintPatricksWolfAwarenessSensor>> SAINT_PATRICKS_AWARENESS =
            SENSOR_TYPES.register("saint_patricks_awareness",
                    () -> new SensorType<>(SaintPatricksWolfAwarenessSensor::new));

    public static final Supplier<SensorType<NewYearsWolfAwarenessSensor>> NEW_YEARS_AWARENESS =
            SENSOR_TYPES.register("new_years_awareness",
                    () -> new SensorType<>(NewYearsWolfAwarenessSensor::new));

    public static final Supplier<SensorType<ValentinesWolfAwarenessSensor>> VALENTINES_AWARENESS =
            SENSOR_TYPES.register("valentines_awareness",
                    () -> new SensorType<>(ValentinesWolfAwarenessSensor::new));

    public static final Supplier<SensorType<EasterWolfAwarenessSensor>> EASTER_AWARENESS =
            SENSOR_TYPES.register("easter_awareness",
                    () -> new SensorType<>(EasterWolfAwarenessSensor::new));

    public static final Supplier<SensorType<FireworkWolfAwarenessSensor>> FIREWORK_AWARENESS =
            SENSOR_TYPES.register("firework_awareness",
                    () -> new SensorType<>(FireworkWolfAwarenessSensor::new));

    private ModSensorTypes() {
    }
}
