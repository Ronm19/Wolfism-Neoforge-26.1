package net.ronm19.wolfism.entity.ai.sensor;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;

import net.ronm19.wolfism.entity.AbstractWolfismWolf;
import net.ronm19.wolfism.entity.custom.CommandWolf;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;

/**
 * Command Wolf's information network.
 *
 * Awareness may observe farther than ordinary physical aggro. It does not give
 * permission for marathon pursuit; AbstractWolfismWolf still owns target range.
 */
public final class CommandWolfAwarenessSensor extends Sensor<CommandWolf> {

    @Override
    protected void doTick(ServerLevel level, CommandWolf wolf) {
        double radius = CommandWolf.COMMAND_AWARENESS_RADIUS;

        List<LivingEntity> threats = level.getEntitiesOfClass(
                LivingEntity.class,
                wolf.getBoundingBox().inflate(radius),
                wolf::isValidCommandThreat);

        LivingEntity priority = threats.stream()
                .min(Comparator.comparingDouble(
                        wolf::commandThreatScore))
                .orElse(null);

        LivingEntity dangerous = threats.stream()
                .filter(wolf::isDangerousCommandThreat)
                .min(Comparator.comparingDouble(
                        wolf::commandThreatScore))
                .orElse(null);

        LivingEntity owner = wolf.getOwner();

        List<AbstractWolfismWolf> wolfFamily =
                wolf.getOperationalFamily(
                        level,
                        CommandWolf.COMMAND_FAMILY_RADIUS);

        LivingEntity vulnerable = wolfFamily.stream()
                .filter(member -> wolf.distanceToSqr(member)
                        <= CommandWolf.COMMAND_LOCAL_FAMILY_RADIUS
                        * CommandWolf.COMMAND_LOCAL_FAMILY_RADIUS)
                .map(member -> (LivingEntity) member)
                .filter(wolf::isVulnerableCommandFamilyMember)
                .min(Comparator.comparingDouble(
                        member -> member.getHealth()
                                / Math.max(1.0F, member.getMaxHealth())))
                .orElse(null);

        if (owner != null
                && owner.isAlive()
                && wolf.distanceToSqr(owner)
                <= CommandWolf.COMMAND_LOCAL_FAMILY_RADIUS
                * CommandWolf.COMMAND_LOCAL_FAMILY_RADIUS
                && wolf.isVulnerableCommandFamilyMember(owner)) {

            if (vulnerable == null
                    || owner.getHealth()
                    / Math.max(1.0F, owner.getMaxHealth())
                    < vulnerable.getHealth()
                    / Math.max(1.0F, vulnerable.getMaxHealth())) {
                vulnerable = owner;
            }
        }

        LivingEntity isolated = wolfFamily.stream()
                .filter(member -> member != wolf)
                .map(member -> (LivingEntity) member)
                .filter(wolf::isIsolatedCommandFamilyMember)
                .max(Comparator.comparingDouble(
                        member -> owner == null
                                ? 0.0D
                                : member.distanceToSqr(owner)))
                .orElse(null);

        Brain<CommandWolf> brain = wolf.getBrain();

        setOrErase(
                brain,
                ModMemoryModuleTypes.COMMAND_PRIORITY_THREAT.get(),
                priority);
        setOrErase(
                brain,
                ModMemoryModuleTypes.COMMAND_DANGEROUS_THREAT.get(),
                dangerous);
        setOrErase(
                brain,
                ModMemoryModuleTypes.COMMAND_VULNERABLE_FAMILY.get(),
                vulnerable);
        setOrErase(
                brain,
                ModMemoryModuleTypes.COMMAND_ISOLATED_FAMILY.get(),
                isolated);

        brain.setMemory(
                ModMemoryModuleTypes.COMMAND_HOSTILE_COUNT.get(),
                threats.size());

        int commandableFamily = (int) wolfFamily.stream()
                .filter(member -> member != wolf)
                .filter(member -> wolf.distanceToSqr(member)
                        <= CommandWolf.COMMAND_LOCAL_FAMILY_RADIUS
                        * CommandWolf.COMMAND_LOCAL_FAMILY_RADIUS)
                .count();

        brain.setMemory(
                ModMemoryModuleTypes.COMMAND_FAMILY_COUNT.get(),
                commandableFamily);
    }

    private static <T> void setOrErase(
            Brain<CommandWolf> brain,
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
                ModMemoryModuleTypes.COMMAND_PRIORITY_THREAT.get(),
                ModMemoryModuleTypes.COMMAND_DANGEROUS_THREAT.get(),
                ModMemoryModuleTypes.COMMAND_VULNERABLE_FAMILY.get(),
                ModMemoryModuleTypes.COMMAND_ISOLATED_FAMILY.get(),
                ModMemoryModuleTypes.COMMAND_HOSTILE_COUNT.get(),
                ModMemoryModuleTypes.COMMAND_FAMILY_COUNT.get());
    }
}
