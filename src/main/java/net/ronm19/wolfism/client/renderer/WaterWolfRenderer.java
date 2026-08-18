package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.layers.WolfCollarLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.model.WaterAdultWolfModel;
import net.ronm19.wolfism.client.model.WaterBabyWolfModel;
import net.ronm19.wolfism.client.renderer.state.WaterWolfRenderState;
import net.ronm19.wolfism.entity.custom.WaterWolf;

/**
 * Water Wolf renderer with dedicated adult/baby swim models and a custom
 * WolfRenderState subclass.
 *
 * <p>The renderer deliberately keeps {@link WolfRenderState} as its declared
 * state type. That makes it directly compatible with vanilla wolf collar/armor
 * layers and with {@link WolfModel}, while {@link #createRenderState()} returns
 * the richer {@link WaterWolfRenderState} at runtime. The custom models inspect
 * that subtype when they apply the aquatic animation.</p>
 */
public final class WaterWolfRenderer extends AgeableMobRenderer<WaterWolf, WolfRenderState, WolfModel> {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/water_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/water_wolf_baby.png");

    /** Reuses Mojang's normal Wolf -> WolfRenderState extraction logic. */
    private final WolfRenderer vanillaStateExtractor;

    public WaterWolfRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new WaterAdultWolfModel(context.bakeLayer(WaterAdultWolfModel.LAYER_LOCATION)),
                new WaterBabyWolfModel(context.bakeLayer(WaterBabyWolfModel.LAYER_LOCATION)),
                0.5F);

        this.vanillaStateExtractor = new WolfRenderer(context);

        // Because the declared renderer state is still WolfRenderState, these
        // remain ordinary type-safe vanilla layers: no copied collar/armor code
        // and no generic bridge casts are needed.
        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new WolfCollarLayer(this));
    }

    @Override
    public WaterWolfRenderState createRenderState() {
        return new WaterWolfRenderState();
    }

    @Override
    public void extractRenderState(WaterWolf entity, WolfRenderState baseState, float partialTick) {
        // Populate all ordinary wolf state first: baby/sit/angry state, shake,
        // wet shade, collar data, body armor data, head rotation, etc.
        this.vanillaStateExtractor.extractRenderState(entity, baseState, partialTick);

        if (!(baseState instanceof WaterWolfRenderState state)) {
            return;
        }

        state.isSwimmingInWater = entity.isInWater() && !entity.onGround() && !entity.isInSittingPose();
        state.isUnderwater = entity.isUnderWater();
        state.bubbleShelterActive = entity.isBubbleShelterActive();
        state.swimCycle = entity.tickCount + partialTick;

        Vec3 movement = entity.getDeltaMovement();
        double horizontal = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
        state.swimAmount = state.isSwimmingInWater
                ? Mth.clamp((float) (horizontal * 5.0D + Math.abs(movement.y) * 3.0D), 0.35F, 1.0F)
                : 0.0F;
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
