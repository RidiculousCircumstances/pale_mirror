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
        var swept = actor.getBoundingBox().expandTowards(delta).deflate(0.000001D);
        if (!level.noCollision(actor, swept)) return java.util.Optional.of(Rejection.BLOCK_COLLISION);
        if (!level.getEntities(actor, swept,
                entity -> entity instanceof LivingEntity living && living.isAlive() && !living.isSpectator()).isEmpty())
            return java.util.Optional.of(Rejection.LIVING_BODY_COLLISION);
        int supportY = BlockPos.containing(centre.x, centre.y - 0.000001D, centre.z).getY();
        for (int x = (int) Math.floor(swept.minX); x <= (int) Math.floor(swept.maxX); x++)
            for (int z = (int) Math.floor(swept.minZ); z <= (int) Math.floor(swept.maxZ); z++) {
                var support = new BlockPos(x, supportY, z);
                if (!FrontierV3LoadedWorld.ready(level, support) || !level.getFluidState(support.above()).isEmpty())
                    return java.util.Optional.of(Rejection.UNKNOWN_OR_WET_FOOTPRINT);
            }
        // Vanilla may stop a wide body overhanging the apron. Each interpolated
        // footprint must retain real contact; its centre need not be above a block.
        for (int sample = 1; sample <= 4; sample++) {
            var point = actor.position().add(delta.scale(sample / 4.0D));
            var support = BlockPos.containing(point.x, centre.y - 0.000001D, point.z);
            if (!scope.permits(new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(support.getX(), support.getY(), support.getZ())))
                return java.util.Optional.of(Rejection.OUTSIDE_SCOPE);
            if (!FrontierV3BodyObservation.hasDryContact(level, actor.getBoundingBox().move(delta.scale(sample / 4.0D))))
                return java.util.Optional.of(Rejection.MISSING_SUPPORT);
        }
        return java.util.Optional.empty();
    }
}
