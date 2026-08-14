package net.ronm19.wolfism;

import net.ronm19.wolfism.datagen.ModDataGenerators;
import net.ronm19.wolfism.event.ModEntityEvents;
import net.ronm19.wolfism.registry.ModEntities;
import net.ronm19.wolfism.registry.ModItems;
import net.ronm19.wolfism.registry.ModMemoryModuleTypes;
import net.ronm19.wolfism.registry.ModSensorTypes;
import net.ronm19.wolfism.tab.ModCreativeModeTabs;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Wolfism.MOD_ID)
public class Wolfism {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "wolfism";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public Wolfism(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        ModMemoryModuleTypes.MEMORY_MODULE_TYPES.register(modEventBus);
        ModSensorTypes.SENSOR_TYPES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeModeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(ModEntityEvents::registerAttributes);
        modEventBus.addListener(ModEntityEvents::registerSpawnPlacements);
        modEventBus.addListener(ModDataGenerators::gatherData);

        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);
        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
    }
}
