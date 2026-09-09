package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.ronm19.wolfism.entity.custom.CreatorWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Family geometry and shared-combat sensor for Creator Wolf. */
public final class CreatorFamilySensor extends Sensor<CreatorWolf> {
    public CreatorFamilySensor() { super(4); }

    @Override
    protected void doTick(ServerLevel level, CreatorWolf wolf) {
        List<Wolf> family = wolf.findFamilyWolves(level, CreatorWolf.FAMILY_RADIUS);

        LivingEntity sharedThreat = family.stream()
                .map(Wolf::getTarget)
                .filter(wolf::isPotentialCreatorThreat)
                .max(Comparator.comparingDouble(wolf::scoreCreatorThreat))
                .orElse(null);

        Brain<CreatorWolf> brain = wolf.getBrain();
        brain.setMemory(ModMemoryModuleTypes.CREATOR_FAMILY_COUNT.get(), Math.max(1, family.size()));
        if (sharedThreat != null) brain.setMemory(ModMemoryModuleTypes.CREATOR_SHARED_THREAT.get(), sharedThreat);
        else brain.eraseMemory(ModMemoryModuleTypes.CREATOR_SHARED_THREAT.get());
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.CREATOR_FAMILY_COUNT.get(),
                ModMemoryModuleTypes.CREATOR_SHARED_THREAT.get(),
                ModMemoryModuleTypes.CREATOR_BEAM_COOLDOWN.get(),
                ModMemoryModuleTypes.CREATOR_TOUCH_COOLDOWN.get(),
                ModMemoryModuleTypes.CREATOR_INTERVENTION_COOLDOWN.get(),
                ModMemoryModuleTypes.CREATOR_PERFECT_INSTINCT_ACTIVE.get(),
                ModMemoryModuleTypes.CREATOR_PERFECT_INSTINCT_COOLDOWN.get(),
                ModMemoryModuleTypes.CREATOR_WILL_ACTIVE.get(),
                ModMemoryModuleTypes.CREATOR_WILL_COOLDOWN.get(),
                ModMemoryModuleTypes.CREATOR_RECOVERY_ACTIVE.get(),
                ModMemoryModuleTypes.CREATOR_ADAPTATION_LEVEL.get());
    }
}
