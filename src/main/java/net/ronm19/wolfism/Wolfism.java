package net.ronm19.wolfism;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.ronm19.wolfism.datagen.ModDataGenerators;
import net.ronm19.wolfism.event.ModEntityEvents;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModItems;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tab.ModCreativeModeTabs;
import net.ronm19.wolfism.worldgen.region.SculkPlainsRegion;
import org.slf4j.Logger;
import terrablender.api.Regions;

@Mod(Wolfism.MOD_ID)
public final class Wolfism {
    public static final String MOD_ID = "wolfism";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Wolfism(IEventBus modEventBus, ModContainer modContainer) {
        ModMemoryModuleTypes.MEMORY_MODULE_TYPES.register(modEventBus);
        ModSensorTypes.SENSOR_TYPES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        modEventBus.addListener(ModEntityEvents::registerAttributes);
        modEventBus.addListener(ModEntityEvents::registerSpawnPlacements);
        modEventBus.addListener(ModDataGenerators::gatherData);
    }

    private void commonSetup(
            FMLCommonSetupEvent event
    ) {

        event.enqueueWork(() -> {

            Regions.register(
                    new SculkPlainsRegion(
                            Identifier.fromNamespaceAndPath(
                                    MOD_ID,
                                    "sculk_plains_region"
                            ),
                            /*
                             * Very low region weight.
                             *
                             * We want uncommon surface pockets,
                             * not giant Sculk continents.
                             */
                            1
                    )
            );
        });
    }
}
