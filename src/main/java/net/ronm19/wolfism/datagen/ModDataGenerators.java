package net.ronm19.wolfism.datagen;

import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class ModDataGenerators {
    public static void gatherData(GatherDataEvent.Client event) {
        // Datapack registry objects must be created before providers that may use the updated lookup.
        event.createDatapackRegistryObjects(ModBiomeModifierProvider.BUILDER);

        event.createProvider(ModBiomeTagProvider::new);
        event.createProvider(ModBlockTagProvider::new);
        event.createProvider(ModModelProvider::new);
        event.createProvider(ModSoundDefinitionsProvider::new);
    }

    private ModDataGenerators() {
    }
}
