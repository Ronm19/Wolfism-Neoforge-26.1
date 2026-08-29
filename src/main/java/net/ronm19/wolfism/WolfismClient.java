package net.ronm19.wolfism;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.ronm19.wolfism.client.model.*;
import net.ronm19.wolfism.client.renderer.*;
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
        event.registerEntityRenderer(ModEntities.SPIRIT_WOLF.get(), SpiritWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.SHADOW_WOLF.get(), ShadowWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.GOLDEN_WOLF.get(), GoldenWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.CHERRY_WOLF.get(), CherryWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.VIOLET_WOLF.get(), VioletWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.GEM_WOLF.get(), GemWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.MUSHROOM_WOLF.get(), MushroomWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.BEE_WOLF.get(), BeeWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.ZOMBIE_WOLF.get(), ZombieWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.SKELETON_WOLF.get(), SkeletonWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.HUSK_WOLF.get(), HuskWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.DROWNED_WOLF.get(), DrownedWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.PHANTOM_WOLF.get(), PhantomWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.BLOOD_WOLF.get(), BloodWolfRenderer ::new);
        event.registerEntityRenderer(ModEntities.END_WOLF.get(), EndWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.SCULK_WOLF.get(), SculkWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.INFERNAL_WOLF.get(), InfernalWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.OMEN_WOLF.get(), OmenWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.ASTRAL_WOLF.get(), AstralWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.ANGEL_WOLF.get(), AngelWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.DEMON_WOLF.get(), DemonWolfRenderer::new);
        event.registerEntityRenderer(ModEntities.GRAVE_WOLF.get(), GraveWolfRenderer::new);


        event.registerEntityRenderer(ModEntities.BONE_SHARD.get(), BoneShardRenderer::new);
    }
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(WaterAdultWolfModel.LAYER_LOCATION, WaterAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(WaterBabyWolfModel.LAYER_LOCATION, WaterBabyWolfModel::createWaterBodyLayer);
        event.registerLayerDefinition(SolarAdultWolfModel.LAYER_LOCATION, SolarAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(SolarBabyWolfModel.LAYER_LOCATION, SolarBabyWolfModel::createSolarBodyLayer);
        event.registerLayerDefinition(LunarAdultWolfModel.LAYER_LOCATION, LunarAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(LunarBabyWolfModel.LAYER_LOCATION, LunarBabyWolfModel::createLunarBodyLayer);
        event.registerLayerDefinition(SpiritAdultWolfModel.LAYER_LOCATION, SpiritAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(SpiritBabyWolfModel.LAYER_LOCATION, SpiritBabyWolfModel::createSpiritBodyLayer);
        event.registerLayerDefinition(ShadowAdultWolfModel.LAYER_LOCATION, ShadowAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(ShadowBabyWolfModel.LAYER_LOCATION, ShadowBabyWolfModel::createShadowBodyLayer);
        event.registerLayerDefinition(GoldenAdultWolfModel.LAYER_LOCATION, GoldenAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(GoldenBabyWolfModel.LAYER_LOCATION, GoldenBabyWolfModel::createGoldenBodyLayer);
        event.registerLayerDefinition(CherryAdultWolfModel.LAYER_LOCATION, CherryAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(CherryBabyWolfModel.LAYER_LOCATION, CherryBabyWolfModel::createCherryBodyLayer);
        event.registerLayerDefinition(MushroomAdultWolfModel.LAYER_LOCATION, MushroomAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(MushroomBabyWolfModel.LAYER_LOCATION, MushroomBabyWolfModel::createMushroomBodyLayer);
        event.registerLayerDefinition(BeeAdultWolfModel.LAYER_LOCATION, BeeAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(BeeBabyWolfModel.LAYER_LOCATION, BeeBabyWolfModel::createBeeBodyLayer);
        event.registerLayerDefinition(PhantomWolfModel.LAYER_LOCATION, PhantomWolfModel::createBodyLayer);
        event.registerLayerDefinition(PhantomWolfBabyModel.LAYER_LOCATION, PhantomWolfBabyModel::createBodyLayer);
        event.registerLayerDefinition(AngelAdultWolfModel.LAYER_LOCATION, AngelAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(AngelBabyWolfModel.LAYER_LOCATION, AngelBabyWolfModel::createBodyLayer);
        event.registerLayerDefinition(SculkAdultWolfModel.LAYER_LOCATION, SculkAdultWolfModel::createBodyLayer);
        event.registerLayerDefinition(SculkBabyWolfModel.LAYER_LOCATION, SculkBabyWolfModel ::createBodyLayer);
        event.registerLayerDefinition(DemonWolfModel.LAYER_LOCATION, DemonWolfModel ::createBodyLayer);
        event.registerLayerDefinition(DemonWolfBabyModel.LAYER_LOCATION, DemonWolfBabyModel ::createBodyLayer);
        event.registerLayerDefinition(WolfismAdultCollarModel.LAYER_LOCATION, WolfismAdultCollarModel::createBodyLayer);
        event.registerLayerDefinition(WolfismBabyCollarModel.LAYER_LOCATION, WolfismBabyCollarModel::createBodyLayer);

    }
}
