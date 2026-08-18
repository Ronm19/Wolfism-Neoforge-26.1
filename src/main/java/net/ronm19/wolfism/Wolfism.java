package net.ronm19.wolfism;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.ronm19.wolfism.datagen.ModDataGenerators;
import net.ronm19.wolfism.event.ModEntityEvents;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModItems;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tab.ModCreativeModeTabs;
import org.slf4j.Logger;

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

        modEventBus.addListener(ModEntityEvents::registerAttributes);
        modEventBus.addListener(ModEntityEvents::registerSpawnPlacements);
        modEventBus.addListener(ModDataGenerators::gatherData);
    }
}
