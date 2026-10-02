package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Reprojects only a prepared, identity-verified body after its old executor surrendered to COLD. */
final class FrontierV3AmbientBodyHandoff {
    private FrontierV3AmbientBodyHandoff() { }
    static FrontierV3AmbientActorExecutor.Result place(ServerLevel level, FrontierWorldState state,
            SubjectId actor, Mob body, BodyPosition target,
            FrontierV3SceneBehaviorRegistry.StandingPositionProvider standing) {
        var lease = state.ambientLeases().get(actor);
        // Refreshing an already admitted body is not a new COLD -> HOT handoff.
        if (lease != null && lease.status() == AmbientLeaseStatus.HOT)
            return FrontierV3AmbientActorExecutor.Result.CURRENT;
        if (lease == null || lease.status() != AmbientLeaseStatus.PREPARED || !lease.handoffBody().equals(target))
            return FrontierV3AmbientActorExecutor.Result.CONFLICT;
        var surfaces = AmbientPlacementPolicy.candidates(state, lease);
        return placeDeclared(level, lease, body, target, surfaces, standing);
    }
    static FrontierV3AmbientActorExecutor.Result placeDeclared(ServerLevel level, AmbientActorLease lease,
            Mob body, BodyPosition target, java.util.List<SurfaceAnchor> surfaces,
            FrontierV3SceneBehaviorRegistry.StandingPositionProvider standing) {
        if (lease.status() == AmbientLeaseStatus.HOT) return FrontierV3AmbientActorExecutor.Result.CURRENT;
        if (lease.status() != AmbientLeaseStatus.PREPARED || !lease.handoffBody().equals(target)
                || surfaces.isEmpty() || !surfaces.getFirst().equals(target.supportingSurface()))
            return FrontierV3AmbientActorExecutor.Result.CONFLICT;
        FrontierV3GoalNavigation.stop(body);
        body.setNoAi(true);
        return FrontierV3BodyPlacement.placeRetained(level, body, surfaces,
                new FrontierV3NavigationScope.Restricted(LocalNavigationEnvelope.along(surfaces, surfaces)), standing::resolve)
                ? FrontierV3AmbientActorExecutor.Result.CURRENT : FrontierV3AmbientActorExecutor.Result.DEFERRED;
    }
}
