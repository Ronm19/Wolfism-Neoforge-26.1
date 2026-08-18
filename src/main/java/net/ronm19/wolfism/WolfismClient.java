package net.ronm19.wolfism;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.ronm19.wolfism.client.renderer.ArcticWolfRenderer;
import net.ronm19.wolfism.client.renderer.BlackWolfRenderer;
import net.ronm19.wolfism.client.renderer.SandWolfRenderer;
import net.ronm19.wolfism.client.renderer.DireWolfRenderer;
import net.ronm19.wolfism.client.renderer.FireWolfRenderer;
import net.ronm19.wolfism.client.renderer.FrostWolfRenderer;
import net.ronm19.wolfism.client.renderer.StormWolfRenderer;
import net.ronm19.wolfism.client.renderer.TimberWolfRenderer;
import net.ronm19.wolfism.client.renderer.WaterWolfRenderer;
import net.ronm19.wolfism.client.renderer.EarthWolfRenderer;
import net.ronm19.wolfism.client.renderer.SolarWolfRenderer;
import net.ronm19.wolfism.client.renderer.LunarWolfRenderer;
import net.ronm19.wolfism.client.model.WaterAdultWolfModel;
import net.ronm19.wolfism.client.model.WaterBabyWolfModel;
import net.ronm19.wolfism.client.model.SolarAdultWolfModel;
import net.ronm19.wolfism.client.model.SolarBabyWolfModel;
import net.ronm19.wolfism.client.model.LunarAdultWolfModel;
import net.ronm19.wolfism.client.model.LunarBabyWolfModel;
import net.ronm19.wolfism.registry.ModEntities;

@EventBusSubscriber(modid = Wolfism.MOD_ID, value = Dist.CLIENT)
public final class WolfismClient {
    private WolfismClient() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TIMBER_WOLF.get(), TimberWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCTIC_WOLF.get(), ArcticWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.BLACK_WOLF.get(), BlackWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.SAND_WOLF.get(), SandWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.DIRE_WOLF.get(), DireWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.FIRE_WOLF.get(), FireWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.FROST_WOLF.get(), FrostWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.STORM_WOLF.get(), StormWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.WATER_WOLF.get(), WaterWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.EARTH_WOLF.get(), EarthWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.SOLAR_WOLF.get(), SolarWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.LUNAR_WOLF.get(), LunarWolfRenderer::new);
    }
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WaterAdultWolfModel.LAYER_LOCATION, WaterAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(WaterBabyWolfModel.LAYER_LOCATION, WaterBabyWolfModel::createWaterBodyLayer);
        event.registerLayerDefinition(SolarAdultWolfModel.LAYER_LOCATION, SolarAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(SolarBabyWolfModel.LAYER_LOCATION, SolarBabyWolfModel::createSolarBodyLayer);
        event.registerLayerDefinition(LunarAdultWolfModel.LAYER_LOCATION, LunarAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(LunarBabyWolfModel.LAYER_LOCATION, LunarBabyWolfModel::createLunarBodyLayer);
    }
}
