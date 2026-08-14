package net.ronm19.wolfism.registry;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.ai.sensor.ArcticWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.ArcticWolfPreySensor;
import net.ronm19.wolfism.entity.ai.sensor.TimberWolfPackSensor;
import net.ronm19.wolfism.entity.ai.sensor.TimberWolfPreySensor;

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

    private ModSensorTypes() {
    }
}
