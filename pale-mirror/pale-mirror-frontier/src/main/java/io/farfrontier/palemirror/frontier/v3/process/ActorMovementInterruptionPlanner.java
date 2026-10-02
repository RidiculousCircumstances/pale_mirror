package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Movement owner separates mandatory access clearance from the optional remainder of a journey. */
final class ActorMovementInterruptionPlanner implements ActivityInterruptionPlanner {
    @Override public Assessment assess(FrontierWorldState state, SubjectId residentId, long atTick) {
        ActorMovement movement = state.actorMovements().get(residentId);
        if (movement == null) return new Ready(state, state, List.of());
        Optional<BodyPosition> checkpoint = checkpoint(state, movement, atTick);
        if (checkpoint.isEmpty()) return new Waiting(ActorExecutionCoordinator.sceneOwns(state, residentId)
                ? Reason.AUTHORITY_HANDOFF : Reason.SERVICE_CLEARANCE);
        ActorMovementInterrupted event = new ActorMovementInterrupted(residentId,
                movement.order().goalRevision(), atTick, checkpoint.orElseThrow());
        return new Ready(state, reduce(state, residentId, event), List.of(new ProposedEvent(residentId, event),
                new ProposedEvent(residentId, new ScheduleEffect.Cancelled(ActorMovementProcess.progress(
                        movement, Math.addExact(movement.issuedAtTick(), 1L)).id()))));
    }

    private static Optional<BodyPosition> checkpoint(FrontierWorldState state, ActorMovement movement, long atTick) {
        SubjectId actorId = movement.order().actorId();
        if (atTick < movement.issuedAtTick() || state.humanPopulation().meals().containsKey(actorId)
                || ActorExecutionCoordinator.sceneOwns(state, actorId)) return Optional.empty();
        // Admission may inspect an older parked activity wake after this route was committed.
        // That historical instant cannot authorize interrupting the newer travel checkpoint.
        if (movement.coldTravel().filter(travel -> atTick < travel.departedAtTick()).isPresent())
            return Optional.empty();
        // This typed context explicitly declares an optional personal journey after service.
        // Future mandatory movement kinds must supply their own interruption capability.
        if (!(movement.context() instanceof ActorMovementContext.ServiceExit exit)) return Optional.empty();
        AmbientActorLease lease = state.ambientLeases().get(actorId);
        if (lease != null && lease.status() != AmbientLeaseStatus.CLOSED
                && (lease.status() != AmbientLeaseStatus.HOT || lease.goal() != AmbientGoalKind.ACTOR_MOVEMENT
                    || !lease.goalBody().supportingSurface().equals(movement.order().legalStations().getFirst())))
            return Optional.empty();
        BodyPosition current = ActorMovementProcess.bodyAt(state, actorId, atTick);
        return ServiceAccessCoordinator.boundary(state, exit.depotId()).cleared(current)
                ? Optional.of(current) : Optional.empty();
    }

    static FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, ActorMovementInterrupted event) {
        ActorMovement movement = state.actorMovements().get(subject);
        if (!subject.equals(event.actorId()) || movement == null
                || movement.order().goalRevision() != event.goalRevision()
                || !checkpoint(state, movement, event.atTick()).equals(Optional.of(event.retainedBody())))
            throw new IllegalArgumentException("interruption has no exact safe movement checkpoint");
        var movements = new LinkedHashMap<>(state.actorMovements());
        movements.remove(subject);
        var actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(subject, actors.get(subject).withBody(event.retainedBody()));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorMovements(movements).actorLocations(actors));
    }
}
