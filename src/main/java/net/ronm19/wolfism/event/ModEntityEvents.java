package net.ronm19.wolfism.event;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.ronm19.wolfism.entity.custom.ArcticWolf;
import net.ronm19.wolfism.entity.custom.TimberWolf;
import net.ronm19.wolfism.registry.ModEntities;

public final class ModEntityEvents {
    private ModEntityEvents() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.TIMBER_WOLF.get(), Wolf.createAttributes().build());
        event.put(ModEntities.ARCTIC_WOLF.get(), Wolf.createAttributes().build());
    }

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                ModEntities.TIMBER_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                TimberWolf::checkTimberWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(
                ModEntities.ARCTIC_WOLF.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ArcticWolf::checkArcticWolfSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
