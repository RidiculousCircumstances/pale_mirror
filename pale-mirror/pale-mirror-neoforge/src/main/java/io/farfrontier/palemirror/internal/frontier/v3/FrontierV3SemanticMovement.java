package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SemanticTraversalArrival;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Objects;
import java.util.Set;

/**
 * Minecraft provider for {@link SemanticTraversalArrival}.  It reports only exact collision
 * facts for a named retained support.  Local navigation remains an actuator concern and has no
 * arrival or cursor authority.
 */
final class FrontierV3SemanticMovement {
    private FrontierV3SemanticMovement() { }

    static SemanticTraversalArrival.Contract pedestrian(SurfaceAnchor surface) {
        return new SemanticTraversalArrival.Contract(surface, Set.of(TraversalCapability.PEDESTRIAN), 2);
    }

    static SemanticTraversalArrival.Disposition at(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        return SemanticTraversalArrival.evaluate(observe(level, worker, surface), pedestrian(surface));
    }

    static SemanticTraversalArrival.Disposition target(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        return SemanticTraversalArrival.target(targetObservation(level, worker, surface), pedestrian(surface));
    }

    static boolean arrived(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        if (at(level, worker, surface) != SemanticTraversalArrival.Disposition.ARRIVED) return false;
        // A supported feet cell is a position fact, not proof that a pedestrian has
        // cleared the preceding station. Stop only when its complete horizontal
        // footprint fits the goal column; otherwise a follower cannot enter the
        // vacated cell even though the leader has nominally "arrived".
        AABB footprint = worker.getBoundingBox();
        return footprint.minX >= surface.x() && footprint.maxX <= surface.x() + 1.0D
                && footprint.minZ >= surface.z() && footprint.maxZ <= surface.z() + 1.0D;
    }

    static boolean targetIsNavigable(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        return target(level, worker, surface) == SemanticTraversalArrival.Disposition.IN_PROGRESS;
    }

    /** Goal selection keeps pedestrians soft; shared traffic still forbids entering their bodies. */
    static boolean targetGeometryIsNavigable(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        BlockPos support = block(surface);
        return SemanticTraversalArrival.target(new SemanticTraversalArrival.Observation(surface,
                medium(level, support.above()), hasSupport(level, support), clearGeometry(level, worker, surface)),
                pedestrian(surface)) == SemanticTraversalArrival.Disposition.IN_PROGRESS;
    }

    /**
     * Reports a physical body that is still inside the explicit HOT latitude of one retained
     * edge.  This is deliberately separate from arrival: only {@link #arrived} can advance a
     * canonical cursor.  The observed support comes from Minecraft collision, while the
     * permitted set is derived solely from the immutable current/next canonical bodies.
     */
    static boolean withinRetainedEdgeEnvelope(ServerLevel level, Mob worker, SurfaceAnchor current, SurfaceAnchor next) {
        BlockPos observed = worker.getOnPos();
        return withinRetainedEdgeEnvelope(observed, current, next)
                && level.getFluidState(observed.above()).isEmpty();
    }

    /** Pure retained-support predicate used by the field-edge recurrence guard. */
    static boolean withinRetainedEdgeEnvelope(BlockPos observed, SurfaceAnchor current, SurfaceAnchor next) {
        LocalNavigationEnvelope envelope = LocalNavigationEnvelope.around(current.standingBody(), next.standingBody());
        return envelope.contains(new BlockPosition(observed.getX(), observed.getY(), observed.getZ()));
    }

    static String detail(SemanticTraversalArrival.Disposition disposition) {
        return switch (Objects.requireNonNull(disposition, "arrival disposition")) {
            case ARRIVED -> "arrived";
            case IN_PROGRESS -> "in-progress";
            case BLOCKED_SUPPORT -> "blocked-support";
            case BLOCKED_CLEARANCE -> "blocked-clearance";
            case BLOCKED_MEDIUM -> "blocked-undeclared-medium";
            case OFF_CONTRACT -> "off-contract";
        };
    }

    private static SemanticTraversalArrival.Observation observe(ServerLevel level, Mob worker, SurfaceAnchor expected) {
        var observed = FrontierV3BodyObservation.capture(worker);
        SurfaceAnchor actual = observed.position().supportingSurface();
        boolean exactSupport = observed.support().filter(expected::equals).isPresent();
        // `getOnPos` is Minecraft's collision-authoritative named support.  The volatile
        // onGround flag is reset during ordinary entity/restart hand-off ordering, so treating
        // that flag as a second arrival datum would reject an already exact support fact.
        // Arrival is an observation of this already-supported body, not admission of
        // a hypothetical body at the centre of the block. A passing neighbour cannot
        // undo arrival or turn a prepared station effect into a new movement order.
        // Actual displacement still fails exactSupport; real block/fluid changes still
        // fail clearance. Prospective target occupancy remains checked separately below.
        boolean actualClearance = exactSupport && hasSupport(level, block(expected))
                && level.getFluidState(block(expected).above()).isEmpty()
                && !level.getBlockCollisions(worker, worker.getBoundingBox()).iterator().hasNext();
        return new SemanticTraversalArrival.Observation(actual, medium(level, worker.blockPosition()),
                exactSupport, actualClearance);
    }

    private static SemanticTraversalArrival.Observation targetObservation(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        BlockPos support = block(surface);
        return new SemanticTraversalArrival.Observation(surface, medium(level, support.above()),
                hasSupport(level, support), clear(level, worker, surface));
    }

    private static boolean hasSupport(ServerLevel level, BlockPos support) {
        BlockState state = level.getBlockState(support);
        return !state.getCollisionShape(level, support).isEmpty() && level.getFluidState(support).isEmpty();
    }

    /** Uses the exact target body volume, so slabs and carpets are valid only when they really support this body. */
    private static boolean clear(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        if (!clearGeometry(level, worker, surface)) return false;
        AABB target = worker.getBoundingBox().move(point(level, surface).subtract(worker.position()));
        return level.getEntities(worker, target.inflate(0.001D),
                entity -> entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()).isEmpty();
    }

    private static boolean clearGeometry(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        BlockPos support = block(surface);
        if (!hasSupport(level, support) || !level.getFluidState(support.above()).isEmpty()) return false;
        Vec3 point = point(level, surface);
        AABB target = worker.getBoundingBox().move(point.subtract(worker.position()));
        // Terrain and hard collision remain mandatory even when a semantic goal is
        // occupied by a pedestrian that the shared traffic owner may ask to yield.
        return level.noCollision(worker, target);
    }

    private static SemanticTraversalArrival.Medium medium(ServerLevel level, BlockPos position) {
        if (level.getFluidState(position).isEmpty()) return SemanticTraversalArrival.Medium.AIR;
        if (level.getFluidState(position).is(net.minecraft.tags.FluidTags.WATER)) return SemanticTraversalArrival.Medium.WATER;
        if (level.getFluidState(position).is(net.minecraft.tags.FluidTags.LAVA)) return SemanticTraversalArrival.Medium.LAVA;
        return SemanticTraversalArrival.Medium.OTHER;
    }

    static Vec3 point(SurfaceAnchor surface) {
        return new Vec3(surface.x() + .5D, surface.y() + 1.0D, surface.z() + .5D);
    }

    /**
     * Returns the physical feet point for one exact semantic support.  The semantic anchor is
     * deliberately still the block coordinate; its physical top is a fact supplied by the
     * shared support provider, not a movement-side Y tolerance.  This matters for farmland,
     * slabs and other legitimate non-full collision shapes: a body standing on their real top
     * must be allowed to take the next retained horizontal edge instead of endlessly trying to
     * lift to the integer cell above the anchor.
     */
    static Vec3 point(ServerLevel level, SurfaceAnchor surface) {
        BlockPos support = block(surface);
        VoxelShape shape = level.getBlockState(support).getCollisionShape(level, support);
        if (shape.isEmpty()) return point(surface);
        return new Vec3(surface.x() + .5D, support.getY() + shape.max(net.minecraft.core.Direction.Axis.Y), surface.z() + .5D);
    }

    private static BlockPos block(SurfaceAnchor surface) { return new BlockPos(surface.x(), surface.y(), surface.z()); }
}
