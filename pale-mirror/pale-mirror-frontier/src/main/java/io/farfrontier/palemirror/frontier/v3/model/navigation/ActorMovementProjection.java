package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;

/** Read-only as-of body projection from the sole retained movement and physical authority. */
public final class ActorMovementProjection {
    private static final ThreadLocal<KnownRouteBarrierIndex> BARRIERS = ThreadLocal.withInitial(KnownRouteBarrierIndex::new);
    private ActorMovementProjection() { }

    public static BodyPosition bodyAt(FrontierWorldState state, SubjectId actorId, long atTick) {
        ActorLocation actor = state.actorLocations().get(actorId);
        if (actor == null) throw new IllegalArgumentException("moving actor lacks canonical body");
        var physical = state.fencedRecovery().current().get(ActorBodyId.recoveryBindingId(actorId));
        if (physical != null && (physical.phase() == FencedRecoveryPhase.RUNNING
                || physical.phase() == FencedRecoveryPhase.AMBIGUOUS)) return actor.body();
        ActorMovement movement = state.actorMovements().get(actorId);
        if (movement != null && movement.coldTravel().isPresent()) {
            TimedKnownRoute travel = movement.coldTravel().orElseThrow();
            int index = travel.indexAt(Math.min(atTick, travel.arrivalTick() - 1L));
            int barrier = firstKnownBarrier(state, travel);
            if (barrier >= 0) index = Math.min(index, barrier - 1);
            return BodyPosition.above(travel.route().get(index));
        }
        return actor.body();
    }

    public static int firstKnownBarrier(FrontierWorldState state, TimedKnownRoute travel) {
        return BARRIERS.get().first(state.physicalDeltas(), travel.route());
    }
}
