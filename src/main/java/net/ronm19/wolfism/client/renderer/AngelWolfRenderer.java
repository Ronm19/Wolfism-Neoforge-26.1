package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.model.AngelAdultWolfModel;
import net.ronm19.wolfism.client.model.AngelBabyWolfModel;
import net.ronm19.wolfism.client.model.WolfismAdultCollarModel;
import net.ronm19.wolfism.client.model.WolfismBabyCollarModel;
import net.ronm19.wolfism.client.renderer.layer.AngelWolfCollarLayer;
import net.ronm19.wolfism.client.renderer.layer.BeeWolfCollarLayer;
import net.ronm19.wolfism.client.renderer.state.AngelWolfRenderState;
import net.ronm19.wolfism.entity.custom.AngelWolf;

/** Renderer for the adult/baby Angel Wolf visual scaffold. */
public final class AngelWolfRenderer
        extends AgeableMobRenderer<AngelWolf, WolfRenderState, WolfModel> {

    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID,
            "textures/entity/wolf/angel_wolf.png");

    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID,
            "textures/entity/wolf/angel_wolf_baby.png");

    private final WolfRenderer vanillaStateExtractor;

    public AngelWolfRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new AngelAdultWolfModel(context.bakeLayer(AngelAdultWolfModel.LAYER_LOCATION)),
                new AngelBabyWolfModel(context.bakeLayer(AngelBabyWolfModel.LAYER_LOCATION)),
                0.65F);

        this.vanillaStateExtractor = new WolfRenderer(context);
        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new AngelWolfCollarLayer(this, new WolfismAdultCollarModel(context.bakeLayer(WolfismAdultCollarModel.LAYER_LOCATION)),
                new WolfismBabyCollarModel(context.bakeLayer(WolfismBabyCollarModel.LAYER_LOCATION))));
    }

    @Override
    public AngelWolfRenderState createRenderState() {
        return new AngelWolfRenderState();
    }

    @Override
    public void extractRenderState(
            AngelWolf entity,
            WolfRenderState baseState,
            float partialTick) {
        this.vanillaStateExtractor.extractRenderState(entity, baseState, partialTick);


        if (baseState instanceof AngelWolfRenderState state) {
            // Keep the phase continuous regardless of state changes so a
            // takeoff never restarts the cosine cycle from an arbitrary pose.
            state.flapTime = entity.tickCount + partialTick;
            state.flightBlend = state.isSitting
                    ? 0.0F
                    : entity.getFlightAnimationBlend(partialTick);
            state.isFlying = !state.isSitting && state.flightBlend > 0.001F;

            if (state.isFlying) {
                state.walkAnimationPos = 0.0F;
                state.walkAnimationSpeed = 0.0F;
            }
        }
    }

    @Override
    public Identifier getTextureLocation(WolfRenderState state) {
        return state.isBaby ? BABY_TEXTURE : ADULT_TEXTURE;
    }
}
