package net.ronm19.wolfism.registry;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.ai.sensor.ArcticWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.ArcticWolfPreySensor;
import net.ronm19.wolfism.entity.ai.sensor.BlackWolfNightPreySensor;
import net.ronm19.wolfism.entity.ai.sensor.BlackWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.SandWolfDesertSensor;
import net.ronm19.wolfism.entity.ai.sensor.SandWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.DireWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.DireWolfPreySensor;
import net.ronm19.wolfism.entity.ai.sensor.FireWolfCombatSensor;
import net.ronm19.wolfism.entity.ai.sensor.FireWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.FrostWolfCombatSensor;
import net.ronm19.wolfism.entity.ai.sensor.FrostWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.StormWolfCombatSensor;
import net.ronm19.wolfism.entity.ai.sensor.StormWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.TimberWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.TimberWolfPreySensor;
import net.ronm19.wolfism.entity.ai.sensor.WaterWolfAquaticSensor;
import net.ronm19.wolfism.entity.ai.sensor.WaterWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.EarthWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.EarthWolfTerrainSensor;
import net.ronm19.wolfism.entity.ai.sensor.SolarWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.LunarWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.LunarWolfNightWatchSensor;

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

    private ModSensorTypes() {
    }
}
