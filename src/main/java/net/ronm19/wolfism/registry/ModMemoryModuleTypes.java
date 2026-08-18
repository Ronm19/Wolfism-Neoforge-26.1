package net.ronm19.wolfism.registry;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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

    private ModMemoryModuleTypes() {
    }
}
