package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.AngelWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Fast family-health sensor for Angel Wolf's healer/support Brain.
 */
public final class AngelWolfFamilySensor extends Sensor<AngelWolf> {
    public AngelWolfFamilySensor() {
        super(5);
    }

    @Override
    protected void doTick(ServerLevel level, AngelWolf wolf) {
        Brain<AngelWolf> brain = wolf.getBrain();

        List<LivingEntity> family = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(AngelWolf.SUPPORT_SEARCH_RADIUS),
                wolf::isAngelFamilyMember);

        LivingEntity injured = family.stream()
                .filter(member -> member.getHealth() < member.getMaxHealth() * 0.90F)
                .min(Comparator.comparingDouble(wolf::healthRatio))
                .orElse(null);

        LivingEntity critical = family.stream()
                .filter(member -> member.getHealth() <= member.getMaxHealth() * 0.45F)
                .min(Comparator.comparingDouble(wolf::healthRatio))
                .orElse(null);

        int injuredCount = (int) family.stream()
                .filter(member -> member.getHealth() < member.getMaxHealth() * 0.78F)
                .count();

        brain.setMemory(ModMemoryModuleTypes.ANGEL_FAMILY_SIZE.get(), family.size());
        brain.setMemory(ModMemoryModuleTypes.ANGEL_INJURED_COUNT.get(), injuredCount);
        setOrErase(brain, ModMemoryModuleTypes.ANGEL_INJURED_FAMILY.get(), injured);
        setOrErase(brain, ModMemoryModuleTypes.ANGEL_CRITICAL_FAMILY.get(), critical);
    }

    private static <T> void setOrErase(
            Brain<AngelWolf> brain,
            MemoryModuleType<T> memory,
            T value) {
        if (value != null) {
            brain.setMemory(memory, value);
        } else {
            brain.eraseMemory(memory);
        }
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.ANGEL_FAMILY_SIZE.get(),
                ModMemoryModuleTypes.ANGEL_INJURED_COUNT.get(),
                ModMemoryModuleTypes.ANGEL_INJURED_FAMILY.get(),
                ModMemoryModuleTypes.ANGEL_CRITICAL_FAMILY.get());
    }
}
