package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.LinkedHashMap;

/** Scope acknowledgement only. Common body authority alone records physical position. */
public final class AmbientBodyConfirmationProcess {
    private AmbientBodyConfirmationProcess() { }
    public static FrontierWorldState reduce(FrontierWorldState state, AmbientBodyConfirmed evidence) {
        AmbientActorLease lease = state.ambientLeases().get(evidence.actorId());
        ActorLocation actor = state.actorLocations().get(evidence.actorId());
        if (lease == null || lease.revision() != evidence.leaseRevision() || actor == null
                || actor.condition().status() != ActorLifeStatus.ALIVE
                || !actor.body().equals(evidence.observedBody())
                || ActorBodyAuthority.require(state, evidence.bodyId()).phase() != FencedRecoveryPhase.RUNNING)
            throw new IllegalArgumentException("scope acknowledgement lacks its independently inspected body or lease epoch");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), evidence.observedBody().supportingSurface().support());
        if (evidence.boundary() == AmbientBodyConfirmed.Boundary.ADMISSION) {
            if (lease.status() != AmbientLeaseStatus.PREPARED || !lease.handoffBody().equals(evidence.previousBody())
                    || !AmbientPlacementPolicy.candidates(state, lease).contains(evidence.observedBody().supportingSurface()))
                throw new IllegalArgumentException("body admission is outside its declared prepared placement zone");
        } else if (lease.status() != AmbientLeaseStatus.HOT || !serviceOccupancyChanged(state, evidence.actorId(),
                evidence.previousBody(), evidence.observedBody())) {
            throw new IllegalArgumentException("body checkpoint is not a current HOT service occupancy change");
        }
        FrontierWorldState next = state;
        if (evidence.boundary() != AmbientBodyConfirmed.Boundary.ADMISSION) return next;
        var leases = new LinkedHashMap<>(next.ambientLeases());
        leases.put(evidence.actorId(), new AmbientActorLease(lease.actorId(), evidence.observedBody(), lease.handoffInstant(),
                lease.revision(), lease.status(), lease.goal(), lease.goalBody()));
        next = next.withChanges(FrontierWorldStateUpdate.begin().ambientLeases(leases));
        next = AmbientPlacementPolicy.admitted(next, lease, evidence.observedBody());
        return AmbientLeaseStateProcess.transition(next, evidence.actorId(), AmbientLeaseStatus.HOT);
    }
    public static boolean serviceOccupancyChanged(FrontierWorldState state, SubjectId actorId,
                                                 BodyPosition previous, BodyPosition observed) {
        ResidentProfile resident = state.humanPopulation().resident(actorId);
        return resident != null && SettlementServiceAccessPoints.occupancyChanged(state, resident.settlementId(), previous, observed);
    }
}
