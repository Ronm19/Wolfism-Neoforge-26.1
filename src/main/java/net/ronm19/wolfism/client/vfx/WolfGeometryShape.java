package net.ronm19.wolfism.client.vfx;

import com.lowdragmc.photon.client.gameobject.emitter.IParticleEmitter;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.IShape;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** One Photon emitter for an authored path. No per-sample objects or random lost endpoints. */
final class WolfGeometryShape implements IShape {
    private final List<Vec3> points;
    private final boolean outward;

    WolfGeometryShape(List<Vec3> points, boolean outward) {
        this.points = List.copyOf(points);
        this.outward = outward;
    }

    @Override
    public void nextPosVel(TileParticle particle, IParticleEmitter emitter, Vector3f position,
            Vector3f rotation, Vector3f scale) {
        int batch = particle.getParticleBatchIndex();
        int total = Math.max(1, particle.getParticleBatchCount());
        int index = total <= 1 ? 0 : Math.min(points.size() - 1,
                Math.round(batch * (points.size() - 1f) / (total - 1)));
        var point = points.isEmpty() ? Vec3.ZERO : points.get(index);
        var local = new Vector3f((float) point.x, (float) point.y, (float) point.z).mul(scale);
        particle.setSimPos(new Vector3f(local).add(position).add(particle.getSimPosWithoutNoise()), true);
        if (outward && local.lengthSquared() > .00001f) particle.setInternalVelocity(local.normalize().mul(.05f));
        else particle.setInternalVelocity(new Vector3f());
    }
}
