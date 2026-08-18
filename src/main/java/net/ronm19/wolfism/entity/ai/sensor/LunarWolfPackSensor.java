package net.ronm19.wolfism.entity.ai.sensor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.ronm19.wolfism.entity.custom.LunarWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/** Social awareness plus registration of Lunar ability-state memories. */
public final class LunarWolfPackSensor extends Sensor<LunarWolf> {
    public static final double PACK_SCAN_RADIUS = 30.0D;

    @Override
    protected void doTick(ServerLevel level, LunarWolf wolf) {
        Brain<LunarWolf> brain = wolf.getBrain();
        List<LunarWolf> packmates = level.getEntitiesOfClass(
                LunarWolf.class,
                wolf.getBoundingBox().inflate(PACK_SCAN_RADIUS),
                candidate -> candidate != wolf && candidate.isAlive() && wolf.isLunarPackmate(candidate));

        LunarWolf nearest = null;
        LunarWolf nearestAdult = null;
        double nearestD = Double.MAX_VALUE;
        double nearestAdultD = Double.MAX_VALUE;
        LivingEntity sharedThreat = null;
        List<LunarWolf> adults = new ArrayList<>();
        if (!wolf.isBaby()) adults.add(wolf);

        for (LunarWolf mate : packmates) {
            double d = wolf.distanceToSqr(mate);
            if (d < nearestD) { nearestD = d; nearest = mate; }
            if (!mate.isBaby()) {
                adults.add(mate);
                if (d < nearestAdultD) { nearestAdultD = d; nearestAdult = mate; }
            }
            LivingEntity t = mate.getTarget();
            if (t != null && t.isAlive() && wolf.isValidLunarCombatTarget(t) && sharedThreat == null) sharedThreat = t;
        }

        LunarWolf leader = adults.stream().min(Comparator.comparing(LunarWolf::getUUID)).orElse(null);
        brain.setMemory(ModMemoryModuleTypes.LUNAR_PACK_SIZE.get(), packmates.size() + 1);
        brain.setMemory(ModMemoryModuleTypes.LUNAR_ADULT_PACK_SIZE.get(), adults.size());
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_LUNAR_PACKMATE.get(), nearest);
        setOrErase(brain, ModMemoryModuleTypes.NEAREST_LUNAR_ADULT_PACKMATE.get(), nearestAdult);
        setOrErase(brain, ModMemoryModuleTypes.LUNAR_PACK_LEADER.get(), leader);
        setOrErase(brain, ModMemoryModuleTypes.LUNAR_PACK_THREAT.get(), sharedThreat);
    }

    private static <T> void setOrErase(Brain<LunarWolf> brain, MemoryModuleType<T> memory, T value) {
        if (value != null) brain.setMemory(memory, value); else brain.eraseMemory(memory);
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(
                ModMemoryModuleTypes.NEAREST_LUNAR_PACKMATE.get(),
                ModMemoryModuleTypes.NEAREST_LUNAR_ADULT_PACKMATE.get(),
                ModMemoryModuleTypes.LUNAR_PACK_LEADER.get(),
                ModMemoryModuleTypes.LUNAR_PACK_SIZE.get(),
                ModMemoryModuleTypes.LUNAR_ADULT_PACK_SIZE.get(),
                ModMemoryModuleTypes.LUNAR_PACK_THREAT.get(),
                ModMemoryModuleTypes.LUNAR_BEAM_TARGET.get(),
                ModMemoryModuleTypes.LUNAR_BEAM_COOLDOWN.get(),
                ModMemoryModuleTypes.LUNAR_SHIELD_COOLDOWN.get(),
                ModMemoryModuleTypes.LUNAR_DREAMSTEP_COOLDOWN.get(),
                ModMemoryModuleTypes.LUNAR_HOWL_COOLDOWN.get());
    }
}
