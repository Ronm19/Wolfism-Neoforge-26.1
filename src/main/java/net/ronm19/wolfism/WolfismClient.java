package net.ronm19.wolfism;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.ronm19.wolfism.client.renderer.ArcticWolfRenderer;
import net.ronm19.wolfism.client.renderer.TimberWolfRenderer;
import net.ronm19.wolfism.registry.ModEntities;

@EventBusSubscriber(modid = Wolfism.MOD_ID, value = Dist.CLIENT)
public final class WolfismClient {
    private WolfismClient() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TIMBER_WOLF.get(), TimberWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.ARCTIC_WOLF.get(), ArcticWolfRenderer ::new);
    }
}
