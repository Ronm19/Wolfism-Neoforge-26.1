package net.ronm19.wolfism.client.renderer;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.ronm19.wolfism.entity.custom.RiftPortalEntity;

/**
 * RiftPortalEntity is intentionally geometry-free in V1.
 *
 * <p>The server-authoritative entity owns transport/collision logic while the
 * portal itself is visualized with synchronized server particles. Keeping the
 * renderer empty makes the first gameplay pass low-risk. A textured translucent
 * oval can later be added here without changing RiftWolf or portal mechanics.</p>
 */
public final class RiftPortalRenderer
        extends EntityRenderer<RiftPortalEntity, EntityRenderState> {

    public RiftPortalRenderer( EntityRendererProvider.Context context) {super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
