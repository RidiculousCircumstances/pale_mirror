package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.Optional;

/** One conversion boundary for physical pedestrian poses, never a goal-arrival oracle. */
final class FrontierV3BodyObservation {
    private FrontierV3BodyObservation() { }

    static Observation capture(Entity body) {
        Objects.requireNonNull(body, "physical body");
        var support = body.getOnPos();
        if (body.level() instanceof ServerLevel level && level.hasChunkAt(support)
                && level.hasChunkAt(support.above())) {
            var collision = level.getBlockState(support).getCollisionShape(level, support);
            var bounds = body.getBoundingBox();
            var contactTop = collision.toAabbs().stream()
                    .filter(box -> bounds.maxX > support.getX() + box.minX && bounds.minX < support.getX() + box.maxX
                            && bounds.maxZ > support.getZ() + box.minZ && bounds.minZ < support.getZ() + box.maxZ)
                    .mapToDouble(box -> support.getY() + box.maxY).max();
            if (!collision.isEmpty() && level.getFluidState(support).isEmpty()
                    && level.getFluidState(support.above()).isEmpty()
                    && contactTop.isPresent() && Math.abs(bounds.minY - contactTop.orElseThrow()) <= 0.1D) {
                var surface = SurfaceAnchor.at(support.getX(), support.getY(), support.getZ());
                return new Observation(surface.standingBody(), Optional.of(surface));
            }
        }
        // A falling/swimming pose is a spatial cell, not evidence of a supporting block.
        return new Observation(new BodyPosition(body.getBlockX(), body.getBlockY(), body.getBlockZ()), Optional.empty());
    }

    static BodyPosition position(Entity body) { return capture(body).position(); }

    /** GroundPathNavigation requires current contact even for a newly created NoAI body. */
    static void refreshGroundContact(ServerLevel level, net.minecraft.world.entity.Mob body) {
        body.setOnGround(!level.noCollision(body, body.getBoundingBox().move(0.0D, -0.01D, 0.0D)));
    }

    record Observation(BodyPosition position, Optional<SurfaceAnchor> support) {
        Observation {
            Objects.requireNonNull(position, "observed body position");
            Objects.requireNonNull(support, "observed support");
            if (support.isPresent() && !support.orElseThrow().standingBody().equals(position))
                throw new IllegalArgumentException("observed body disagrees with collision support");
        }

        Optional<BodyPosition> supportedBody() { return support.map(SurfaceAnchor::standingBody); }
    }
}
