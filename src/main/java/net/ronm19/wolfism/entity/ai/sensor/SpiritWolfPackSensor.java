package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.SpiritWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Family, injury and supernatural awareness for Spirit Wolf. */
public final class SpiritWolfPackSensor extends Sensor<SpiritWolf> {
    public static final double PACK_SCAN_RADIUS = 32.0D;
    public static final double FAMILY_SCAN_RADIUS_DAY = 16.0D;
    public static final double FAMILY_SCAN_RADIUS_NIGHT = 22.0D;
    public static final double SUPERNATURAL_SCAN_RADIUS_DAY = 18.0D;
    public static final double SUPERNATURAL_SCAN_RADIUS_NIGHT = 30.0D;

    @Override
    protected void doTick(ServerLevel level, SpiritWolf wolf) {
        Brain<SpiritWolf> brain = wolf.getBrain();
        List<SpiritWolf> pack = level.getEntitiesOfClass(SpiritWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                other -> other != wolf && other.isAlive() && wolf.isSpiritPackmate(other));

        SpiritWolf nearest = pack.stream().min(Comparator.comparingDouble(wolf::distanceToSqr)).orElse(null);
        SpiritWolf nearestAdult = pack.stream().filter(w -> !w.isBaby())
                .min(Comparator.comparingDouble(wolf::distanceToSqr)).orElse(null);
        LivingEntity sharedThreat = pack.stream().map(SpiritWolf::getTarget).filter(t -> t != null && t.isAlive())
                .filter(wolf::isValidSpiritCombatTarget).findFirst().orElse(null);

        double familyRadius = level.isDarkOutside() ? FAMILY_SCAN_RADIUS_NIGHT : FAMILY_SCAN_RADIUS_DAY;
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                wolf.getBoundingBox().inflate(familyRadius), LivingEntity::isAlive);
        LivingEntity injured = nearby.stream().filter(wolf::isSpiritFamilyMember)
                .filter(e -> e.getHealth() < e.getMaxHealth() * 0.82F)
                .min(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth())).orElse(null);
        LivingEntity critical = nearby.stream().filter(wolf::isSpiritFamilyMember)
                .filter(e -> e.getHealth() <= e.getMaxHealth() * 0.32F)
                .min(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth())).orElse(null);
        double supernaturalRadius = level.isDarkOutside() ? SUPERNATURAL_SCAN_RADIUS_NIGHT : SUPERNATURAL_SCAN_RADIUS_DAY;
        LivingEntity supernatural = level.getEntitiesOfClass(LivingEntity.class,
                        wolf.getBoundingBox().inflate(supernaturalRadius), LivingEntity::isAlive).stream()
                .filter(wolf::isSupernaturalThreat)
                .min(Comparator.comparingDouble(wolf::distanceToSqr)).orElse(null);

        brain.setMemory(ModMemoryModuleTypes.SPIRIT_PACK_SIZE.get(), pack.size() + 1);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SPIRIT_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_SPIRIT_ADULT_PACKMATE.get(), nearestAdult);
        setOrErase(brain, ModMemoryModuleTypes.SPIRIT_PACK_THREAT.get(), sharedThreat);
        setOrErase(brain, ModMemoryModuleTypes.SPIRIT_INJURED_FAMILY.get(), injured);
        setOrErase(brain, ModMemoryModuleTypes.SPIRIT_CRITICAL_FAMILY.get(), critical);
        setOrErase(brain, ModMemoryModuleTypes.SPIRIT_SUPERNATURAL_THREAT.get(), supernatural);
    }

    private static <T> void setOrErase(Brain<SpiritWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) brain.setMemory(memory, value); else brain.eraseMemory(memory);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_SPIRIT_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_SPIRIT_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.SPIRIT_PACK_SIZE.get(),
                ModMemoryModuleTypes.SPIRIT_PACK_THREAT.get(),
                ModMemoryModuleTypes.SPIRIT_INJURED_FAMILY.get(),
                ModMemoryModuleTypes.SPIRIT_CRITICAL_FAMILY.get(),
                ModMemoryModuleTypes.SPIRIT_SUPERNATURAL_THREAT.get(),
                ModMemoryModuleTypes.SPIRIT_MEND_COOLDOWN.get(),
                ModMemoryModuleTypes.SPIRIT_SOUL_GUARD_COOLDOWN.get(),
                ModMemoryModuleTypes.SPIRIT_GUARDIAN_COOLDOWN.get());
    }
}
