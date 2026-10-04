package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** One engineering step protocol for both construction and maintenance. */
final class EngineeringAssemblyProgress {
    private EngineeringAssemblyProgress() { }
    static SubjectId validate(FrontierWorldState state, EngineeringWorkOrder owner, EngineeringWorkAssembly next,
                              ActorExecutionGroup executions, Optional<EngineeringHotArrival> hot) {
        var current = owner == null ? null : owner.assembly().orElse(null);
        if (current == null || !current.members().keySet().equals(next.members().keySet()))
            throw new IllegalArgumentException("engineering step has no matching retained crew");
        executions.requireDeclaration(ActorActivityKind.ENGINEERING_ASSEMBLY, owner.id(), owner.engineeringTeam().orElseThrow().memberIds());
        executions.requireCurrent(state.actorExecutions());
        var moved = current.members().keySet().stream().filter(actor -> !current.members().get(actor).equals(next.members().get(actor)))
                .reduce((left, right) -> { throw new IllegalArgumentException("engineering step changes more than one member"); })
                .orElseThrow(() -> new IllegalArgumentException("engineering step advances no member"));
        if (!current.advance(moved).equals(next)) throw new IllegalArgumentException("engineering step changes its retained versioned route");
        var actor = state.actorLocations().get(moved);
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("engineering step advances a nonliving member");
        var lease = state.ambientLeases().get(moved);
        if (hot.isEmpty()) {
            if (!ActorExecutionCoordinator.coldAvailable(state, moved) || (lease != null && lease.status() != AmbientLeaseStatus.CLOSED))
                throw new IllegalArgumentException("COLD engineering cannot advance its physically held member");
            if (!actor.supportingSurface().equals(current.members().get(moved).currentSurface())
                    || !EngineeringJourneyKnowledge.openEdge(state, owner, moved))
                throw new IllegalArgumentException("COLD engineering step lacks its retained predecessor or known open edge");
        } else {
            var captured = hot.orElseThrow();
            var id = captured.actuation();
            if (!id.execution().actorId().equals(moved) || !executions.members().contains(id.execution())
                    || ActorBodyAuthority.require(state, id.body()).phase() != FencedRecoveryPhase.RUNNING
                    || lease == null || lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.ENGINEERING_ASSEMBLY
                    || lease.revision() != captured.leaseRevision() || !lease.goalBody().equals(next.members().get(moved).currentSurface().standingBody())
                    || !actor.body().equals(lease.goalBody()))
                throw new IllegalArgumentException("HOT engineering step lacks captured authority or independent body inspection");
        }
        return moved;
    }
}
