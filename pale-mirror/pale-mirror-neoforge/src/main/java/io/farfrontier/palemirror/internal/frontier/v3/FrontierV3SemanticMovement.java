package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SemanticTraversalArrival;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        return at(level, worker, surface) == SemanticTraversalArrival.Disposition.ARRIVED;
    }

    static boolean targetIsNavigable(ServerLevel level, Mob worker, SurfaceAnchor surface) {
        return target(level, worker, surface) == SemanticTraversalArrival.Disposition.IN_PROGRESS;
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
        BlockPos observedSupport = worker.getOnPos();
        SurfaceAnchor actual = SurfaceAnchor.at(observedSupport.getX(), observedSupport.getY(), observedSupport.getZ());
        boolean exactSupport = actual.equals(expected);
        // `getOnPos` is Minecraft's collision-authoritative named support.  The volatile
        // onGround flag is reset during ordinary entity/restart hand-off ordering, so treating
        // that flag as a second arrival datum would reject an already exact support fact.
        return new SemanticTraversalArrival.Observation(actual, medium(level, worker.blockPosition()),
                exactSupport && hasSupport(level, observedSupport), exactSupport && clear(level, worker, expected));
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
        BlockPos support = block(surface);
        if (!hasSupport(level, support) || !level.getFluidState(support.above()).isEmpty()) return false;
        Vec3 point = point(surface);
        AABB target = worker.getBoundingBox().move(point.subtract(worker.position()));
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

    private static BlockPos block(SurfaceAnchor surface) { return new BlockPos(surface.x(), surface.y(), surface.z()); }
}
