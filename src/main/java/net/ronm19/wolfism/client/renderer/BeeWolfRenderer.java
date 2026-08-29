package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.model.animal.wolf.WolfModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.WolfArmorLayer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.resources.Identifier;
import net.ronm19.wolfism.Wolfism;
import net.ronm19.wolfism.client.model.BeeAdultWolfModel;
import net.ronm19.wolfism.client.model.BeeBabyWolfModel;
import net.ronm19.wolfism.client.model.WolfismAdultCollarModel;
import net.ronm19.wolfism.client.model.WolfismBabyCollarModel;
import net.ronm19.wolfism.client.renderer.layer.BeeWolfCollarLayer;
import net.ronm19.wolfism.client.renderer.layer.PhantomWolfCollarLayer;
import net.ronm19.wolfism.client.renderer.state.BeeWolfRenderState;
import net.ronm19.wolfism.entity.custom.BeeWolf;

/** Dedicated 64x64 Bee Wolf renderer with vanilla wolf state/layers preserved. */
public final class BeeWolfRenderer extends AgeableMobRenderer<BeeWolf, WolfRenderState, WolfModel> {
    private static final Identifier ADULT_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/bee_wolf.png");
    private static final Identifier BABY_TEXTURE = Identifier.fromNamespaceAndPath(
            Wolfism.MOD_ID, "textures/entity/wolf/bee_wolf_baby.png");

    private final WolfRenderer vanillaStateExtractor;

    public BeeWolfRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new BeeAdultWolfModel(context.bakeLayer(BeeAdultWolfModel.LAYER_LOCATION)),
                new BeeBabyWolfModel(context.bakeLayer(BeeBabyWolfModel.LAYER_LOCATION)),
                0.5F);

        this.vanillaStateExtractor = new WolfRenderer(context);
        this.addLayer(new WolfArmorLayer(this, context.getModelSet(), context.getEquipmentRenderer()));
        this.addLayer(new BeeWolfCollarLayer(this, new WolfismAdultCollarModel(context.bakeLayer(WolfismAdultCollarModel.LAYER_LOCATION)),
                new WolfismBabyCollarModel(context.bakeLayer(WolfismBabyCollarModel.LAYER_LOCATION))));
    }

    @Override
    public BeeWolfRenderState createRenderState() {
        return new BeeWolfRenderState();
    }

    @Override
    public void extractRenderState(BeeWolf entity, WolfRenderState baseState, float partialTick) {
        this.vanillaStateExtractor.extractRenderState(entity, baseState, partialTick);
        if (baseState instanceof BeeWolfRenderState state) {
            state.onGround = entity.onGround();
            state.catchUpFlying = entity.isCatchUpFlying();
            if (state.catchUpFlying) {
                // Flight should look like Bee flight, not a wolf sprinting in
                // mid-air. Ground walking animation resumes automatically when
                // catch-up/defensive flight ends.
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
