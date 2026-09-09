package net.ronm19.wolfism.registry;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.*;

/**
 * Runtime memories used by Wolfism brains.
 *
 * <p>These memories intentionally have no codec. They describe nearby entities,
 * short-lived tactical state, environment awareness, and cooldown state that is
 * rebuilt after loading rather than permanently serialized.</p>
 */
public final class ModMemoryModuleTypes {
    public static final DeferredRegister<MemoryModuleType<?>> MEMORY_MODULE_TYPES =
            DeferredRegister.create(BuiltInRegistries.MEMORY_MODULE_TYPE, Wolfism.MOD_ID);

    public static final Supplier<MemoryModuleType<TimberWolf>> NEAREST_TIMBER_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_timber_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<TimberWolf>> NEAREST_TIMBER_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_timber_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<TimberWolf>> TIMBER_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("timber_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> TIMBER_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("timber_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> TIMBER_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("timber_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> TIMBER_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("timber_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> NEAREST_TIMBER_PREY =
            MEMORY_MODULE_TYPES.register("nearest_timber_prey", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> TIMBER_HUNT_TARGET =
            MEMORY_MODULE_TYPES.register("timber_hunt_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> TIMBER_FOREST_COVER =
            MEMORY_MODULE_TYPES.register("timber_forest_cover", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> TIMBER_HUNT_COOLDOWN =
            MEMORY_MODULE_TYPES.register("timber_hunt_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<ArcticWolf>> NEAREST_ARCTIC_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_arctic_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<ArcticWolf>> NEAREST_ARCTIC_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_arctic_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<ArcticWolf>> ARCTIC_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("arctic_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ARCTIC_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("arctic_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ARCTIC_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("arctic_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> ARCTIC_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("arctic_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> NEAREST_ARCTIC_PREY =
            MEMORY_MODULE_TYPES.register("nearest_arctic_prey", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> ARCTIC_HUNT_TARGET =
            MEMORY_MODULE_TYPES.register("arctic_hunt_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> ARCTIC_COLD_ENVIRONMENT =
            MEMORY_MODULE_TYPES.register("arctic_cold_environment", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ARCTIC_TRACKING_TICKS =
            MEMORY_MODULE_TYPES.register("arctic_tracking_ticks", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ARCTIC_HUNT_COOLDOWN =
            MEMORY_MODULE_TYPES.register("arctic_hunt_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlackWolf>> NEAREST_BLACK_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_black_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlackWolf>> NEAREST_BLACK_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_black_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlackWolf>> BLACK_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("black_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BLACK_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("black_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BLACK_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("black_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> BLACK_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("black_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> NEAREST_BLACK_PREY =
            MEMORY_MODULE_TYPES.register("nearest_black_prey", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> BLACK_HUNT_TARGET =
            MEMORY_MODULE_TYPES.register("black_hunt_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> BLACK_NIGHT_ACTIVE =
            MEMORY_MODULE_TYPES.register("black_night_active", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> BLACK_AMBUSH_TARGET =
            MEMORY_MODULE_TYPES.register("black_ambush_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BLACK_AMBUSH_COOLDOWN =
            MEMORY_MODULE_TYPES.register("black_ambush_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BLACK_HUNT_COOLDOWN =
            MEMORY_MODULE_TYPES.register("black_hunt_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<SandWolf>> NEAREST_SAND_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_sand_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<SandWolf>> NEAREST_SAND_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_sand_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<SandWolf>> SAND_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("sand_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SAND_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("sand_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SAND_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("sand_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> SAND_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("sand_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> NEAREST_SAND_PREY =
            MEMORY_MODULE_TYPES.register("nearest_sand_prey", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> SAND_HUNT_TARGET =
            MEMORY_MODULE_TYPES.register("sand_hunt_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> SAND_DESERT_ENVIRONMENT =
            MEMORY_MODULE_TYPES.register("sand_desert_environment", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<net.minecraft.core.BlockPos>> SAND_BURIED_INTEREST =
            MEMORY_MODULE_TYPES.register("sand_buried_interest", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SAND_HUNT_COOLDOWN =
            MEMORY_MODULE_TYPES.register("sand_hunt_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    public static final Supplier<MemoryModuleType<DireWolf>> NEAREST_DIRE_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_dire_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<DireWolf>> NEAREST_DIRE_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_dire_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<DireWolf>> DIRE_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("dire_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> DIRE_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("dire_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> DIRE_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("dire_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> DIRE_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("dire_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> NEAREST_DIRE_PREY =
            MEMORY_MODULE_TYPES.register("nearest_dire_prey", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> DIRE_HUNT_TARGET =
            MEMORY_MODULE_TYPES.register("dire_hunt_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> DIRE_HUNT_COOLDOWN =
            MEMORY_MODULE_TYPES.register("dire_hunt_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    public static final Supplier<MemoryModuleType<FireWolf>> NEAREST_FIRE_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_fire_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<FireWolf>> NEAREST_FIRE_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_fire_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<FireWolf>> FIRE_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("fire_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> FIRE_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("fire_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> FIRE_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("fire_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> FIRE_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("fire_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> NEAREST_FIRE_PREY =
            MEMORY_MODULE_TYPES.register("nearest_fire_prey", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> FIRE_HUNT_TARGET =
            MEMORY_MODULE_TYPES.register("fire_hunt_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> FIRE_HUNT_COOLDOWN =
            MEMORY_MODULE_TYPES.register("fire_hunt_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> FIRE_HOT_ENVIRONMENT =
            MEMORY_MODULE_TYPES.register("fire_hot_environment", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> FIRE_RUSH_TARGET =
            MEMORY_MODULE_TYPES.register("fire_rush_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> FIRE_RUSH_COOLDOWN =
            MEMORY_MODULE_TYPES.register("fire_rush_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    public static final Supplier<MemoryModuleType<FrostWolf>> NEAREST_FROST_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_frost_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<FrostWolf>> NEAREST_FROST_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_frost_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<FrostWolf>> FROST_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("frost_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> FROST_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("frost_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> FROST_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("frost_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> FROST_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("frost_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> FROST_COLD_ENVIRONMENT =
            MEMORY_MODULE_TYPES.register("frost_cold_environment", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> FROST_CHILLED_TARGET =
            MEMORY_MODULE_TYPES.register("frost_chilled_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> FROST_ICE_SPIKE_TARGET =
            MEMORY_MODULE_TYPES.register("frost_ice_spike_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> FROST_ICE_SPIKE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("frost_ice_spike_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    public static final Supplier<MemoryModuleType<StormWolf>> NEAREST_STORM_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_storm_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<StormWolf>> NEAREST_STORM_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_storm_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<StormWolf>> STORM_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("storm_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> STORM_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("storm_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> STORM_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("storm_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> STORM_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("storm_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    /** 0 = clear, 1 = rain at the wolf, 2 = natural thunderstorm at the wolf. */
    public static final Supplier<MemoryModuleType<Integer>> STORM_WEATHER_STATE =
            MEMORY_MODULE_TYPES.register("storm_weather_state", () -> new MemoryModuleType<>(Optional.empty()));

    /** 0-100 electrical resource used by Thunder Strike and Thunderstorm. */
    public static final Supplier<MemoryModuleType<Integer>> STORM_CHARGE =
            MEMORY_MODULE_TYPES.register("storm_charge", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> STORM_THUNDER_STRIKE_TARGET =
            MEMORY_MODULE_TYPES.register("storm_thunder_strike_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> STORM_THUNDER_STRIKE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("storm_thunder_strike_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> STORM_THUNDERSTORM_COOLDOWN =
            MEMORY_MODULE_TYPES.register("storm_thunderstorm_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    public static final Supplier<MemoryModuleType<WaterWolf>> NEAREST_WATER_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_water_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<WaterWolf>> NEAREST_WATER_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_water_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<WaterWolf>> WATER_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("water_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WATER_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("water_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WATER_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("water_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> WATER_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("water_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> WATER_RESCUE_TARGET =
            MEMORY_MODULE_TYPES.register("water_rescue_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> WATER_AQUATIC_THREAT =
            MEMORY_MODULE_TYPES.register("water_aquatic_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> WATER_CURRENT_DASH_TARGET =
            MEMORY_MODULE_TYPES.register("water_current_dash_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WATER_CURRENT_DASH_COOLDOWN =
            MEMORY_MODULE_TYPES.register("water_current_dash_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WATER_BUBBLE_SHELTER_COOLDOWN =
            MEMORY_MODULE_TYPES.register("water_bubble_shelter_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WATER_PRESSURE_WAVE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("water_pressure_wave_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    public static final Supplier<MemoryModuleType<EarthWolf>> NEAREST_EARTH_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_earth_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<EarthWolf>> NEAREST_EARTH_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_earth_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<EarthWolf>> EARTH_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("earth_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> EARTH_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("earth_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> EARTH_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("earth_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> EARTH_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("earth_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> EARTH_TERRAIN_SCORE =
            MEMORY_MODULE_TYPES.register("earth_terrain_score", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>> EARTH_NEAREST_TERRAIN =
            MEMORY_MODULE_TYPES.register("earth_nearest_terrain", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> EARTH_CHARGE_TARGET =
            MEMORY_MODULE_TYPES.register("earth_charge_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> EARTH_CHARGE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("earth_charge_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> EARTH_DIRT_SHELL_COOLDOWN =
            MEMORY_MODULE_TYPES.register("earth_dirt_shell_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> EARTH_SHOCKWAVE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("earth_shockwave_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> EARTH_DIRT_BLAST_TARGET =
            MEMORY_MODULE_TYPES.register("earth_dirt_blast_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> EARTH_DIRT_BLAST_COOLDOWN =
            MEMORY_MODULE_TYPES.register("earth_dirt_blast_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> EARTH_EARTHQUAKE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("earth_earthquake_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    public static final Supplier<MemoryModuleType<SolarWolf>> NEAREST_SOLAR_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_solar_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<SolarWolf>> NEAREST_SOLAR_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_solar_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<SolarWolf>> SOLAR_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("solar_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SOLAR_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("solar_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SOLAR_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("solar_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> SOLAR_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("solar_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SOLAR_FLARE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("solar_flare_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> SOLAR_SUNBEAM_TARGET =
            MEMORY_MODULE_TYPES.register("solar_sunbeam_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SOLAR_SUNBEAM_COOLDOWN =
            MEMORY_MODULE_TYPES.register("solar_sunbeam_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> SOLAR_DASH_TARGET =
            MEMORY_MODULE_TYPES.register("solar_dash_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SOLAR_DASH_COOLDOWN =
            MEMORY_MODULE_TYPES.register("solar_dash_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> SOLAR_ASCENSION_TARGET =
            MEMORY_MODULE_TYPES.register("solar_ascension_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SOLAR_ASCENSION_COOLDOWN =
            MEMORY_MODULE_TYPES.register("solar_ascension_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LunarWolf>> NEAREST_LUNAR_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_lunar_packmate", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LunarWolf>> NEAREST_LUNAR_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_lunar_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LunarWolf>> LUNAR_PACK_LEADER =
            MEMORY_MODULE_TYPES.register("lunar_pack_leader", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> LUNAR_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("lunar_pack_size", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> LUNAR_ADULT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("lunar_adult_pack_size", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> LUNAR_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("lunar_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> LUNAR_WATCH_THREAT =
            MEMORY_MODULE_TYPES.register("lunar_watch_threat", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> LUNAR_BEAM_TARGET =
            MEMORY_MODULE_TYPES.register("lunar_beam_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> LUNAR_BEAM_COOLDOWN =
            MEMORY_MODULE_TYPES.register("lunar_beam_cooldown", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> LUNAR_SHIELD_COOLDOWN =
            MEMORY_MODULE_TYPES.register("lunar_shield_cooldown", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> LUNAR_DREAMSTEP_COOLDOWN =
            MEMORY_MODULE_TYPES.register("lunar_dreamstep_cooldown", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> LUNAR_HOWL_COOLDOWN =
            MEMORY_MODULE_TYPES.register("lunar_howl_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<SpiritWolf>> NEAREST_SPIRIT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_spirit_packmate", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<SpiritWolf>> NEAREST_SPIRIT_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_spirit_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> SPIRIT_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("spirit_pack_size", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> SPIRIT_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("spirit_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> SPIRIT_INJURED_FAMILY =
            MEMORY_MODULE_TYPES.register("spirit_injured_family", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> SPIRIT_CRITICAL_FAMILY =
            MEMORY_MODULE_TYPES.register("spirit_critical_family", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> SPIRIT_SUPERNATURAL_THREAT =
            MEMORY_MODULE_TYPES.register("spirit_supernatural_threat", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> SPIRIT_MEND_COOLDOWN =
            MEMORY_MODULE_TYPES.register("spirit_mend_cooldown", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> SPIRIT_SOUL_GUARD_COOLDOWN =
            MEMORY_MODULE_TYPES.register("spirit_soul_guard_cooldown", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> SPIRIT_GUARDIAN_COOLDOWN =
            MEMORY_MODULE_TYPES.register("spirit_guardian_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    // Angel Wolf: family-health + threat memories for her Brain-driven support AI.
    public static final Supplier<MemoryModuleType<Integer>> ANGEL_FAMILY_SIZE =
            MEMORY_MODULE_TYPES.register("angel_family_size", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> ANGEL_INJURED_COUNT =
            MEMORY_MODULE_TYPES.register("angel_injured_count", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> ANGEL_INJURED_FAMILY =
            MEMORY_MODULE_TYPES.register("angel_injured_family", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> ANGEL_CRITICAL_FAMILY =
            MEMORY_MODULE_TYPES.register("angel_critical_family", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> ANGEL_FAMILY_THREAT =
            MEMORY_MODULE_TYPES.register("angel_family_threat", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> ANGEL_UNDEAD_THREAT =
            MEMORY_MODULE_TYPES.register("angel_undead_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<ShadowWolf>> NEAREST_SHADOW_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_shadow_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<ShadowWolf>> NEAREST_SHADOW_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_shadow_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SHADOW_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("shadow_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> SHADOW_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("shadow_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> SHADOW_DARKNESS_ACTIVE =
            MEMORY_MODULE_TYPES.register("shadow_darkness_active", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> SHADOW_VOID_DASH_TARGET =
            MEMORY_MODULE_TYPES.register("shadow_void_dash_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SHADOW_VOID_DASH_COOLDOWN =
            MEMORY_MODULE_TYPES.register("shadow_void_dash_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SHADOW_BLADES_COOLDOWN =
            MEMORY_MODULE_TYPES.register("shadow_blades_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SHADOW_DUSK_VEIL_COOLDOWN =
            MEMORY_MODULE_TYPES.register("shadow_dusk_veil_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SHADOW_ASSASSIN_COOLDOWN =
            MEMORY_MODULE_TYPES.register("shadow_assassin_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<GoldenWolf>> NEAREST_GOLDEN_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_golden_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<GoldenWolf>> NEAREST_GOLDEN_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_golden_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> GOLDEN_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("golden_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> GOLDEN_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("golden_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>> GOLDEN_RESOURCE_POS =
            MEMORY_MODULE_TYPES.register("golden_resource_pos", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> GOLDEN_FORTUNE_DIG_COOLDOWN =
            MEMORY_MODULE_TYPES.register("golden_fortune_dig_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> GOLDEN_RADIANT_SHARE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("golden_radiant_share_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> GOLDEN_BARRIER_COOLDOWN =
            MEMORY_MODULE_TYPES.register("golden_barrier_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> GOLDEN_BLESSING_COOLDOWN =
            MEMORY_MODULE_TYPES.register("golden_blessing_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<CherryWolf>> NEAREST_CHERRY_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_cherry_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<CherryWolf>> NEAREST_CHERRY_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_cherry_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> CHERRY_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("cherry_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> CHERRY_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("cherry_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> CHERRY_INJURED_FAMILY =
            MEMORY_MODULE_TYPES.register("cherry_injured_family", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>> CHERRY_BLOSSOM_POS =
            MEMORY_MODULE_TYPES.register("cherry_blossom_pos", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> CHERRY_PETAL_AID_COOLDOWN =
            MEMORY_MODULE_TYPES.register("cherry_petal_aid_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> CHERRY_BURST_COOLDOWN =
            MEMORY_MODULE_TYPES.register("cherry_burst_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> CHERRY_BLOOMING_PATH_COOLDOWN =
            MEMORY_MODULE_TYPES.register("cherry_blooming_path_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> CHERRY_SANCTUARY_COOLDOWN =
            MEMORY_MODULE_TYPES.register("cherry_sanctuary_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<VioletWolf>> NEAREST_VIOLET_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_violet_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<VioletWolf>> NEAREST_VIOLET_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_violet_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> VIOLET_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("violet_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> VIOLET_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("violet_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> VIOLET_INJURED_FAMILY =
            MEMORY_MODULE_TYPES.register("violet_injured_family", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> VIOLET_LEAP_TARGET =
            MEMORY_MODULE_TYPES.register("violet_leap_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> VIOLET_LEAP_COOLDOWN =
            MEMORY_MODULE_TYPES.register("violet_leap_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> VIOLET_AURA_COOLDOWN =
            MEMORY_MODULE_TYPES.register("violet_aura_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> VIOLET_HOWL_COOLDOWN =
            MEMORY_MODULE_TYPES.register("violet_howl_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> VIOLET_BLOOM_COOLDOWN =
            MEMORY_MODULE_TYPES.register("violet_bloom_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<GemWolf>> NEAREST_GEM_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_gem_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<GemWolf>> NEAREST_GEM_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_gem_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> GEM_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("gem_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> GEM_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("gem_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>> GEM_ORE_POS =
            MEMORY_MODULE_TYPES.register("gem_ore_pos", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>> GEM_MARKED_ORE_POS =
            MEMORY_MODULE_TYPES.register("gem_marked_ore_pos", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> GEM_VEIN_MARK_COOLDOWN =
            MEMORY_MODULE_TYPES.register("gem_vein_mark_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> GEM_CRYSTAL_DASH_COOLDOWN =
            MEMORY_MODULE_TYPES.register("gem_crystal_dash_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> GEM_GEO_RESONANCE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("gem_geo_resonance_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<MushroomWolf>> NEAREST_MUSHROOM_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_mushroom_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<MushroomWolf>> NEAREST_MUSHROOM_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_mushroom_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> MUSHROOM_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("mushroom_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> MUSHROOM_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("mushroom_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>> MUSHROOM_FIND_POS =
            MEMORY_MODULE_TYPES.register("mushroom_find_pos", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> MUSHROOM_INJURED_FAMILY =
            MEMORY_MODULE_TYPES.register("mushroom_injured_family", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> MUSHROOM_SPORE_BURST_COOLDOWN =
            MEMORY_MODULE_TYPES.register("mushroom_spore_burst_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BeeWolf>> NEAREST_BEE_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_bee_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BeeWolf>> NEAREST_BEE_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_bee_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BEE_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("bee_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> BEE_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("bee_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>> BEE_HONEY_FIND_POS =
            MEMORY_MODULE_TYPES.register("bee_honey_find_pos", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BEE_POLLINATION_COOLDOWN =
            MEMORY_MODULE_TYPES.register("bee_pollination_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BEE_HASTE_AURA_COOLDOWN =
            MEMORY_MODULE_TYPES.register("bee_haste_aura_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    // Zombie Wolf #21 -------------------------------------------------
    public static final Supplier<MemoryModuleType<ZombieWolf>> NEAREST_ZOMBIE_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_zombie_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<ZombieWolf>> NEAREST_ZOMBIE_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_zombie_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ZOMBIE_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("zombie_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> ZOMBIE_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("zombie_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> ZOMBIE_GRAVE_SCENT_TARGET =
            MEMORY_MODULE_TYPES.register("zombie_grave_scent_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ZOMBIE_DEATHLESS_RUSH_COOLDOWN =
            MEMORY_MODULE_TYPES.register("zombie_deathless_rush_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ZOMBIE_RISE_AGAIN_COOLDOWN =
            MEMORY_MODULE_TYPES.register("zombie_rise_again_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    // Skeleton Wolf #22 -------------------------------------------------
    public static final Supplier<MemoryModuleType<SkeletonWolf>> NEAREST_SKELETON_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_skeleton_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<SkeletonWolf>> NEAREST_SKELETON_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_skeleton_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SKELETON_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("skeleton_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> SKELETON_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("skeleton_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SKELETON_BONE_SHOT_COOLDOWN =
            MEMORY_MODULE_TYPES.register("skeleton_bone_shot_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SKELETON_BONE_RATTLE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("skeleton_bone_rattle_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SKELETON_VOLLEY_COOLDOWN =
            MEMORY_MODULE_TYPES.register("skeleton_volley_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SKELETON_MARROW_GUARD_COOLDOWN =
            MEMORY_MODULE_TYPES.register("skeleton_marrow_guard_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    // Husk Wolf #23 -------------------------------------------------
    public static final Supplier<MemoryModuleType<HuskWolf>> NEAREST_HUSK_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_husk_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<HuskWolf>> NEAREST_HUSK_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_husk_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> HUSK_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("husk_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> HUSK_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("husk_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> HUSK_SANDSTORM_COOLDOWN =
            MEMORY_MODULE_TYPES.register("husk_sandstorm_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> HUSK_DRYING_HOWL_COOLDOWN =
            MEMORY_MODULE_TYPES.register("husk_drying_howl_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    // Drowned Wolf #24 -------------------------------------------------
    public static final Supplier<MemoryModuleType<DrownedWolf>> NEAREST_DROWNED_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_drowned_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<DrownedWolf>> NEAREST_DROWNED_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_drowned_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> DROWNED_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("drowned_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> DROWNED_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("drowned_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> DROWNED_TRIDENT_COOLDOWN =
            MEMORY_MODULE_TYPES.register("drowned_trident_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> DROWNED_DROWN_OTHERS_COOLDOWN =
            MEMORY_MODULE_TYPES.register("drowned_drown_others_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    // Phantom Wolf #25 -------------------------------------------------
    public static final Supplier<MemoryModuleType<PhantomWolf>> NEAREST_PHANTOM_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_phantom_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<PhantomWolf>> NEAREST_PHANTOM_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_phantom_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> PHANTOM_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("phantom_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> PHANTOM_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("phantom_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> PHANTOM_RECON_TARGET =
            MEMORY_MODULE_TYPES.register("phantom_recon_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> PHANTOM_DIVE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("phantom_dive_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> PHANTOM_PHASING_BITE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("phantom_phasing_bite_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> PHANTOM_SCREECH_COOLDOWN =
            MEMORY_MODULE_TYPES.register("phantom_screech_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> PHANTOM_SWEEP_COOLDOWN =
            MEMORY_MODULE_TYPES.register("phantom_sweep_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> PHANTOM_SKY_HUNTER_COOLDOWN =
            MEMORY_MODULE_TYPES.register("phantom_sky_hunter_cooldown", () -> new MemoryModuleType<>(Optional.empty()));

    // Blood Wolf #26 -------------------------------------------------
    public static final Supplier<MemoryModuleType<BloodWolf>> NEAREST_BLOOD_PACKMATE =
            MEMORY_MODULE_TYPES.register(
                    "nearest_blood_packmate",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BloodWolf>> NEAREST_BLOOD_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register(
                    "nearest_blood_adult_packmate",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BLOOD_PACK_SIZE =
            MEMORY_MODULE_TYPES.register(
                    "blood_pack_size",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> BLOOD_PACK_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "blood_pack_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    // End Wolf #27 ---------------------------------------------------
    public static final Supplier<MemoryModuleType<EndWolf>> NEAREST_END_PACKMATE =
            MEMORY_MODULE_TYPES.register(
                    "nearest_end_packmate",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<EndWolf>> NEAREST_END_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register(
                    "nearest_end_adult_packmate",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> END_PACK_SIZE =
            MEMORY_MODULE_TYPES.register(
                    "end_pack_size",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> END_PACK_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "end_pack_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    // Sculk Wolf #28 -------------------------------------------------
    public static final Supplier<MemoryModuleType<SculkWolf>> NEAREST_SCULK_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_sculk_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<SculkWolf>> NEAREST_SCULK_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_sculk_adult_packmate", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> SCULK_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("sculk_pack_size", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> SCULK_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("sculk_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));

    // Infernal Wolf #29 -----------------------------------------------
    public static final Supplier<MemoryModuleType<InfernalWolf>> NEAREST_INFERNAL_PACKMATE =
            MEMORY_MODULE_TYPES.register(
                    "nearest_infernal_packmate",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<InfernalWolf>> NEAREST_INFERNAL_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register(
                    "nearest_infernal_adult_packmate",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> INFERNAL_PACK_SIZE =
            MEMORY_MODULE_TYPES.register(
                    "infernal_pack_size",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> INFERNAL_PACK_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "infernal_pack_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> INFERNAL_AWARENESS_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "infernal_awareness_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>> INFERNAL_LAVA_HAZARD =
            MEMORY_MODULE_TYPES.register(
                    "infernal_lava_hazard",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>> INFERNAL_SAFE_POS =
            MEMORY_MODULE_TYPES.register(
                    "infernal_safe_pos",
                    () -> new MemoryModuleType<>(Optional.empty()));

    // Omen Wolf #30 --------------------------------------------------
    public static final Supplier<MemoryModuleType<OmenWolf>> NEAREST_OMEN_PACKMATE =
            MEMORY_MODULE_TYPES.register(
                    "nearest_omen_packmate",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<OmenWolf>> NEAREST_OMEN_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register(
                    "nearest_omen_adult_packmate",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> OMEN_PACK_SIZE =
            MEMORY_MODULE_TYPES.register(
                    "omen_pack_size",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> OMEN_PACK_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "omen_pack_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> OMEN_DEVELOPING_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "omen_developing_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> OMEN_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "omen_hostile_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> OMEN_RAID_ACTIVE =
            MEMORY_MODULE_TYPES.register(
                    "omen_raid_active",
                    () -> new MemoryModuleType<>(Optional.empty()));

    // Astral Wolf #31 -----------------------------------------------
    public static final Supplier<MemoryModuleType<AstralWolf>> NEAREST_ASTRAL_PACKMATE =
            MEMORY_MODULE_TYPES.register(
                    "nearest_astral_packmate",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<AstralWolf>> NEAREST_ASTRAL_ADULT_PACKMATE =
            MEMORY_MODULE_TYPES.register(
                    "nearest_astral_adult_packmate",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ASTRAL_PACK_SIZE =
            MEMORY_MODULE_TYPES.register(
                    "astral_pack_size",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> ASTRAL_SHARED_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "astral_shared_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> ASTRAL_FAMILY_IN_DANGER =
            MEMORY_MODULE_TYPES.register(
                    "astral_family_in_danger",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ASTRAL_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "astral_hostile_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>> ASTRAL_SAFE_ROUTE_POS =
            MEMORY_MODULE_TYPES.register(
                    "astral_safe_route_pos",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>> ASTRAL_HOME_POS =
            MEMORY_MODULE_TYPES.register(
                    "astral_home_pos",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> DEMON_PRIORITY_TARGET =
            MEMORY_MODULE_TYPES.register("demon_priority_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> DEMON_CLUSTER_TARGET =
            MEMORY_MODULE_TYPES.register("demon_cluster_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> DEMON_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register("demon_hostile_count", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> DEMON_CHARM_THREAT =
            MEMORY_MODULE_TYPES.register("demon_charm_threat", () -> new MemoryModuleType<>(Optional.empty()));

    // Wolf King #38 ---------------------------------------------------
    public static final Supplier<MemoryModuleType<Wolf>> NEAREST_WOLF_KING_ALLY =
            MEMORY_MODULE_TYPES.register(
                    "nearest_wolf_king_ally",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WOLF_KING_ALLY_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_ally_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> WOLF_KING_SHARED_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_shared_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> WOLF_KING_PRIORITY_TARGET =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_priority_target",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> WOLF_KING_OWNER_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_owner_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> WOLF_KING_THREATENED_ALLY =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_threatened_ally",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WOLF_KING_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_hostile_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> WOLF_KING_PACK_SCATTERED =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_pack_scattered",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> WOLF_KING_PACK_OUTNUMBERED =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_pack_outnumbered",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> WOLF_KING_MARKED_TARGET =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_marked_target",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WOLF_KING_ROYAL_COMMAND_COOLDOWN =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_royal_command_cooldown",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WOLF_KING_COMMANDING_HOWL_COOLDOWN =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_commanding_howl_cooldown",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WOLF_KING_IRON_WILL_COOLDOWN =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_iron_will_cooldown",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WOLF_KING_LEADERSHIP_DASH_COOLDOWN =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_leadership_dash_cooldown",
                    () -> new MemoryModuleType<>(Optional.empty()));



    // Alpha-King V2 wild hunt / operational pack memories.
    public static final Supplier<MemoryModuleType<LivingEntity>> WOLF_KING_ALPHA_HUNT_TARGET =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_alpha_hunt_target",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> WOLF_KING_ALPHA_HUNT_ACTIVE =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_alpha_hunt_active",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WOLF_KING_WILD_RECRUIT_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_wild_recruit_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> WOLF_KING_ALPHA_FOLLOWER_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "wolf_king_alpha_follower_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    // Primordial Wolf #39 ---------------------------------------------
    public static final Supplier<MemoryModuleType<LivingEntity>> PRIMORDIAL_PRIORITY_THREAT =
            MEMORY_MODULE_TYPES.register("primordial_priority_threat", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> PRIMORDIAL_SHARED_THREAT =
            MEMORY_MODULE_TYPES.register("primordial_shared_threat", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> PRIMORDIAL_FAMILY_IN_DANGER =
            MEMORY_MODULE_TYPES.register("primordial_family_in_danger", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> PRIMORDIAL_FAMILY_ATTACKER =
            MEMORY_MODULE_TYPES.register("primordial_family_attacker", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> PRIMORDIAL_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register("primordial_hostile_count", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> PRIMORDIAL_PACK_COUNT =
            MEMORY_MODULE_TYPES.register("primordial_pack_count", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Boolean>> PRIMORDIAL_PACK_OUTNUMBERED =
            MEMORY_MODULE_TYPES.register("primordial_pack_outnumbered", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Boolean>> PRIMORDIAL_PRIMAL_STATE_ACTIVE =
            MEMORY_MODULE_TYPES.register("primordial_primal_state_active", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Boolean>> PRIMORDIAL_FIRST_PACK_ACTIVE =
            MEMORY_MODULE_TYPES.register("primordial_first_pack_active", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> PRIMORDIAL_PRIMAL_STATE_COOLDOWN =
            MEMORY_MODULE_TYPES.register("primordial_primal_state_cooldown", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> PRIMORDIAL_FIRST_PACK_COOLDOWN =
            MEMORY_MODULE_TYPES.register("primordial_first_pack_cooldown", () -> new MemoryModuleType<>(Optional.empty()));


    // Creator Wolf — secret #53 / six-star ----------------------------
    public static final Supplier<MemoryModuleType<LivingEntity>> CREATOR_PRIORITY_THREAT =
            MEMORY_MODULE_TYPES.register("creator_priority_threat", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> CREATOR_SHARED_THREAT =
            MEMORY_MODULE_TYPES.register("creator_shared_threat", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> CREATOR_ENDANGERED_FAMILY =
            MEMORY_MODULE_TYPES.register("creator_endangered_family", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> CREATOR_FAMILY_ATTACKER =
            MEMORY_MODULE_TYPES.register("creator_family_attacker", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> CREATOR_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register("creator_hostile_count", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> CREATOR_FAMILY_COUNT =
            MEMORY_MODULE_TYPES.register("creator_family_count", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> CREATOR_BEAM_COOLDOWN =
            MEMORY_MODULE_TYPES.register("creator_beam_cooldown", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> CREATOR_TOUCH_COOLDOWN =
            MEMORY_MODULE_TYPES.register("creator_touch_cooldown", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> CREATOR_INTERVENTION_COOLDOWN =
            MEMORY_MODULE_TYPES.register("creator_intervention_cooldown", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Boolean>> CREATOR_PERFECT_INSTINCT_ACTIVE =
            MEMORY_MODULE_TYPES.register("creator_perfect_instinct_active", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> CREATOR_PERFECT_INSTINCT_COOLDOWN =
            MEMORY_MODULE_TYPES.register("creator_perfect_instinct_cooldown", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Boolean>> CREATOR_WILL_ACTIVE =
            MEMORY_MODULE_TYPES.register("creator_will_active", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> CREATOR_WILL_COOLDOWN =
            MEMORY_MODULE_TYPES.register("creator_will_cooldown", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Boolean>> CREATOR_RECOVERY_ACTIVE =
            MEMORY_MODULE_TYPES.register("creator_recovery_active", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> CREATOR_ADAPTATION_LEVEL =
            MEMORY_MODULE_TYPES.register("creator_adaptation_level", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<MagmaWolf>> NEAREST_MAGMA_PACKMATE =
            MEMORY_MODULE_TYPES.register("nearest_magma_packmate", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> MAGMA_PACK_SIZE =
            MEMORY_MODULE_TYPES.register("magma_pack_size", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> MAGMA_PACK_THREAT =
            MEMORY_MODULE_TYPES.register("magma_pack_threat", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> MAGMA_TACTICAL_THREAT =
            MEMORY_MODULE_TYPES.register("magma_tactical_threat", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<BlockPos>> MAGMA_LAVA_SURFACE =
            MEMORY_MODULE_TYPES.register("magma_lava_surface", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> MAGMA_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register("magma_hostile_count", () -> new MemoryModuleType<>(Optional.empty()));

    // Vampire Wolf — adaptive predator tactical memories.
    public static final Supplier<MemoryModuleType<LivingEntity>> VAMPIRE_FLYING_TARGET =
            MEMORY_MODULE_TYPES.register("vampire_flying_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> VAMPIRE_DRAIN_TARGET =
            MEMORY_MODULE_TYPES.register("vampire_drain_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> VAMPIRE_CHARM_TARGET =
            MEMORY_MODULE_TYPES.register("vampire_charm_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> VAMPIRE_VULNERABLE_FAMILY =
            MEMORY_MODULE_TYPES.register("vampire_vulnerable_family", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> VAMPIRE_CLUSTER_TARGET =
            MEMORY_MODULE_TYPES.register("vampire_cluster_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> VAMPIRE_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register("vampire_hostile_count", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> SPECTRAL_RANGED_TARGET =
            MEMORY_MODULE_TYPES.register("spectral_ranged_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> SPECTRAL_PHASE_TARGET =
            MEMORY_MODULE_TYPES.register("spectral_phase_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> SPECTRAL_VULNERABLE_FAMILY =
            MEMORY_MODULE_TYPES.register("spectral_vulnerable_family", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> SPECTRAL_CLUSTER_TARGET =
            MEMORY_MODULE_TYPES.register("spectral_cluster_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> SPECTRAL_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register("spectral_hostile_count", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> TOXIC_RANGED_TARGET =
            MEMORY_MODULE_TYPES.register("toxic_ranged_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> TOXIC_CLUSTER_TARGET =
            MEMORY_MODULE_TYPES.register("toxic_cluster_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> TOXIC_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register("toxic_hostile_count", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> WAR_PRIMARY_TARGET =
            MEMORY_MODULE_TYPES.register("war_primary_target", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<LivingEntity>> WAR_VULNERABLE_FAMILY =
            MEMORY_MODULE_TYPES.register("war_vulnerable_family", () -> new MemoryModuleType<>(Optional.empty()));
    public static final Supplier<MemoryModuleType<Integer>> WAR_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register("war_hostile_count", () -> new MemoryModuleType<>(Optional.empty()));

// Illager Wolf #46 ----------------------------------------------------

    public static final Supplier<MemoryModuleType<LivingEntity>>
            ILLAGER_PRIMARY_TARGET = MEMORY_MODULE_TYPES.register(
                    "illager_primary_target", () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>>
            ILLAGER_ARMORED_TARGET =
            MEMORY_MODULE_TYPES.register(
                    "illager_armored_target",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>>
            ILLAGER_RAIDER_TARGET =
            MEMORY_MODULE_TYPES.register(
                    "illager_raider_target",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>>
            ILLAGER_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "illager_hostile_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>>
            ANCIENT_PRIMARY_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "ancient_primary_threat",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>>
            ANCIENT_VULNERABLE_FAMILY =
            MEMORY_MODULE_TYPES.register(
                    "ancient_vulnerable_family",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    // #47 Ancient Wolf

    public static final Supplier<MemoryModuleType<BlockPos>>
            ANCIENT_HAZARD_POS =
            MEMORY_MODULE_TYPES.register(
                    "ancient_hazard_pos",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>>
            ANCIENT_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "ancient_hostile_count",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    // #48 Blade Wolf

    public static final Supplier<MemoryModuleType<LivingEntity>>
            BLADE_PRIMARY_TARGET =
            MEMORY_MODULE_TYPES.register(
                    "blade_primary_target",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>>
            BLADE_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "blade_hostile_count",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>>
            BLADE_CLUSTERED_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "blade_clustered_count",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    // Raven Wolf #49 =====================================================

    public static final Supplier<MemoryModuleType<LivingEntity>>
            RAVEN_PRIORITY_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "raven_priority_threat",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>>
            RAVEN_OBSERVED_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "raven_observed_threat",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>>
            RAVEN_REPORTED_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "raven_reported_threat",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    public static final Supplier<MemoryModuleType<BlockPos>>
            RAVEN_LOOT_POS =
            MEMORY_MODULE_TYPES.register(
                    "raven_loot_pos",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>>
            RAVEN_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "raven_hostile_count",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>>
            RAVEN_NEARBY_RAVEN_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "raven_nearby_raven_count",
                    () -> new MemoryModuleType<>(
                            Optional.empty()));

    // --- #50 Command Wolf

    public static final Supplier<MemoryModuleType<LivingEntity>>
            COMMAND_PRIORITY_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "command_priority_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>>
            COMMAND_DANGEROUS_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "command_dangerous_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>>
            COMMAND_VULNERABLE_FAMILY =
            MEMORY_MODULE_TYPES.register(
                    "command_vulnerable_family",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>>
            COMMAND_ISOLATED_FAMILY =
            MEMORY_MODULE_TYPES.register(
                    "command_isolated_family",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>>
            COMMAND_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "command_hostile_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>>
            COMMAND_FAMILY_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "command_family_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    // --- #51 Ash Wolf ----------- //

    public static final Supplier<MemoryModuleType<LivingEntity>> ASH_PRIORITY_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "ash_priority_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> ASH_RANGED_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "ash_ranged_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> ASH_BURNING_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "ash_burning_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> ASH_VULNERABLE_FAMILY =
            MEMORY_MODULE_TYPES.register(
                    "ash_vulnerable_family",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ASH_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "ash_hostile_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ASH_CLUSTER_SIZE =
            MEMORY_MODULE_TYPES.register(
                    "ash_cluster_size",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> ASH_FOCUSING_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "ash_focusing_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    // --- #52 Wither Wolf ----------- //

    /** Persistent vanilla Wither quarry selected by Wither Awareness. */
    public static final Supplier<MemoryModuleType<LivingEntity>> WITHER_QUARRY =
            MEMORY_MODULE_TYPES.register(
                    "wither_quarry",
                    () -> new MemoryModuleType<>(Optional.empty()));

    /** 0 = none, 1 = spawning/invulnerable, 2 = ranged-accessible, 3 = armored melee phase. */
    public static final Supplier<MemoryModuleType<Integer>> WITHER_BOSS_PHASE =
            MEMORY_MODULE_TYPES.register(
                    "wither_boss_phase",
                    () -> new MemoryModuleType<>(Optional.empty()));

    /** Best ordinary hostile for Withering Projectile focus. */
    public static final Supplier<MemoryModuleType<LivingEntity>> WITHER_PRIORITY_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "wither_priority_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    /** Family member currently in the most urgent Wither-related danger. */
    public static final Supplier<MemoryModuleType<LivingEntity>> WITHER_VULNERABLE_FAMILY =
            MEMORY_MODULE_TYPES.register(
                    "wither_vulnerable_family",
                    () -> new MemoryModuleType<>(Optional.empty()));

    /** Number of valid ordinary hostiles inside Wither Wolf's artillery awareness. */
    public static final Supplier<MemoryModuleType<Integer>> WITHER_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "wither_hostile_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    /** Number of nearby Wither skulls currently threatening the family. */
    public static final Supplier<MemoryModuleType<Integer>> WITHER_INCOMING_SKULL_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "wither_incoming_skull_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    // --- #53 Blaze Wolf ----------- //

    public static final Supplier<MemoryModuleType<LivingEntity>> BLAZE_PRIORITY_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "blaze_priority_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> BLAZE_RANGED_TARGET =
            MEMORY_MODULE_TYPES.register(
                    "blaze_ranged_target",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> BLAZE_CLOSE_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "blaze_close_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> BLAZE_FRONTLINE_LOCKED_TARGET =
            MEMORY_MODULE_TYPES.register(
                    "blaze_frontline_locked_target",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BLAZE_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "blaze_hostile_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BLAZE_CLUSTER_SIZE =
            MEMORY_MODULE_TYPES.register(
                    "blaze_cluster_size",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BLAZE_FIRE_IMMUNE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "blaze_fire_immune_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> BLAZE_INCOMING_FIREBALL_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "blaze_incoming_fireball_count",
                    () -> new MemoryModuleType<>(Optional.empty()));



    // --- Holiday #1 Halloween Wolf ----------- //

    public static final Supplier<MemoryModuleType<LivingEntity>> HALLOWEEN_PRIORITY_THREAT =
            MEMORY_MODULE_TYPES.register(
                    "halloween_priority_threat",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<LivingEntity>> HALLOWEEN_VULNERABLE_FAMILY =
            MEMORY_MODULE_TYPES.register(
                    "halloween_vulnerable_family",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Integer>> HALLOWEEN_HOSTILE_COUNT =
            MEMORY_MODULE_TYPES.register(
                    "halloween_hostile_count",
                    () -> new MemoryModuleType<>(Optional.empty()));

    public static final Supplier<MemoryModuleType<Boolean>> HALLOWEEN_LOW_LIGHT =
            MEMORY_MODULE_TYPES.register(
                    "halloween_low_light",
                    () -> new MemoryModuleType<>(Optional.empty()));

    private ModMemoryModuleTypes() {
    }
}