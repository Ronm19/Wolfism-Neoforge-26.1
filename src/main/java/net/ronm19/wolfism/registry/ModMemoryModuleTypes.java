package net.ronm19.wolfism.registry;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.entity.custom.ArcticWolf;
import net.ronm19.wolfism.entity.custom.TimberWolf;

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

    private ModMemoryModuleTypes() {
    }
}
