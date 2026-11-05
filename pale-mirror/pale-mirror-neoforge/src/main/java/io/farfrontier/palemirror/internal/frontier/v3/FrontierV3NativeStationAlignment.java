package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Native endpoint alignment only. Never selects a route, teleports or acknowledges arrival. */
final class FrontierV3NativeStationAlignment {
    enum Rejection { NO_SUPPORTED_BODY, INVALID_TARGET, NO_ACCEPTED_ENDPOINT, ENDPOINT_NOT_REACHED,
        OUTSIDE_NATIVE_ENDPOINT, BLOCK_COLLISION, LIVING_BODY_COLLISION, UNKNOWN_OR_WET_FOOTPRINT,
        OUTSIDE_SCOPE, MISSING_SUPPORT }
    private FrontierV3NativeStationAlignment() { }

    static boolean safe(ServerLevel level, Mob actor, SurfaceAnchor target, Path accepted,
                        FrontierV3NavigationScope scope) {
        return rejection(level, actor, target, accepted, scope).isEmpty();
    }
    /** Exact failure evidence on demand; no timer, state mutation or physical acknowledgement. */
    static java.util.Optional<Rejection> rejection(ServerLevel level, Mob actor, SurfaceAnchor target, Path accepted,
                                                  FrontierV3NavigationScope scope) {
        if (FrontierV3BodyObservation.capture(actor).support().isEmpty()) return java.util.Optional.of(Rejection.NO_SUPPORTED_BODY);
        if (!FrontierV3SemanticMovement.targetIsNavigable(level, actor, target)) return java.util.Optional.of(Rejection.INVALID_TARGET);
        boolean alreadySupported = FrontierV3SemanticMovement.at(level, actor, target)
                == io.farfrontier.palemirror.frontier.v3.model.SemanticTraversalArrival.Disposition.ARRIVED;
        if (!alreadySupported && (accepted == null || !accepted.canReach() || accepted.getEndNode() == null
                || !accepted.getEndNode().asBlockPos().equals(new BlockPos(target.x(), target.y() + 1, target.z()))))
            return java.util.Optional.of(Rejection.NO_ACCEPTED_ENDPOINT);
        if (!alreadySupported && accepted.getNextNodeIndex() < accepted.getNodeCount() - 1)
            return java.util.Optional.of(Rejection.ENDPOINT_NOT_REACHED);
        Vec3 centre = FrontierV3SemanticMovement.point(level, target);
        Vec3 delta = centre.subtract(actor.position());
        // Vanilla offsets a node by floor(width + 1) / 2. A wide body can finish
        // in the adjacent support column: requiring exact support before steering
        // to the centre creates a circular arrival prerequisite.
        double offset = Math.floor(actor.getBbWidth() + 1.0D) / 2.0D - 0.5D;
        // PathNavigation advances its final node within maxDistanceToWaypoint,
        // not at that node's exact wanted point. Bound the bridge by BOTH native
        // width-derived values; no fixed near-goal radius grants arrival.
        double waypointTolerance = actor.getBbWidth() > 0.75F ? actor.getBbWidth() / 2.0D
                : 0.75D - actor.getBbWidth() / 2.0D;
        double latitude = offset + waypointTolerance + 0.125D;
        if (Math.abs(delta.x) > latitude || Math.abs(delta.z) > latitude || Math.abs(delta.y) > 0.125D)
            return java.util.Optional.of(Rejection.OUTSIDE_NATIVE_ENDPOINT);
        // A descending diagonal's enclosing box extends through the support
        // still under the old footprint (e.g. full block -> dirt path). Prove
        // supported contacts and the raised horizontal/vertical legs separately;
        // the ordinary native movement controller still performs every move.
        var previous = actor.getBoundingBox();
        for (int sample = 1; sample <= 4; sample++) {
            var point = actor.position().add(delta.scale(sample / 4.0D));
            var next = contactBounds(level, actor.getBoundingBox().move(point.subtract(actor.position())),
                    Math.min(actor.getY(), centre.y), Math.max(actor.getY(), centre.y));
            if (next.isEmpty()) return java.util.Optional.of(Rejection.MISSING_SUPPORT);
            var body = next.orElseThrow();
            double dy = body.minY - previous.minY;
            var raised = previous.move(0, Math.max(0, dy), 0);
            var highDestination = body.move(0, Math.max(0, -dy), 0);
            var legs = java.util.List.of(previous.expandTowards(0, Math.max(0, dy), 0),
                    raised.expandTowards(body.minX - previous.minX, 0, body.minZ - previous.minZ),
                    highDestination.expandTowards(0, Math.min(0, dy), 0));
            for (var leg : legs) {
                var swept = leg.deflate(0.000001D);
                if (!level.noBlockCollision(actor, swept)) return java.util.Optional.of(Rejection.BLOCK_COLLISION);
                if (!level.getEntities(actor, swept,
                        entity -> entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()).isEmpty())
                    return java.util.Optional.of(Rejection.LIVING_BODY_COLLISION);
                if (!level.noCollision(actor, swept)) return java.util.Optional.of(Rejection.BLOCK_COLLISION);
                int supportY = (int) Math.floor(body.minY - 0.000001D);
                for (int x = (int) Math.floor(swept.minX); x <= (int) Math.floor(swept.maxX); x++)
                    for (int z = (int) Math.floor(swept.minZ); z <= (int) Math.floor(swept.maxZ); z++) {
                        var support = new BlockPos(x, supportY, z);
                        if (!FrontierV3LoadedWorld.ready(level, support) || !level.getFluidState(support.above()).isEmpty())
                            return java.util.Optional.of(Rejection.UNKNOWN_OR_WET_FOOTPRINT);
                    }
            }
            // The route's hard scope names support columns; real body clearance
            // is checked above, including its permitted overhang at an edge.
            var support = BlockPos.containing(point.x, body.minY - 0.000001D, point.z);
            if (!scope.permits(new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(support.getX(), support.getY(), support.getZ())))
                return java.util.Optional.of(Rejection.OUTSIDE_SCOPE);
            previous = body;
        }
        return java.util.Optional.empty();
    }
    /** Highest real footprint contact within the already admitted small height bridge. */
    private static java.util.Optional<net.minecraft.world.phys.AABB> contactBounds(ServerLevel level,
            net.minecraft.world.phys.AABB bounds, double minimum, double maximum) {
        double top = Double.NEGATIVE_INFINITY;
        for (int x = (int) Math.floor(bounds.minX + 0.000001D); x <= (int) Math.floor(bounds.maxX - 0.000001D); x++)
            for (int z = (int) Math.floor(bounds.minZ + 0.000001D); z <= (int) Math.floor(bounds.maxZ - 0.000001D); z++)
                for (int y = (int) Math.floor(minimum - 0.000001D); y <= (int) Math.floor(maximum); y++) {
                    var pos = new BlockPos(x, y, z);
                    if (!FrontierV3LoadedWorld.ready(level, pos) || !level.getFluidState(pos).isEmpty()) return java.util.Optional.empty();
                    for (var box : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                        double candidate = y + box.maxY;
                        if (bounds.maxX > x + box.minX && bounds.minX < x + box.maxX
                                && bounds.maxZ > z + box.minZ && bounds.minZ < z + box.maxZ
                                && candidate >= minimum - 0.01D && candidate <= maximum + 0.01D) top = Math.max(top, candidate);
                    }
                }
        if (!Double.isFinite(top)) return java.util.Optional.empty();
        var supported = bounds.move(0, top - bounds.minY, 0);
        return FrontierV3BodyObservation.hasDryContact(level, supported) ? java.util.Optional.of(supported) : java.util.Optional.empty();
    }

}
